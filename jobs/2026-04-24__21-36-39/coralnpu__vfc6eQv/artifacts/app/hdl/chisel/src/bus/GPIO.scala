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

/** GPIO configuration parameters. */
case class GPIOParameters(width: Int = 8)

/**
  * GPIO peripheral with TileLink-UL slave interface.
  *
  * CSR map:
  *   0x00 : DATA_IN   (read-only)  - value of gpio_i pins
  *   0x04 : DATA_OUT  (read/write) - drives gpio_o
  *   0x08 : OUT_EN    (read/write) - drives gpio_en_o
  */
class GPIO(p: Parameters, gp: GPIOParameters) extends Module {
  val tlp = new TLULParameters(p)

  val io = IO(new Bundle {
    val tl       = Flipped(new OpenTitanTileLink.Host2Device(tlp))
    val gpio_i   = Input(UInt(gp.width.W))
    val gpio_o   = Output(UInt(gp.width.W))
    val gpio_en_o = Output(UInt(gp.width.W))
  })

  // CSR registers
  val dataOutReg = RegInit(0.U(gp.width.W))
  val outEnReg   = RegInit(0.U(gp.width.W))

  io.gpio_o    := dataOutReg
  io.gpio_en_o := outEnReg

  // -------------------------------------------------------------------------
  // TL-UL state machine
  // -------------------------------------------------------------------------
  val sIdle :: sResp :: Nil = Enum(2)
  val state = RegInit(sIdle)

  val respData    = RegInit(0.U(tlp.dataBits.W))
  val respError   = RegInit(false.B)
  val respSource  = RegInit(0.U(tlp.sourceBits.W))
  val respSize    = RegInit(0.U(tlp.sizeBits.W))
  val respOpcode  = RegInit(TLULOpcodesD.AccessAck)

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
          when(addr === 0x00.U) {
            respData := io.gpio_i
          }.elsewhen(addr === 0x04.U) {
            respData := dataOutReg
          }.elsewhen(addr === 0x08.U) {
            respData := outEnReg
          }.otherwise {
            respData  := 0.U
            respError := true.B
          }
        }.elsewhen(isWrite) {
          respOpcode := TLULOpcodesD.AccessAck
          when(addr === 0x04.U) {
            dataOutReg := io.tl.a.bits.data(gp.width - 1, 0)
          }.elsewhen(addr === 0x08.U) {
            outEnReg := io.tl.a.bits.data(gp.width - 1, 0)
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
