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

/** Multi-cycle restoring integer divider.
  *
  * Implements a classic non-restoring (shift-subtract) long division algorithm.
  * The division takes `width` cycles after the request is accepted.
  *
  * When `signed` is asserted, the inputs are treated as two's-complement signed
  * integers and the outputs are the signed quotient / remainder.
  *
  * @param width  Operand width in bits (default: 32).
  */
class IDiv(width: Int = 32) extends Module {

  class ReqBundle extends Bundle {
    val dividend = UInt(width.W)
    val divisor  = UInt(width.W)
    val signed   = Bool()
  }

  class RespBundle extends Bundle {
    val quotient  = UInt(width.W)
    val remainder = UInt(width.W)
  }

  val io = IO(new Bundle {
    val req  = Flipped(Decoupled(new ReqBundle))
    val resp = Decoupled(new RespBundle)
  })

  // State machine
  val sIdle :: sRunning :: sDone :: Nil = Enum(3)
  val state = RegInit(sIdle)

  // Registered operands
  val dividend = Reg(UInt(width.W))
  val divisor  = Reg(UInt(width.W))
  val isSigned = Reg(Bool())
  val negDvnd  = Reg(Bool())  // whether the original dividend was negative
  val negDvsr  = Reg(Bool())  // whether the original divisor was negative

  // Iteration counter
  val counter = Reg(UInt(log2Ceil(width + 1).W))

  // Partial remainder (2*width bits: [remainder | quotient])
  val partial = Reg(UInt((2 * width).W))

  // Result registers
  val quotientReg  = Reg(UInt(width.W))
  val remainderReg = Reg(UInt(width.W))

  // Default outputs
  io.req.ready  := state === sIdle
  io.resp.valid := state === sDone
  io.resp.bits.quotient  := quotientReg
  io.resp.bits.remainder := remainderReg

  when(state === sIdle && io.req.valid) {
    val rawDividend = io.req.bits.dividend
    val rawDivisor  = io.req.bits.divisor
    val signed      = io.req.bits.signed

    isSigned := signed

    // For signed division take absolute values and remember signs
    val dvndNeg = signed && rawDividend(width - 1)
    val dvrsNeg = signed && rawDivisor(width - 1)
    negDvnd := dvndNeg
    negDvsr := dvrsNeg

    dividend := Mux(dvndNeg, (~rawDividend + 1.U)(width - 1, 0), rawDividend)
    divisor  := Mux(dvrsNeg, (~rawDivisor  + 1.U)(width - 1, 0), rawDivisor)

    // Initialise partial remainder: upper half = 0, lower half = |dividend|
    partial := Cat(0.U(width.W),
                   Mux(dvndNeg, (~rawDividend + 1.U)(width - 1, 0), rawDividend))
    counter := 0.U
    state   := sRunning
  }

  when(state === sRunning) {
    // Restoring division step:
    //   1. Shift partial left by 1.
    //   2. Trial subtract divisor from upper half.
    //   3. If result >= 0, keep and set quotient bit; else restore.

    val shifted  = partial << 1.U  // (2*width+1) bits
    val upper    = shifted(2 * width - 1, width)   // upper width bits
    val lower    = shifted(width - 1, 0)            // lower width bits (quotient so far)

    val trial    = upper.zext - divisor.zext  // SInt(width+2.W): sign bit at width+1
    val quotBit  = !trial(width + 1)          // non-negative result => quotient bit = 1

    val newUpper = Mux(quotBit, trial(width - 1, 0), upper)
    val newLower = Cat(lower(width - 2, 0), quotBit)

    partial := Cat(newUpper, newLower)
    counter := counter + 1.U

    when(counter === (width - 1).U) {
      // Iteration done – apply sign corrections
      val q = newLower
      val r = newUpper

      // Quotient sign: negative if exactly one of dividend/divisor was negative
      val qNeg = negDvnd ^ negDvsr
      // Remainder sign: same as dividend
      val rNeg = negDvnd

      quotientReg  := Mux(qNeg, (~q + 1.U)(width - 1, 0), q)
      remainderReg := Mux(rNeg, (~r + 1.U)(width - 1, 0), r)

      state := sDone
    }
  }

  when(state === sDone && io.resp.ready) {
    state := sIdle
  }
}
