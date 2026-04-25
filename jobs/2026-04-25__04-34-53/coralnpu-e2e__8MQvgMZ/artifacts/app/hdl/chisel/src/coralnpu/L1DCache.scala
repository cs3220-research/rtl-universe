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

/** L1 Data Cache.
  *
  * For CoreMini configurations this is a pass-through: every load/store is
  * treated as uncached and forwarded directly to the backing data memory.
  */
class L1DCache(p: Parameters) extends Module {
  val io = IO(new Bundle {
    val cpu = Flipped(new DBusBundle(p.lsuDataBits))
    val mem = new DBusBundle(p.lsuDataBits)
  })

  // Simple wire-through — no cache logic.
  io.mem.valid  := io.cpu.valid
  io.mem.addr   := io.cpu.addr
  io.mem.write  := io.cpu.write
  io.mem.wdata  := io.cpu.wdata
  io.mem.wmask  := io.cpu.wmask
  io.mem.size   := io.cpu.size
  io.cpu.ready  := io.mem.ready
  io.cpu.rdata  := io.mem.rdata
}

/** Emission entry-point for the L1DCache standalone build. */
class EmitL1DCache extends App {
  val p = new Parameters
  circt.stage.ChiselStage.emitSystemVerilog(new L1DCache(p))
}

/** Emission entry-point for the L1DCacheBank standalone build. */
class EmitL1DCacheBank extends App {
  val p = new Parameters
  circt.stage.ChiselStage.emitSystemVerilog(new L1DCache(p))
}
