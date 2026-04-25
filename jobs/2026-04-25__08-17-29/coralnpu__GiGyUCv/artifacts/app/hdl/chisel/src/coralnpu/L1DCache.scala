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

/** L1 Data Cache stub. */
class L1DCache(p: Parameters) extends Module {
  val io = IO(new Bundle {
    val dbus = Flipped(new DBusIO(p))
    val axi  = new AxiMasterBundle(p.axiAddrBits, p.axi2DataBits, p.axi2IdBits)
  })

  // Passthrough stub
  val bridge = Module(new DBus2AxiV2(p))
  io.dbus <> bridge.io.dbus
  io.axi  <> bridge.io.axi
}

/** L1 Data Cache Bank stub. */
class L1DCacheBank(p: Parameters) extends Module {
  val io = IO(new Bundle {
    val dbus = Flipped(new DBusIO(p))
    val axi  = new AxiMasterBundle(p.axiAddrBits, p.axi2DataBits, p.axi2IdBits)
  })
  val bridge = Module(new DBus2AxiV2(p))
  io.dbus <> bridge.io.dbus
  io.axi  <> bridge.io.axi
}

object EmitL1DCache extends App {
  import circt.stage.ChiselStage
  val p = new Parameters
  ChiselStage.emitSystemVerilog(new L1DCache(p))
}

object EmitL1DCacheBank extends App {
  import circt.stage.ChiselStage
  val p = new Parameters
  ChiselStage.emitSystemVerilog(new L1DCacheBank(p))
}
