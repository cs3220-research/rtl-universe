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

/** L0 Fetch cache stub (optional stage before uncached fetch). */
class FetchL0(p: Parameters) extends Module {
  val nInst = p.fetchDataBits / p.ilen

  val io = IO(new Bundle {
    val fetchAddr = Flipped(Decoupled(UInt(p.addrBits.W)))
    val fetchData = Valid(new Bundle {
      val addr = UInt(p.addrBits.W)
      val inst = Vec(nInst, UInt(p.ilen.W))
    })
    val ibus = new IBusInterface(p)
    val flush = Input(Bool())
  })

  // Pass-through stub
  io.fetchAddr.ready    := false.B
  io.fetchData.valid    := false.B
  io.fetchData.bits     := 0.U.asTypeOf(io.fetchData.bits)
  io.ibus.valid         := false.B
  io.ibus.addr          := 0.U
}
