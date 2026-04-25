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

package coralnpu

import chisel3._
import chisel3.util._
import bus._

/** Instruction-bus to AXI4 read-only adapter.
  *
  * Translates the simple IBus (valid/ready/addr/rdata) interface into an
  * AXI4 read transaction. Only read channels are driven; write channels
  * are tied off.
  */
class IBus2Axi(p: Parameters) extends Module {
  val io = IO(new Bundle {
    val ibus = Flipped(new IBus)
    val axi  = new AxiMasterIO(32, p.fetchDataBits, p.axi2IdBits)
  })

  // FSM states
  val sIdle :: sReadAddr :: sReadData :: Nil = Enum(3)
  val state = RegInit(sIdle)

  val savedAddr = Reg(UInt(32.W))

  // Write channels: not used, tie off
  io.axi.write.addr.valid     := false.B
  io.axi.write.addr.bits      := 0.U.asTypeOf(new AxiWriteAddrChannel(32, p.axi2IdBits))
  io.axi.write.data.valid     := false.B
  io.axi.write.data.bits      := 0.U.asTypeOf(new AxiWriteDataChannel(p.fetchDataBits))
  io.axi.write.resp.ready     := false.B

  // Defaults
  io.axi.read.addr.valid      := false.B
  io.axi.read.addr.bits       := 0.U.asTypeOf(new AxiReadAddrChannel(32, p.axi2IdBits))
  io.axi.read.data.ready      := false.B
  io.ibus.ready               := false.B

  switch(state) {
    is(sIdle) {
      when(io.ibus.valid) {
        savedAddr := io.ibus.addr
        state     := sReadAddr
      }
    }
    is(sReadAddr) {
      io.axi.read.addr.valid        := true.B
      io.axi.read.addr.bits.addr    := savedAddr
      io.axi.read.addr.bits.id      := 0.U
      io.axi.read.addr.bits.len     := 0.U
      io.axi.read.addr.bits.size    := log2Ceil(p.fetchDataBits / 8).U
      io.axi.read.addr.bits.burst   := 1.U
      when(io.axi.read.addr.ready) {
        state := sReadData
      }
    }
    is(sReadData) {
      io.axi.read.data.ready := true.B
      when(io.axi.read.data.valid) {
        io.ibus.ready := true.B
        state         := sIdle
      }
    }
  }
}

object EmitIBus2Axi extends App {
  import circt.stage.ChiselStage
  ChiselStage.emitSystemVerilog(new IBus2Axi(new Parameters), args)
}
