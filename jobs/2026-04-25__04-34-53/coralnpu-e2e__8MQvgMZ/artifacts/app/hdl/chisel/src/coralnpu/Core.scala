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

/** Core — integrates SCore with the memory subsystem (TCM, caches, fabric).
  *
  * Memory map (CPU byte addresses):
  * {{{
  *   0x00000000 .. itcmSizeBytes-1  → ITCM
  *   0x00010000 .. 0x10000+dtcm-1  → DTCM
  *   0x20000000 ..                  → External (AXI master when useAxi=true)
  * }}}
  *
  * When `p.useAxi` or `p.useTlul` is true, the AXI slave and master ports are
  * active.  Otherwise they are tied off.
  */
class Core(p: Parameters) extends Module {

  val ITCM_BASE: Long = 0x00000000L
  val DTCM_BASE: Long = 0x00010000L
  val EXT_BASE:  Long = 0x20000000L

  val io = IO(new Bundle {
    // AXI slave (128-bit) — host back-door for firmware loading and CSR access
    val axi_slave  = Flipped(new AxiBundle(128, p.addrBits, p.axiIdBits))
    // AXI master — external memory access
    val axi_master = new AxiBundle(p.lsuDataBits, p.addrBits, p.axi2IdBits)

    val irq          = Input(Bool())
    val timer_irq    = Input(Bool())
    val software_irq = Input(Bool())

    // pcStart: boot address override (0 = use CSR value)
    val pcStart  = Input(UInt(32.W))
    val wfi      = Output(Bool())
    val halted   = Output(Bool())
    val fault    = Output(Bool())

    val dm_req = Flipped(Decoupled(new DebugModuleReq))
    val dm_rsp = Decoupled(new DebugModuleRsp)

    val te = Input(Bool())
  })

  // ── CSR module ─────────────────────────────────────────────────────────────
  val csrMod = Module(new CoreAxiCSR(p))
  csrMod.io.internal     := false.B

  // ── SCore ─────────────────────────────────────────────────────────────────
  val score = Module(new SCore(p))
  score.io.irq          := io.irq
  score.io.timer_irq    := io.timer_irq
  score.io.software_irq := io.software_irq
  score.io.te           := io.te
  io.wfi                := score.io.wfi
  io.halted             := score.io.halted
  io.fault              := score.io.fault
  score.io.dm_req       <> io.dm_req
  score.io.dm_rsp       <> io.dm_rsp

  csrMod.io.halted      := score.io.halted
  csrMod.io.fault       := score.io.fault
  csrMod.io.coralnpu_csr := score.io.csr_value

  // Boot address: io.pcStart override > CSR register
  score.io.pcStart := Mux(io.pcStart =/= 0.U, io.pcStart, csrMod.io.pcStart)

  // ── L1 caches (pass-through stubs) ────────────────────────────────────────
  val l1i = Module(new L1ICache(p))
  val l1d = Module(new L1DCache(p))
  l1i.io.cpu <> score.io.ibus
  l1d.io.cpu <> score.io.dbus

  // ── TCMs ──────────────────────────────────────────────────────────────────
  val itcm = Module(new TCM(p.itcmSizeBytes, ITCM_BASE))
  val dtcm = Module(new TCM(p.dtcmSizeBytes, DTCM_BASE))

  // Backdoor defaults — overridden when AXI slave is connected
  itcm.io.backdoor.en    := false.B
  itcm.io.backdoor.we    := false.B
  itcm.io.backdoor.addr  := 0.U
  itcm.io.backdoor.wdata := 0.U
  itcm.io.backdoor.wmask := 0.U
  dtcm.io.backdoor.en    := false.B
  dtcm.io.backdoor.we    := false.B
  dtcm.io.backdoor.addr  := 0.U
  dtcm.io.backdoor.wdata := 0.U
  dtcm.io.backdoor.wmask := 0.U

  // ── Instruction bus → ITCM ────────────────────────────────────────────────
  val ibusAddr = l1i.io.mem.addr
  itcm.io.cpu.en    := l1i.io.mem.valid && (ibusAddr < p.itcmSizeBytes.U)
  itcm.io.cpu.we    := false.B
  itcm.io.cpu.addr  := ibusAddr(log2Ceil(p.itcmSizeBytes) - 1, 0)
  itcm.io.cpu.wdata := 0.U
  itcm.io.cpu.wmask := 0.U
  l1i.io.mem.ready  := ibusAddr < p.itcmSizeBytes.U
  l1i.io.mem.rdata  := itcm.io.cpu.rdata

  // ── Data bus fabric ────────────────────────────────────────────────────────
  val memRegions = Seq(
    new MemoryRegion(ITCM_BASE, p.itcmSizeBytes, MemoryRegionType.IMEM),
    new MemoryRegion(DTCM_BASE, p.dtcmSizeBytes, MemoryRegionType.DMEM),
    new MemoryRegion(EXT_BASE,  0x10000000L,      MemoryRegionType.Peripheral),
  )
  val fabricMux = Module(new FabricMux(p, memRegions))

  fabricMux.io.source.readDataAddr.valid  := l1d.io.mem.valid && !l1d.io.mem.write
  fabricMux.io.source.readDataAddr.bits   := l1d.io.mem.addr
  fabricMux.io.source.writeDataAddr.valid := l1d.io.mem.valid && l1d.io.mem.write
  fabricMux.io.source.writeDataAddr.bits  := l1d.io.mem.addr
  fabricMux.io.source.writeDataBits       := l1d.io.mem.wdata
  fabricMux.io.source.writeDataStrb       := l1d.io.mem.wmask

  for (i <- 0 until memRegions.length) { fabricMux.io.periBusy(i) := false.B }

  l1d.io.mem.ready := fabricMux.io.source.readDataAddr.ready ||
                      fabricMux.io.source.writeDataAddr.ready
  l1d.io.mem.rdata := fabricMux.io.source.readData.bits

  // ── ITCM dbus port ────────────────────────────────────────────────────────
  val itcmPort  = fabricMux.io.ports(0)
  val itcmDbWr  = itcmPort.writeDataAddr.valid
  val itcmDbEn  = itcmPort.readDataAddr.valid || itcmPort.writeDataAddr.valid

  when(itcmDbEn) {
    itcm.io.cpu.en    := true.B
    itcm.io.cpu.we    := itcmDbWr
    itcm.io.cpu.addr  := Mux(itcmDbWr,
      itcmPort.writeDataAddr.bits(log2Ceil(p.itcmSizeBytes) - 1, 0),
      itcmPort.readDataAddr.bits (log2Ceil(p.itcmSizeBytes) - 1, 0))
    itcm.io.cpu.wdata := itcmPort.writeDataBits
    itcm.io.cpu.wmask := itcmPort.writeDataStrb
    l1i.io.mem.ready  := false.B  // stall ibus when dbus wins ITCM port
  }

  itcmPort.readDataAddr.ready  := true.B
  itcmPort.writeDataAddr.ready := true.B
  itcmPort.readData.valid      := RegNext(itcmPort.readDataAddr.valid)
  itcmPort.readData.bits       := itcm.io.cpu.rdata

  // ── DTCM dbus port ────────────────────────────────────────────────────────
  val dtcmPort = fabricMux.io.ports(1)
  val dtcmDbWr = dtcmPort.writeDataAddr.valid
  val dtcmDbEn = dtcmPort.readDataAddr.valid || dtcmPort.writeDataAddr.valid

  dtcm.io.cpu.en    := dtcmDbEn
  dtcm.io.cpu.we    := dtcmDbWr
  dtcm.io.cpu.addr  := Mux(dtcmDbWr,
    dtcmPort.writeDataAddr.bits(log2Ceil(p.dtcmSizeBytes) - 1, 0),
    dtcmPort.readDataAddr.bits (log2Ceil(p.dtcmSizeBytes) - 1, 0))
  dtcm.io.cpu.wdata := dtcmPort.writeDataBits
  dtcm.io.cpu.wmask := dtcmPort.writeDataStrb

  dtcmPort.readDataAddr.ready  := true.B
  dtcmPort.writeDataAddr.ready := true.B
  dtcmPort.readData.valid      := RegNext(dtcmPort.readDataAddr.valid)
  dtcmPort.readData.bits       := dtcm.io.cpu.rdata

  // ── External AXI master ────────────────────────────────────────────────────
  val extPort = fabricMux.io.ports(2)

  if (p.useAxi) {
    val dbus2axi = Module(new DBus2AxiV2(p))

    dbus2axi.io.dbus.valid := extPort.readDataAddr.valid || extPort.writeDataAddr.valid
    dbus2axi.io.dbus.addr  := Mux(extPort.writeDataAddr.valid,
      extPort.writeDataAddr.bits + EXT_BASE.U,
      extPort.readDataAddr.bits  + EXT_BASE.U)
    dbus2axi.io.dbus.write := extPort.writeDataAddr.valid
    dbus2axi.io.dbus.wdata := extPort.writeDataBits
    dbus2axi.io.dbus.wmask := extPort.writeDataStrb
    dbus2axi.io.dbus.size  := (p.lsuDataBits / 8).U

    extPort.readDataAddr.ready  := dbus2axi.io.dbus.ready && !extPort.writeDataAddr.valid
    extPort.writeDataAddr.ready := dbus2axi.io.dbus.ready &&  extPort.writeDataAddr.valid
    extPort.readData.valid      := dbus2axi.io.dbus.ready && extPort.readDataAddr.valid
    extPort.readData.bits       := dbus2axi.io.dbus.rdata
    fabricMux.io.periBusy(2)   := extPort.readDataAddr.valid || extPort.writeDataAddr.valid

    io.axi_master <> dbus2axi.io.axi
  } else {
    extPort.readDataAddr.ready  := false.B
    extPort.writeDataAddr.ready := false.B
    extPort.readData.valid      := false.B
    extPort.readData.bits       := 0.U
    // Tie off unused AXI master port
    io.axi_master.read.addr.valid        := false.B
    io.axi_master.read.addr.bits         := 0.U.asTypeOf(io.axi_master.read.addr.bits)
    io.axi_master.read.data.ready        := false.B
    io.axi_master.write.addr.valid       := false.B
    io.axi_master.write.addr.bits        := 0.U.asTypeOf(io.axi_master.write.addr.bits)
    io.axi_master.write.data.valid       := false.B
    io.axi_master.write.data.bits        := 0.U.asTypeOf(io.axi_master.write.data.bits)
    io.axi_master.write.resp.ready       := false.B
  }

  // ── AXI slave back-door ────────────────────────────────────────────────────
  if (p.useAxi || p.useTlul) {
    val axiSlv = Module(new AxiSlave(p))
    axiSlv.io.axi    <> io.axi_slave
    axiSlv.io.csrAxi <> csrMod.io.axi

    // ITCM back-door (word-addressed → byte-addressed)
    val itcmBdByteAddr = Cat(axiSlv.io.itcm.addr, 0.U(log2Ceil(128 / 8).W))
    itcm.io.backdoor.en    := axiSlv.io.itcm.en
    itcm.io.backdoor.we    := axiSlv.io.itcm.we
    itcm.io.backdoor.addr  := itcmBdByteAddr(log2Ceil(p.itcmSizeBytes) - 1, 0)
    itcm.io.backdoor.wdata := axiSlv.io.itcm.wdata
    itcm.io.backdoor.wmask := axiSlv.io.itcm.wmask
    axiSlv.io.itcm.rdata   := itcm.io.backdoor.rdata

    // DTCM back-door
    val dtcmBdByteAddr = Cat(axiSlv.io.dtcm.addr, 0.U(log2Ceil(128 / 8).W))
    dtcm.io.backdoor.en    := axiSlv.io.dtcm.en
    dtcm.io.backdoor.we    := axiSlv.io.dtcm.we
    dtcm.io.backdoor.addr  := dtcmBdByteAddr(log2Ceil(p.dtcmSizeBytes) - 1, 0)
    dtcm.io.backdoor.wdata := axiSlv.io.dtcm.wdata
    dtcm.io.backdoor.wmask := axiSlv.io.dtcm.wmask
    axiSlv.io.dtcm.rdata   := dtcm.io.backdoor.rdata
  } else {
    // Tie off AXI slave
    csrMod.io.axi.read.addr.valid      := false.B
    csrMod.io.axi.read.addr.bits       := 0.U.asTypeOf(csrMod.io.axi.read.addr.bits)
    csrMod.io.axi.read.data.ready      := false.B
    csrMod.io.axi.write.addr.valid     := false.B
    csrMod.io.axi.write.addr.bits      := 0.U.asTypeOf(csrMod.io.axi.write.addr.bits)
    csrMod.io.axi.write.data.valid     := false.B
    csrMod.io.axi.write.data.bits      := 0.U.asTypeOf(csrMod.io.axi.write.data.bits)
    csrMod.io.axi.write.resp.ready     := false.B
    // Tie off AXI slave inputs
    io.axi_slave.read.addr.ready       := false.B
    io.axi_slave.read.data.valid       := false.B
    io.axi_slave.read.data.bits        := 0.U.asTypeOf(io.axi_slave.read.data.bits)
    io.axi_slave.write.addr.ready      := false.B
    io.axi_slave.write.data.ready      := false.B
    io.axi_slave.write.resp.valid      := false.B
    io.axi_slave.write.resp.bits       := 0.U.asTypeOf(io.axi_slave.write.resp.bits)
  }
}
