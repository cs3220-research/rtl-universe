// Copyright 2025 Google LLC
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

/** RISC-V CLINT (Core Local Interruptor).
  *
  * Implements the minimal CLINT register map:
  *   0x0000 : MSIP (machine software interrupt pending)
  *   0x4000 : MTIMECMP (64-bit machine timer compare)
  *   0xBFF8 : MTIME (64-bit machine timer)
  *
  * @param p TL-UL parameters
  */
class Clint(p: TLULParameters) extends Module {
  val io = IO(new Bundle {
    val tl        = Flipped(new TLBundleUL(p))
    val timer_irq = Output(Bool())
    val sw_irq    = Output(Bool())
  })

  // Internal registers.
  val mtime    = RegInit(0.U(64.W))
  val mtimecmp = RegInit(~0.U(64.W))
  val msip     = RegInit(false.B)

  // Increment timer every cycle.
  mtime := mtime + 1.U

  io.timer_irq := mtime >= mtimecmp
  io.sw_irq    := msip

  // ---------------------------------------------------------------------------
  // TL-UL interface (simplified: single in-flight, one-cycle response)
  // ---------------------------------------------------------------------------
  io.tl.a.ready := true.B

  val respValid = RegInit(false.B)
  val respData  = RegInit(0.U(p.dataWidth.W))
  val respSrc   = RegInit(0.U(p.sourceWidth.W))
  val respOp    = RegInit(TLULOpcodesD.AccessAck)
  val respSz    = RegInit(0.U(p.sizeWidth.W))
  val respErr   = RegInit(false.B)

  when(io.tl.a.fire) {
    val addr = io.tl.a.bits.address
    respValid := true.B
    respSrc   := io.tl.a.bits.source
    respSz    := io.tl.a.bits.size
    respErr   := false.B

    when(io.tl.a.bits.opcode === TLULOpcodesA.Get) {
      respOp := TLULOpcodesD.AccessAckData
      respData := MuxLookup(addr(15, 0), 0.U)(Seq(
        0x0000.U -> msip.asUInt,
        0x4000.U -> mtime(31, 0),
        0x4004.U -> mtime(63, 32),
        0xBFF8.U -> mtimecmp(31, 0),
        0xBFFC.U -> mtimecmp(63, 32)
      ))
    } .otherwise {
      respOp := TLULOpcodesD.AccessAck
      respData := 0.U
      when(addr(15, 0) === 0x0000.U) {
        msip := io.tl.a.bits.data(0)
      }
      when(addr(15, 0) === 0x4000.U) {
        mtimecmp := Cat(mtimecmp(63, 32), io.tl.a.bits.data(31, 0))
      }
      when(addr(15, 0) === 0x4004.U) {
        mtimecmp := Cat(io.tl.a.bits.data(31, 0), mtimecmp(31, 0))
      }
    }
  }
  when(io.tl.d.fire) { respValid := false.B }

  io.tl.d.valid        := respValid
  io.tl.d.bits.opcode  := respOp
  io.tl.d.bits.param   := 0.U
  io.tl.d.bits.size    := respSz
  io.tl.d.bits.source  := respSrc
  io.tl.d.bits.sink    := 0.U
  io.tl.d.bits.denied  := false.B
  io.tl.d.bits.data    := respData
  io.tl.d.bits.corrupt := false.B
  io.tl.d.bits.error   := respErr
}
