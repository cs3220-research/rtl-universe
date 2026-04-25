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
import coralnpu.Parameters

/**
  * Platform-Level Interrupt Controller (PLIC).
  *
  * Manages interrupt sources and arbitrates to produce an external interrupt
  * pending (eip) signal for a single hart.
  *
  * Register map:
  *   0x0000..0x0ffc : Priority registers (one per source, 32-bit)
  *   0x1000..0x107c : Interrupt pending bits (32 sources per word)
  *   0x2000..0x20fc : Interrupt enable bits (32 sources per word)
  *   0x200000       : Priority threshold
  *   0x200004       : Claim/complete register
  *
  * @param p        coralnpu Parameters
  * @param nSources Number of interrupt sources (default 32)
  */
class Plic(p: Parameters, nSources: Int = 32) extends Module {
  val tlp = new TLULParameters(p)

  val io = IO(new Bundle {
    val tl  = Flipped(new OpenTitanTileLink.Host2Device(tlp))
    val irq = Input(Vec(nSources, Bool()))
    val eip = Output(Bool())
  })

  // Registers
  val priorityReg   = RegInit(VecInit(Seq.fill(nSources)(0.U(32.W))))
  val enableReg     = RegInit(0.U(nSources.W))
  val thresholdReg  = RegInit(0.U(32.W))
  val pendingReg    = RegInit(0.U(nSources.W))
  val claimedReg    = RegInit(0.U(nSources.W))

  // Update pending: set on irq edge, clear on claim
  val irqVec = Cat(io.irq.reverse)
  pendingReg := (pendingReg | irqVec) & ~claimedReg
  claimedReg := 0.U  // auto-clear

  // EIP: any enabled pending interrupt with priority > threshold
  val activeBits = Wire(Vec(nSources, Bool()))
  for (i <- 0 until nSources) {
    activeBits(i) := pendingReg(i) && enableReg(i) && (priorityReg(i) > thresholdReg)
  }
  io.eip := activeBits.reduce(_ || _)

  // TL-UL slave
  val sIdle :: sResp :: Nil = Enum(2)
  val state = RegInit(sIdle)

  val respData   = RegInit(0.U(tlp.dataBits.W))
  val respError  = RegInit(false.B)
  val respSource = RegInit(0.U(tlp.sourceBits.W))
  val respSize   = RegInit(0.U(tlp.sizeBits.W))
  val respOpcode = RegInit(TLULOpcodesD.AccessAck)

  io.tl.a.ready := false.B
  io.tl.d.valid := false.B
  io.tl.d.bits  := 0.U.asTypeOf(new TLULChannelD(tlp))

  switch(state) {
    is(sIdle) {
      io.tl.a.ready := true.B
      when(io.tl.a.valid) {
        val addr    = io.tl.a.bits.address
        val isWrite = (io.tl.a.bits.opcode === TLULOpcodesA.PutFullData) ||
                      (io.tl.a.bits.opcode === TLULOpcodesA.PutPartialData)
        val isGet   = io.tl.a.bits.opcode === TLULOpcodesA.Get

        respSource := io.tl.a.bits.source
        respSize   := io.tl.a.bits.size
        respError  := false.B
        respData   := 0.U

        // Priority register range: 0x0000..0x0ffc
        val inPriority = addr < (nSources * 4).U
        val prioIdx    = addr(log2Ceil(nSources) + 1, 2)

        // Pending bits: 0x1000
        val inPending  = addr === 0x1000.U

        // Enable bits: 0x2000
        val inEnable   = addr === 0x2000.U

        // Threshold: 0x200000
        val inThreshold = addr === 0x200000.U

        // Claim/complete: 0x200004
        val inClaim    = addr === 0x200004.U

        when(isGet) {
          respOpcode := TLULOpcodesD.AccessAckData
          when(inPriority) {
            respData := priorityReg(prioIdx)
          }.elsewhen(inPending) {
            respData := pendingReg
          }.elsewhen(inEnable) {
            respData := enableReg
          }.elsewhen(inThreshold) {
            respData := thresholdReg
          }.elsewhen(inClaim) {
            // Find highest-priority pending+enabled interrupt
            val claimId = Wire(UInt(log2Ceil(nSources + 1).W))
            claimId := 0.U
            for (i <- nSources - 1 to 0 by -1) {
              when(activeBits(i)) { claimId := (i + 1).U }
            }
            respData := claimId
            when(claimId > 0.U) {
              claimedReg := 1.U << (claimId - 1.U)
            }
          }.otherwise {
            respError := true.B
          }
        }.elsewhen(isWrite) {
          respOpcode := TLULOpcodesD.AccessAck
          when(inPriority) {
            priorityReg(prioIdx) := io.tl.a.bits.data(31, 0)
          }.elsewhen(inEnable) {
            enableReg := io.tl.a.bits.data(nSources - 1, 0)
          }.elsewhen(inThreshold) {
            thresholdReg := io.tl.a.bits.data(31, 0)
          }.elsewhen(inClaim) {
            // Complete: clear the pending bit for the completed interrupt
            val completeId = io.tl.a.bits.data(log2Ceil(nSources), 0)
            when(completeId > 0.U && completeId <= nSources.U) {
              pendingReg := pendingReg & ~(1.U << (completeId - 1.U))
            }
          }.otherwise {
            respError := true.B
          }
        }.otherwise {
          respOpcode := TLULOpcodesD.AccessAck
          respError  := true.B
        }

        state := sResp
      }
    }

    is(sResp) {
      io.tl.d.valid        := true.B
      io.tl.d.bits.opcode  := respOpcode
      io.tl.d.bits.param   := 0.U
      io.tl.d.bits.size    := respSize
      io.tl.d.bits.source  := respSource
      io.tl.d.bits.sink    := 0.U
      io.tl.d.bits.denied  := false.B
      io.tl.d.bits.data    := respData
      io.tl.d.bits.corrupt := false.B
      io.tl.d.bits.error   := respError

      when(io.tl.d.ready) {
        state := sIdle
      }
    }
  }
}
