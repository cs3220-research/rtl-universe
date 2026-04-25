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

/** Hardware Bundle representing an IEEE 754 single-precision (32-bit) float.
  *
  * Layout (MSB to LSB):
  *   [31]    sign     (1 bit)
  *   [30:23] exponent (8 bits, biased by 127)
  *   [22:0]  mantissa (23 bits, implicit leading 1 for normal numbers)
  */
class Fp32 extends Bundle {
  val sign     = UInt(1.W)
  val exponent = UInt(8.W)
  val mantissa = UInt(23.W)

  /** Returns true when this value represents positive or negative zero.
    * Zero is encoded as exponent == 0 and mantissa == 0.
    */
  def isZero(): Bool = (exponent === 0.U) && (mantissa === 0.U)

  /** Returns true when this value represents positive or negative infinity.
    * Inf is encoded as exponent == 0xFF and mantissa == 0.
    */
  def isInf(): Bool = (exponent === 0xFF.U) && (mantissa === 0.U)

  /** Returns true when this value represents NaN (Not a Number).
    * NaN is encoded as exponent == 0xFF and mantissa != 0.
    */
  def isNan(): Bool = (exponent === 0xFF.U) && (mantissa =/= 0.U)

  /** Pack the three fields back into a single 32-bit UInt word. */
  def asWord(): UInt = Cat(sign, exponent, mantissa)
}

/** Companion object for Fp32 providing constructors. */
object Fp32 {
  /** Unpack a 32-bit UInt word into an Fp32 Bundle. */
  def fromWord(word: UInt): Fp32 = {
    val fp = Wire(new Fp32)
    fp.sign     := word(31)
    fp.exponent := word(30, 23)
    fp.mantissa := word(22, 0)
    fp
  }

  /** Convert an unsigned or signed integer to a single-precision float.
    *
    * @param int    The 32-bit integer value (UInt representation).
    * @param signed When true, treat int as a signed (two's-complement) value.
    * @return An Fp32 bundle containing the hardware floating-point conversion.
    *
    * The algorithm:
    *   1. Determine sign (for signed conversion, check the MSB).
    *   2. Compute the absolute value.
    *   3. Count leading zeros to find the position of the implicit leading 1.
    *   4. Shift the mantissa and compute the biased exponent.
    *   5. Handle the zero case specially.
    */
  def fromInteger(int: UInt, signed: Bool): Fp32 = {
    val fp = Wire(new Fp32)

    // --- Sign handling ---
    val sign  = Mux(signed && int(31), 1.U(1.W), 0.U(1.W))
    // Absolute value: for signed negative, negate; otherwise use as-is.
    val absVal = Mux(signed && int(31), (~int + 1.U)(31, 0), int)

    // --- Leading-zero count to determine exponent ---
    // We work with a 32-bit value.  The IEEE exponent is biased by 127.
    // For a normal number with highest set bit at position k (0-indexed from LSB),
    // the unbiased exponent is k, so biased exponent = k + 127.
    //
    // We use a priority encoder on the reversed bit vector to find the most
    // significant set bit.

    // Detect zero: if absVal == 0, output is 0.0
    val isZeroVal = (absVal === 0.U)

    // Priority-encode to find the position of the leading 1 (bit index from LSB).
    // chisel3.util.Log2 returns the index of the most significant set bit.
    val leadingBitPos = Log2(absVal) // UInt, result in range 0..31

    // Biased exponent = leadingBitPos + 127
    val biasedExp = leadingBitPos + 127.U

    // Mantissa: shift absVal left so the leading 1 is in bit position 23,
    // then take bits [22:0].
    // absVal has its leading 1 at position leadingBitPos.
    // We need to shift left by (23 - leadingBitPos) or right by (leadingBitPos - 23).
    // Use a barrel shifter: shift left by (23 - leadingBitPos) keeping 32 bits,
    // then take the lower 23 bits (which are the fractional mantissa bits).
    //
    // Shift amount: if leadingBitPos >= 23, shift right; else shift left.
    val shiftLeft  = 23.U >= leadingBitPos
    val shiftAmt   = Mux(shiftLeft, 23.U - leadingBitPos, leadingBitPos - 23.U)
    val shifted    = Mux(shiftLeft, absVal << shiftAmt, absVal >> shiftAmt)
    val mantissa   = shifted(22, 0)

    fp.sign     := Mux(isZeroVal, 0.U, sign)
    fp.exponent := Mux(isZeroVal, 0.U, biasedExp)
    fp.mantissa := Mux(isZeroVal, 0.U, mantissa)

    fp
  }
}
