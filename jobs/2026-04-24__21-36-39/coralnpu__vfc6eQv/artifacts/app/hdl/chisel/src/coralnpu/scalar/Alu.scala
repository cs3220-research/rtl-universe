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

/** ALU operation encoding (Zbb / Bitmanip extension operations). */
object AluOp extends ChiselEnum {
  // Unary operations (use rs1 only)
  val SEXTB, SEXTH, ZEXTH, CLZ, CTZ, CPOP, ORCB, REV8 = Value
  // Binary operations (use rs1 and rs2)
  val XNOR, ORN, ANDN, MAX, MAXU, MIN, MINU, ROL, ROR = Value
}

/** Request bundle sent to the ALU. */
class AluRequest extends Bundle {
  val addr = UInt(5.W)   // destination register address
  val op   = AluOp()     // operation to perform
}

/** Result bundle produced by the ALU. */
class AluResult extends Bundle {
  val addr = UInt(5.W)
  val data = UInt(32.W)
}

/** Source operand bundle (with validity bit). */
class AluOperand extends Bundle {
  val valid = Bool()
  val data  = UInt(32.W)
}

/** Single-cycle Bitmanip ALU.
  *
  * Timing: result is registered and appears at `rd` one clock cycle after
  * a valid `req` is presented with valid operands.
  */
class Alu(p: Parameters) extends Module {
  val io = IO(new Bundle {
    val req = Input(Valid(new AluRequest))
    val rs1 = Input(new AluOperand)
    val rs2 = Input(new AluOperand)
    val rd  = Output(Valid(new AluResult))
  })

  // -----------------------------------------------------------------------
  // Combinational result computation
  // -----------------------------------------------------------------------
  val rs1 = io.rs1.data
  val rs2 = io.rs2.data

  // Count leading zeros of a 32-bit value
  def clz32(x: UInt): UInt = {
    val rev = Reverse(x)
    val pe  = PriorityEncoder(Cat(rev, 1.U(1.W)))  // ensure at least one bit set
    Mux(x === 0.U, 32.U(6.W), (31.U - PriorityEncoder(Reverse(x)))(5, 0))
  }

  // Count trailing zeros of a 32-bit value
  def ctz32(x: UInt): UInt = {
    Mux(x === 0.U, 32.U(6.W), PriorityEncoder(x)(5, 0))
  }

  // Population count (count number of 1 bits)
  def popcount32(x: UInt): UInt = {
    val bits = (0 until 32).map(i => x(i).asUInt)
    bits.reduce(_ +& _)(5, 0)
  }

  // OR-combine bytes: if any bit in byte N is set, set all 8 bits of byte N
  def orcb32(x: UInt): UInt = {
    Cat(
      Fill(8, x(31, 24).orR),
      Fill(8, x(23, 16).orR),
      Fill(8, x(15,  8).orR),
      Fill(8, x( 7,  0).orR)
    )
  }

  // Reverse byte order (bswap)
  def rev8_32(x: UInt): UInt = {
    Cat(x(7, 0), x(15, 8), x(23, 16), x(31, 24))
  }

  // Rotate left (32-bit)
  def rol32(x: UInt, shamt: UInt): UInt = {
    val s = shamt(4, 0)
    ((x << s) | (x >> (32.U - s)))(31, 0)
  }

  // Rotate right (32-bit)
  def ror32(x: UInt, shamt: UInt): UInt = {
    val s = shamt(4, 0)
    ((x >> s) | (x << (32.U - s)))(31, 0)
  }

  val result = Wire(UInt(32.W))
  result := 0.U

  switch(io.req.bits.op) {
    // ---- Unary ----
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
      result := Mux(rs1 === 0.U, 32.U,
        (31.U - PriorityEncoder(Reverse(rs1)))(5, 0))
    }
    is(AluOp.CTZ) {
      result := Mux(rs1 === 0.U, 32.U, PriorityEncoder(rs1)(5, 0))
    }
    is(AluOp.CPOP) {
      result := popcount32(rs1)
    }
    is(AluOp.ORCB) {
      result := orcb32(rs1)
    }
    is(AluOp.REV8) {
      result := rev8_32(rs1)
    }
    // ---- Binary ----
    is(AluOp.XNOR) {
      result := ~(rs1 ^ rs2)
    }
    is(AluOp.ORN) {
      result := rs1 | ~rs2
    }
    is(AluOp.ANDN) {
      result := rs1 & ~rs2
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
      result := rol32(rs1, rs2)
    }
    is(AluOp.ROR) {
      result := ror32(rs1, rs2)
    }
  }

  // -----------------------------------------------------------------------
  // Pipeline register (1-cycle latency)
  // -----------------------------------------------------------------------
  val rdValid = RegNext(io.req.valid, init = false.B)
  val rdAddr  = RegNext(io.req.bits.addr)
  val rdData  = RegNext(result)

  io.rd.valid      := rdValid
  io.rd.bits.addr  := rdAddr
  io.rd.bits.data  := rdData
}

/** Emit helper used by the build system. */
object EmitAlu extends App {
  import circt.stage.ChiselStage
  ChiselStage.emitSystemVerilog(new Alu(new Parameters), args)
}
