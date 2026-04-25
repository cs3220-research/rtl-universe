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

/** GPIO controller configuration.
  *
  * @param width  Number of GPIO pins.
  */
case class GPIOParameters(width: Int) {
  require(width >= 1 && width <= 32)
  // Legacy compatibility: expose width as nPins
  val nPins: Int = width
}

/** GPIO controller with TileLink-UL register interface.
  *
  * Register map (32-bit word accesses):
  * {{{
  * 0x00 : DATA_IN   (R)   — current state of input pins
  * 0x04 : DATA_OUT  (R/W) — output data register
  * 0x08 : OUT_EN    (R/W) — output enable (1 = drive, 0 = high-Z)
  * }}}
  *
  * @param p   Project-wide parameters.
  * @param gp  GPIO-specific parameters.
  */
class GPIO(p: coralnpu.Parameters, gp: GPIOParameters) extends Module {
  val tlulP = new TLULParameters(new coralnpu.Parameters {
    lsuDataBits = 32
  })

  val io = IO(new Bundle {
    val tl       = new OpenTitanTileLink.Device2Host(tlulP)
    val gpio_i   = Input(UInt(gp.nPins.W))
    val gpio_o   = Output(UInt(gp.nPins.W))
    val gpio_en_o = Output(UInt(gp.nPins.W))
  })

  // -------------------------------------------------------------------------
  // GPIO registers
  // -------------------------------------------------------------------------
  val dataOut = RegInit(0.U(gp.nPins.W))
  val outEn   = RegInit(0.U(gp.nPins.W))

  io.gpio_o    := dataOut
  io.gpio_en_o := outEn

  // -------------------------------------------------------------------------
  // TL-UL register interface
  // -------------------------------------------------------------------------
  val aFire  = io.tl.a.valid && io.tl.d.ready
  val aAddr  = io.tl.a.bits.address(7, 0)
  val aData  = io.tl.a.bits.data
  val aWrite = (io.tl.a.bits.opcode === TLULOpcodesA.PutFullData.asUInt ||
                io.tl.a.bits.opcode === TLULOpcodesA.PutPartialData.asUInt)

  val rdata = Wire(UInt(32.W))
  rdata := 0.U
  val error = Wire(Bool())
  error := false.B

  when(aFire) {
    when(aAddr === 0x00.U) {
      when(aWrite) {
        // DATA_IN is read-only; writes return error
        error := true.B
      }.otherwise {
        rdata := io.gpio_i.pad(32)
      }
    }.elsewhen(aAddr === 0x04.U) {
      when(aWrite) {
        dataOut := aData(gp.nPins - 1, 0)
      }.otherwise {
        rdata := dataOut.pad(32)
      }
    }.elsewhen(aAddr === 0x08.U) {
      when(aWrite) {
        outEn := aData(gp.nPins - 1, 0)
      }.otherwise {
        rdata := outEn.pad(32)
      }
    }.otherwise {
      error := true.B
    }
  }

  // -------------------------------------------------------------------------
  // Response pipeline (1-cycle latency)
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
