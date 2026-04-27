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

/** CoreAxi — top-level RawModule with explicit AXI4 clock and active-low reset.
  *
  * Emitted as e.g. `CoreMiniAxi.sv` and instantiated by the cocotb testbench
  * via `core_mini_axi_interface.py`.
  *
  * Port naming follows the testbench conventions:
  * {{{
  *   io_aclk, io_aresetn
  *   io_axi_slave_read_addr_valid, …
  *   io_axi_master_read_addr_valid, …
  *   io_irq, io_timer_irq, io_software_irq
  *   io_wfi, io_halted, io_fault
  *   io_boot_addr, io_te
  *   io_dm_req_valid, io_dm_req_ready, io_dm_req_bits_*
  *   io_dm_rsp_valid, io_dm_rsp_ready, io_dm_rsp_bits_*
  * }}}
  */
class CoreAxi(p: Parameters, val topName: String = "CoreMiniAxi")
    extends RawModule {

  override def desiredName: String = topName

  // Ensure AXI slave and master are included in Core
  p.useAxi = true

  // ── Explicit clock / reset ─────────────────────────────────────────────────
  val io_aclk    = IO(Input(Clock()))
  val io_aresetn = IO(Input(Bool()))   // active-low

  // ── AXI slave (128-bit, host → core) ──────────────────────────────────────
  val io_axi_slave = IO(Flipped(new AxiBundle(128, p.addrBits, p.axiIdBits)))

  // ── AXI master (core → external memory, lsuDataBits-wide) ─────────────────
  val io_axi_master = IO(new AxiBundle(p.lsuDataBits, p.addrBits, p.axi2IdBits))

  // ── Interrupts ─────────────────────────────────────────────────────────────
  val io_irq          = IO(Input(Bool()))
  val io_timer_irq    = IO(Input(Bool()))
  val io_software_irq = IO(Input(Bool()))

  // ── Status ─────────────────────────────────────────────────────────────────
  val io_wfi    = IO(Output(Bool()))
  val io_halted = IO(Output(Bool()))
  val io_fault  = IO(Output(Bool()))

  // ── Boot address override (0 = use CSR) ────────────────────────────────────
  val io_boot_addr = IO(Input(UInt(32.W)))

  // ── Test enable ────────────────────────────────────────────────────────────
  val io_te = IO(Input(Bool()))

  // ── Debug module ───────────────────────────────────────────────────────────
  val io_dm_req = IO(Flipped(Decoupled(new DebugModuleReq)))
  val io_dm_rsp = IO(Decoupled(new DebugModuleRsp))

  // ── Comprehensive debug port ────────────────────────────────────────────────
  val io_debug = IO(new CoreDebugBundle(p))

  // ── Comprehensive debug port ────────────────────────────────────────────────
  val io_debug = IO(new CoreDebugBundle(p))

  // ── Clock gate + reset synchroniser ───────────────────────────────────────
  val cg      = Module(new ClockGate)
  val rstSync = Module(new RstSync)

  rstSync.io.clk := io_aclk
  rstSync.io.d   := io_aresetn  // 0 = async reset; 1 = running
  val syncRstN = rstSync.io.q

  cg.io.clk_i  := io_aclk
  cg.io.te     := io_te

  // ── Core (all logic on gated clock) ─────────────────────────────────────────
  withClockAndReset(cg.io.clk_o, !syncRstN) {
    val core = Module(new Core(p))

    core.io.axi_slave  <> io_axi_slave
    core.io.axi_master <> io_axi_master

    core.io.irq          := io_irq
    core.io.timer_irq    := io_timer_irq
    core.io.software_irq := io_software_irq
    core.io.pcStart      := io_boot_addr
    core.io.te           := io_te

    core.io.dm_req <> io_dm_req
    core.io.dm_rsp <> io_dm_rsp
    core.io.debug  <> io_debug

    io_wfi    := core.io.wfi
    io_halted := core.io.halted
    io_fault  := core.io.fault

    // Clock gate enable: always enabled (CSR clock gate handled at Core level)
    cg.io.enable := true.B
  }
}

/** Emit entry-point for the EmitCore standalone build.
  *
  * Parses command-line flags and emits SystemVerilog plus a C++ parameter header.
  */
object EmitCore extends App {
  val p = new Parameters

  var moduleName = "CoreMini"
  var outputDir  = "."

  args.foreach { arg =>
    val stripped = arg.stripPrefix("--")
    val kv = stripped.split("=", 2)
    val key   = kv(0)
    val value = if (kv.length > 1) kv(1) else "true"
    key match {
      case "moduleName"         => moduleName          = value
      case "outputDir"          => outputDir           = value
      case "target-dir"         => outputDir           = value
      case "enableFloat"        => p.enableFloat        = value.toLowerCase != "false"
      case "enableRvv"          => p.enableRvv          = value.toLowerCase != "false"
      case "enableFetchL0"      => p.enableFetchL0      = value.toLowerCase != "false"
      case "enableVerification" => p.enableVerification = value.toLowerCase != "false"
      case "fetchDataBits"      => p.fetchDataBits      = value.toInt
      case "lsuDataBits"        => p.lsuDataBits        = value.toInt
      case "itcmSizeKBytes"     => p.itcmSizeBytes      = MemorySize.kbytes(value.toInt)
      case "dtcmSizeKBytes"     => p.dtcmSizeBytes      = MemorySize.kbytes(value.toInt)
      case "useAxi"             => p.useAxi             = value.toLowerCase != "false"
      case "useTlul"            => p.useTlul            = value.toLowerCase != "false"
      case _                    => // ignore
    }
  }

  // Build the top-level module
  val topModule: () => RawModule =
    if (p.useTlul)      () => new CoreTlul(p, moduleName + "Tlul")
    else                () => new CoreAxi(p, moduleName + "Axi")

  val topName = if (p.useTlul) moduleName + "Tlul" else moduleName + "Axi"
  val svName  = s"$topName.sv"
  val zipName = s"$topName.zip"

  // Emit SystemVerilog as a single combined file (blackboxes ClockGate/RstSync/Sram excluded)
  val svContent = _root_.circt.stage.ChiselStage.emitSystemVerilog(
    topModule(),
    firtoolOpts = Array(
      "-disable-all-randomization",
      "-strip-debug-info",
      "--default-layer-specialization=disable",
    )
  )
  val svWriter = new java.io.FileWriter(s"$outputDir/$svName")
  svWriter.write(svContent)
  svWriter.close()

  // Emit C++ parameter header
  val hdrContent =
    s"""// Auto-generated by EmitCore — do not edit.
#ifndef VCORE_PARAMETERS_H_
#define VCORE_PARAMETERS_H_

#define KP_lsuDataBits              ${p.lsuDataBits}
#define KP_fetchDataBits            ${p.fetchDataBits}
#define KP_addrBits                 ${p.addrBits}
#define KP_axi2AddrBits             ${p.axi2AddrBits}
#define KP_axi2IdBits               ${p.axi2IdBits}
#define KP_axiIdBits                ${p.axiIdBits}
#define KP_enableFloat              ${p.enableFloat}
#define KP_enableRvv                ${p.enableRvv}
#define KP_rvvVlen                  128
#define KP_retirementBufferSize     ${RetirementBufferConfig.size}
#define KP_retirementBufferIdxWidth ${RetirementBufferConfig.idxWidth}
#define KP_itcmSizeKBytes           ${p.itcmSizeBytes / 1024}
#define KP_dtcmSizeKBytes           ${p.dtcmSizeBytes / 1024}

#endif  // VCORE_PARAMETERS_H_
"""
  val hdrName = s"V${topName}_parameters.h"
  val fw = new java.io.FileWriter(s"$outputDir/$hdrName")
  fw.write(hdrContent)
  fw.close()

  // Emit zip containing the generated SV file (required by chisel_cc_library genrule)
  val svBytes = java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(s"$outputDir/$svName"))
  val zos = new java.util.zip.ZipOutputStream(
    new java.io.FileOutputStream(s"$outputDir/$zipName"))
  zos.putNextEntry(new java.util.zip.ZipEntry(svName))
  zos.write(svBytes)
  zos.closeEntry()
  zos.close()
}
