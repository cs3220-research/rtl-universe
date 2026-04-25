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

/** IBus-to-AXI bridge: converts internal instruction bus to AXI read. */
class IBus2Axi(p: Parameters) extends Module {
  val io = IO(new Bundle {
    val ibus = Flipped(new IBusIO(p))
    val axi  = new AxiMasterBundle(p.axiAddrBits, p.fetchDataBits, p.axiIdBits)
  })

  // Minimal stub: wire read channel
  io.axi.read.addr.valid       := io.ibus.valid
  io.axi.read.addr.bits.id     := 0.U
  io.axi.read.addr.bits.addr   := io.ibus.addr
  io.axi.read.addr.bits.len    := 0.U
  io.axi.read.addr.bits.size   := log2Ceil(p.fetchDataBits / 8).U
  io.axi.read.addr.bits.burst  := 1.U
  io.ibus.ready                := io.axi.read.data.valid
  io.ibus.rdata                := io.axi.read.data.bits.data
  io.axi.read.data.ready       := true.B

  // Tie off write channel
  io.axi.write.addr.valid      := false.B
  io.axi.write.addr.bits       := 0.U.asTypeOf(io.axi.write.addr.bits)
  io.axi.write.data.valid      := false.B
  io.axi.write.data.bits       := 0.U.asTypeOf(io.axi.write.data.bits)
  io.axi.write.resp.ready      := false.B
}
