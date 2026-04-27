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

// ── Decoded instruction bundle ────────────────────────────────────────────────
class DecodedInstruction extends Bundle {
  val valid       = Bool()
  val rs1Addr     = UInt(5.W)
  val rs2Addr     = UInt(5.W)
  val rdAddr      = UInt(5.W)
  val rs1Valid    = Bool()
  val rs2Valid    = Bool()
  val rdValid     = Bool()
  val imm         = SInt(32.W)
  val isLoad      = Bool()
  val isStore     = Bool()
  val isBranch    = Bool()
  val isJump      = Bool()
  val isSystem    = Bool()
  val isMpause    = Bool()
  val isWfi       = Bool()
  val isEcall     = Bool()
  val isEbreak    = Bool()
  val isMret      = Bool()
  val isMul       = Bool()
  val isDiv       = Bool()
  val isAlu2      = Bool()
  val isCsr       = Bool()
  val isFloat     = Bool()
  val isRvv       = Bool()
  val size        = UInt(2.W)  // 0=byte, 1=half, 2=word
  val signExtLoad = Bool()
  val aluOp       = AluOp()
  val mluOp       = MluOp()
  val dvuOp       = DvuOp()
  val csrAddr     = UInt(12.W)
  val csrOp       = UInt(2.W)  // 1=rw, 2=set, 3=clear
  val branchType  = UInt(3.W)  // funct3
}

// ── Decoder object ────────────────────────────────────────────────────────────
object Decoder {
  // RV32 opcode map
  val OPC_LOAD    = "b0000011".U(7.W)
  val OPC_FLOAD   = "b0000111".U(7.W)
  val OPC_OPIMM   = "b0010011".U(7.W)
  val OPC_AUIPC   = "b0010111".U(7.W)
  val OPC_FSTORE  = "b0100111".U(7.W)
  val OPC_STORE   = "b0100011".U(7.W)
  val OPC_OP      = "b0110011".U(7.W)
  val OPC_LUI     = "b0110111".U(7.W)
  val OPC_BRANCH  = "b1100011".U(7.W)
  val OPC_JALR    = "b1100111".U(7.W)
  val OPC_JAL     = "b1101111".U(7.W)
  val OPC_SYSTEM  = "b1110011".U(7.W)
  val OPC_VECTOR  = "b1010111".U(7.W)
  val OPC_FMADD   = "b1000011".U(7.W)
  val OPC_FMSUB   = "b1000111".U(7.W)
  val OPC_FNMSUB  = "b1001011".U(7.W)
  val OPC_FNMADD  = "b1001111".U(7.W)
  val OPC_FPOP    = "b1010011".U(7.W)

  def decode(inst: UInt): DecodedInstruction = {
    val d = Wire(new DecodedInstruction)

    // Field extraction
    val opcode = inst(6,0)
    val rd     = inst(11,7)
    val funct3 = inst(14,12)
    val rs1    = inst(19,15)
    val rs2    = inst(24,20)
    val funct7 = inst(31,25)

    // Immediates
    val immI  = Cat(Fill(21, inst(31)), inst(30,20)).asSInt
    val immS  = Cat(Fill(21, inst(31)), inst(30,25), inst(11,7)).asSInt
    val immB  = Cat(Fill(20, inst(31)), inst(7), inst(30,25), inst(11,8), 0.U(1.W)).asSInt
    val immU  = Cat(inst(31,12), 0.U(12.W)).asSInt
    val immJ  = Cat(Fill(12, inst(31)), inst(19,12), inst(20), inst(30,21), 0.U(1.W)).asSInt

    // Whole-word matches for special instructions
    val isEcall  = inst === "h00000073".U
    val isEbreak = inst === "h00100073".U
    val isMret   = inst === "h30200073".U
    val isWfi    = inst === "h10500073".U
    val isMpause = inst === "h08000073".U

    // M extension (funct7 == 0x01)
    val isMext = (opcode === OPC_OP) && (funct7 === "b0000001".U)

    // Zbb bit-manipulation extensions
    val isZbb = Wire(Bool())
    isZbb := false.B

    // Zbb single-source ops (all encoded as OP-IMM with specific funct7/funct3/rs2 fields)
    val isCLZ   = (opcode === OPC_OPIMM) && (funct7 === "b0110000".U) && (funct3 === "b001".U) && (rs2 === 0.U)
    val isCTZ   = (opcode === OPC_OPIMM) && (funct7 === "b0110000".U) && (funct3 === "b001".U) && (rs2 === 1.U)
    val isCPOP  = (opcode === OPC_OPIMM) && (funct7 === "b0110000".U) && (funct3 === "b001".U) && (rs2 === 2.U)
    // SEXT.B / SEXT.H (encoded as OP-IMM with funct7=0x30, funct3=0x1)
    val isSEXTB = (opcode === OPC_OPIMM) && (funct7 === "b0110000".U) && (funct3 === "b001".U) && (rs2 === 4.U)
    val isSEXTH = (opcode === OPC_OPIMM) && (funct7 === "b0110000".U) && (funct3 === "b001".U) && (rs2 === 5.U)
    val isZEXTH = (opcode === OPC_OP)    && (funct7 === "b0000100".U) && (funct3 === "b100".U)
    // Logic with negate
    val isANDN  = (opcode === OPC_OP) && (funct7 === "b0100000".U) && (funct3 === "b111".U)
    val isORN   = (opcode === OPC_OP) && (funct7 === "b0100000".U) && (funct3 === "b110".U)
    val isXNOR  = (opcode === OPC_OP) && (funct7 === "b0100000".U) && (funct3 === "b100".U)
    // Min/Max
    val isMAX   = (opcode === OPC_OP) && (funct7 === "b0000101".U) && (funct3 === "b110".U)
    val isMAXU  = (opcode === OPC_OP) && (funct7 === "b0000101".U) && (funct3 === "b111".U)
    val isMIN   = (opcode === OPC_OP) && (funct7 === "b0000101".U) && (funct3 === "b100".U)
    val isMINU  = (opcode === OPC_OP) && (funct7 === "b0000101".U) && (funct3 === "b101".U)
    // Rotate
    val isROL   = (opcode === OPC_OP) && (funct7 === "b0110000".U) && (funct3 === "b001".U)
    val isROR   = (opcode === OPC_OP)    && (funct7 === "b0110000".U) && (funct3 === "b101".U)
    val isRORI  = (opcode === OPC_OPIMM) && (funct7 === "b0110000".U) && (funct3 === "b101".U)
    // ORC.B and REV8
    val isORCB  = (opcode === OPC_OPIMM) && (funct7 === "b0101000".U) && (funct3 === "b101".U) && (rs2 === "b00111".U)
    val isREV8  = (opcode === OPC_OPIMM) && (funct7 === "b0110100".U) && (funct3 === "b101".U) && (rs2 === "b11000".U)

    isZbb := isCLZ || isCTZ || isCPOP || isSEXTB || isSEXTH || isZEXTH ||
             isANDN || isORN || isXNOR ||
             isMAX || isMAXU || isMIN || isMINU ||
             isROL || isROR || isRORI || isORCB || isREV8

    val aluOpSel = Wire(AluOp())
    aluOpSel := AluOp.SEXTB
    when (isCLZ)   { aluOpSel := AluOp.CLZ   }
    .elsewhen (isCTZ)  { aluOpSel := AluOp.CTZ   }
    .elsewhen (isCPOP) { aluOpSel := AluOp.CPOP  }
    .elsewhen (isSEXTB){ aluOpSel := AluOp.SEXTB }
    .elsewhen (isSEXTH){ aluOpSel := AluOp.SEXTH }
    .elsewhen (isZEXTH){ aluOpSel := AluOp.ZEXTH }
    .elsewhen (isANDN) { aluOpSel := AluOp.ANDN  }
    .elsewhen (isORN)  { aluOpSel := AluOp.ORN   }
    .elsewhen (isXNOR) { aluOpSel := AluOp.XNOR  }
    .elsewhen (isMAX)  { aluOpSel := AluOp.MAX   }
    .elsewhen (isMAXU) { aluOpSel := AluOp.MAXU  }
    .elsewhen (isMIN)  { aluOpSel := AluOp.MIN   }
    .elsewhen (isMINU) { aluOpSel := AluOp.MINU  }
    .elsewhen (isROL)  { aluOpSel := AluOp.ROL   }
    .elsewhen (isROR || isRORI) { aluOpSel := AluOp.ROR }
    .elsewhen (isORCB) { aluOpSel := AluOp.ORCB  }
    .elsewhen (isREV8) { aluOpSel := AluOp.REV8  }

    // CSR instructions
    val isCsrInst = (opcode === OPC_SYSTEM) && !isEcall && !isEbreak && !isMret && !isWfi && !isMpause

    val csrOpSel = Wire(UInt(2.W))
    csrOpSel := 1.U
    when (funct3 === "b001".U || funct3 === "b101".U) { csrOpSel := 1.U }  // CSRRW / CSRRWI
    .elsewhen (funct3 === "b010".U || funct3 === "b110".U) { csrOpSel := 2.U }  // CSRRS / CSRRSI
    .elsewhen (funct3 === "b011".U || funct3 === "b111".U) { csrOpSel := 3.U }  // CSRRC / CSRRCI

    // Float extension detection
    val isFloatInst = (opcode === OPC_FLOAD) || (opcode === OPC_FSTORE) ||
                      (opcode === OPC_FMADD) || (opcode === OPC_FMSUB) ||
                      (opcode === OPC_FNMSUB)|| (opcode === OPC_FNMADD)||
                      (opcode === OPC_FPOP)

    // ── Assign fields ────────────────────────────────────────────────────────
    d.valid    := true.B
    d.rs1Addr  := rs1
    d.rs2Addr  := rs2
    d.rdAddr   := rd

    // Which source registers are read
    d.rs1Valid := (opcode === OPC_OP)     || (opcode === OPC_OPIMM)  ||
                  (opcode === OPC_LOAD)   || (opcode === OPC_STORE)  ||
                  (opcode === OPC_BRANCH) || (opcode === OPC_JALR)   ||
                  (opcode === OPC_SYSTEM && isCsrInst && funct3(2) === 0.U) ||
                  (opcode === OPC_FLOAD)  || (opcode === OPC_FSTORE) ||
                  isZbb

    d.rs2Valid := (opcode === OPC_OP) || (opcode === OPC_STORE) ||
                  (opcode === OPC_BRANCH) ||
                  (opcode === OPC_FSTORE) ||
                  (isZbb && !isSEXTB && !isSEXTH && !isZEXTH && !isCLZ && !isCTZ && !isCPOP && !isORCB && !isREV8 && !isRORI)

    // rd is written for everything except STORE, BRANCH, (some system)
    d.rdValid := (opcode === OPC_OP) || (opcode === OPC_OPIMM) ||
                 (opcode === OPC_LOAD)  || (opcode === OPC_JAL)   ||
                 (opcode === OPC_JALR)  || (opcode === OPC_LUI)   ||
                 (opcode === OPC_AUIPC) ||
                 (opcode === OPC_SYSTEM && isCsrInst) ||
                 isZbb || (opcode === OPC_FLOAD) || isFloatInst

    // Immediate selection
    d.imm := MuxCase(immI, Seq(
      (opcode === OPC_STORE || opcode === OPC_FSTORE) -> immS,
      (opcode === OPC_BRANCH) -> immB,
      (opcode === OPC_LUI || opcode === OPC_AUIPC)    -> immU,
      (opcode === OPC_JAL)    -> immJ
    ))

    d.isLoad      := (opcode === OPC_LOAD)
    d.isStore     := (opcode === OPC_STORE)
    d.isBranch    := (opcode === OPC_BRANCH)
    d.isJump      := (opcode === OPC_JAL) || (opcode === OPC_JALR)
    d.isSystem    := (opcode === OPC_SYSTEM)
    d.isMpause    := isMpause
    d.isWfi       := isWfi
    d.isEcall     := isEcall
    d.isEbreak    := isEbreak
    d.isMret      := isMret
    d.isMul       := isMext && (funct3 === "b000".U || funct3 === "b001".U ||
                                 funct3 === "b010".U || funct3 === "b011".U)
    d.isDiv       := isMext && (funct3 === "b100".U || funct3 === "b101".U ||
                                 funct3 === "b110".U || funct3 === "b111".U)
    d.isAlu2      := isZbb
    d.isCsr       := isCsrInst
    d.isFloat     := isFloatInst
    d.isRvv       := (opcode === OPC_VECTOR)

    // Memory size (funct3[1:0])
    d.size        := funct3(1,0)
    d.signExtLoad := !funct3(2)   // LB/LH have bit 2=0; LBU/LHU have bit2=1

    d.aluOp   := aluOpSel
    d.csrAddr := inst(31,20)
    d.csrOp   := csrOpSel
    d.branchType := funct3

    // MLU op selection
    val mluOpSel = Wire(MluOp())
    mluOpSel := MluOp.MUL
    when (funct3 === "b000".U) { mluOpSel := MluOp.MUL    }
    .elsewhen (funct3 === "b001".U) { mluOpSel := MluOp.MULH   }
    .elsewhen (funct3 === "b010".U) { mluOpSel := MluOp.MULHSU }
    .elsewhen (funct3 === "b011".U) { mluOpSel := MluOp.MULHU  }
    d.mluOp := mluOpSel

    // DVU op selection
    val dvuOpSel = Wire(DvuOp())
    dvuOpSel := DvuOp.DIV
    when (funct3 === "b100".U) { dvuOpSel := DvuOp.DIV  }
    .elsewhen (funct3 === "b101".U) { dvuOpSel := DvuOp.DIVU }
    .elsewhen (funct3 === "b110".U) { dvuOpSel := DvuOp.REM  }
    .elsewhen (funct3 === "b111".U) { dvuOpSel := DvuOp.REMU }
    d.dvuOp := dvuOpSel

    d
  }
}
