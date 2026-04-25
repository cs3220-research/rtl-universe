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

object BruOp extends ChiselEnum {
  val BEQ, BNE, BLT, BGE, BLTU, BGEU, JAL, JALR = Value
}

// Branch/jump unit – purely combinational (single-cycle).
class Bru(p: Parameters) extends Module {
  val io = IO(new Bundle {
    val req = Flipped(Valid(new Bundle {
      val op     = BruOp()
      val pc     = UInt(32.W)
      val imm    = SInt(32.W)
      val rs1    = UInt(32.W)
      val rs2    = UInt(32.W)
      val rdAddr = UInt(5.W)
    }))
    // Branch target (valid when branch/jump taken)
    val branch = Valid(UInt(32.W))
    // Link register result (JAL / JALR write pc+4 to rd)
    val rd = Valid(new Bundle {
      val addr = UInt(5.W)
      val data = UInt(32.W)
    })
  })

  val op  = io.req.bits.op
  val pc  = io.req.bits.pc
  val imm = io.req.bits.imm
  val rs1 = io.req.bits.rs1
  val rs2 = io.req.bits.rs2

  // Branch condition evaluation
  val taken = Wire(Bool())
  taken := false.B
  switch (op) {
    is (BruOp.BEQ)  { taken := (rs1 === rs2) }
    is (BruOp.BNE)  { taken := (rs1 =/= rs2) }
    is (BruOp.BLT)  { taken := (rs1.asSInt < rs2.asSInt) }
    is (BruOp.BGE)  { taken := (rs1.asSInt >= rs2.asSInt) }
    is (BruOp.BLTU) { taken := (rs1 < rs2) }
    is (BruOp.BGEU) { taken := (rs1 >= rs2) }
    is (BruOp.JAL)  { taken := true.B }
    is (BruOp.JALR) { taken := true.B }
  }

  // Target address
  val target = Wire(UInt(32.W))
  target := 0.U
  switch (op) {
    is (BruOp.JALR) {
      // JALR: target = (rs1 + imm) & ~1
      target := (rs1.asSInt + imm).asUInt & ~1.U(32.W)
    }
    is (BruOp.JAL, BruOp.BEQ, BruOp.BNE, BruOp.BLT, BruOp.BGE, BruOp.BLTU, BruOp.BGEU) {
      target := (pc.asSInt + imm).asUInt
    }
  }

  val isJump = (op === BruOp.JAL) || (op === BruOp.JALR)

  io.branch.valid := io.req.valid && taken
  io.branch.bits  := target

  // JAL / JALR write pc+4 back to rd
  io.rd.valid      := io.req.valid && isJump
  io.rd.bits.addr  := io.req.bits.rdAddr
  io.rd.bits.data  := pc + 4.U
}
