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

/** L1 Instruction Cache stub. */
class L1ICache(p: Parameters) extends Module {
  val io = IO(new Bundle {
    val ibus = Flipped(new IBusIO(p))
    val axi  = new AxiMasterBundle(p.axiAddrBits, p.fetchDataBits, p.axiIdBits)
  })

  // Passthrough stub via IBus2Axi
  val bridge = Module(new IBus2Axi(p))
  io.ibus <> bridge.io.ibus
  io.axi  <> bridge.io.axi
}

object EmitL1ICache extends App {
  import circt.stage.ChiselStage
  val p = new Parameters
  ChiselStage.emitSystemVerilog(new L1ICache(p))
}
