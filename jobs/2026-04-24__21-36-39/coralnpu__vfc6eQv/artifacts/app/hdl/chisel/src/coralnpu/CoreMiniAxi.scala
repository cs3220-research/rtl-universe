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
import bus._

/** Debug IO for CoreMiniAxi – all fields are outputs from the core. */
class CoreMiniAxiDebugIO(p: Parameters) extends Bundle {
  val en                          = Output(UInt(4.W))
  val cycles                      = Output(UInt(32.W))
  val addr_0                      = Output(UInt(32.W))
  val addr_1                      = Output(UInt(32.W))
  val addr_2                      = Output(UInt(32.W))
  val addr_3                      = Output(UInt(32.W))
  val inst_0                      = Output(UInt(32.W))
  val inst_1                      = Output(UInt(32.W))
  val inst_2                      = Output(UInt(32.W))
  val inst_3                      = Output(UInt(32.W))
  val dbus_valid                  = Output(Bool())
  val dbus_bits_addr              = Output(UInt(32.W))
  val dbus_bits_wdata             = Output(UInt(p.lsuDataBits.W))
  val dbus_bits_write             = Output(Bool())
  val dispatch_0_instFire         = Output(Bool())
  val dispatch_1_instFire         = Output(Bool())
  val dispatch_2_instFire         = Output(Bool())
  val dispatch_3_instFire         = Output(Bool())
  val dispatch_0_instAddr         = Output(UInt(32.W))
  val dispatch_1_instAddr         = Output(UInt(32.W))
  val dispatch_2_instAddr         = Output(UInt(32.W))
  val dispatch_3_instAddr         = Output(UInt(32.W))
  val dispatch_0_instInst         = Output(UInt(32.W))
  val dispatch_1_instInst         = Output(UInt(32.W))
  val dispatch_2_instInst         = Output(UInt(32.W))
  val dispatch_3_instInst         = Output(UInt(32.W))
  val regfile_writeAddr_0_valid   = Output(Bool())
  val regfile_writeAddr_1_valid   = Output(Bool())
  val regfile_writeAddr_2_valid   = Output(Bool())
  val regfile_writeAddr_3_valid   = Output(Bool())
  val regfile_writeAddr_0_bits    = Output(UInt(5.W))
  val regfile_writeAddr_1_bits    = Output(UInt(5.W))
  val regfile_writeAddr_2_bits    = Output(UInt(5.W))
  val regfile_writeAddr_3_bits    = Output(UInt(5.W))
  val regfile_writeData_0_valid   = Output(Bool())
  val regfile_writeData_1_valid   = Output(Bool())
  val regfile_writeData_2_valid   = Output(Bool())
  val regfile_writeData_3_valid   = Output(Bool())
  val regfile_writeData_4_valid   = Output(Bool())
  val regfile_writeData_5_valid   = Output(Bool())
  val regfile_writeData_0_bits_addr = Output(UInt(5.W))
  val regfile_writeData_1_bits_addr = Output(UInt(5.W))
  val regfile_writeData_2_bits_addr = Output(UInt(5.W))
  val regfile_writeData_3_bits_addr = Output(UInt(5.W))
  val regfile_writeData_4_bits_addr = Output(UInt(5.W))
  val regfile_writeData_5_bits_addr = Output(UInt(5.W))
  val regfile_writeData_0_bits_data = Output(UInt(32.W))
  val regfile_writeData_1_bits_data = Output(UInt(32.W))
  val regfile_writeData_2_bits_data = Output(UInt(32.W))
  val regfile_writeData_3_bits_data = Output(UInt(32.W))
  val regfile_writeData_4_bits_data = Output(UInt(32.W))
  val regfile_writeData_5_bits_data = Output(UInt(32.W))
  // Float debug (present when enableFloat, included unconditionally to match testbench)
  val float_writeAddr_valid       = Output(Bool())
  val float_writeAddr_bits        = Output(UInt(5.W))
  val float_writeData_0_valid     = Output(Bool())
  val float_writeData_1_valid     = Output(Bool())
  val float_writeData_0_bits_addr = Output(UInt(32.W))
  val float_writeData_1_bits_addr = Output(UInt(32.W))
  val float_writeData_0_bits_data = Output(UInt(32.W))
  val float_writeData_1_bits_data = Output(UInt(32.W))
  // Retirement buffer debug (KP_retirementBufferSize = 4 slots)
  private val rbDataW = if (p.enableRvv) 128 else 32
  private val rbIdxW  = 2
  val rb_inst_0_valid      = Output(Bool())
  val rb_inst_0_bits_pc    = Output(UInt(32.W))
  val rb_inst_0_bits_inst  = Output(UInt(32.W))
  val rb_inst_0_bits_idx   = Output(UInt(rbIdxW.W))
  val rb_inst_0_bits_data  = Output(UInt(rbDataW.W))
  val rb_inst_0_bits_trap  = Output(Bool())
  val rb_inst_1_valid      = Output(Bool())
  val rb_inst_1_bits_pc    = Output(UInt(32.W))
  val rb_inst_1_bits_inst  = Output(UInt(32.W))
  val rb_inst_1_bits_idx   = Output(UInt(rbIdxW.W))
  val rb_inst_1_bits_data  = Output(UInt(rbDataW.W))
  val rb_inst_1_bits_trap  = Output(Bool())
  val rb_inst_2_valid      = Output(Bool())
  val rb_inst_2_bits_pc    = Output(UInt(32.W))
  val rb_inst_2_bits_inst  = Output(UInt(32.W))
  val rb_inst_2_bits_idx   = Output(UInt(rbIdxW.W))
  val rb_inst_2_bits_data  = Output(UInt(rbDataW.W))
  val rb_inst_2_bits_trap  = Output(Bool())
  val rb_inst_3_valid      = Output(Bool())
  val rb_inst_3_bits_pc    = Output(UInt(32.W))
  val rb_inst_3_bits_inst  = Output(UInt(32.W))
  val rb_inst_3_bits_idx   = Output(UInt(rbIdxW.W))
  val rb_inst_3_bits_data  = Output(UInt(rbDataW.W))
  val rb_inst_3_bits_trap  = Output(Bool())
  // RVV vector write debug (included when enableRvv, 8 vec writes per slot)
  val rb_inst_0_bits_vecWrites_0_valid      = Output(Bool())
  val rb_inst_0_bits_vecWrites_0_bits_data  = Output(UInt(128.W))
  val rb_inst_0_bits_vecWrites_0_bits_idx   = Output(UInt(5.W))
  val rb_inst_0_bits_vecWrites_1_valid      = Output(Bool())
  val rb_inst_0_bits_vecWrites_1_bits_data  = Output(UInt(128.W))
  val rb_inst_0_bits_vecWrites_1_bits_idx   = Output(UInt(5.W))
  val rb_inst_0_bits_vecWrites_2_valid      = Output(Bool())
  val rb_inst_0_bits_vecWrites_2_bits_data  = Output(UInt(128.W))
  val rb_inst_0_bits_vecWrites_2_bits_idx   = Output(UInt(5.W))
  val rb_inst_0_bits_vecWrites_3_valid      = Output(Bool())
  val rb_inst_0_bits_vecWrites_3_bits_data  = Output(UInt(128.W))
  val rb_inst_0_bits_vecWrites_3_bits_idx   = Output(UInt(5.W))
  val rb_inst_0_bits_vecWrites_4_valid      = Output(Bool())
  val rb_inst_0_bits_vecWrites_4_bits_data  = Output(UInt(128.W))
  val rb_inst_0_bits_vecWrites_4_bits_idx   = Output(UInt(5.W))
  val rb_inst_0_bits_vecWrites_5_valid      = Output(Bool())
  val rb_inst_0_bits_vecWrites_5_bits_data  = Output(UInt(128.W))
  val rb_inst_0_bits_vecWrites_5_bits_idx   = Output(UInt(5.W))
  val rb_inst_0_bits_vecWrites_6_valid      = Output(Bool())
  val rb_inst_0_bits_vecWrites_6_bits_data  = Output(UInt(128.W))
  val rb_inst_0_bits_vecWrites_6_bits_idx   = Output(UInt(5.W))
  val rb_inst_0_bits_vecWrites_7_valid      = Output(Bool())
  val rb_inst_0_bits_vecWrites_7_bits_data  = Output(UInt(128.W))
  val rb_inst_0_bits_vecWrites_7_bits_idx   = Output(UInt(5.W))
  val rb_inst_1_bits_vecWrites_0_valid      = Output(Bool())
  val rb_inst_1_bits_vecWrites_0_bits_data  = Output(UInt(128.W))
  val rb_inst_1_bits_vecWrites_0_bits_idx   = Output(UInt(5.W))
  val rb_inst_1_bits_vecWrites_1_valid      = Output(Bool())
  val rb_inst_1_bits_vecWrites_1_bits_data  = Output(UInt(128.W))
  val rb_inst_1_bits_vecWrites_1_bits_idx   = Output(UInt(5.W))
  val rb_inst_1_bits_vecWrites_2_valid      = Output(Bool())
  val rb_inst_1_bits_vecWrites_2_bits_data  = Output(UInt(128.W))
  val rb_inst_1_bits_vecWrites_2_bits_idx   = Output(UInt(5.W))
  val rb_inst_1_bits_vecWrites_3_valid      = Output(Bool())
  val rb_inst_1_bits_vecWrites_3_bits_data  = Output(UInt(128.W))
  val rb_inst_1_bits_vecWrites_3_bits_idx   = Output(UInt(5.W))
  val rb_inst_1_bits_vecWrites_4_valid      = Output(Bool())
  val rb_inst_1_bits_vecWrites_4_bits_data  = Output(UInt(128.W))
  val rb_inst_1_bits_vecWrites_4_bits_idx   = Output(UInt(5.W))
  val rb_inst_1_bits_vecWrites_5_valid      = Output(Bool())
  val rb_inst_1_bits_vecWrites_5_bits_data  = Output(UInt(128.W))
  val rb_inst_1_bits_vecWrites_5_bits_idx   = Output(UInt(5.W))
  val rb_inst_1_bits_vecWrites_6_valid      = Output(Bool())
  val rb_inst_1_bits_vecWrites_6_bits_data  = Output(UInt(128.W))
  val rb_inst_1_bits_vecWrites_6_bits_idx   = Output(UInt(5.W))
  val rb_inst_1_bits_vecWrites_7_valid      = Output(Bool())
  val rb_inst_1_bits_vecWrites_7_bits_data  = Output(UInt(128.W))
  val rb_inst_1_bits_vecWrites_7_bits_idx   = Output(UInt(5.W))
  val rb_inst_2_bits_vecWrites_0_valid      = Output(Bool())
  val rb_inst_2_bits_vecWrites_0_bits_data  = Output(UInt(128.W))
  val rb_inst_2_bits_vecWrites_0_bits_idx   = Output(UInt(5.W))
  val rb_inst_2_bits_vecWrites_1_valid      = Output(Bool())
  val rb_inst_2_bits_vecWrites_1_bits_data  = Output(UInt(128.W))
  val rb_inst_2_bits_vecWrites_1_bits_idx   = Output(UInt(5.W))
  val rb_inst_2_bits_vecWrites_2_valid      = Output(Bool())
  val rb_inst_2_bits_vecWrites_2_bits_data  = Output(UInt(128.W))
  val rb_inst_2_bits_vecWrites_2_bits_idx   = Output(UInt(5.W))
  val rb_inst_2_bits_vecWrites_3_valid      = Output(Bool())
  val rb_inst_2_bits_vecWrites_3_bits_data  = Output(UInt(128.W))
  val rb_inst_2_bits_vecWrites_3_bits_idx   = Output(UInt(5.W))
  val rb_inst_2_bits_vecWrites_4_valid      = Output(Bool())
  val rb_inst_2_bits_vecWrites_4_bits_data  = Output(UInt(128.W))
  val rb_inst_2_bits_vecWrites_4_bits_idx   = Output(UInt(5.W))
  val rb_inst_2_bits_vecWrites_5_valid      = Output(Bool())
  val rb_inst_2_bits_vecWrites_5_bits_data  = Output(UInt(128.W))
  val rb_inst_2_bits_vecWrites_5_bits_idx   = Output(UInt(5.W))
  val rb_inst_2_bits_vecWrites_6_valid      = Output(Bool())
  val rb_inst_2_bits_vecWrites_6_bits_data  = Output(UInt(128.W))
  val rb_inst_2_bits_vecWrites_6_bits_idx   = Output(UInt(5.W))
  val rb_inst_2_bits_vecWrites_7_valid      = Output(Bool())
  val rb_inst_2_bits_vecWrites_7_bits_data  = Output(UInt(128.W))
  val rb_inst_2_bits_vecWrites_7_bits_idx   = Output(UInt(5.W))
  val rb_inst_3_bits_vecWrites_0_valid      = Output(Bool())
  val rb_inst_3_bits_vecWrites_0_bits_data  = Output(UInt(128.W))
  val rb_inst_3_bits_vecWrites_0_bits_idx   = Output(UInt(5.W))
  val rb_inst_3_bits_vecWrites_1_valid      = Output(Bool())
  val rb_inst_3_bits_vecWrites_1_bits_data  = Output(UInt(128.W))
  val rb_inst_3_bits_vecWrites_1_bits_idx   = Output(UInt(5.W))
  val rb_inst_3_bits_vecWrites_2_valid      = Output(Bool())
  val rb_inst_3_bits_vecWrites_2_bits_data  = Output(UInt(128.W))
  val rb_inst_3_bits_vecWrites_2_bits_idx   = Output(UInt(5.W))
  val rb_inst_3_bits_vecWrites_3_valid      = Output(Bool())
  val rb_inst_3_bits_vecWrites_3_bits_data  = Output(UInt(128.W))
  val rb_inst_3_bits_vecWrites_3_bits_idx   = Output(UInt(5.W))
  val rb_inst_3_bits_vecWrites_4_valid      = Output(Bool())
  val rb_inst_3_bits_vecWrites_4_bits_data  = Output(UInt(128.W))
  val rb_inst_3_bits_vecWrites_4_bits_idx   = Output(UInt(5.W))
  val rb_inst_3_bits_vecWrites_5_valid      = Output(Bool())
  val rb_inst_3_bits_vecWrites_5_bits_data  = Output(UInt(128.W))
  val rb_inst_3_bits_vecWrites_5_bits_idx   = Output(UInt(5.W))
  val rb_inst_3_bits_vecWrites_6_valid      = Output(Bool())
  val rb_inst_3_bits_vecWrites_6_bits_data  = Output(UInt(128.W))
  val rb_inst_3_bits_vecWrites_6_bits_idx   = Output(UInt(5.W))
  val rb_inst_3_bits_vecWrites_7_valid      = Output(Bool())
  val rb_inst_3_bits_vecWrites_7_bits_data  = Output(UInt(128.W))
  val rb_inst_3_bits_vecWrites_7_bits_idx   = Output(UInt(5.W))
}

/** Debug Module interface for CoreMiniAxi. */
class CoreMiniAxiDmIO extends Bundle {
  val req_valid        = Input(Bool())
  val req_ready        = Output(Bool())
  val req_bits_address = Input(UInt(32.W))
  val req_bits_data    = Input(UInt(32.W))
  val req_bits_op      = Input(UInt(2.W))
  val rsp_valid        = Output(Bool())
  val rsp_ready        = Input(Bool())
  val rsp_bits_data    = Output(UInt(32.W))
  val rsp_bits_op      = Output(UInt(2.W))
}

/**
  * CoreMiniAxi – top-level processor module with AXI4 master and slave ports.
  *
  * Uses RawModule so the clock and reset are explicit ports (io_aclk, io_aresetn)
  * compatible with AXI naming conventions and the Verilator-based testbenches.
  */
class CoreMiniAxi(p: Parameters) extends RawModule {
  override def desiredName: String = {
    val sizeSuffix =
      if (p.itcmSizeKBytes == 1024 && p.dtcmSizeKBytes == 1024) "Highmem"
      else if (p.itcmSizeKBytes == 512 && p.dtcmSizeKBytes == 512) "_ITCM512KB_DTCM512KB"
      else ""
    p.moduleName + sizeSuffix + "Axi"
  }
  val io = IO(new Bundle {
    val aclk    = Input(Clock())
    val aresetn = Input(Bool())

    // Status
    val halted = Output(Bool())
    val fault  = Output(Bool())
    val wfi    = Output(Bool())

    // Interrupts / control
    val irq          = Input(Bool())
    val timer_irq    = Input(Bool())
    val software_irq = Input(Bool())
    val te           = Input(Bool())
    val boot_addr    = Input(UInt(32.W))

    // AXI master port (core → external memory)
    val axi_master = new AxiMasterIO(p.axi2AddrBits, p.lsuDataBits, p.axi2IdBits)

    // AXI slave port (external controller → core TCMs)
    val axi_slave = Flipped(new AxiMasterIO(p.axi2AddrBits, p.lsuDataBits, p.axi2IdBits))

    // Debug module interface
    val dm = new CoreMiniAxiDmIO

    // Debug observation outputs
    val debug = new CoreMiniAxiDebugIO(p)
  })

  // -------------------------------------------------------------------------
  // Default all outputs to safe/idle values.
  // -------------------------------------------------------------------------
  io.halted := false.B
  io.fault  := false.B
  io.wfi    := false.B

  // AXI master: core is not issuing any transactions
  io.axi_master.read.addr.valid  := false.B
  io.axi_master.read.addr.bits   := 0.U.asTypeOf(new AxiReadAddrChannel(p.axi2AddrBits, p.axi2IdBits))
  io.axi_master.read.data.ready  := false.B
  io.axi_master.write.addr.valid := false.B
  io.axi_master.write.addr.bits  := 0.U.asTypeOf(new AxiWriteAddrChannel(p.axi2AddrBits, p.axi2IdBits))
  io.axi_master.write.data.valid := false.B
  io.axi_master.write.data.bits  := 0.U.asTypeOf(new AxiWriteDataChannel(p.lsuDataBits))
  io.axi_master.write.resp.ready := false.B

  // AXI slave: accept nothing; no responses
  io.axi_slave.read.addr.ready  := false.B
  io.axi_slave.read.data.valid  := false.B
  io.axi_slave.read.data.bits   := 0.U.asTypeOf(new AxiReadDataChannel(p.lsuDataBits, p.axi2IdBits))
  io.axi_slave.write.addr.ready := false.B
  io.axi_slave.write.data.ready := false.B
  io.axi_slave.write.resp.valid := false.B
  io.axi_slave.write.resp.bits  := 0.U.asTypeOf(new AxiWriteRespChannel(p.axi2IdBits))

  // Debug module
  io.dm.req_ready     := false.B
  io.dm.rsp_valid     := false.B
  io.dm.rsp_bits_data := 0.U
  io.dm.rsp_bits_op   := 0.U

  // Debug outputs – all tied to zero for the stub
  io.debug := 0.U.asTypeOf(new CoreMiniAxiDebugIO(p))
}
