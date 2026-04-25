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

/** Decoded instruction bundle. */
class DecodedInstruction extends Bundle {
  val valid   = Bool()
  val pc      = UInt(32.W)
  val inst    = UInt(32.W)
  val rs1     = UInt(5.W)
  val rs2     = UInt(5.W)
  val rd      = UInt(5.W)
  val imm     = SInt(32.W)
  val useRs1  = Bool()
  val useRs2  = Bool()
  val hasRd   = Bool()
  val isBranch = Bool()
  val isJump  = Bool()
  val isLoad  = Bool()
  val isStore = Bool()
  val isSys   = Bool()
  val isFloat = Bool()
  val isVector = Bool()
}

/** RV32IMAF(D) instruction decoder stub. */
class Decode(p: Parameters) extends Module {
  val io = IO(new Bundle {
    val inst  = Input(Valid(UInt(32.W)))
    val pc    = Input(UInt(32.W))
    val out   = Output(new DecodedInstruction)
  })

  io.out := 0.U.asTypeOf(new DecodedInstruction)
  io.out.inst := io.inst.bits
  io.out.pc   := io.pc
  io.out.valid := io.inst.valid
}
