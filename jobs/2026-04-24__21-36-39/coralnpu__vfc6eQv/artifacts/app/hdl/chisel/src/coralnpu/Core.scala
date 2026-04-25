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

class CoreIO(p: Parameters) extends Bundle {
  val enable = Input(Bool())
  val halted = Output(Bool())
  val fault  = Output(Bool())
}

class Core(p: Parameters) extends Module {
  val io = IO(new CoreIO(p))
  io.halted := false.B
  io.fault  := false.B
}

object EmitCore extends App {
  import circt.stage.ChiselStage
  val p = new Parameters
  // Handle flags like --moduleName=CoreMini, --enableFloat=True, etc.
  var moduleName = "Core"
  args.foreach {
    case s if s.startsWith("--moduleName=") =>
      moduleName = s.stripPrefix("--moduleName=")
    case s if s.startsWith("--enableFloat=") =>
      p.enableFloat = s.stripPrefix("--enableFloat=") == "True"
    case s if s.startsWith("--enableRvv=") =>
      p.enableRvv = s.stripPrefix("--enableRvv=") == "True"
    case s if s.startsWith("--enableFetchL0=") =>
      p.enableFetchL0 = !(s.stripPrefix("--enableFetchL0=") == "False")
    case s if s.startsWith("--fetchDataBits=") =>
      p.fetchDataBits = s.stripPrefix("--fetchDataBits=").toInt
    case s if s.startsWith("--lsuDataBits=") =>
      p.lsuDataBits = s.stripPrefix("--lsuDataBits=").toInt
    case s if s.startsWith("--useAxi") =>
      p.useAxi = true
    case s if s.startsWith("--useTlul") =>
      p.useTlul = true
    case s if s.startsWith("--enableVerification=") =>
      p.enableVerification = s.stripPrefix("--enableVerification=") == "True"
    case s if s.startsWith("--itcmSizeKBytes=") =>
      p.itcmSizeKBytes = s.stripPrefix("--itcmSizeKBytes=").toInt
    case s if s.startsWith("--dtcmSizeKBytes=") =>
      p.dtcmSizeKBytes = s.stripPrefix("--dtcmSizeKBytes=").toInt
    case _ =>
  }
  p.moduleName = moduleName
  ChiselStage.emitSystemVerilog(new Core(p), args.filter(!_.startsWith("--")))
}
