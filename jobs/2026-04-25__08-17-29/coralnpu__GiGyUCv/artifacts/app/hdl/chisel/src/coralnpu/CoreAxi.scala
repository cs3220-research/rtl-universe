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

/** Top-level AXI interface for the core. */
class CoreAxiIO(p: Parameters) extends Bundle {
  val axi    = Flipped(new AxiMasterBundle(p.axiAddrBits, p.axiDataBits, p.axiIdBits))
  val halted = Output(Bool())
  val fault  = Output(Bool())
}

/** Emitter for the core with AXI interface. */
object EmitCore extends App {
  import circt.stage.ChiselStage
  // Parse simple flags
  val moduleName = args.sliding(2).find(_(0) == "--moduleName").map(_(1)).getOrElse("Core")
  val p = new Parameters
  p.moduleName = moduleName
  // Emit stub core
  ChiselStage.emitSystemVerilog(new Core(p))
}

object EmitAlu extends App {
  import circt.stage.ChiselStage
  val p = new Parameters
  ChiselStage.emitSystemVerilog(new Alu(p))
}

object EmitMlu extends App {
  import circt.stage.ChiselStage
  val p = new Parameters
  ChiselStage.emitSystemVerilog(new Mlu(p))
}

object EmitDvu extends App {
  import circt.stage.ChiselStage
  val p = new Parameters
  // Dvu stub
  println("EmitDvu: stub")
}
