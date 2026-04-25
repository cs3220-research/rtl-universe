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

// ── Scalar RISC-V Core (SCore) ────────────────────────────────────────────
// RV32IMF + Zbb + Zicsr implementation.
// Simple in-order FSM pipeline:
//   RESET → FETCH → DECODE → EXECUTE → (multi-cycle: WAIT_DIV) → WRITEBACK
//   Any trap → TRAP
//   WFI → WFISLEEP (wait for interrupt)
//   mpause → PAUSED (permanent halt)
class SCore(p: Parameters) extends Module {

  val io = IO(new Bundle {
    val ibus        = new IBusBundle(p.fetchDataBits)
    val dbus        = new DBusBundle(p.lsuDataBits)
    val irq         = Input(Bool())
    val timer_irq   = Input(Bool())
    val software_irq = Input(Bool())
    val halted      = Output(Bool())
    val wfi         = Output(Bool())
    val fault       = Output(Bool())
    val boot_addr   = Input(UInt(32.W))
    val dispatch    = Vec(4, Valid(new DispatchDebugInfo))
    val dm_req      = Flipped(Decoupled(new DebugModuleReq))
    val dm_rsp      = Decoupled(new DebugModuleRsp)
  })

  // ── Sub-modules ────────────────────────────────────────────────────────────
  val regfile = Module(new Regfile(p, nReadPorts = 4, nWritePorts = 6))
  val alu     = Module(new Alu(p))
  val mlu     = Module(new Mlu(p))
  val dvu     = Module(new Dvu(p))
  val bru     = Module(new Bru(p))
  val lsu     = Module(new Lsu(p))
  val csr     = Module(new Csr(p))
  val debug   = Module(new Debug(p))
  val fault_m = Module(new FaultManager(p))

  // ── FSM states ─────────────────────────────────────────────────────────────
  object State extends ChiselEnum {
    val RESET, FETCH, DECODE, EXECUTE, WAIT_DIV, WAIT_LSU, WRITEBACK, TRAP,
        WFISLEEP, PAUSED = Value
  }
  val state    = RegInit(State.RESET)
  val pc       = RegInit(0.U(32.W))
  val inst     = RegInit(0.U(32.W))
  val decoded  = RegInit(0.U.asTypeOf(new DecodedInstruction))
  val rs1Data  = RegInit(0.U(32.W))
  val rs2Data  = RegInit(0.U(32.W))
  val wbAddr   = RegInit(0.U(5.W))
  val wbData   = RegInit(0.U(32.W))
  val wbValid  = RegInit(false.B)
  val halted   = RegInit(false.B)
  val wfiMode  = RegInit(false.B)

  // ── Fetch bus defaults ─────────────────────────────────────────────────────
  io.ibus.valid := false.B
  io.ibus.addr  := pc

  // ── Data bus defaults (driven by LSU) ──────────────────────────────────────
  io.dbus <> lsu.io.dbus

  // ── Regfile: scoreboard defaults ──────────────────────────────────────────
  regfile.io.scoreboard_set := 0.U
  for (i <- 0 until 4) {
    regfile.io.read_ports(i).valid := false.B
    regfile.io.read_ports(i).addr  := 0.U
  }
  for (i <- 0 until 6) {
    regfile.io.write_ports(i).valid := false.B
    regfile.io.write_ports(i).addr  := 0.U
    regfile.io.write_ports(i).data  := 0.U
  }

  // ── ALU defaults ───────────────────────────────────────────────────────────
  alu.io.req.valid      := false.B
  alu.io.req.bits.addr  := 0.U
  alu.io.req.bits.op    := AluOp.SEXTB
  alu.io.rs1.valid      := false.B
  alu.io.rs1.data       := 0.U
  alu.io.rs2.valid      := false.B
  alu.io.rs2.data       := 0.U

  // ── MLU defaults ───────────────────────────────────────────────────────────
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

  // ── DVU defaults ───────────────────────────────────────────────────────────
  dvu.io.req.valid     := false.B
  dvu.io.req.bits.addr := 0.U
  dvu.io.req.bits.op   := DvuOp.DIV
  dvu.io.rs1.valid     := false.B
  dvu.io.rs1.data      := 0.U
  dvu.io.rs2.valid     := false.B
  dvu.io.rs2.data      := 0.U
  dvu.io.rd.ready      := false.B

  // ── BRU defaults ───────────────────────────────────────────────────────────
  bru.io.req.valid         := false.B
  bru.io.req.bits.op       := BruOp.BEQ
  bru.io.req.bits.pc       := 0.U
  bru.io.req.bits.imm      := 0.S
  bru.io.req.bits.rs1      := 0.U
  bru.io.req.bits.rs2      := 0.U
  bru.io.req.bits.rdAddr   := 0.U

  // ── LSU defaults ───────────────────────────────────────────────────────────
  lsu.io.req.valid         := false.B
  lsu.io.req.bits.addr     := 0.U
  lsu.io.req.bits.rdAddr   := 0.U
  lsu.io.req.bits.op       := LsuOp.LOAD
  lsu.io.req.bits.wdata    := 0.U
  lsu.io.req.bits.size     := 0.U
  lsu.io.req.bits.signExt  := false.B

  // ── CSR defaults ───────────────────────────────────────────────────────────
  csr.io.req.valid        := false.B
  csr.io.req.bits.addr    := 0.U
  csr.io.req.bits.op      := CsrOp.CSRRS
  csr.io.req.bits.wdata   := 0.U
  csr.io.req.bits.rdAddr  := 0.U
  csr.io.irq              := io.irq
  csr.io.timer_irq        := io.timer_irq
  csr.io.software_irq     := io.software_irq
  csr.io.trap_entry       := false.B
  csr.io.trap_cause       := 0.U
  csr.io.trap_pc          := 0.U
  csr.io.mret             := false.B
  // CsrReadPort value(0) = boot address
  csr.io.value.value(0)   := io.boot_addr  // override: boot_addr from external

  // ── Debug module ───────────────────────────────────────────────────────────
  debug.io.req      <> io.dm_req
  debug.io.rsp      <> io.dm_rsp
  for (i <- 0 until 32) {
    debug.io.regfile_read(i) := regfile.io.read_ports(0).data  // placeholder
  }
  debug.io.pc     := pc
  debug.io.halted := halted

  // ── Fault manager ──────────────────────────────────────────────────────────
  fault_m.io.report_fault := false.B
  fault_m.io.fault_cause  := 0.U
  io.fault := fault_m.io.fault

  // ── Dispatch debug (slot 0 only for single-issue) ─────────────────────────
  for (i <- 0 until 4) {
    io.dispatch(i).valid           := false.B
    io.dispatch(i).bits.instFire   := false.B
    io.dispatch(i).bits.instAddr   := 0.U
    io.dispatch(i).bits.instInst   := 0.U
  }

  // ── Interrupt checking ─────────────────────────────────────────────────────
  val mieReg    = csr.io.mie
  val mstatusReg = csr.io.mstatus
  val mieGlobal = mstatusReg(3)   // mstatus.MIE

  val extIrqPending = io.irq         && mieReg(11) && mieGlobal
  val timIrqPending = io.timer_irq   && mieReg(7)  && mieGlobal
  val swiIrqPending = io.software_irq && mieReg(3) && mieGlobal
  val anyIrqPending = extIrqPending || timIrqPending || swiIrqPending

  // Trap cause for interrupts
  val irqCause = Wire(UInt(32.W))
  irqCause := MuxCase("h80000003".U, Seq(
    extIrqPending -> "h8000000b".U,   // Machine external interrupt
    timIrqPending -> "h80000007".U,   // Machine timer interrupt
    swiIrqPending -> "h80000003".U    // Machine software interrupt
  ))

  // ── FSM ────────────────────────────────────────────────────────────────────
  io.halted := halted
  io.wfi    := wfiMode

  switch (state) {

    // ── RESET ─────────────────────────────────────────────────────────────────
    is (State.RESET) {
      pc    := io.boot_addr
      state := State.FETCH
    }

    // ── FETCH ─────────────────────────────────────────────────────────────────
    is (State.FETCH) {
      io.ibus.valid := true.B
      io.ibus.addr  := pc

      when (io.ibus.ready) {
        // Pick first instruction from the fetch word
        val fetchInst = Wire(UInt(32.W))
        fetchInst := io.ibus.rdata(31, 0)

        inst  := fetchInst
        state := State.DECODE
      }
    }

    // ── DECODE ────────────────────────────────────────────────────────────────
    is (State.DECODE) {
      val dec = Decoder.decode(inst)
      decoded := dec

      // Read source registers
      regfile.io.read_ports(0).valid := dec.rs1Valid
      regfile.io.read_ports(0).addr  := dec.rs1Addr
      regfile.io.read_ports(1).valid := dec.rs2Valid
      regfile.io.read_ports(1).addr  := dec.rs2Addr

      rs1Data := regfile.io.read_ports(0).data
      rs2Data := regfile.io.read_ports(1).data

      // Emit dispatch debug info
      io.dispatch(0).valid           := true.B
      io.dispatch(0).bits.instFire   := true.B
      io.dispatch(0).bits.instAddr   := pc
      io.dispatch(0).bits.instInst   := inst

      state := State.EXECUTE
    }

    // ── EXECUTE ───────────────────────────────────────────────────────────────
    is (State.EXECUTE) {
      val dec  = decoded
      val opcode = inst(6,0)

      // ── RV32I base ALU operations ─────────────────────────────────────────
      // Compute result using combinational logic
      val aluResult = Wire(UInt(32.W))
      aluResult := 0.U

      val funct3 = inst(14,12)
      val funct7 = inst(31,25)
      val imm    = dec.imm
      val rs1    = rs1Data
      val rs2    = rs2Data

      // Base integer ALU for non-Zbb ops
      when (opcode === "b0110011".U || opcode === "b0010011".U) {
        // OP and OP-IMM
        val operand2 = Mux(opcode === "b0010011".U, imm.asUInt, rs2)
        switch (funct3) {
          is ("b000".U) {  // ADD/SUB/ADDI
            aluResult := Mux(opcode === "b0110011".U && funct7(5),
                             rs1 - operand2, rs1 + operand2)
          }
          is ("b001".U) {  // SLL/SLLI
            aluResult := rs1 << operand2(4,0)
          }
          is ("b010".U) {  // SLT/SLTI
            aluResult := Mux(rs1.asSInt < operand2.asSInt, 1.U, 0.U)
          }
          is ("b011".U) {  // SLTU/SLTIU
            aluResult := Mux(rs1 < operand2, 1.U, 0.U)
          }
          is ("b100".U) {  // XOR/XORI
            aluResult := rs1 ^ operand2
          }
          is ("b101".U) {  // SRL/SRA/SRLI/SRAI
            aluResult := Mux(funct7(5), (rs1.asSInt >> operand2(4,0)).asUInt,
                                         rs1 >> operand2(4,0))
          }
          is ("b110".U) {  // OR/ORI
            aluResult := rs1 | operand2
          }
          is ("b111".U) {  // AND/ANDI
            aluResult := rs1 & operand2
          }
        }
      }

      // LUI: load upper immediate
      when (opcode === "b0110111".U) {
        aluResult := imm.asUInt
      }

      // AUIPC
      when (opcode === "b0010111".U) {
        aluResult := (pc.asSInt + imm).asUInt
      }

      // ── Special instructions ───────────────────────────────────────────────
      when (dec.isMpause) {
        halted := true.B
        state  := State.PAUSED
      } .elsewhen (dec.isWfi) {
        wfiMode := true.B
        state   := State.WFISLEEP
      } .elsewhen (dec.isEcall) {
        csr.io.trap_entry := true.B
        csr.io.trap_cause := "h0000000b".U  // Environment call from M-mode
        csr.io.trap_pc    := pc
        pc    := csr.io.mtvec
        state := State.FETCH
      } .elsewhen (dec.isEbreak) {
        csr.io.trap_entry := true.B
        csr.io.trap_cause := "h00000003".U  // Breakpoint
        csr.io.trap_pc    := pc
        pc    := csr.io.mtvec
        state := State.FETCH
      } .elsewhen (dec.isMret) {
        csr.io.mret := true.B
        pc    := csr.io.mepc
        state := State.FETCH
      } .elsewhen (dec.isCsr) {
        // CSR instruction
        val csrWdata = Mux(funct3(2),
          inst(19,15).zext.asUInt,  // zimm for *I variants
          rs1)
        csr.io.req.valid       := true.B
        csr.io.req.bits.addr   := dec.csrAddr
        csr.io.req.bits.wdata  := csrWdata
        csr.io.req.bits.rdAddr := dec.rdAddr

        val csrOpCode = Wire(CsrOp())
        csrOpCode := CsrOp.CSRRS
        switch (funct3) {
          is ("b001".U) { csrOpCode := CsrOp.CSRRW  }
          is ("b010".U) { csrOpCode := CsrOp.CSRRS  }
          is ("b011".U) { csrOpCode := CsrOp.CSRRC  }
          is ("b101".U) { csrOpCode := CsrOp.CSRRWI }
          is ("b110".U) { csrOpCode := CsrOp.CSRRSI }
          is ("b111".U) { csrOpCode := CsrOp.CSRRCI }
        }
        csr.io.req.bits.op := csrOpCode

        when (csr.io.rd.valid && dec.rdValid) {
          regfile.io.write_ports(0).valid := true.B
          regfile.io.write_ports(0).addr  := dec.rdAddr
          regfile.io.write_ports(0).data  := csr.io.rd.bits.data
        }

        pc    := pc + 4.U
        state := State.FETCH
      } .elsewhen (dec.isBranch || dec.isJump) {
        // Branch / jump
        val bruOp = Wire(BruOp())
        bruOp := BruOp.BEQ
        when (dec.isJump) {
          bruOp := Mux(opcode === "b1100111".U, BruOp.JALR, BruOp.JAL)
        } .otherwise {
          switch (funct3) {
            is ("b000".U) { bruOp := BruOp.BEQ  }
            is ("b001".U) { bruOp := BruOp.BNE  }
            is ("b100".U) { bruOp := BruOp.BLT  }
            is ("b101".U) { bruOp := BruOp.BGE  }
            is ("b110".U) { bruOp := BruOp.BLTU }
            is ("b111".U) { bruOp := BruOp.BGEU }
          }
        }

        bru.io.req.valid       := true.B
        bru.io.req.bits.op     := bruOp
        bru.io.req.bits.pc     := pc
        bru.io.req.bits.imm    := dec.imm
        bru.io.req.bits.rs1    := rs1
        bru.io.req.bits.rs2    := rs2
        bru.io.req.bits.rdAddr := dec.rdAddr

        when (bru.io.branch.valid) {
          pc := bru.io.branch.bits
        } .otherwise {
          pc := pc + 4.U
        }

        when (bru.io.rd.valid) {
          regfile.io.write_ports(0).valid := true.B
          regfile.io.write_ports(0).addr  := bru.io.rd.bits.addr
          regfile.io.write_ports(0).data  := bru.io.rd.bits.data
        }

        state := State.FETCH
      } .elsewhen (dec.isLoad || dec.isStore) {
        val lsuOp = Mux(dec.isLoad, LsuOp.LOAD, LsuOp.STORE)
        lsu.io.req.valid        := true.B
        lsu.io.req.bits.addr    := (rs1.asSInt + dec.imm).asUInt
        lsu.io.req.bits.rdAddr  := dec.rdAddr
        lsu.io.req.bits.op      := lsuOp
        lsu.io.req.bits.wdata   := rs2
        lsu.io.req.bits.size    := dec.size
        lsu.io.req.bits.signExt := dec.signExtLoad
        state := State.WAIT_LSU
      } .elsewhen (dec.isMul) {
        mlu.io.req(0).valid      := true.B
        mlu.io.req(0).bits.addr  := dec.rdAddr
        mlu.io.req(0).bits.op    := dec.mluOp
        mlu.io.rs1(0).valid      := true.B
        mlu.io.rs1(0).data       := rs1
        mlu.io.rs2(0).valid      := true.B
        mlu.io.rs2(0).data       := rs2
        mlu.io.rd.ready          := true.B
        // Check if result ready this cycle (combinational from regNext)
        state := State.WRITEBACK
        pc := pc + 4.U
      } .elsewhen (dec.isDiv) {
        dvu.io.req.valid     := true.B
        dvu.io.req.bits.addr := dec.rdAddr
        dvu.io.req.bits.op   := dec.dvuOp
        dvu.io.rs1.valid     := true.B
        dvu.io.rs1.data      := rs1
        dvu.io.rs2.valid     := true.B
        dvu.io.rs2.data      := rs2
        state := State.WAIT_DIV
        pc    := pc + 4.U
      } .elsewhen (dec.isAlu2) {
        // Zbb ALU – 1-cycle registered result; wait in WRITEBACK
        alu.io.req.valid      := true.B
        alu.io.req.bits.addr  := dec.rdAddr
        alu.io.req.bits.op    := dec.aluOp
        alu.io.rs1.valid      := true.B
        alu.io.rs1.data       := rs1
        alu.io.rs2.valid      := true.B
        alu.io.rs2.data       := rs2
        state := State.WRITEBACK
        pc    := pc + 4.U
      } .otherwise {
        // Default: simple rd write-back
        when (dec.rdValid && dec.rdAddr =/= 0.U) {
          regfile.io.write_ports(0).valid := true.B
          regfile.io.write_ports(0).addr  := dec.rdAddr
          regfile.io.write_ports(0).data  := aluResult
        }
        pc    := pc + 4.U
        state := State.FETCH

        // Check interrupts at each instruction retirement
        when (anyIrqPending) {
          csr.io.trap_entry := true.B
          csr.io.trap_cause := irqCause
          csr.io.trap_pc    := pc + 4.U
          pc    := csr.io.mtvec
          state := State.FETCH
        }
      }
    }

    // ── WAIT_LSU ──────────────────────────────────────────────────────────────
    is (State.WAIT_LSU) {
      when (!lsu.io.busy) {
        when (lsu.io.rd.valid) {
          regfile.io.write_ports(0).valid := true.B
          regfile.io.write_ports(0).addr  := lsu.io.rd.bits.addr
          regfile.io.write_ports(0).data  := lsu.io.rd.bits.data
        }
        pc    := pc + 4.U
        state := State.FETCH
      }
    }

    // ── WAIT_DIV ──────────────────────────────────────────────────────────────
    is (State.WAIT_DIV) {
      dvu.io.rd.ready := true.B
      when (dvu.io.rd.valid) {
        regfile.io.write_ports(0).valid := true.B
        regfile.io.write_ports(0).addr  := dvu.io.rd.bits.addr
        regfile.io.write_ports(0).data  := dvu.io.rd.bits.data
        state := State.FETCH
      }
    }

    // ── WRITEBACK ──────────────────────────────────────────────────────────────
    // Used after Zbb ALU (1 cycle) or Mul (1 cycle)
    is (State.WRITEBACK) {
      when (alu.io.rd.valid) {
        regfile.io.write_ports(0).valid := true.B
        regfile.io.write_ports(0).addr  := alu.io.rd.bits.addr
        regfile.io.write_ports(0).data  := alu.io.rd.bits.data
        state := State.FETCH
      }
      mlu.io.rd.ready := true.B
      when (mlu.io.rd.valid) {
        regfile.io.write_ports(0).valid := true.B
        regfile.io.write_ports(0).addr  := mlu.io.rd.bits.addr
        regfile.io.write_ports(0).data  := mlu.io.rd.bits.data
        state := State.FETCH
      }
    }

    // ── TRAP ─────────────────────────────────────────────────────────────────
    is (State.TRAP) {
      pc    := csr.io.mtvec
      state := State.FETCH
    }

    // ── WFISLEEP ──────────────────────────────────────────────────────────────
    is (State.WFISLEEP) {
      when (anyIrqPending) {
        wfiMode := false.B
        // Take interrupt
        csr.io.trap_entry := true.B
        csr.io.trap_cause := irqCause
        csr.io.trap_pc    := pc + 4.U
        pc    := csr.io.mtvec
        state := State.FETCH
      }
    }

    // ── PAUSED ────────────────────────────────────────────────────────────────
    is (State.PAUSED) {
      // Permanent halt after mpause
      halted := true.B
    }
  }
}
