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

/** Core-Local Interrupt Controller (CLINT).
  *
  * Implements the standard RISC-V CLINT memory-mapped register block:
  *
  * {{{
  * 0x0000 + 4*i  : msip[i]      (software interrupt pending, 1 bit per core)
  * 0x4000 + 8*i  : mtimecmp[i]  (64-bit machine timer compare, per core)
  * 0xBFF8        : mtime_lo     (lower 32 bits of mtime counter)
  * 0xBFFC        : mtime_hi     (upper 32 bits of mtime counter)
  * }}}
  *
  * The TL-UL interface uses 32-bit accesses.  64-bit aligned accesses are
  * split into two 32-bit operations by the host.
  *
  * @param addrBits  Address width of the TL-UL network.
  * @param numCores  Number of hardware threads (HARTs) supported.
  */
class Clint(addrBits: Int = 32, numCores: Int = 1) extends Module {
  val tlulP = new TLULParameters(new coralnpu.Parameters {
    lsuDataBits = 32
  })

  val io = IO(new Bundle {
    val tl           = new OpenTitanTileLink.Device2Host(tlulP)
    val msip         = Output(Vec(numCores, Bool()))
    val mtime        = Output(UInt(64.W))
    val mtimecmp_hit = Output(Vec(numCores, Bool()))
  })

  // -------------------------------------------------------------------------
  // Registers
  // -------------------------------------------------------------------------
  val msip      = RegInit(VecInit(Seq.fill(numCores)(false.B)))
  val mtime     = RegInit(0.U(64.W))
  val mtimecmp  = RegInit(VecInit(Seq.fill(numCores)(~0.U(64.W)))) // default: max value

  // Free-running mtime counter
  mtime := mtime + 1.U

  // -------------------------------------------------------------------------
  // Outputs
  // -------------------------------------------------------------------------
  io.msip  := msip
  io.mtime := mtime
  for (i <- 0 until numCores) {
    io.mtimecmp_hit(i) := (mtime >= mtimecmp(i))
  }

  // -------------------------------------------------------------------------
  // TL-UL slave logic
  // -------------------------------------------------------------------------
  // Simple single-cycle response
  val aFire  = io.tl.a.valid && io.tl.d.ready
  val aAddr  = io.tl.a.bits.address(15, 0)  // only bottom 16 bits for decode
  val aData  = io.tl.a.bits.data
  val aWrite = (io.tl.a.bits.opcode === TLULOpcodesA.PutFullData.asUInt ||
                io.tl.a.bits.opcode === TLULOpcodesA.PutPartialData.asUInt)

  val rdata = Wire(UInt(32.W))
  rdata := 0.U
  val error = Wire(Bool())
  error := false.B

  when(aFire) {
    // Decode address
    val inMsip      = (aAddr < (numCores * 4).U)
    val inMtimecmp  = (aAddr >= 0x4000.U && aAddr < (0x4000 + numCores * 8).U)
    val inMtimeLo   = (aAddr === 0xBFF8.U)
    val inMtimeHi   = (aAddr === 0xBFFC.U)

    when(inMsip) {
      val idx = aAddr >> 2
      when(aWrite) {
        when(idx < numCores.U) {
          msip(idx(log2Ceil(numCores) - 1, 0)) := aData(0)
        }
      }.otherwise {
        rdata := Mux(idx < numCores.U, msip(idx(log2Ceil(numCores) - 1, 0)), 0.U)
      }
    }.elsewhen(inMtimecmp) {
      val base   = aAddr - 0x4000.U
      val idx    = base >> 3
      val isHigh = base(2)
      when(idx < numCores.U) {
        val i = idx(log2Ceil(numCores) - 1, 0)
        when(aWrite) {
          when(isHigh) {
            mtimecmp(i) := Cat(aData(31, 0), mtimecmp(i)(31, 0))
          }.otherwise {
            mtimecmp(i) := Cat(mtimecmp(i)(63, 32), aData(31, 0))
          }
        }.otherwise {
          rdata := Mux(isHigh, mtimecmp(i)(63, 32), mtimecmp(i)(31, 0))
        }
      }
    }.elsewhen(inMtimeLo) {
      when(aWrite) {
        mtime := Cat(mtime(63, 32), aData(31, 0))
      }.otherwise {
        rdata := mtime(31, 0)
      }
    }.elsewhen(inMtimeHi) {
      when(aWrite) {
        mtime := Cat(aData(31, 0), mtime(31, 0))
      }.otherwise {
        rdata := mtime(63, 32)
      }
    }.otherwise {
      error := true.B
    }
  }

  // -------------------------------------------------------------------------
  // Response
  // -------------------------------------------------------------------------
  val rdataReg  = RegNext(rdata)
  val errorReg  = RegNext(error)
  val srcReg    = RegNext(io.tl.a.bits.source)
  val sizeReg   = RegNext(io.tl.a.bits.size)
  val isGetReg  = RegNext(!aWrite)
  val validReg  = RegNext(aFire, false.B)

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
