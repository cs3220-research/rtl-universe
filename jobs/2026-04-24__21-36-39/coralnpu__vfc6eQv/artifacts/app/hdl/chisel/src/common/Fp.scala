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

/** IEEE 754 single-precision floating-point bundle. */
class Fp32 extends Bundle {
  val sign     = Bool()
  val exponent = UInt(8.W)
  val mantissa = UInt(23.W)

  /** Returns true when the value is positive or negative zero (exp=0, mantissa=0). */
  def isZero(): Bool = (exponent === 0.U) && (mantissa === 0.U)

  /** Returns true when the value is +/-Inf (exp=0xFF, mantissa=0). */
  def isInf(): Bool = (exponent === 0xFF.U) && (mantissa === 0.U)

  /** Returns true when the value is NaN (exp=0xFF, mantissa!=0). */
  def isNan(): Bool = (exponent === 0xFF.U) && (mantissa =/= 0.U)
}

object Fp32 {
  /** Reinterpret a 32-bit word as an Fp32. */
  def fromWord(word: UInt): Fp32 = {
    val fp = Wire(new Fp32)
    fp.sign     := word(31)
    fp.exponent := word(30, 23)
    fp.mantissa := word(22, 0)
    fp
  }

  /** Convert an integer (signed or unsigned) to Fp32.
    *
    * Implements round-to-nearest-even for the mantissa.
    *
    * @param int    The integer value as a UInt (caller handles sign interpretation).
    * @param signed If true, treat `int` as a two's-complement signed value.
    */
  def fromInteger(int: UInt, signed: Bool): Fp32 = {
    val fp = Wire(new Fp32)

    // Determine sign and magnitude
    val isNeg   = signed && int(31)
    val magnitude = Mux(isNeg, (~int) + 1.U, int)

    // Special case: value is zero
    val isZero = magnitude === 0.U

    // Find the position of the leading 1 (0-indexed from LSB).
    // PriorityEncoder(Reverse(x)) gives (width-1) - leadPos for a width-bit signal.
    // Since magnitude is 32-bit: leadPos = 31 - PriorityEncoder(Reverse(magnitude)).
    val leadPos = 31.U - PriorityEncoder(Reverse(magnitude))  // position of highest set bit

    // The unbiased exponent = leadPos (position of leading 1)
    // Biased exponent = leadPos + 127
    val biasedExp = leadPos +& 127.U

    // Shift magnitude so leading 1 is at bit 23
    // mantissa = magnitude << (23 - leadPos) if leadPos <= 23
    //          = magnitude >> (leadPos - 23)  if leadPos > 23  (with rounding)
    val shiftLeft  = 23.U - leadPos
    val shiftRight = leadPos - 23.U

    // Compute pre-round mantissa (24 bits with implicit leading 1 at bit 23)
    val mantissaFull = Mux(leadPos <= 23.U,
      (magnitude << shiftLeft)(23, 0),
      (magnitude >> shiftRight)(23, 0)
    )

    // Round bit and sticky for round-to-nearest-even when shifting right
    val roundBit  = Mux(leadPos > 23.U, (magnitude >> (shiftRight - 1.U))(0), 0.U)
    val stickyMask = Mux(leadPos > 23.U,
      ((1.U << (shiftRight - 1.U)) - 1.U)(31, 0),
      0.U(32.W))
    val stickyBit = Mux(leadPos > 23.U, (magnitude & stickyMask).orR, false.B)

    // Round-to-nearest-even: increment mantissa if round=1 and (sticky=1 or lsb=1)
    val doRound = roundBit.asBool && (stickyBit || mantissaFull(0))
    val mRndRaw = mantissaFull +& Mux(doRound, 1.U, 0.U)  // 25 bits (carry-preserving)

    // If carry propagates out of bit 23 (bit 24 of mRndRaw is set), increment exponent
    val mantissaOverflow = mRndRaw(24)
    val finalMantissa = Mux(mantissaOverflow, 0.U(23.W), mRndRaw(22, 0))
    val finalExp      = Mux(mantissaOverflow, biasedExp + 1.U, biasedExp)(7, 0)

    fp.sign     := isNeg
    fp.exponent := Mux(isZero, 0.U, finalExp)
    fp.mantissa := Mux(isZero, 0.U, finalMantissa)
    fp
  }
}
