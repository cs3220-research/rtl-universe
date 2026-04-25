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

/** AXI slave adapter stub: wraps an AXI master bundle as seen by a subordinate. */
class AxiSlave(p: Parameters) extends Module {
  val io = IO(new Bundle {
    val axi = Flipped(new AxiMasterBundle(p.axiAddrBits, p.axiDataBits, p.axiIdBits))
  })

  // Default tie-offs
  io.axi.read.addr.ready      := false.B
  io.axi.read.data.valid      := false.B
  io.axi.read.data.bits       := 0.U.asTypeOf(io.axi.read.data.bits)
  io.axi.write.addr.ready     := false.B
  io.axi.write.data.ready     := false.B
  io.axi.write.resp.valid     := false.B
  io.axi.write.resp.bits      := 0.U.asTypeOf(io.axi.write.resp.bits)
}
