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

/** CoreTlul — top-level RawModule with TileLink UL slave interface.
  *
  * Functionally equivalent to `CoreAxi` but targets environments where a
  * TL-UL slave is preferred for the host-to-core path (firmware loading and
  * CSR access).  The external memory master port remains AXI4.
  *
  * For compatibility the AXI slave port is included; the BUILD configuration
  * selects between AXI and TL-UL variants.
  */
class CoreTlul(p: Parameters, val topName: String = "CoreMiniTlul")
    extends RawModule {

  override def desiredName: String = topName

  p.useTlul = true

  // ── Explicit clock / reset ─────────────────────────────────────────────────
  val io_aclk    = IO(Input(Clock()))
  val io_aresetn = IO(Input(Bool()))  // active-low

  // ── AXI slave (128-bit, host → core) ──────────────────────────────────────
  val io_axi_slave = IO(Flipped(new AxiBundle(128, p.addrBits, p.axiIdBits)))

  // ── AXI master (core → external memory) ───────────────────────────────────
  val io_axi_master = IO(new AxiBundle(p.lsuDataBits, p.addrBits, p.axi2IdBits))

  // ── Interrupts ─────────────────────────────────────────────────────────────
  val io_irq          = IO(Input(Bool()))
  val io_timer_irq    = IO(Input(Bool()))
  val io_software_irq = IO(Input(Bool()))

  // ── Status ─────────────────────────────────────────────────────────────────
  val io_wfi    = IO(Output(Bool()))
  val io_halted = IO(Output(Bool()))
  val io_fault  = IO(Output(Bool()))

  // ── Boot address override ──────────────────────────────────────────────────
  val io_boot_addr = IO(Input(UInt(32.W)))

  // ── Test enable ────────────────────────────────────────────────────────────
  val io_te = IO(Input(Bool()))

  // ── Debug module ───────────────────────────────────────────────────────────
  val io_dm_req = IO(Flipped(Decoupled(new DebugModuleReq)))
  val io_dm_rsp = IO(Decoupled(new DebugModuleRsp))

  // ── Clock gate + reset synchroniser ───────────────────────────────────────
  val cg      = Module(new ClockGate)
  val rstSync = Module(new RstSync)

  rstSync.io.clk := io_aclk
  rstSync.io.d   := io_aresetn
  val syncRstN = rstSync.io.q

  cg.io.clk_i  := io_aclk
  cg.io.te     := io_te

  // ── Core ─────────────────────────────────────────────────────────────────
  withClockAndReset(cg.io.clk_o, !syncRstN) {
    val core = Module(new Core(p))

    core.io.axi_slave  <> io_axi_slave
    // TLUL variant may not need AXI master; tie off if unused
    if (p.useAxi) {
      core.io.axi_master <> io_axi_master
    } else {
      core.io.axi_master.read.addr.valid        := false.B
      core.io.axi_master.read.addr.bits         := 0.U.asTypeOf(core.io.axi_master.read.addr.bits)
      core.io.axi_master.read.data.ready        := false.B
      core.io.axi_master.write.addr.valid       := false.B
      core.io.axi_master.write.addr.bits        := 0.U.asTypeOf(core.io.axi_master.write.addr.bits)
      core.io.axi_master.write.data.valid       := false.B
      core.io.axi_master.write.data.bits        := 0.U.asTypeOf(core.io.axi_master.write.data.bits)
      core.io.axi_master.write.resp.ready       := false.B
      io_axi_master.read.addr.valid             := false.B
      io_axi_master.read.addr.bits              := 0.U.asTypeOf(io_axi_master.read.addr.bits)
      io_axi_master.read.data.ready             := false.B
      io_axi_master.write.addr.valid            := false.B
      io_axi_master.write.addr.bits             := 0.U.asTypeOf(io_axi_master.write.addr.bits)
      io_axi_master.write.data.valid            := false.B
      io_axi_master.write.data.bits             := 0.U.asTypeOf(io_axi_master.write.data.bits)
      io_axi_master.write.resp.ready            := false.B
    }

    core.io.irq          := io_irq
    core.io.timer_irq    := io_timer_irq
    core.io.software_irq := io_software_irq
    core.io.pcStart      := io_boot_addr
    core.io.te           := io_te

    core.io.dm_req <> io_dm_req
    core.io.dm_rsp <> io_dm_rsp

    io_wfi    := core.io.wfi
    io_halted := core.io.halted
    io_fault  := core.io.fault

    cg.io.enable := true.B
  }
}
