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

/** Execution unit target for a decoded instruction. */
object ExecUnit extends ChiselEnum {
  val ALU   = Value
  val MLU   = Value
  val DVU   = Value
  val FPU   = Value
  val LSU   = Value
  val BRU   = Value
  val CSR   = Value
  val SYS   = Value  // fence, ecall, ebreak, mret
  val RVV   = Value  // vector instructions
  val NOP   = Value
}

/** Decoded instruction bundle passed to the issue stage. */
class DecodedInst extends Bundle {
  val valid     = Bool()
  val pc        = UInt(32.W)
  val inst      = UInt(32.W)
  val execUnit  = ExecUnit()
  val rs1Addr   = UInt(5.W)
  val rs2Addr   = UInt(5.W)
  val rs1Valid  = Bool()  // instruction uses rs1
  val rs2Valid  = Bool()  // instruction uses rs2
  val rdAddr    = UInt(5.W)
  val rdValid   = Bool()  // instruction writes rd
  val imm       = SInt(32.W)
  val isFloat   = Bool()  // floating-point instruction
  val exception = Bool()  // illegal instruction or other decode fault
}

/** Instruction decoder.
  *
  * Converts a raw 32-bit RISC-V instruction into a DecodedInst bundle.
  * Supports RV32IMAF_Zbb_Zve32x.
  *
  * Combinational (no pipeline register here; the decode stage register is
  * external to this module).
  */
class Decode(p: Parameters) extends Module {
  val io = IO(new Bundle {
    val inst    = Input(UInt(32.W))
    val pc      = Input(UInt(32.W))
    val valid   = Input(Bool())
    val decoded = Output(new DecodedInst)
  })

  // -----------------------------------------------------------------------
  // Instruction field extraction
  // -----------------------------------------------------------------------
  val opcode = io.inst(6, 0)
  val rd     = io.inst(11, 7)
  val funct3 = io.inst(14, 12)
  val rs1    = io.inst(19, 15)
  val rs2    = io.inst(24, 20)
  val funct7 = io.inst(31, 25)

  // Immediate value extraction
  val immI = Cat(Fill(21, io.inst(31)), io.inst(30, 20)).asSInt
  val immS = Cat(Fill(21, io.inst(31)), io.inst(30, 25), io.inst(11, 7)).asSInt
  val immB = Cat(Fill(20, io.inst(31)), io.inst(7), io.inst(30, 25),
                  io.inst(11, 8), 0.U(1.W)).asSInt
  val immU = Cat(io.inst(31, 12), 0.U(12.W)).asSInt
  val immJ = Cat(Fill(12, io.inst(31)), io.inst(19, 12), io.inst(20),
                  io.inst(30, 21), 0.U(1.W)).asSInt

  // -----------------------------------------------------------------------
  // Default decoded values
  // -----------------------------------------------------------------------
  val dec = Wire(new DecodedInst)
  dec.valid     := io.valid
  dec.pc        := io.pc
  dec.inst      := io.inst
  dec.execUnit  := ExecUnit.NOP
  dec.rs1Addr   := rs1
  dec.rs2Addr   := rs2
  dec.rs1Valid  := false.B
  dec.rs2Valid  := false.B
  dec.rdAddr    := rd
  dec.rdValid   := false.B
  dec.imm       := immI
  dec.isFloat   := false.B
  dec.exception := false.B

  // -----------------------------------------------------------------------
  // Decode table (abbreviated subset — enough to compile)
  // -----------------------------------------------------------------------
  switch(opcode) {
    is("b0110011".U) {  // R-type integer
      dec.rs1Valid := true.B; dec.rs2Valid := true.B; dec.rdValid := true.B
      dec.execUnit := Mux(funct7 === "b0000001".U,
        Mux(funct3(2), ExecUnit.DVU, ExecUnit.MLU),
        ExecUnit.ALU)
    }
    is("b0010011".U) {  // I-type integer
      dec.rs1Valid := true.B; dec.rdValid := true.B
      dec.execUnit := ExecUnit.ALU; dec.imm := immI
    }
    is("b0000011".U) {  // LOAD
      dec.rs1Valid := true.B; dec.rdValid := true.B
      dec.execUnit := ExecUnit.LSU; dec.imm := immI
    }
    is("b0100011".U) {  // STORE
      dec.rs1Valid := true.B; dec.rs2Valid := true.B
      dec.execUnit := ExecUnit.LSU; dec.imm := immS
    }
    is("b1100011".U) {  // BRANCH
      dec.rs1Valid := true.B; dec.rs2Valid := true.B
      dec.execUnit := ExecUnit.BRU; dec.imm := immB
    }
    is("b1101111".U) {  // JAL
      dec.rdValid := true.B
      dec.execUnit := ExecUnit.BRU; dec.imm := immJ
    }
    is("b1100111".U) {  // JALR
      dec.rs1Valid := true.B; dec.rdValid := true.B
      dec.execUnit := ExecUnit.BRU; dec.imm := immI
    }
    is("b0110111".U) {  // LUI
      dec.rdValid := true.B
      dec.execUnit := ExecUnit.ALU; dec.imm := immU
    }
    is("b0010111".U) {  // AUIPC
      dec.rdValid := true.B
      dec.execUnit := ExecUnit.ALU; dec.imm := immU
    }
    is("b1110011".U) {  // SYSTEM (CSR, ECALL, EBREAK, MRET)
      dec.rs1Valid := true.B; dec.rdValid := true.B
      dec.execUnit := Mux(funct3 =/= 0.U, ExecUnit.CSR, ExecUnit.SYS)
    }
    is("b0001111".U) {  // FENCE
      dec.execUnit := ExecUnit.SYS
    }
    // FP load/store
    is("b0000111".U) {
      dec.rs1Valid := true.B; dec.rdValid := true.B
      dec.execUnit := ExecUnit.LSU; dec.isFloat := true.B; dec.imm := immI
    }
    is("b0100111".U) {
      dec.rs1Valid := true.B; dec.rs2Valid := true.B
      dec.execUnit := ExecUnit.LSU; dec.isFloat := true.B; dec.imm := immS
    }
    // FP compute (F extension)
    is("b1010011".U) {
      dec.rs1Valid := true.B; dec.rs2Valid := true.B; dec.rdValid := true.B
      dec.execUnit := ExecUnit.FPU; dec.isFloat := true.B
    }
    is("b1000011".U, "b1000111".U, "b1001011".U, "b1001111".U) {  // FMADD etc.
      dec.rs1Valid := true.B; dec.rs2Valid := true.B; dec.rdValid := true.B
      dec.execUnit := ExecUnit.FPU; dec.isFloat := true.B
    }
    // RVV
    is("b1010111".U) {
      dec.rs1Valid := true.B; dec.rs2Valid := true.B
      dec.execUnit := ExecUnit.RVV
    }
  }

  io.decoded := dec
}
