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

/** IEEE 754 single-precision floating point bundle. */
class Fp32 extends Bundle {
  val sign     = Bool()
  val exponent = UInt(8.W)
  val mantissa = UInt(23.W)

  /** Returns true if this represents +0 or -0. */
  def isZero(): Bool = (exponent === 0.U) && (mantissa === 0.U)

  /** Returns true if this represents +Inf or -Inf. */
  def isInf(): Bool = (exponent === 0xFF.U) && (mantissa === 0.U)

  /** Returns true if this represents a NaN. */
  def isNan(): Bool = (exponent === 0xFF.U) && (mantissa =/= 0.U)
}

object Fp32 {
  /** Constructs an Fp32 from a 32-bit IEEE 754 word. */
  def fromWord(w: UInt): Fp32 = {
    require(w.getWidth == 32)
    val fp = Wire(new Fp32)
    fp.sign     := w(31)
    fp.exponent := w(30, 23)
    fp.mantissa := w(22,  0)
    fp
  }

  /** Converts a UInt or SInt (passed as UInt) to Fp32.
    *
    * @param int    The integer value as UInt(32.W).
    * @param signed Whether to interpret int as a signed (two's-complement) integer.
    */
  def fromInteger(int: UInt, signed: Bool): Fp32 = {
    require(int.getWidth == 32)

    val fp = Wire(new Fp32)

    // Determine sign and absolute magnitude
    val isNeg   = signed && int(31)
    val absVal  = Mux(isNeg, (~int).asUInt + 1.U, int)(31, 0)  // 32-bit absolute value

    // Special case: value is zero
    val isZero = absVal === 0.U

    // Find leading-one position (bit index of most-significant 1) in absVal.
    // lzc = number of leading zeros in 32-bit absVal (0..32)
    // msb position = 31 - lzc  (0-indexed from lsb)
    // For nonzero: exponent = 127 + msb_pos = 127 + 31 - lzc = 158 - lzc
    val lzc = PriorityEncoder(Reverse(absVal))  // counts trailing zeros of reversed = leading zeros of original

    // msb_pos in [0,31]: exponent biased = 127 + msb_pos
    val msbPos  = (31.U(6.W) - lzc)(5, 0)       // position of MSB, 6-bit arithmetic
    val expVal  = (127.U(9.W) + msbPos)(8, 0)   // biased exponent, force 9-bit then slice

    // Mantissa: the 23 bits below the implicit leading 1.
    // absVal = 1.mantissa * 2^msb_pos
    // Shift absVal left so the leading 1 is at bit 31.
    // Then bits [30:8] are the 23 mantissa bits, bits [7:0] are the round/sticky bits.
    // We need to do round-to-nearest-even.
    val shifted = (absVal << lzc)(31, 0)  // shift absVal left by lzc, keep 32 bits
    val mantissa23  = shifted(30, 8)      // 23 bits of mantissa
    val roundBit    = shifted(7)          // the bit just below mantissa
    val stickyBits  = shifted(6, 0) =/= 0.U  // any bits below round bit
    // Round to nearest even
    val roundUp    = roundBit && (stickyBits || mantissa23(0))
    val mantRnded  = mantissa23 +& roundUp  // 24-bit result (can carry into bit 23)
    val mantissa23f = mantRnded(22, 0)
    // If rounding caused carry into bit 23, increment exponent
    val expAdj     = Mux(mantRnded(23), 1.U(9.W), 0.U(9.W))
    val expFinal   = (expVal.asUInt +& expAdj)(7, 0)

    fp.sign     := isNeg
    fp.exponent := Mux(isZero, 0.U(8.W), expFinal)
    fp.mantissa := Mux(isZero, 0.U(23.W), mantissa23f)

    fp
  }
}
