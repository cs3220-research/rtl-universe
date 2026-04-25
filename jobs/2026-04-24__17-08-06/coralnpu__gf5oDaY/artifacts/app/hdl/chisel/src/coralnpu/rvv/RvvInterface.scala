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

package coralnpu.rvv

import chisel3._

/** RVV (RISC-V Vector Extension) interface bundles. */

/** A compressed RVV instruction format (for internal use). */
class RvvCompressedInstruction extends Bundle {
  val inst   = UInt(32.W)
  val offset = UInt(5.W)
}

object RvvCompressedInstruction {
  def from_uncompressed(inst: UInt, offset: UInt): RvvCompressedInstruction = {
    val c = Wire(new RvvCompressedInstruction)
    c.inst   := inst
    c.offset := offset
    c
  }
}

/** A decoded RVV stage-1 instruction. */
class RvvS1DecodedInstruction extends Bundle {
  val op      = RvvAluOp()
  val vd      = UInt(5.W)
  val vs1     = UInt(5.W)
  val vs2     = UInt(5.W)
  val vm      = Bool()       // mask bit (0=masked, 1=unmasked)
  val rs1     = UInt(5.W)   // scalar source
  val imm     = SInt(5.W)   // immediate
  val funct3  = UInt(3.W)   // encoding type (VV=0, VI=3, VX=4, ...)
}

/** RVV ALU interface to the backend. */
class RvvAluInterface extends Bundle {
  val valid   = Output(Bool())
  val ready   = Input(Bool())
  val op      = Output(RvvAluOp())
  val vd      = Output(UInt(5.W))
  val vs1     = Output(UInt(5.W))
  val vs2     = Output(UInt(5.W))
  val vm      = Output(Bool())
}

/** RVV status outputs from the core. */
class RvvStatus extends Bundle {
  val vl    = UInt(32.W)
  val vtype = UInt(32.W)
}
