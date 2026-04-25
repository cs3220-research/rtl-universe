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

/** RISC-V CSR operation encoding. */
object CsrOp extends ChiselEnum {
  val CSRRW  = Value  // CSR read/write
  val CSRRS  = Value  // CSR read and set bits
  val CSRRC  = Value  // CSR read and clear bits
  val CSRRWI = Value  // CSR read/write immediate
  val CSRRSI = Value  // CSR read and set bits immediate
  val CSRRCI = Value  // CSR read and clear bits immediate
}

/** Machine-mode CSR addresses (subset used by CoralNPU). */
object CsrAddr {
  val MSTATUS   = 0x300.U(12.W)
  val MISA      = 0x301.U(12.W)
  val MTVEC     = 0x305.U(12.W)
  val MSCRATCH  = 0x340.U(12.W)
  val MEPC      = 0x341.U(12.W)
  val MCAUSE    = 0x342.U(12.W)
  val MTVAL     = 0x343.U(12.W)
  val CYCLE     = 0xC00.U(12.W)
  val INSTRET   = 0xC02.U(12.W)
  // CoralNPU custom: software-visible CSRs
  val CORALNPU_STATUS = 0xFC0.U(12.W)
}

/** CSR access request from the scalar pipeline. */
class CsrRequest extends Bundle {
  val op   = CsrOp()
  val addr = UInt(12.W)
  val rs1  = UInt(32.W)   // source value or zero-extended uimm5 for immediates
  val rd   = UInt(5.W)
}

/** CSR access result. */
class CsrResult extends Bundle {
  val rd      = UInt(5.W)
  val rdData  = UInt(32.W)
  val exception = Bool()
}

/** External (software-facing) CSR value bundle.
  *
  * Used by CoreAxiCSR to expose a set of read-only status registers that
  * software can poll via the AXI CSR port.
  */
class CoralNPUCsr extends Bundle {
  // value(0): CORALNPU_STATUS read by the CoreAxiCSR module
  val value = Vec(1, UInt(32.W))
}

/** Scalar CSR unit.
  *
  * Manages machine-mode CSRs and the CoralNPU custom status register.
  */
class Csr(p: Parameters) extends Module {
  val io = IO(new Bundle {
    val req         = Input(Valid(new CsrRequest))
    val result      = Output(Valid(new CsrResult))
    // External software-visible CSRs
    val coralnpu_csr = Input(new CoralNPUCsr)
    // Trap interface
    val trapPC      = Input(Valid(UInt(32.W)))   // PC where trap occurred
    val trapCause   = Input(UInt(32.W))          // MCAUSE value
    val trapVal     = Input(UInt(32.W))          // MTVAL value
    val mtvec       = Output(UInt(32.W))          // trap vector
    val mepc        = Output(UInt(32.W))          // exception PC
  })

  // -----------------------------------------------------------------------
  // CSR storage registers
  // -----------------------------------------------------------------------
  val mstatus  = RegInit(0.U(32.W))
  val mtvec    = RegInit(0.U(32.W))
  val mscratch = RegInit(0.U(32.W))
  val mepc     = RegInit(0.U(32.W))
  val mcause   = RegInit(0.U(32.W))
  val mtval    = RegInit(0.U(32.W))
  val cycle    = RegInit(0.U(64.W))
  val instret  = RegInit(0.U(64.W))

  // Free-running cycle counter
  cycle := cycle + 1.U

  // Handle trap
  when(io.trapPC.valid) {
    mepc   := io.trapPC.bits
    mcause := io.trapCause
    mtval  := io.trapVal
    // Disable machine-mode interrupts (MIE) on trap
    mstatus := Cat(mstatus(31, 4), 0.U(1.W), mstatus(2, 0))
  }

  io.mtvec := mtvec
  io.mepc  := mepc

  // -----------------------------------------------------------------------
  // CSR read (combinational)
  // -----------------------------------------------------------------------
  val rdData = Wire(UInt(32.W))
  rdData := 0.U
  switch(io.req.bits.addr) {
    is(CsrAddr.MSTATUS)  { rdData := mstatus }
    is(CsrAddr.MTVEC)    { rdData := mtvec }
    is(CsrAddr.MSCRATCH) { rdData := mscratch }
    is(CsrAddr.MEPC)     { rdData := mepc }
    is(CsrAddr.MCAUSE)   { rdData := mcause }
    is(CsrAddr.MTVAL)    { rdData := mtval }
    is(CsrAddr.CYCLE)    { rdData := cycle(31, 0) }
    is(CsrAddr.INSTRET)  { rdData := instret(31, 0) }
    is(CsrAddr.CORALNPU_STATUS) { rdData := io.coralnpu_csr.value(0) }
  }

  // -----------------------------------------------------------------------
  // CSR write
  // -----------------------------------------------------------------------
  when(io.req.valid) {
    val wdata = Wire(UInt(32.W))
    wdata := 0.U
    switch(io.req.bits.op) {
      is(CsrOp.CSRRW, CsrOp.CSRRWI) { wdata := io.req.bits.rs1 }
      is(CsrOp.CSRRS, CsrOp.CSRRSI) { wdata := rdData | io.req.bits.rs1 }
      is(CsrOp.CSRRC, CsrOp.CSRRCI) { wdata := rdData & ~io.req.bits.rs1 }
    }
    switch(io.req.bits.addr) {
      is(CsrAddr.MSTATUS)  { mstatus  := wdata }
      is(CsrAddr.MTVEC)    { mtvec    := wdata }
      is(CsrAddr.MSCRATCH) { mscratch := wdata }
      is(CsrAddr.MEPC)     { mepc     := wdata }
      is(CsrAddr.MCAUSE)   { mcause   := wdata }
      is(CsrAddr.MTVAL)    { mtval    := wdata }
    }
  }

  // -----------------------------------------------------------------------
  // 1-cycle pipeline register
  // -----------------------------------------------------------------------
  val res = Wire(new CsrResult)
  res.rd        := io.req.bits.rd
  res.rdData    := rdData
  res.exception := false.B  // TODO: illegal CSR access detection

  io.result.valid     := RegNext(io.req.valid, init = false.B)
  io.result.bits      := RegNext(res)
}
