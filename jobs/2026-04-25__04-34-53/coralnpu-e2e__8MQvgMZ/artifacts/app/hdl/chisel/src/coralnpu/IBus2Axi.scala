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

/** Instruction-bus to AXI4 read bridge.
  *
  * Converts instruction-fetch requests from the CPU (IBusBundle) into AXI4
  * read transactions.  Only single-beat (len=0) reads are generated.
  *
  * When `ibus.valid` is asserted and `ibus.ready` is not yet asserted,
  * the bridge issues an AXI read-address channel transaction.  When the
  * read data returns on the R channel, `ibus.ready` is asserted together
  * with `ibus.rdata` for one cycle.
  */
class IBus2Axi(p: Parameters) extends Module {
  val io = IO(new Bundle {
    val ibus = Flipped(new IBusBundle(p.fetchDataBits, p.addrBits))
    val axi  = new AxiBundle(p.fetchDataBits, p.addrBits, p.axiIdBits)
  })

  // -------------------------------------------------------------------------
  // State machine
  // -------------------------------------------------------------------------
  private object State extends ChiselEnum {
    val sIdle, sReadAddr, sReadData = Value
  }
  import State._

  val state   = RegInit(sIdle)
  val addrReg = Reg(UInt(p.addrBits.W))

  // AXI write channel: unused, tie off
  io.axi.write.addr.valid        := false.B
  io.axi.write.addr.bits.id     := 0.U
  io.axi.write.addr.bits.addr   := 0.U
  io.axi.write.addr.bits.len    := 0.U
  io.axi.write.addr.bits.size   := 2.U
  io.axi.write.addr.bits.burst  := 1.U
  io.axi.write.addr.bits.prot   := 0.U
  io.axi.write.addr.bits.lock   := 0.U
  io.axi.write.addr.bits.cache  := 0.U
  io.axi.write.addr.bits.qos    := 0.U
  io.axi.write.addr.bits.region := 0.U
  io.axi.write.data.valid        := false.B
  io.axi.write.data.bits.data   := 0.U
  io.axi.write.data.bits.strb   := 0.U
  io.axi.write.data.bits.last   := false.B
  io.axi.write.resp.ready       := false.B

  // AXI read defaults
  io.axi.read.addr.valid         := false.B
  io.axi.read.addr.bits.id      := 0.U
  io.axi.read.addr.bits.addr    := addrReg
  io.axi.read.addr.bits.len     := 0.U
  io.axi.read.addr.bits.size    := log2Ceil(p.fetchDataBits / 8).U
  io.axi.read.addr.bits.burst   := 1.U  // INCR
  io.axi.read.addr.bits.prot    := 0.U
  io.axi.read.addr.bits.lock    := 0.U
  io.axi.read.addr.bits.cache   := 0.U
  io.axi.read.addr.bits.qos     := 0.U
  io.axi.read.addr.bits.region  := 0.U
  io.axi.read.data.ready        := false.B

  // IBus defaults
  io.ibus.ready := false.B
  io.ibus.rdata := io.axi.read.data.bits.data

  switch(state) {
    is(sIdle) {
      when(io.ibus.valid) {
        addrReg := io.ibus.addr
        state   := sReadAddr
      }
    }

    is(sReadAddr) {
      io.axi.read.addr.valid      := true.B
      io.axi.read.addr.bits.addr  := addrReg
      io.axi.read.addr.bits.size  := log2Ceil(p.fetchDataBits / 8).U
      when(io.axi.read.addr.ready) {
        state := sReadData
      }
    }

    is(sReadData) {
      io.axi.read.data.ready := true.B
      io.ibus.rdata          := io.axi.read.data.bits.data
      when(io.axi.read.data.valid) {
        io.ibus.ready := true.B
        state := sIdle
      }
    }
  }
}
