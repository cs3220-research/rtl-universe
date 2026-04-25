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

/** ALU operation codes. */
object AluOp extends ChiselEnum {
  val NOP, ADD, ADDI, SUB, AND, ANDI, OR, ORI, XOR, XORI,
      SLL, SLLI, SRL, SRLI, SRA, SRAI,
      SLT, SLTI, SLTU, SLTIU,
      LW, LH, LB, LHU, LBU,
      SW, SH, SB,
      BEQ, BNE, BLT, BGE, BLTU, BGEU,
      AUIPC, LUI, JAL, JALR,
      FENCE,
      // Bitmanip (Zbb) extensions
      SEXTB, SEXTH, ZEXTH,
      CLZ, CTZ, CPOP,
      ORCB, REV8,
      XNOR, ORN, ANDN,
      MAX, MAXU, MIN, MINU,
      ROL, ROR,
      RORI,
      // CSR ops (encoded as ALU ops)
      ECALL, EBREAK, MRET, WFI = Value
}

/** Arithmetic Logic Unit for the scalar core.
  *
  * Pipeline: inputs are latched on the clock edge and output is
  * available one cycle later.
  */
class Alu(p: Parameters) extends Module {
  val addrWidth = log2Ceil(p.nRegs)

  val io = IO(new Bundle {
    val req = Flipped(Valid(new Bundle {
      val addr = UInt(addrWidth.W)
      val op   = AluOp()
    }))
    val rs1 = Input(new Bundle {
      val valid = Bool()
      val data  = UInt(32.W)
    })
    val rs2 = Input(new Bundle {
      val valid = Bool()
      val data  = UInt(32.W)
    })
    val rd = Valid(new Bundle {
      val addr = UInt(addrWidth.W)
      val data = UInt(32.W)
    })
  })

  // Pipeline register
  val rdValid   = RegInit(false.B)
  val rdAddr    = RegInit(0.U(addrWidth.W))
  val rdData    = RegInit(0.U(32.W))

  // Default: not valid
  rdValid := false.B

  when(io.req.valid) {
    val rs1 = io.rs1.data
    val rs2 = io.rs2.data

    rdAddr  := io.req.bits.addr
    rdValid := true.B

    val result = Wire(UInt(32.W))
    result := 0.U

    switch(io.req.bits.op) {
      // ---- Bitmanip ----
      is(AluOp.SEXTB) {
        result := Cat(Fill(24, rs1(7)), rs1(7, 0))
      }
      is(AluOp.SEXTH) {
        result := Cat(Fill(16, rs1(15)), rs1(15, 0))
      }
      is(AluOp.ZEXTH) {
        result := Cat(0.U(16.W), rs1(15, 0))
      }
      is(AluOp.CLZ) {
        // Count leading zeros of rs1 (32-bit).
        // Iterate from i=31 down to 0 so that i=0 (bit 31) wins last.
        val clzResult = Wire(UInt(6.W))
        clzResult := 32.U
        for (i <- 31 to 0 by -1) {
          when(rs1(31 - i)) {
            clzResult := i.U
          }
        }
        result := clzResult
      }
      is(AluOp.CTZ) {
        // Count trailing zeros of rs1 (32-bit).
        // Iterate from i=31 down to 0 so that the lowest set bit (small i) wins last.
        val ctzResult = Wire(UInt(6.W))
        ctzResult := 32.U
        for (i <- 31 to 0 by -1) {
          when(rs1(i)) {
            ctzResult := i.U
          }
        }
        result := ctzResult
      }
      is(AluOp.CPOP) {
        val bits = (0 until 32).map(i => rs1(i).asUInt)
        result := bits.reduce(_ +& _)
      }
      is(AluOp.ORCB) {
        // Set each byte to 0xFF if any bit in that byte is set, else 0x00
        result := Cat(
          Mux(rs1(31, 24) =/= 0.U, 0xFF.U(8.W), 0x00.U(8.W)),
          Mux(rs1(23, 16) =/= 0.U, 0xFF.U(8.W), 0x00.U(8.W)),
          Mux(rs1(15,  8) =/= 0.U, 0xFF.U(8.W), 0x00.U(8.W)),
          Mux(rs1( 7,  0) =/= 0.U, 0xFF.U(8.W), 0x00.U(8.W))
        )
      }
      is(AluOp.REV8) {
        // Reverse bytes
        result := Cat(rs1(7, 0), rs1(15, 8), rs1(23, 16), rs1(31, 24))
      }
      is(AluOp.XNOR) {
        result := ~(rs1 ^ rs2)
      }
      is(AluOp.ORN) {
        result := rs1 | (~rs2)
      }
      is(AluOp.ANDN) {
        result := rs1 & (~rs2)
      }
      is(AluOp.MAX) {
        result := Mux(rs1.asSInt > rs2.asSInt, rs1, rs2)
      }
      is(AluOp.MAXU) {
        result := Mux(rs1 > rs2, rs1, rs2)
      }
      is(AluOp.MIN) {
        result := Mux(rs1.asSInt < rs2.asSInt, rs1, rs2)
      }
      is(AluOp.MINU) {
        result := Mux(rs1 < rs2, rs1, rs2)
      }
      is(AluOp.ROL) {
        val shamt = rs2(4, 0)
        result := (rs1 << shamt) | (rs1 >> (32.U - shamt))
      }
      is(AluOp.ROR) {
        val shamt = rs2(4, 0)
        result := (rs1 >> shamt) | (rs1 << (32.U - shamt))
      }
      is(AluOp.RORI) {
        val shamt = rs2(4, 0)
        result := (rs1 >> shamt) | (rs1 << (32.U - shamt))
      }
      // ---- Standard RV32I ----
      is(AluOp.ADD, AluOp.ADDI) {
        result := rs1 + rs2
      }
      is(AluOp.SUB) {
        result := rs1 - rs2
      }
      is(AluOp.AND, AluOp.ANDI) {
        result := rs1 & rs2
      }
      is(AluOp.OR, AluOp.ORI) {
        result := rs1 | rs2
      }
      is(AluOp.XOR, AluOp.XORI) {
        result := rs1 ^ rs2
      }
      is(AluOp.SLL, AluOp.SLLI) {
        result := rs1 << rs2(4, 0)
      }
      is(AluOp.SRL, AluOp.SRLI) {
        result := rs1 >> rs2(4, 0)
      }
      is(AluOp.SRA, AluOp.SRAI) {
        result := (rs1.asSInt >> rs2(4, 0)).asUInt
      }
      is(AluOp.SLT, AluOp.SLTI) {
        result := (rs1.asSInt < rs2.asSInt).asUInt
      }
      is(AluOp.SLTU, AluOp.SLTIU) {
        result := (rs1 < rs2).asUInt
      }
      is(AluOp.LUI) {
        result := rs2  // immediate passed in rs2
      }
      is(AluOp.AUIPC) {
        result := rs1 + rs2
      }
    }

    rdData := result
  }

  io.rd.valid      := rdValid
  io.rd.bits.addr  := rdAddr
  io.rd.bits.data  := rdData
}

/** Emitter for ALU. */
object EmitAlu extends App {
  val p = new Parameters
  _root_.circt.stage.ChiselStage.emitSystemVerilog(new Alu(p))
}
