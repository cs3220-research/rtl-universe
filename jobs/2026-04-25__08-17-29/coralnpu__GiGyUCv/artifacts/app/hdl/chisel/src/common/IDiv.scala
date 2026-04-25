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

package common

import chisel3._
import chisel3.util._

/** Integer divider (non-restoring, iterative).
  *
  * @param width  Bit width of operands.
  */
class IDiv(width: Int) extends Module {
  val io = IO(new Bundle {
    val in  = Flipped(Decoupled(new Bundle {
      val dividend = UInt(width.W)
      val divisor  = UInt(width.W)
      val signed   = Bool()
    }))
    val out = Decoupled(new Bundle {
      val quotient  = UInt(width.W)
      val remainder = UInt(width.W)
    })
  })

  // Simple combinational divider (not synthesizable for large widths, but functional for tests)
  val dividend = io.in.bits.dividend
  val divisor  = io.in.bits.divisor
  val isSigned = io.in.bits.signed

  // Signed magnitude
  val aSign = isSigned && dividend(width - 1)
  val bSign = isSigned && divisor(width - 1)
  val absA  = Mux(aSign, (~dividend).asUInt + 1.U, dividend)
  val absB  = Mux(bSign, (~divisor).asUInt + 1.U, divisor)

  val quot = Mux(absB === 0.U, Fill(width, 1.U), absA / absB)
  val rem  = Mux(absB === 0.U, absA, absA % absB)

  val quotSign = aSign ^ bSign
  val remSign  = aSign

  val quotOut = Mux(quotSign, (~quot).asUInt + 1.U, quot)
  val remOut  = Mux(remSign,  (~rem ).asUInt + 1.U, rem)

  // Simple: always ready, single-cycle
  val valid = RegInit(false.B)
  val bits  = Reg(new Bundle {
    val quotient  = UInt(width.W)
    val remainder = UInt(width.W)
  })

  when (io.in.valid && io.in.ready) {
    valid         := true.B
    bits.quotient  := quotOut
    bits.remainder := remOut
  } .elsewhen (io.out.valid && io.out.ready) {
    valid := false.B
  }

  io.in.ready    := !valid || io.out.ready
  io.out.valid   := valid
  io.out.bits    := bits
}
