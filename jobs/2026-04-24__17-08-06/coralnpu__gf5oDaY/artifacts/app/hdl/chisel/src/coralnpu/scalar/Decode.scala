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
class DecodedInst(p: Parameters) extends Bundle {
  val valid  = Bool()
  val rs1    = UInt(log2Ceil(p.nRegs).W)
  val rs2    = UInt(log2Ceil(p.nRegs).W)
  val rd     = UInt(log2Ceil(p.nRegs).W)
  val op     = AluOp()
  val imm    = SInt(32.W)
  val useImm = Bool()
  val isLoad = Bool()
  val isStore = Bool()
  val isBranch = Bool()
  val isJump  = Bool()
  val isMul   = Bool()
  val isDiv   = Bool()
}

/** Stub instruction decoder. */
class Decode(p: Parameters) extends Module {
  val io = IO(new Bundle {
    val inst = Input(UInt(p.ilen.W))
    val out  = Output(new DecodedInst(p))
  })

  // Stub: output NOP for all instructions
  io.out := 0.U.asTypeOf(new DecodedInst(p))
  io.out.op := AluOp.NOP
}
