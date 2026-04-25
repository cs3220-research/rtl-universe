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

object AluOp extends ChiselEnum {
  // Zbb unary ops
  val SEXTB, SEXTH, ZEXTH = Value
  val CLZ, CTZ, CPOP = Value
  val ORCB, REV8 = Value
  // Zbb binary ops
  val XNOR, ORN, ANDN = Value
  val MAX, MAXU, MIN, MINU = Value
  val ROL, ROR = Value
  // Standard ALU ops
  val ADD, SUB, AND, OR, XOR = Value
  val SLL, SRL, SRA = Value
  val SLT, SLTU = Value
  val LUI = Value
  // Multiply
  val MUL, MULH, MULHU, MULHSU = Value
}

class RegData(p: Parameters) extends Bundle {
  val addr = UInt(5.W)
  val data = UInt(32.W)
}

class RegSource(p: Parameters) extends Bundle {
  val valid = Bool()
  val data  = UInt(32.W)
}

class AluRequest(p: Parameters) extends Bundle {
  val addr = UInt(5.W)
  val op   = AluOp()
}

class Alu(p: Parameters) extends Module {
  val io = IO(new Bundle {
    val req = Flipped(Valid(new AluRequest(p)))
    val rs1 = Input(new RegSource(p))
    val rs2 = Input(new RegSource(p))
    val rd  = Valid(new RegData(p))
  })

  // Pipeline register (1-cycle latency)
  val req_r  = RegNext(io.req)
  val rs1_r  = RegNext(io.rs1)
  val rs2_r  = RegNext(io.rs2)

  val a   = rs1_r.data
  val b   = rs2_r.data

  // --- Unary ops ---
  val sextb = Cat(Fill(24, a(7)),  a(7, 0))
  val sexth = Cat(Fill(16, a(15)), a(15, 0))
  val zexth = Cat(0.U(16.W),       a(15, 0))

  // Count leading zeros
  val clzResult = Wire(UInt(6.W))
  clzResult := Mux(a === 0.U, 32.U,
    MuxCase(0.U, (31 to 0 by -1).map { i =>
      a(i) -> (31 - i).U
    })
  )

  // Count trailing zeros
  val ctzResult = Wire(UInt(6.W))
  ctzResult := Mux(a === 0.U, 32.U,
    MuxCase(0.U, (0 until 32).map { i =>
      a(i) -> i.U
    })
  )

  // Population count
  val cpopResult = PopCount(a)

  // ORC.B: for each byte, if any bit is set, fill byte with 0xFF
  val orcb = Cat(
    Mux(a(31, 24).orR, 0xFF.U(8.W), 0.U(8.W)),
    Mux(a(23, 16).orR, 0xFF.U(8.W), 0.U(8.W)),
    Mux(a(15,  8).orR, 0xFF.U(8.W), 0.U(8.W)),
    Mux(a( 7,  0).orR, 0xFF.U(8.W), 0.U(8.W))
  )

  // REV8: reverse byte order
  val rev8 = Cat(a(7, 0), a(15, 8), a(23, 16), a(31, 24))

  // --- Binary ops ---
  val xnorResult = ~(a ^ b)
  val ornResult  = a | ~b
  val andnResult = a & ~b

  // Signed comparison (treat as SInt)
  val aS = a.asSInt
  val bS = b.asSInt
  val maxResult  = Mux(aS > bS, a, b)
  val maxuResult = Mux(a > b, a, b)
  val minResult  = Mux(aS < bS, a, b)
  val minuResult = Mux(a < b, a, b)

  val shamt = b(4, 0)
  val rolResult = (a << shamt) | (a >> (32.U - shamt))
  val rorResult = (a >> shamt) | (a << (32.U - shamt))

  val addResult = a + b
  val subResult = a - b
  val andResult = a & b
  val orResult  = a | b
  val xorResult = a ^ b
  val sllResult = a << shamt
  val srlResult = a >> shamt
  val sraResult = (a.asSInt >> shamt).asUInt
  val sltResult = (aS < bS).asUInt
  val sltuResult = (a < b).asUInt

  val result = MuxLookup(req_r.bits.op, 0.U)(Seq(
    AluOp.SEXTB -> sextb,
    AluOp.SEXTH -> sexth,
    AluOp.ZEXTH -> zexth,
    AluOp.CLZ   -> clzResult,
    AluOp.CTZ   -> ctzResult,
    AluOp.CPOP  -> cpopResult,
    AluOp.ORCB  -> orcb,
    AluOp.REV8  -> rev8,
    AluOp.XNOR  -> xnorResult,
    AluOp.ORN   -> ornResult,
    AluOp.ANDN  -> andnResult,
    AluOp.MAX   -> maxResult,
    AluOp.MAXU  -> maxuResult,
    AluOp.MIN   -> minResult,
    AluOp.MINU  -> minuResult,
    AluOp.ROL   -> rolResult,
    AluOp.ROR   -> rorResult,
    AluOp.ADD   -> addResult,
    AluOp.SUB   -> subResult,
    AluOp.AND   -> andResult,
    AluOp.OR    -> orResult,
    AluOp.XOR   -> xorResult,
    AluOp.SLL   -> sllResult,
    AluOp.SRL   -> srlResult,
    AluOp.SRA   -> sraResult,
    AluOp.SLT   -> sltResult,
    AluOp.SLTU  -> sltuResult,
  ))

  io.rd.valid      := req_r.valid && rs1_r.valid
  io.rd.bits.addr  := req_r.bits.addr
  io.rd.bits.data  := result
}
