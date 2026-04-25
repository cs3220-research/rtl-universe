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

/** Branch unit operation encoding. */
object BruOp extends ChiselEnum {
  val BEQ  = Value  // branch if equal
  val BNE  = Value  // branch if not equal
  val BLT  = Value  // branch if less than (signed)
  val BGE  = Value  // branch if greater or equal (signed)
  val BLTU = Value  // branch if less than (unsigned)
  val BGEU = Value  // branch if greater or equal (unsigned)
  val JAL  = Value  // unconditional jump-and-link
  val JALR = Value  // jump-and-link register
}

/** Branch unit request. */
class BruRequest extends Bundle {
  val op    = BruOp()
  val pc    = UInt(32.W)   // program counter of the branch instruction
  val rs1   = UInt(32.W)   // source register 1 value
  val rs2   = UInt(32.W)   // source register 2 value
  val imm   = SInt(32.W)   // sign-extended immediate (branch offset or jump target offset)
  val rd    = UInt(5.W)    // destination register (for JAL/JALR)
}

/** Branch unit result. */
class BruResult extends Bundle {
  val taken     = Bool()        // branch/jump is taken
  val target    = UInt(32.W)   // computed target PC
  val rdValid   = Bool()        // true when rd should be written (JAL/JALR)
  val rdAddr    = UInt(5.W)    // destination register
  val rdData    = UInt(32.W)   // return address (PC+4)
  val exception = Bool()        // instruction-address-misaligned
}

/** Branch/jump resolution unit.
  *
  * Single-cycle: result is registered and appears one clock after a valid request.
  */
class Bru(p: Parameters) extends Module {
  val io = IO(new Bundle {
    val req    = Input(Valid(new BruRequest))
    val result = Output(Valid(new BruResult))
  })

  val res = Wire(new BruResult)
  res.taken     := false.B
  res.target    := 0.U
  res.rdValid   := false.B
  res.rdAddr    := io.req.bits.rd
  res.rdData    := io.req.bits.pc + 4.U
  res.exception := false.B

  val rs1    = io.req.bits.rs1
  val rs2    = io.req.bits.rs2
  val pc     = io.req.bits.pc
  val imm    = io.req.bits.imm
  val target = (pc.asSInt + imm).asUInt

  switch(io.req.bits.op) {
    is(BruOp.BEQ)  { res.taken := (rs1 === rs2);               res.target := target }
    is(BruOp.BNE)  { res.taken := (rs1 =/= rs2);              res.target := target }
    is(BruOp.BLT)  { res.taken := rs1.asSInt < rs2.asSInt;     res.target := target }
    is(BruOp.BGE)  { res.taken := rs1.asSInt >= rs2.asSInt;    res.target := target }
    is(BruOp.BLTU) { res.taken := rs1 < rs2;                   res.target := target }
    is(BruOp.BGEU) { res.taken := rs1 >= rs2;                  res.target := target }
    is(BruOp.JAL)  {
      res.taken   := true.B
      res.target  := target
      res.rdValid := true.B
    }
    is(BruOp.JALR) {
      val jalrTarget = Cat((rs1.asSInt + imm).asUInt(31, 1), 0.U(1.W))
      res.taken   := true.B
      res.target  := jalrTarget
      res.rdValid := true.B
    }
  }
  // Misaligned target check
  res.exception := res.taken && res.target(1, 0) =/= 0.U

  // 1-cycle pipeline register
  io.result.valid := RegNext(io.req.valid, init = false.B)
  io.result.bits  := RegNext(res)
}
