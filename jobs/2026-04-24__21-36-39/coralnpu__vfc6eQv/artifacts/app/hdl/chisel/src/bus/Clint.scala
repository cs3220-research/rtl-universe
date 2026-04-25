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
  * Core-Level Interrupt Controller (CLINT).
  *
  * Generates machine software (msip) and machine timer (mtip) interrupts.
  * Compatible with the RISC-V CLINT specification.
  *
  * Register map (byte addresses):
  *   0x0000 : msip      (r/w, 32-bit) - machine software interrupt pending
  *   0x4000 : mtimecmp  (r/w, 64-bit) - timer compare register
  *   0xbff8 : mtime     (r/w, 64-bit) - machine timer
  */
class Clint(p: Parameters) extends Module {
  val tlp = new TLULParameters(p)

  val io = IO(new Bundle {
    val tl   = Flipped(new OpenTitanTileLink.Host2Device(tlp))
    val msip = Output(Bool())
    val mtip = Output(Bool())
  })

  // CLINT registers
  val msipReg     = RegInit(0.U(32.W))
  val mtimecmpReg = RegInit(((BigInt(1) << 64) - 1).U(64.W))  // max value = no interrupt
  val mtimeReg    = RegInit(0.U(64.W))

  // Timer increment every cycle
  mtimeReg := mtimeReg + 1.U

  // Interrupts
  io.msip := msipReg(0)
  io.mtip := mtimeReg >= mtimecmpReg

  // TL-UL slave state machine
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

        when(isGet) {
          respOpcode := TLULOpcodesD.AccessAckData
          when(addr === 0x0000.U) {
            respData := msipReg
          }.elsewhen(addr === 0x4000.U) {
            respData := mtimecmpReg(tlp.dataBits - 1, 0)
          }.elsewhen(addr === 0xbff8.U) {
            respData := mtimeReg(tlp.dataBits - 1, 0)
          }.otherwise {
            respError := true.B
          }
        }.elsewhen(isWrite) {
          respOpcode := TLULOpcodesD.AccessAck
          when(addr === 0x0000.U) {
            msipReg := io.tl.a.bits.data(31, 0)
          }.elsewhen(addr === 0x4000.U) {
            mtimecmpReg := io.tl.a.bits.data(63.min(tlp.dataBits - 1), 0)
          }.elsewhen(addr === 0xbff8.U) {
            mtimeReg := io.tl.a.bits.data(63.min(tlp.dataBits - 1), 0)
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
