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
import coralnpu.{Parameters, MemorySize}

/** Top-level CoralNPU Chisel subsystem.
  *
  * Contains the CoralNPU core(s), on-chip crossbar, TCM memories,
  * and peripheral interfaces.
  */
class CoralNPUChiselSubsystem(p: Parameters) extends Module {
  val io = IO(new Bundle {
    // AXI master port (external memory access)
    val axi = new AxiMasterIO(32, 128, 6)
    // Interrupt inputs
    val irq = Input(UInt(32.W))
    // Debug interface
    val debug_req = Input(Bool())
    // Status
    val halted = Output(Bool())
    val fault  = Output(Bool())
  })

  io.axi.read.addr.valid  := false.B
  io.axi.read.addr.bits   := DontCare
  io.axi.read.data.ready  := false.B
  io.axi.write.addr.valid := false.B
  io.axi.write.addr.bits  := DontCare
  io.axi.write.data.valid := false.B
  io.axi.write.data.bits  := DontCare
  io.axi.write.resp.ready := false.B
  io.halted := false.B
  io.fault  := false.B
}

/** Test harness wrapper for simulation. */
class CoralNPUChiselSubsystemTestHarness(p: Parameters) extends Module {
  val io = IO(new Bundle {
    val axi = new AxiMasterIO(32, 128, 6)
    val irq = Input(UInt(32.W))
    val halted = Output(Bool())
    val fault  = Output(Bool())
  })
  val dut = Module(new CoralNPUChiselSubsystem(p))
  dut.io.axi       <> io.axi
  dut.io.irq       := io.irq
  dut.io.debug_req := false.B
  io.halted        := dut.io.halted
  io.fault         := dut.io.fault
}

/** Generation config parsed from command-line flags. */
class SubsystemGenConfig(args: Array[String]) {
  var itcmSizeKBytes:    Int     = 8
  var dtcmSizeKBytes:    Int     = 32
  var enableTestHarness: Boolean = false
  var moduleName:        String  = "CoralNPUChiselSubsystem"

  var i = 0
  while (i < args.length) {
    args(i) match {
      case "--itcmSizeKBytes" if i + 1 < args.length =>
        itcmSizeKBytes = args(i + 1).toInt; i += 2
      case "--dtcmSizeKBytes" if i + 1 < args.length =>
        dtcmSizeKBytes = args(i + 1).toInt; i += 2
      case "--enableTestHarness" =>
        enableTestHarness = true; i += 1
      case "--moduleName" if i + 1 < args.length =>
        moduleName = args(i + 1); i += 2
      case _ =>
        i += 1
    }
  }

  def toParameters: Parameters = {
    val p = new Parameters
    p.itcmSizeKBytes = itcmSizeKBytes
    p.dtcmSizeKBytes = dtcmSizeKBytes
    p
  }
}

/** Emitter entry point for CoralNPUChiselSubsystem. */
object CoralNPUChiselSubsystemEmitter extends App {
  val cfg = new SubsystemGenConfig(args)
  val p   = cfg.toParameters
  val moduleName = if (cfg.enableTestHarness) cfg.moduleName + "TestHarness"
                   else cfg.moduleName
  println(s"Emitting: $moduleName  (itcm=${cfg.itcmSizeKBytes}KB dtcm=${cfg.dtcmSizeKBytes}KB)")
}
