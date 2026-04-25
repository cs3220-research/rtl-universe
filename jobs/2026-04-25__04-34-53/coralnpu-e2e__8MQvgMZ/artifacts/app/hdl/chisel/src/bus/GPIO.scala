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

/** GPIO peripheral parameters. */
case class GPIOParameters(width: Int = 8)

/** General-Purpose I/O peripheral with a TL-UL register interface.
  *
  * Register map (byte addresses):
  *   0x00 : DATA_IN  – current pin input values (read-only)
  *   0x04 : DATA_OUT – output data register (read/write)
  *   0x08 : OUT_EN   – output-enable register (read/write; 1 = output)
  *
  * @param p  coralnpu system parameters
  * @param gp GPIO-specific parameters
  */
class GPIO(p: coralnpu.Parameters, gp: GPIOParameters) extends Module {
  private val tlP = TLULParameters(p)

  val io = IO(new Bundle {
    val tl        = Flipped(new OpenTitanTileLink.Host2Device(tlP))
    val gpio_o    = Output(UInt(gp.width.W))
    val gpio_en_o = Output(UInt(gp.width.W))
    val gpio_i    = Input(UInt(gp.width.W))
  })

  // Output registers.
  val dataOut = RegInit(0.U(gp.width.W))
  val outEn   = RegInit(0.U(gp.width.W))

  io.gpio_o    := dataOut
  io.gpio_en_o := outEn

  // ---------------------------------------------------------------------------
  // TL-UL interface
  // ---------------------------------------------------------------------------
  io.tl.a.ready := true.B

  val respValid = RegInit(false.B)
  val respData  = RegInit(0.U(tlP.dataWidth.W))
  val respSrc   = RegInit(0.U(tlP.sourceWidth.W))
  val respOp    = RegInit(TLULOpcodesD.AccessAck)
  val respSz    = RegInit(0.U(tlP.sizeWidth.W))

  when(io.tl.a.fire) {
    respValid := true.B
    respSrc   := io.tl.a.bits.source
    respSz    := io.tl.a.bits.size
    val addr  = io.tl.a.bits.address(7, 0)

    when(io.tl.a.bits.opcode =/= TLULOpcodesA.Get) {
      // Write
      when(addr === 0x04.U) { dataOut := io.tl.a.bits.data(gp.width - 1, 0) }
      when(addr === 0x08.U) { outEn   := io.tl.a.bits.data(gp.width - 1, 0) }
      respOp   := TLULOpcodesD.AccessAck
      respData := 0.U
    } .otherwise {
      // Read
      respOp := TLULOpcodesD.AccessAckData
      respData := MuxLookup(addr, 0.U)(Seq(
        0x00.U -> io.gpio_i,
        0x04.U -> dataOut,
        0x08.U -> outEn
      ))
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
  io.tl.d.bits.error   := false.B
}
