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

/** Input command: ina * inb + inc */
class FmaCmd extends Bundle {
  val ina = new Fp32
  val inb = new Fp32
  val inc = new Fp32
}

/** Result of Stage 1: product of ina * inb, normalized.
  *
  * Fields:
  *  - prodSign:      sign of the product
  *  - prodExp:       biased exponent of the product (1..254 for normal, 0 for zero/underflow, 255 for inf)
  *  - prodMantissa:  24-bit significand (bit 23 = implicit 1)
  *  - prodIsZero:    product is zero (or underflow to zero)
  *  - prodIsInf:     product is infinity
  *  - prodIsNan:     product is NaN
  *  - incSign, incExp, incMantissa: passthrough of inc
  *  - incIsZero, incIsInf, incIsNan
  */
class FmaState1 extends Bundle {
  val prodSign     = Bool()
  val prodExp      = UInt(9.W)   // wide to detect overflow
  val prodMantissa = UInt(24.W)  // with implicit leading 1 at bit 23
  val prodIsZero   = Bool()
  val prodIsInf    = Bool()
  val prodIsNan    = Bool()
  val incSign      = Bool()
  val incExp       = UInt(8.W)
  val incMantissa  = UInt(24.W)  // with implicit leading 1 at bit 23
  val incIsZero    = Bool()
  val incIsInf     = Bool()
  val incIsNan     = Bool()
}

/** Result of Stage 2: aligned operands for addition.
  *
  *  Both operands are aligned to the same exponent (the larger one).
  *  - resultExp:   the common exponent (9 bits)
  *  - prodAligned: 27-bit aligned product significand (24-bit + 3 guard bits)
  *  - incAligned:  27-bit aligned inc significand
  *  - prodSign, incSign: signs of the two operands
  *  - isZero, isInf, isNan: special case flags
  */
class FmaState2 extends Bundle {
  val resultExp   = UInt(9.W)
  val prodAligned = UInt(27.W)
  val incAligned  = UInt(27.W)
  val prodSign    = Bool()
  val incSign     = Bool()
  val isZero      = Bool()
  val isInf       = Bool()
  val isNan       = Bool()
}

object Fma {

  /** Stage 1: Compute the product ina * inb.
    *
    * Returns FmaState1 with the normalized product and the passthrough addend.
    */
  def FmaStage1(cmd: FmaCmd): FmaState1 = {
    val s1 = Wire(new FmaState1)

    val ina = cmd.ina
    val inb = cmd.inb
    val inc = cmd.inc

    // Special cases for inputs
    val aIsZero = ina.isZero()
    val bIsZero = inb.isZero()
    val aIsInf  = ina.isInf()
    val bIsInf  = inb.isInf()
    val aIsNan  = ina.isNan()
    val bIsNan  = inb.isNan()

    val prodIsNan  = aIsNan || bIsNan || (aIsInf && bIsZero) || (aIsZero && bIsInf)
    val prodIsInf  = !prodIsNan && (aIsInf || bIsInf)
    val prodIsZeroSpec = !prodIsNan && !prodIsInf && (aIsZero || bIsZero)

    s1.prodSign := ina.sign ^ inb.sign

    // Significands with implicit leading 1 (24 bits each)
    val sigA = Cat(1.U(1.W), ina.mantissa)  // 24 bits
    val sigB = Cat(1.U(1.W), inb.mantissa)  // 24 bits

    // Product is up to 48 bits
    val product = sigA * sigB  // 48 bits

    // Normalize: product bits are either in [2^46, 2^47) or [2^47, 2^48)
    // Raw biased exponent: ina.exp + inb.exp - 127
    // We use wide arithmetic to detect underflow/overflow.
    // rawExpWide = (ina.exp zero-extended to 10 bits) + (inb.exp zero-extended to 10 bits) - 127
    val rawExpWide = (0.U(1.W) ## ina.exponent) +& (0.U(1.W) ## inb.exponent)
    // rawExpWide is 10 bits (max 510)

    val highBit = product(47)  // 1 if MSB at bit 47

    val prodExpRaw = Wire(UInt(10.W))
    val prodMantissa = Wire(UInt(24.W))

    when(highBit) {
      prodMantissa := product(47, 24)
      prodExpRaw   := rawExpWide - 127.U + 1.U  // 10-bit wide
    }.otherwise {
      prodMantissa := product(46, 23)
      prodExpRaw   := rawExpWide - 127.U  // 10-bit wide
    }

    // Detect underflow: rawExpWide < 127 (the subtraction would go negative)
    // or the result exponent is 0 or wraps (i.e., rawExpWide - 127 <= 0)
    // For normal product: prodExpRaw should be in [1, 254].
    // Underflow: rawExpWide < 127 (product exponent <= 0)
    val prodUnderflow = !prodIsNan && !prodIsInf && !prodIsZeroSpec &&
                        rawExpWide <= 127.U  // exp would be 0 or negative -> zero

    // Overflow: prodExpRaw >= 255 (infinity)
    val prodOverflow = !prodIsNan && !prodIsInf && !prodIsZeroSpec &&
                       !prodUnderflow && (prodExpRaw >= 255.U)

    val prodIsZero = prodIsZeroSpec || prodUnderflow
    s1.prodIsZero := prodIsZero
    s1.prodIsInf  := prodIsInf || prodOverflow
    s1.prodIsNan  := prodIsNan

    s1.prodMantissa := Mux(prodIsZero || prodIsNan, 0.U, prodMantissa)
    s1.prodExp      := Mux(prodIsZero || prodIsNan, 0.U,
                         Mux(prodIsInf || prodOverflow, 255.U, prodExpRaw(8, 0)))

    // Passthrough inc
    s1.incSign     := inc.sign
    s1.incExp      := inc.exponent
    s1.incMantissa := Cat(1.U(1.W), inc.mantissa)
    s1.incIsZero   := inc.isZero()
    s1.incIsInf    := inc.isInf()
    s1.incIsNan    := inc.isNan()

    s1
  }

  /** Stage 2: Align the product and the addend to the same exponent.
    *
    * The larger exponent is kept; the smaller operand is shifted right.
    * We use 27 bits (24 + 3 guard bits) to preserve rounding information.
    */
  def FmaStage2(s1: FmaState1): FmaState2 = {
    val s2 = Wire(new FmaState2)

    // Propagate special flags:
    // - NaN if product is NaN or inc is NaN
    // - Inf if product is Inf and inc is not NaN (and not Inf-Inf which is NaN)
    val resultIsNan = s1.prodIsNan || s1.incIsNan ||
                      (s1.prodIsInf && s1.incIsInf && (s1.prodSign =/= s1.incSign))
    val resultIsInf = !resultIsNan && (s1.prodIsInf || s1.incIsInf)

    s2.isNan    := resultIsNan
    s2.isInf    := resultIsInf
    s2.isZero   := s1.prodIsZero && s1.incIsZero
    s2.prodSign := s1.prodSign
    s2.incSign  := s1.incSign

    // Extended significands to 27 bits (24 + 3 guard/rounding bits)
    val prodSig27 = Cat(s1.prodMantissa, 0.U(3.W))  // 27 bits
    val incSig27  = Cat(s1.incMantissa,  0.U(3.W))  // 27 bits

    // Compare exponents; align the smaller to the larger
    val prodExpU = s1.prodExp  // 9 bits (0..511 but actual range 0..254)
    val incExpU  = s1.incExp   // 8 bits (0..255)

    // Use wide comparison
    val prodGtEqInc = prodExpU >= incExpU

    val shiftAmt = Wire(UInt(9.W))
    shiftAmt := Mux(prodGtEqInc,
      prodExpU - incExpU,
      incExpU - prodExpU)

    // Clamp shift to 27 (beyond that, the shifted value is negligible)
    val shiftClamped = Mux(shiftAmt > 27.U, 27.U, shiftAmt)(4, 0)

    val prodAligned = Wire(UInt(27.W))
    val incAligned  = Wire(UInt(27.W))
    val resultExp   = Wire(UInt(9.W))

    when(prodGtEqInc) {
      incAligned  := (incSig27 >> shiftClamped)(26, 0)
      prodAligned := prodSig27
      resultExp   := prodExpU
    }.otherwise {
      prodAligned := (prodSig27 >> shiftClamped)(26, 0)
      incAligned  := incSig27
      resultExp   := incExpU
    }

    // When one operand is zero, use the other's value directly
    val finalProdAligned = Mux(s1.prodIsZero, 0.U(27.W), prodAligned)
    val finalIncAligned  = Mux(s1.incIsZero,  0.U(27.W), incAligned)

    // Determine the result exponent (use the non-zero operand's exponent if one is zero)
    val finalResultExp = Wire(UInt(9.W))
    when(s1.prodIsZero && !s1.incIsZero) {
      finalResultExp := incExpU
    }.elsewhen(!s1.prodIsZero && s1.incIsZero) {
      finalResultExp := prodExpU
    }.otherwise {
      finalResultExp := resultExp
    }

    s2.prodAligned := finalProdAligned
    s2.incAligned  := finalIncAligned
    s2.resultExp   := finalResultExp

    s2
  }

  /** Stage 3: Add/subtract aligned significands, normalize, produce Fp32 output. */
  def FmaStage3(s2: FmaState2): Fp32 = {
    val out = Wire(new Fp32)

    when(s2.isNan) {
      // NaN propagation: canonical NaN
      out.sign     := 0.U
      out.exponent := 0xFF.U
      out.mantissa := 1.U
    }.elsewhen(s2.isInf) {
      // Infinity
      out.sign     := s2.prodSign
      out.exponent := 0xFF.U
      out.mantissa := 0.U
    }.elsewhen(s2.isZero) {
      // Both operands are zero
      out.sign     := 0.U
      out.exponent := 0.U
      out.mantissa := 0.U
    }.otherwise {
      // Normal computation: add or subtract based on signs
      val sameSign = s2.prodSign === s2.incSign

      val sumRaw   = Wire(UInt(28.W))
      val resultSign = Wire(Bool())

      when(sameSign) {
        // Add magnitudes
        sumRaw     := s2.prodAligned +& s2.incAligned
        resultSign := s2.prodSign
      }.otherwise {
        // Subtract: larger - smaller
        when(s2.prodAligned >= s2.incAligned) {
          sumRaw     := s2.prodAligned - s2.incAligned
          resultSign := s2.prodSign
        }.otherwise {
          sumRaw     := s2.incAligned - s2.prodAligned
          resultSign := s2.incSign
        }
      }

      // Normalize the result.
      // sumRaw is 28 bits. The normalized 24-bit significand has its leading 1 at bit 23.
      // In sumRaw, that position corresponds to bit 26 (since we have 3 guard bits).
      // Possible cases after addition:
      //   bit 27 set: overflow; shift right 1, exp++
      //   bit 26 set: already normalized
      //   bit 25..0 set: need to shift left

      val baseExp = s2.resultExp

      // Find the position of the leading 1 in sumRaw (27-bit space, bit 26 = target)
      val msbPos = Wire(UInt(5.W))
      msbPos := 0.U
      for (i <- 0 until 28) {
        when(sumRaw(i)) {
          msbPos := i.U
        }
      }

      val isResultZero = sumRaw === 0.U

      // The 27-bit layout of aligned significands:
      // bit 26: implicit leading 1 (for normalized form)
      // bits 25:3: mantissa (23 bits)
      // bits 2:0: guard/round/sticky bits
      //
      // After addition, sumRaw may be 28 bits (bit 27 set = carry out).

      val targetMsb = 26.U  // target position for the leading 1

      val finalExp  = Wire(UInt(9.W))
      val finalMant = Wire(UInt(23.W))

      when(isResultZero) {
        finalExp  := 0.U
        finalMant := 0.U
      }.elsewhen(msbPos > targetMsb) {
        // Overflow from addition: bit 27 is set, shift right by 1
        // New layout: bit 27 -> implicit 1; bits 26:4 -> mantissa; bits 3:0 -> guard
        // mantissa = sumRaw(26:4) = sumRaw >> 4 with masking
        val shiftR = msbPos - targetMsb  // should be 1
        val shifted = sumRaw >> shiftR
        val newExp = baseExp + shiftR
        when(newExp >= 255.U) {
          finalExp  := 255.U
          finalMant := 0.U
        }.otherwise {
          finalExp  := newExp
          // shifted has MSB (implicit 1) at bit 26; mantissa at bits 25:3
          finalMant := (shifted >> 3)(22, 0)
        }
      }.elsewhen(msbPos === targetMsb) {
        // Already normalized: MSB at bit 26
        // mantissa = sumRaw(25:3)
        when(baseExp === 0.U || baseExp >= 255.U) {
          finalExp  := Mux(baseExp >= 255.U, 255.U, 0.U)
          finalMant := 0.U
        }.otherwise {
          finalExp  := baseExp
          finalMant := (sumRaw >> 3)(22, 0)
        }
      }.otherwise {
        // Need to shift left to normalize (subtraction result)
        // msbPos < 26: shift left by (26 - msbPos)
        val shiftL = targetMsb - msbPos
        val shiftedWide = sumRaw << shiftL
        val shifted = shiftedWide(27, 0)
        when(baseExp <= shiftL) {
          // Underflow to zero
          finalExp  := 0.U
          finalMant := 0.U
        }.otherwise {
          val newExp = baseExp - shiftL
          when(newExp >= 255.U) {
            finalExp  := 255.U
            finalMant := 0.U
          }.otherwise {
            finalExp  := newExp
            // shifted has MSB (implicit 1) at bit 26; mantissa at bits 25:3
            finalMant := (shifted >> 3)(22, 0)
          }
        }
      }

      out.sign     := resultSign
      out.exponent := finalExp(7, 0)
      out.mantissa := finalMant
    }

    out
  }
}
