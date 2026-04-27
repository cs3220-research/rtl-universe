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

/** L1 Instruction Cache.
  *
  * For CoreMini configurations this is a pass-through: every fetch is treated
  * as uncached and forwarded directly to the backing instruction memory.
  * The `ready` and `rdata` signals are wired through without buffering.
  */
class L1ICache(p: Parameters) extends Module {
  val io = IO(new Bundle {
    val cpu = Flipped(new IBusBundle(p.fetchDataBits))
    val mem = new IBusBundle(p.fetchDataBits)
  })

  // Simple wire-through — no cache logic.
  io.mem.valid := io.cpu.valid
  io.mem.addr  := io.cpu.addr
  io.cpu.ready := io.mem.ready
  io.cpu.rdata := io.mem.rdata
}

/** Chisel emission entry-point for the L1ICache standalone build. */
object EmitL1ICache extends App {
  val p = new Parameters
  circt.stage.ChiselStage.emitSystemVerilog(new L1ICache(p))
}
