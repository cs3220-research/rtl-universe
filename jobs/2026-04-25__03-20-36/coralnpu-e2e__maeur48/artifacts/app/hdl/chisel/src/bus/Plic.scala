// Copyright 2026 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package bus

import chisel3._
import chisel3.util._

/** Platform-Level Interrupt Controller (PLIC).
  *
  * Implements a simplified RISC-V PLIC with the following register map
  * (all 32-bit accesses):
  *
  * {{{
  * 0x000000 + 4*src : priority[src]         (3-bit, source 0 unused)
  * 0x001000          : pending[0]            (bit per source)
  * 0x002000 + 4*ctx : enable[ctx][0]         (bit per source per context)
  * 0x200000 + ctx*0x1000 + 0 : threshold[ctx]
  * 0x200000 + ctx*0x1000 + 4 : claim/complete[ctx]
  * }}}
  *
  * @param nSources  Number of interrupt sources (source 0 is reserved/unused).
  * @param nTargets  Number of interrupt targets (contexts, one per HART*privilege).
  */
class Plic(nSources: Int = 32, nTargets: Int = 1) extends Module {
  require(nSources >= 1 && nSources <= 1023)
  require(nTargets >= 1)

  val tlulP = new TLULParameters(new coralnpu.Parameters {
    lsuDataBits = 32
  })

  val io = IO(new Bundle {
    val tl  = new OpenTitanTileLink.Device2Host(tlulP)
    val irq = Input(Vec(nSources, Bool()))
    val eip = Output(Vec(nTargets, Bool()))
  })

  // -------------------------------------------------------------------------
  // PLIC registers
  // -------------------------------------------------------------------------
  val priority  = RegInit(VecInit(Seq.fill(nSources)(0.U(3.W))))
  val enable    = RegInit(VecInit(Seq.fill(nTargets)(VecInit(Seq.fill(nSources)(false.B)))))
  val threshold = RegInit(VecInit(Seq.fill(nTargets)(0.U(3.W))))
  val claimed   = RegInit(VecInit(Seq.fill(nSources)(false.B)))

  // Pending: set by gateway, cleared on claim
  val pending = RegInit(VecInit(Seq.fill(nSources)(false.B)))

  // Level-triggered gateways: pending set when irq high and not claimed
  for (s <- 1 until nSources) {
    when(io.irq(s) && !claimed(s)) {
      pending(s) := true.B
    }
  }

  // -------------------------------------------------------------------------
  // Priority arbitration — for each target find highest-priority pending source
  // -------------------------------------------------------------------------
  val bestId   = Wire(Vec(nTargets, UInt(log2Ceil(nSources + 1).W)))
  val bestPrio = Wire(Vec(nTargets, UInt(3.W)))

  for (t <- 0 until nTargets) {
    bestId(t)   := 0.U
    bestPrio(t) := 0.U
    for (s <- 1 until nSources) {
      when(pending(s) && enable(t)(s) && (priority(s) > bestPrio(t))) {
        bestId(t)   := s.U
        bestPrio(t) := priority(s)
      }
    }
    // EIP: interrupt available if best priority exceeds target threshold
    io.eip(t) := (bestPrio(t) > threshold(t))
  }

  // -------------------------------------------------------------------------
  // TL-UL register interface
  // -------------------------------------------------------------------------
  val aFire  = io.tl.a.valid && io.tl.d.ready
  val aAddr  = io.tl.a.bits.address
  val aData  = io.tl.a.bits.data
  val aWrite = (io.tl.a.bits.opcode === TLULOpcodesA.PutFullData.asUInt ||
                io.tl.a.bits.opcode === TLULOpcodesA.PutPartialData.asUInt)

  val rdata = Wire(UInt(32.W))
  rdata := 0.U
  val error = Wire(Bool())
  error := false.B

  when(aFire) {
    // Priority registers: 0x000000..0x000FFC
    val inPriority = (aAddr < (nSources * 4).U)
    // Pending registers: 0x001000
    val inPending  = (aAddr(23, 12) === 0x001.U)
    // Enable registers:  0x002000..0x002FFF
    val inEnable   = (aAddr(23, 12) === 0x002.U)
    // Threshold / claim: 0x200000..
    val inCtx      = (aAddr(23) === 1.U)

    when(inPriority) {
      val src = aAddr(11, 2)
      when(aWrite) {
        when(src > 0.U && src < nSources.U) {
          priority(src(log2Ceil(nSources) - 1, 0)) := aData(2, 0)
        }
      }.otherwise {
        rdata := Mux(src < nSources.U, priority(src(log2Ceil(nSources) - 1, 0)), 0.U)
      }
    }.elsewhen(inPending) {
      // Read-only: return pending bits packed into 32-bit words
      val word = aAddr(4, 2)
      var packed = 0.U(32.W)
      for (s <- 1 until nSources) {
        if (s / 32 == 0) packed = packed | (pending(s) << s)
      }
      rdata := packed
    }.elsewhen(inEnable) {
      val ctx   = aAddr(9, 7)
      val word  = aAddr(6, 2)
      when(aWrite) {
        when(ctx < nTargets.U) {
          for (s <- 1 until nSources) {
            val bit = s % 32
            val w   = s / 32
            when(word === w.U) {
              enable(ctx(log2Ceil(nTargets) - 1, 0))(s) := aData(bit)
            }
          }
        }
      }.otherwise {
        var packed = 0.U(32.W)
        when(ctx < nTargets.U) {
          for (s <- 1 until nSources) {
            val bit = s % 32
            val w   = s / 32
            when(word === w.U) {
              packed = packed | (enable(ctx(log2Ceil(nTargets) - 1, 0))(s) << bit)
            }
          }
        }
        rdata := packed
      }
    }.elsewhen(inCtx) {
      val ctx     = aAddr(14, 12)
      val isThres = (aAddr(3, 2) === 0.U)
      val isClaim = (aAddr(3, 2) === 1.U)
      when(ctx < nTargets.U) {
        val t = ctx(log2Ceil(nTargets) - 1, 0)
        when(isThres) {
          when(aWrite) {
            threshold(t) := aData(2, 0)
          }.otherwise {
            rdata := threshold(t)
          }
        }.elsewhen(isClaim) {
          when(aWrite) {
            // Complete: clear claim and pending for the source
            val src = aData(log2Ceil(nSources), 0)
            when(src > 0.U && src < nSources.U) {
              claimed(src(log2Ceil(nSources) - 1, 0)) := false.B
              pending(src(log2Ceil(nSources) - 1, 0)) := false.B
            }
          }.otherwise {
            // Claim: return and mark as claimed
            val id = bestId(t)
            rdata := id
            when(id > 0.U) {
              claimed(id(log2Ceil(nSources) - 1, 0)) := true.B
              pending(id(log2Ceil(nSources) - 1, 0)) := false.B
            }
          }
        }
      }
    }.otherwise {
      error := true.B
    }
  }

  // -------------------------------------------------------------------------
  // Response pipeline
  // -------------------------------------------------------------------------
  val rdataReg = RegNext(rdata)
  val errorReg = RegNext(error)
  val srcReg   = RegNext(io.tl.a.bits.source)
  val sizeReg  = RegNext(io.tl.a.bits.size)
  val isGetReg = RegNext(!aWrite)
  val validReg = RegNext(aFire, false.B)

  io.tl.a.ready := io.tl.d.ready
  io.tl.d.valid := validReg

  io.tl.d.bits.opcode  := Mux(isGetReg, TLULOpcodesD.AccessAckData.asUInt, TLULOpcodesD.AccessAck.asUInt)
  io.tl.d.bits.param   := 0.U
  io.tl.d.bits.size    := sizeReg
  io.tl.d.bits.source  := srcReg
  io.tl.d.bits.sink    := 0.U
  io.tl.d.bits.data    := rdataReg
  io.tl.d.bits.user    := 0.U.asTypeOf(new OpenTitanTileLink_D_User)
  io.tl.d.bits.error   := errorReg
  io.tl.d.bits.corrupt := false.B
}
