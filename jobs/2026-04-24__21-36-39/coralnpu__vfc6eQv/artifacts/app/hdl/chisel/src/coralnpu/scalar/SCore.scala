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

import common.{Fp32, InstructionBuffer}

/** External I/O bundle for the scalar core. */
class SCoreIO(p: Parameters) extends Bundle {
  // Instruction bus (to instruction memory / cache)
  val ibus = new IBus

  // Data bus (to data memory / cache)
  val dbus = new DBus(p)

  // CSR-level control signals (from CoreAxiCSR)
  val pcStart  = Input(UInt(32.W))  // reset PC from software
  val coreReset = Input(Bool())      // soft reset

  // Status outputs
  val halted   = Output(Bool())
  val fault    = Output(Bool())
  val instret  = Output(UInt(64.W)) // retired instruction count

  // External CSR values (read by CoreAxiCSR)
  val coralnpu_csr = Input(new CoralNPUCsr)
}

/** Scalar RISC-V core top-level (rv32imf_Zbb).
  *
  * Integrates: FetchControl/Fetcher → InstructionBuffer → Decode →
  * Regfile/FRegfile → {Alu, Mlu, Dvu, Bru, Fpu, Lsu, Csr} → writeback.
  *
  * This is a stub implementation that wires together the sub-modules
  * with the correct interfaces.  Actual scheduling, forwarding, and
  * out-of-order execution are left as TODO for the full implementation.
  */
class SCore(p: Parameters) extends Module {
  val io = IO(new SCoreIO(p))

  // -----------------------------------------------------------------------
  // Instruction fetch
  // -----------------------------------------------------------------------
  val NumInstsPerFetch = 8
  val BufferDepth      = 16

  val fetchCtrl = Module(new FetchControl(p))
  val fetcher   = Module(new Fetcher(p))

  // CSR read port: expose pcStart as CSR value(0) to FetchControl
  val csrVec = Wire(new CsrReadPort(1))
  csrVec.value(0) := io.pcStart

  fetchCtrl.io.csr <> csrVec

  // Connect fetcher to FetchControl
  fetchCtrl.io.fetchAddr <> fetcher.io.ctrl
  fetcher.io.fetch       <> fetchCtrl.io.fetchData

  // IBus (to instruction memory)
  io.ibus.valid := fetcher.io.ibus.valid
  io.ibus.addr  := fetcher.io.ibus.addr
  fetcher.io.ibus.ready := io.ibus.ready
  fetcher.io.ibus.rdata := io.ibus.rdata

  // -----------------------------------------------------------------------
  // Instruction buffer
  // -----------------------------------------------------------------------
  val ibuf = Module(new InstructionBuffer(UInt(32.W), NumInstsPerFetch, BufferDepth))

  ibuf.io.feedIn.nValid := fetchCtrl.io.bufferRequest.nValid
  fetchCtrl.io.bufferRequest.nReady := ibuf.io.feedIn.nReady
  fetchCtrl.io.bufferSpaces := ibuf.io.nSpace

  for (i <- 0 until NumInstsPerFetch) {
    ibuf.io.feedIn.bits(i) := fetcher.io.fetch.bits.inst(i)
  }
  ibuf.io.flush := false.B

  // -----------------------------------------------------------------------
  // Register file
  // -----------------------------------------------------------------------
  val regfile = Module(new Regfile(p, numReadPorts = 2, numWritePorts = 1))
  regfile.io.scoreboard_set := 0.U
  for (i <- 0 until 2) {
    regfile.io.read_ports(i).valid := false.B
    regfile.io.read_ports(i).addr  := 0.U
  }
  regfile.io.write_ports(0).valid := false.B
  regfile.io.write_ports(0).addr  := 0.U
  regfile.io.write_ports(0).data  := 0.U

  // -----------------------------------------------------------------------
  // Floating-point register file (only instantiated when enableFloat)
  // -----------------------------------------------------------------------
  val fregfile = Module(new FRegfile(p, numReadPorts = 2, numWritePorts = 1))
  fregfile.io.scoreboard_set := 0.U
  for (i <- 0 until 2) {
    fregfile.io.read_ports(i).valid := false.B
    fregfile.io.read_ports(i).addr  := 0.U
  }
  fregfile.io.write_ports(0).valid := false.B
  fregfile.io.write_ports(0).addr  := 0.U
  fregfile.io.write_ports(0).data  := 0.U.asTypeOf(new Fp32)

  // -----------------------------------------------------------------------
  // Execution units (stub connections)
  // -----------------------------------------------------------------------
  val alu = Module(new Alu(p))
  alu.io.req.valid     := false.B
  alu.io.req.bits.addr := 0.U
  alu.io.req.bits.op   := AluOp.SEXTB
  alu.io.rs1.valid     := false.B
  alu.io.rs1.data      := 0.U
  alu.io.rs2.valid     := false.B
  alu.io.rs2.data      := 0.U

  val mlu = Module(new Mlu(p))
  for (i <- 0 until 4) {
    mlu.io.req(i).valid     := false.B
    mlu.io.req(i).bits.addr := 0.U
    mlu.io.req(i).bits.op   := MluOp.MUL
    mlu.io.rs1(i).valid     := false.B
    mlu.io.rs1(i).data      := 0.U
    mlu.io.rs2(i).valid     := false.B
    mlu.io.rs2(i).data      := 0.U
  }
  mlu.io.rd.ready := false.B

  val dvu = Module(new Dvu(p))
  dvu.io.req.valid     := false.B
  dvu.io.req.bits.addr := 0.U
  dvu.io.req.bits.op   := DvuOp.DIV
  dvu.io.rs1.valid     := false.B
  dvu.io.rs1.data      := 0.U
  dvu.io.rs2.valid     := false.B
  dvu.io.rs2.data      := 0.U
  dvu.io.rd.ready      := false.B

  val bru = Module(new Bru(p))
  bru.io.req.valid       := false.B
  bru.io.req.bits.op     := BruOp.BEQ
  bru.io.req.bits.pc     := 0.U
  bru.io.req.bits.rs1    := 0.U
  bru.io.req.bits.rs2    := 0.U
  bru.io.req.bits.imm    := 0.S
  bru.io.req.bits.rd     := 0.U

  val fpu = Module(new Fpu)
  fpu.io.cmd.valid         := false.B
  fpu.io.cmd.bits.optype   := FpuOptype.FpuAdd
  fpu.io.cmd.bits.waddr    := 0.U
  fpu.io.cmd.bits.ina      := 0.U.asTypeOf(new Fp32)
  fpu.io.cmd.bits.inb      := 0.U.asTypeOf(new Fp32)
  fpu.io.cmd.bits.inc      := 0.U.asTypeOf(new Fp32)
  fpu.io.output.ready      := false.B

  val csr = Module(new Csr(p))
  csr.io.req.valid       := false.B
  csr.io.req.bits.op     := CsrOp.CSRRW
  csr.io.req.bits.addr   := 0.U
  csr.io.req.bits.rs1    := 0.U
  csr.io.req.bits.rd     := 0.U
  csr.io.coralnpu_csr    := io.coralnpu_csr
  csr.io.trapPC.valid    := false.B
  csr.io.trapPC.bits     := 0.U
  csr.io.trapCause       := 0.U
  csr.io.trapVal         := 0.U

  // -----------------------------------------------------------------------
  // Branch redirect to fetch controller
  // -----------------------------------------------------------------------
  fetchCtrl.io.branch.valid := bru.io.result.valid && bru.io.result.bits.taken
  fetchCtrl.io.branch.bits  := bru.io.result.bits.target

  // -----------------------------------------------------------------------
  // LSU
  // -----------------------------------------------------------------------
  val lsu = Module(new Lsu(p))
  lsu.io.cmd.valid      := false.B
  lsu.io.cmd.bits.op    := LsuOp.LW
  lsu.io.cmd.bits.addr  := 0.U
  lsu.io.cmd.bits.data  := 0.U
  lsu.io.cmd.bits.rdAddr := 0.U
  lsu.io.resp.ready     := false.B

  io.dbus.valid := lsu.io.dbus.valid
  io.dbus.write := lsu.io.dbus.write
  io.dbus.addr  := lsu.io.dbus.addr
  io.dbus.size  := lsu.io.dbus.size
  io.dbus.wdata := lsu.io.dbus.wdata
  io.dbus.wmask := lsu.io.dbus.wmask
  lsu.io.dbus.ready := io.dbus.ready
  lsu.io.dbus.rdata := io.dbus.rdata
  lsu.io.dbus.fault := io.dbus.fault

  // -----------------------------------------------------------------------
  // Status outputs
  // -----------------------------------------------------------------------
  io.halted  := false.B
  io.fault   := false.B
  io.instret := 0.U

  // Decode stage (wired but not yet scheduling)
  val decode = Module(new Decode(p))
  decode.io.inst  := 0.U
  decode.io.pc    := 0.U
  decode.io.valid := false.B
}
