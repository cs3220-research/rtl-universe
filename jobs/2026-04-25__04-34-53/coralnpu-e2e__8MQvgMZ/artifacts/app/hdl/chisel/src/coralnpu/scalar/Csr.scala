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

object CsrOp extends ChiselEnum {
  val CSRRS, CSRRC, CSRRW, CSRRSI, CSRRCI, CSRRWI = Value
}

// Machine-mode CSR unit.
// Implements the standard M-mode CSRs required for RV32IM.
class Csr(p: Parameters) extends Module {
  val io = IO(new Bundle {
    val req = Flipped(Valid(new Bundle {
      val addr   = UInt(12.W)
      val op     = CsrOp()
      val wdata  = UInt(32.W)
      val rdAddr = UInt(5.W)
    }))
    val rd = Valid(new Bundle {
      val addr = UInt(5.W)
      val data = UInt(32.W)
    })
    val cg           = Output(Bool())          // clock gate
    val irq          = Input(Bool())           // external interrupt
    val timer_irq    = Input(Bool())
    val software_irq = Input(Bool())
    val trap_entry   = Input(Bool())
    val trap_cause   = Input(UInt(32.W))
    val trap_pc      = Input(UInt(32.W))
    val mret         = Input(Bool())
    val mtvec        = Output(UInt(32.W))
    val mepc         = Output(UInt(32.W))
    val mie          = Output(UInt(32.W))
    val mstatus      = Output(UInt(32.W))
    val value        = new CsrReadPort
  })

  // ── CSR addresses ──────────────────────────────────────────────────────────
  val ADDR_MSTATUS  = "h300".U(12.W)
  val ADDR_MISA     = "h301".U(12.W)
  val ADDR_MIE      = "h304".U(12.W)
  val ADDR_MTVEC    = "h305".U(12.W)
  val ADDR_MSCRATCH = "h340".U(12.W)
  val ADDR_MEPC     = "h341".U(12.W)
  val ADDR_MCAUSE   = "h342".U(12.W)
  val ADDR_MTVAL    = "h343".U(12.W)
  val ADDR_MIP      = "h344".U(12.W)
  val ADDR_MHARTID  = "hf14".U(12.W)
  val ADDR_CYCLE    = "hc00".U(12.W)
  val ADDR_INSTRET  = "hc02".U(12.W)

  // ── Registers ──────────────────────────────────────────────────────────────
  // mstatus: MIE=bit3, MPIE=bit7, MPP=bits12:11 (always 3=M-mode)
  val mstatus  = RegInit("h00001800".U(32.W))  // MPP=11 (M-mode)
  val mie      = RegInit(0.U(32.W))
  val mtvec    = RegInit(0.U(32.W))
  val mscratch = RegInit(0.U(32.W))
  val mepc     = RegInit(0.U(32.W))
  val mcause   = RegInit(0.U(32.W))
  val mtval    = RegInit(0.U(32.W))
  val cycle    = RegInit(0.U(64.W))
  val instret  = RegInit(0.U(64.W))

  // mip: read-only reflection of external interrupt lines
  val mip = Cat(0.U(20.W), io.irq, 0.U(3.W), io.timer_irq, 0.U(3.W), io.software_irq, 0.U(3.W))
  // misa: RV32IMF (I=8, M=12, F=5) – bit 8 = I, bit 12 = M, bit 5 = F; XLEN field bits 31:30=01
  val misa = "h40001104".U(32.W)  // RV32IM base

  cycle := cycle + 1.U

  // ── CSR read (before write) ────────────────────────────────────────────────
  val csrRdata = Wire(UInt(32.W))
  csrRdata := 0.U
  switch (io.req.bits.addr) {
    is (ADDR_MSTATUS)  { csrRdata := mstatus  }
    is (ADDR_MISA)     { csrRdata := misa      }
    is (ADDR_MIE)      { csrRdata := mie       }
    is (ADDR_MTVEC)    { csrRdata := mtvec     }
    is (ADDR_MSCRATCH) { csrRdata := mscratch  }
    is (ADDR_MEPC)     { csrRdata := mepc      }
    is (ADDR_MCAUSE)   { csrRdata := mcause    }
    is (ADDR_MTVAL)    { csrRdata := mtval     }
    is (ADDR_MIP)      { csrRdata := mip       }
    is (ADDR_MHARTID)  { csrRdata := 0.U       }
    is (ADDR_CYCLE)    { csrRdata := cycle(31,0) }
    is (ADDR_INSTRET)  { csrRdata := instret(31,0) }
  }

  // ── CSR write ──────────────────────────────────────────────────────────────
  val wdata  = io.req.bits.wdata
  val op     = io.req.bits.op

  val newVal = Wire(UInt(32.W))
  newVal := MuxCase(csrRdata, Seq(
    (op === CsrOp.CSRRW  || op === CsrOp.CSRRWI) -> wdata,
    (op === CsrOp.CSRRS  || op === CsrOp.CSRRSI) -> (csrRdata | wdata),
    (op === CsrOp.CSRRC  || op === CsrOp.CSRRCI) -> (csrRdata & ~wdata)
  ))

  when (io.req.valid) {
    switch (io.req.bits.addr) {
      is (ADDR_MSTATUS)  { mstatus  := newVal }
      is (ADDR_MIE)      { mie      := newVal }
      is (ADDR_MTVEC)    { mtvec    := newVal }
      is (ADDR_MSCRATCH) { mscratch := newVal }
      is (ADDR_MEPC)     { mepc     := newVal }
      is (ADDR_MCAUSE)   { mcause   := newVal }
      is (ADDR_MTVAL)    { mtval    := newVal }
    }
    instret := instret + 1.U
  }

  // ── Trap entry ─────────────────────────────────────────────────────────────
  when (io.trap_entry) {
    mepc    := io.trap_pc
    mcause  := io.trap_cause
    // Save MIE into MPIE, clear MIE
    mstatus := Cat(mstatus(31,8),
                   mstatus(3),    // MPIE ← old MIE
                   mstatus(6,4),
                   0.U(1.W),      // MIE ← 0
                   mstatus(2,0))
  }

  // ── MRET ───────────────────────────────────────────────────────────────────
  when (io.mret) {
    // Restore MIE from MPIE, set MPIE=1
    mstatus := Cat(mstatus(31,8),
                   1.U(1.W),         // MPIE ← 1
                   mstatus(6,4),
                   mstatus(7),       // MIE ← old MPIE
                   mstatus(2,0))
  }

  // ── Outputs ────────────────────────────────────────────────────────────────
  io.rd.valid      := io.req.valid
  io.rd.bits.addr  := io.req.bits.rdAddr
  io.rd.bits.data  := csrRdata

  io.cg      := false.B  // clock gate: not yet implemented
  io.mtvec   := mtvec
  io.mepc    := mepc
  io.mie     := mie
  io.mstatus := mstatus

  // CsrReadPort: 0=boot_addr(mtvec as proxy), 1=mtvec, 2=mie, 3=mstatus
  io.value.value(0) := 0.U       // boot_addr is set externally; placeholder
  io.value.value(1) := mtvec
  io.value.value(2) := mie
  io.value.value(3) := mstatus
}
