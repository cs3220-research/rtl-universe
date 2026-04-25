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

/** IEEE 754 single-precision float bundle. */
class Fp32 extends Bundle {
  val sign     = Bool()
  val exponent = UInt(8.W)
  val mantissa = UInt(23.W)

  def isZero(): Bool = exponent === 0.U && mantissa === 0.U
  def isInf():  Bool = exponent === 0xFF.U && mantissa === 0.U
  def isNan():  Bool = exponent === 0xFF.U && mantissa =/= 0.U
}

object Fp32 {
  /** Extract Fp32 fields from a 32-bit word. */
  def fromWord(word: UInt): Fp32 = {
    val fp = Wire(new Fp32)
    fp.sign     := word(31)
    fp.exponent := word(30, 23)
    fp.mantissa := word(22, 0)
    fp
  }

  /** Convert an integer to IEEE 754 float32.
    *
    * @param word   The integer value (interpreted as signed if `signed` is true).
    * @param signed If true, treat `word` as a signed integer (SInt).
    *
    * The conversion implements round-to-nearest-even.
    */
  def fromInteger(word: UInt, signed: Bool): Fp32 = {
    val fp = Wire(new Fp32)

    // Determine sign and absolute value
    val isNeg   = signed && word(31)
    val absVal  = Mux(isNeg, (~word + 1.U)(31, 0), word)

    // Find the position of the highest set bit (0-indexed from LSB).
    // We use a priority encoder over 32 bits.
    // PriorityEncoder returns the index of the lowest set bit; we want the highest.
    // Use a reverse approach: reverse the bits and find the lowest set bit.
    val isZeroVal = absVal === 0.U

    // Find MSB position: highest set bit in absVal
    // Build a 32-bit one-hot mask for the MSB
    val msbPos = Wire(UInt(5.W))
    msbPos := 0.U
    for (i <- 0 until 32) {
      when(absVal(i)) {
        msbPos := i.U
      }
    }

    // Exponent = 127 + msbPos (use 9 bits to avoid overflow issues)
    val rawExp = 127.U(9.W) + msbPos

    // Mantissa: bits below MSB, shifted to fill 23 bits
    // The mantissa is the (msbPos-1) downto 0 bits of absVal,
    // left-aligned into 23 bits.
    // We need to shift absVal left by (23 - msbPos) or right by (msbPos - 23).
    // absVal(msbPos-1 .. 0) << (23 - msbPos) when msbPos <= 23
    // absVal(msbPos-1 .. 23) when msbPos > 23 (with rounding)

    // Full precision mantissa: take 23 bits below MSB, left-shifted
    // For rounding, we need bits 24 and beyond (guard/round/sticky)

    // Compute shifted mantissa (48 bits to capture round bits)
    // shiftedFrac = absVal << (47 - msbPos), MSB of absVal at bit 47
    // Then mantissa = shiftedFrac(46:24)
    // guard = shiftedFrac(23)
    // round/sticky = shiftedFrac(22:0) != 0

    // We use a 55-bit working space to be safe
    val extended = Cat(absVal, 0.U(23.W))  // 55 bits: [absVal:23'b0]
    // We want to left-shift so MSB of absVal is at bit 54 of extended
    // MSB is at position (31 + 23 - msbPos) = 54 - msbPos in extended
    // To bring it to bit 54, shift left by msbPos - (we already have it at 31+23 = 54 for msbPos=31)
    // Actually extended bit 54 corresponds to absVal bit 31.
    // absVal MSB is at bit msbPos in absVal, which is bit (msbPos + 23) in extended.
    // We need it at bit 54, so shift left by (54 - (msbPos + 23)) = 31 - msbPos

    // Use barrel shifter: shift extended left by (31 - msbPos)
    // extended is 55 bits; shifting left by up to 31 gives up to 86 bits.
    // We only need bits [54:0] of the result.
    val shiftAmt = 31.U - msbPos  // how much to shift left (0 to 31)
    // Compute the shifted value and take bits 54:0
    // Since Chisel << extends width, we explicitly keep 55 bits
    val shiftedWide = extended << shiftAmt  // width = 55 + 5 = 60 bits
    val shifted = shiftedWide(54, 0)

    // Now MSB of absVal is at bit 54 (implicit 1)
    // Mantissa bits: 53:31 (23 bits)
    // Guard bit: 30
    // Sticky: 29:0

    val rawMantissa = shifted(53, 31)
    val guardBit    = shifted(30)
    val stickyBits  = shifted(29, 0) =/= 0.U

    // Round to nearest even
    val roundUp = guardBit && (stickyBits || rawMantissa(0))
    val roundedMantissa = rawMantissa + Mux(roundUp, 1.U(23.W), 0.U(23.W))
    // If rounding caused mantissa overflow (all 1s -> 0), increment exponent
    val mantissaOverflow = roundUp && (rawMantissa === "h7FFFFF".U)
    val finalExp = Mux(mantissaOverflow, rawExp + 1.U, rawExp)
    val finalMantissa = Mux(mantissaOverflow, 0.U(23.W), roundedMantissa)

    fp.sign     := isNeg
    fp.exponent := Mux(isZeroVal, 0.U, finalExp(7, 0))
    fp.mantissa := Mux(isZeroVal, 0.U, finalMantissa)

    fp
  }
}
