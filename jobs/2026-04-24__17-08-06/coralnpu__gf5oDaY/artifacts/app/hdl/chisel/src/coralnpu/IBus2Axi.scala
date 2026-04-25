// Copyright 2024 Google LLC
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

package coralnpu

import chisel3._
import chisel3.util._

/**
 * Instruction Bus to AXI read-only bridge.
 */
class IBus2Axi(p: Parameters) extends Module {
  val io = IO(new Bundle {
    val ibus = Flipped(new IBusInterface(p))
    val axi  = new AxiDataRead(p)
  })

  val sIdle :: sAddr :: sData :: Nil = Enum(3)
  val state = RegInit(sIdle)
  val addrReg = RegInit(0.U(p.addrBits.W))

  io.ibus.ready := false.B
  io.ibus.rdata := 0.U
  io.axi.addr.valid := false.B
  io.axi.addr.bits.id   := 0.U
  io.axi.addr.bits.addr := addrReg
  io.axi.addr.bits.len  := 0.U
  io.axi.addr.bits.size := log2Ceil(p.fetchDataBits / 8).U
  io.axi.data.ready := false.B

  switch(state) {
    is(sIdle) {
      when(io.ibus.valid) {
        addrReg := io.ibus.addr
        state   := sAddr
      }
    }
    is(sAddr) {
      io.axi.addr.valid       := true.B
      io.axi.addr.bits.addr   := addrReg
      io.axi.addr.bits.len    := 0.U
      io.axi.addr.bits.size   := log2Ceil(p.fetchDataBits / 8).U
      when(io.axi.addr.ready) {
        state := sData
      }
    }
    is(sData) {
      io.axi.data.ready := true.B
      when(io.axi.data.valid) {
        io.ibus.ready := true.B
        io.ibus.rdata := io.axi.data.bits.data
        state := sIdle
      }
    }
  }
}
