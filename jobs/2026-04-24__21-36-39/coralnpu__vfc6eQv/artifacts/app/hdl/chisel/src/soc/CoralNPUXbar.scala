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

package coralnpu.soc

import chisel3._
import chisel3.util._
import bus._
import coralnpu.Parameters

/** CoralNPU on-chip crossbar.
  *
  * Routes TileLink-UL transactions between one or more masters and
  * one or more address-decoded slaves.
  */
class CoralNPUXbar(p: Parameters) extends Module {
  val tlul_p = new TLULParameters(p)

  val io = IO(new Bundle {
    // Master ports (from cores)
    val masters = Vec(2, Flipped(new OpenTitanTileLink.Host2Device(tlul_p)))
    // Slave ports (to memories / peripherals)
    val slaves  = Vec(3, new OpenTitanTileLink.Host2Device(tlul_p))
  })

  // Default tie-offs
  for (s <- io.slaves) {
    s.a.valid    := false.B
    s.a.bits     := DontCare
    s.d.ready    := false.B
  }
  for (m <- io.masters) {
    m.a.ready    := false.B
    m.d.valid    := false.B
    m.d.bits     := DontCare
  }
}

/** Option-parsing config for Xbar generation. */
class XbarGenConfig(args: Array[String]) {
  var enableTestHarness: Boolean = false
  var moduleName: String = "CoralNPUXbar"

  var i = 0
  while (i < args.length) {
    args(i) match {
      case "--enableTestHarness" => enableTestHarness = true; i += 1
      case "--moduleName" if i + 1 < args.length =>
        moduleName = args(i + 1); i += 2
      case _ => i += 1
    }
  }
}

/** Emitter entry point for the CoralNPU crossbar. */
object CoralNPUXbarEmitter extends App {
  val cfg = new XbarGenConfig(args)
  val p   = new Parameters
  val moduleName = if (cfg.enableTestHarness) cfg.moduleName + "TestHarness"
                   else cfg.moduleName
  println(s"Emitting: $moduleName")
  // Actual emission would happen here via circt or chisel3.stage
}
