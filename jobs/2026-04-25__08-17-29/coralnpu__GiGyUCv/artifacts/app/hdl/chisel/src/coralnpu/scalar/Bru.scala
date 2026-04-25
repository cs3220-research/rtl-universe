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

object BruOp extends ChiselEnum {
  val BEQ, BNE, BLT, BGE, BLTU, BGEU = Value
  val JAL, JALR = Value
}

class BruRequest(p: Parameters) extends Bundle {
  val addr   = UInt(5.W)
  val op     = BruOp()
  val pc     = UInt(32.W)
  val imm    = SInt(32.W)
}

/** Branch Resolution Unit stub. */
class Bru(p: Parameters) extends Module {
  val io = IO(new Bundle {
    val req    = Flipped(Valid(new BruRequest(p)))
    val rs1    = Input(new RegSource(p))
    val rs2    = Input(new RegSource(p))
    val branch = Valid(UInt(32.W))
    val rd     = Valid(new RegData(p))
  })

  io.branch.valid := false.B
  io.branch.bits  := 0.U
  io.rd.valid     := false.B
  io.rd.bits      := 0.U.asTypeOf(new RegData(p))
}
