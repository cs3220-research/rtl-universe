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

// ---------------------------------------------------------------------------
// FMA pipeline stage bundles
// ---------------------------------------------------------------------------

/** Input command to the FMA pipeline. */
class FmaCmd extends Bundle {
  val ina = new Fp32
  val inb = new Fp32
  val inc = new Fp32
}

/** Intermediate state after FMA stage 1 (multiply step). */
class FmaState1 extends Bundle {
  // Sign of the product a*b.
  val prodSign     = UInt(1.W)
  // Biased exponent of the product (before normalization correction).
  // exp_a + exp_b - 127.  10 bits to hold the full signed range.
  val prodBiasedExp = SInt(10.W)
  // 48-bit product mantissa: (1.man_a) * (1.man_b).
  // The implicit binary point is between bits 46 and 45 (since each factor
  // contributes 24 bits with implicit 1 at position 23).
  val prodMantissa = UInt(48.W)
  // Pass-through of the addend.
  val inc          = new Fp32
  // Special case flags.
  val prodIsNan    = Bool()
  val prodIsInf    = Bool()
  val prodIsZero   = Bool()
}

/** Intermediate state after FMA stage 2 (align and add step). */
class FmaState2 extends Bundle {
  val sumSign      = UInt(1.W)
  val sumBiasedExp = SInt(10.W)   // biased exponent before normalization
  // 27-bit unnormalized sum mantissa with 2 guard bits.
  // The leading 1, when present, will be at bit 25 or 26 (overflow case).
  val sumMantissa  = UInt(27.W)
  val isNan        = Bool()
  val isInf        = Bool()
  val isZero       = Bool()
}

// ---------------------------------------------------------------------------
// FMA implementation
// ---------------------------------------------------------------------------

/** Fused Multiply-Add for IEEE 754 single-precision floats.
  *
  * Computes `result = (ina * inb) + inc` using a 3-stage combinational
  * pipeline.  Stages can be registered by the caller if pipelining is needed.
  *
  * Stage 1: multiply ina * inb.
  * Stage 2: align and add the product with inc.
  * Stage 3: normalise and round to produce the final Fp32.
  */
object Fma {

  // -------------------------------------------------------------------------
  // Stage 1: multiply
  // -------------------------------------------------------------------------
  def FmaStage1(cmd: FmaCmd): FmaState1 = {
    val s = Wire(new FmaState1)
    val a = cmd.ina
    val b = cmd.inb

    // Product sign.
    s.prodSign := a.sign ^ b.sign

    // Biased exponent: E_a + E_b - bias (bias = 127).
    // Use sign-extended arithmetic to detect underflow.
    s.prodBiasedExp := (a.exponent.zext + b.exponent.zext - 127.S)(9, 0).asSInt

    // Product mantissa: (1.man_a) * (1.man_b) as 48-bit integer.
    // Implicit leading 1 for normal numbers.
    val manA = Mux(a.exponent =/= 0.U, Cat(1.U(1.W), a.mantissa), Cat(0.U(1.W), a.mantissa))
    val manB = Mux(b.exponent =/= 0.U, Cat(1.U(1.W), b.mantissa), Cat(0.U(1.W), b.mantissa))
    s.prodMantissa := (manA * manB)(47, 0)

    // Special cases.
    s.prodIsNan  := a.isNan() || b.isNan() ||
                    (a.isInf() && b.isZero()) || (a.isZero() && b.isInf())
    s.prodIsInf  := (a.isInf() || b.isInf()) && !s.prodIsNan
    s.prodIsZero := (a.isZero() || b.isZero()) && !s.prodIsInf && !s.prodIsNan

    s.inc := cmd.inc
    s
  }

  // -------------------------------------------------------------------------
  // Stage 2: align addend and add
  //
  // Key insight about the product mantissa representation:
  //   prodMantissa is a 48-bit integer representing (1.man_a)*(1.man_b).
  //   Each factor has its implicit 1 at bit position 23 (0-indexed), so the
  //   product's implicit binary point is between bits 45 and 46:
  //     value = prodMantissa / 2^46
  //   The value is in [1, 4):
  //     - if bit 47 is set: in [2, 4) → true biased exp = prodBiasedExp + 1
  //     - if bit 46 is set (and bit 47 = 0): in [1, 2) → biased exp = prodBiasedExp
  //     - otherwise: denormal product (shouldn't happen for normal inputs)
  //
  // We normalise by finding the leading 1 and adjusting the exponent so that
  // the leading 1 is at bit 23 of a 24-bit normalised mantissa.
  // -------------------------------------------------------------------------
  def FmaStage2(s1: FmaState1): FmaState2 = {
    val s = Wire(new FmaState2)
    val c = s1.inc

    // --- Special case propagation ---
    val cIsNan  = c.isNan()
    val cIsInf  = c.isInf()
    val cIsZero = c.isZero()

    val infSigns = s1.prodIsInf && cIsInf && (s1.prodSign =/= c.sign)
    s.isNan  := s1.prodIsNan || cIsNan || infSigns
    s.isInf  := (s1.prodIsInf || cIsInf) && !s.isNan

    // --- Normalise the product mantissa ---
    // Find the leading 1 bit position in the 48-bit product.
    // For a product of two normal 24-bit floats the leading 1 is at bit 46 or 47.
    val prodLead = Wire(UInt(6.W))
    prodLead := 0.U
    for (k <- 0 until 48) {
      when(s1.prodMantissa(k)) { prodLead := k.U }
    }

    // The product's normalised biased exponent:
    //   prodBiasedExp was computed as E_a + E_b - 127.
    //   The 48-bit mantissa integer represents a value in [1, 4) when the
    //   leading 1 is at bit 46 or 47.  The binary point is after bit 45
    //   (value = int / 2^46).
    //   Normalised: leading 1 at bit 23 of a 24-bit result.
    //   Shift needed to move leading 1 from position `prodLead` to position 23:
    //     shift = 23 - prodLead  (positive → shift right, negative → shift left)
    //   Exponent adjustment: -shift (right shift increases exponent).
    //   Correct biased exp = prodBiasedExp + (prodLead - 46) + (46 - 23)
    //                       = prodBiasedExp + prodLead - 23
    //   But we also need to account for the binary point at bit 45:
    //     value = prodMantissa / 2^46
    //   After normalisation to 1.xxx * 2^e:
    //     prodNormExp = prodBiasedExp + prodLead - 46
    //   and we extract 24 bits starting from prodLead down to (prodLead - 23).
    val prodNormBiasedExp = s1.prodBiasedExp + prodLead.zext - 46.S  // SInt

    // Extract the 24 normalised product mantissa bits (implicit 1 at bit 23).
    // Shift the 48-bit integer so that bit prodLead lands at bit 23.
    val prodMantShift = Wire(SInt(7.W))
    prodMantShift := prodLead.zext - 23.S  // positive → shift right
    val prodMant24 = Wire(UInt(24.W))
    when(prodMantShift >= 0.S) {
      prodMant24 := (s1.prodMantissa >> prodMantShift.asUInt)(23, 0)
    }.otherwise {
      prodMant24 := (s1.prodMantissa << (-prodMantShift).asUInt)(23, 0)
    }

    // Addend c: 24-bit mantissa with implicit leading 1.
    val cMant24 = Mux(c.exponent =/= 0.U,
                      Cat(1.U(1.W), c.mantissa),
                      Cat(0.U(1.W), c.mantissa))
    val cNormBiasedExp = c.exponent.zext  // SInt(9)

    // --- Align the two operands ---
    // Compare exponents and shift the smaller-exponent operand right.
    // We work at 26-bit precision (24 mantissa + 2 guard bits).
    val prodMant26 = Cat(prodMant24, 0.U(2.W))   // 26 bits, 2 LSBs = guard
    val cMant26    = Cat(cMant24,    0.U(2.W))

    val expDiff = prodNormBiasedExp - cNormBiasedExp  // SInt
    val resultBiasedExp = Wire(SInt(10.W))

    val pAligned = Wire(UInt(26.W))
    val cAligned = Wire(UInt(26.W))

    when(expDiff >= 0.S) {
      resultBiasedExp := prodNormBiasedExp
      pAligned := prodMant26
      val shift = expDiff.asUInt
      cAligned := Mux(shift >= 26.U, 0.U, (cMant26 >> shift)(25, 0))
    }.otherwise {
      resultBiasedExp := cNormBiasedExp
      cAligned := cMant26
      val shift = (-expDiff).asUInt
      pAligned := Mux(shift >= 26.U, 0.U, (prodMant26 >> shift)(25, 0))
    }

    // --- Signed add ---
    // Use 28-bit signed arithmetic to capture any carry-out from the 26-bit
    // aligned mantissas.
    val prodS = pAligned.zext   // 27-bit SInt (zero-extend 26-bit)
    val cS    = cAligned.zext

    val prodSigned = Mux(s1.prodSign.asBool, -prodS, prodS)
    val cSigned    = Mux(c.sign.asBool,      -cS,    cS)
    val sumSigned  = prodSigned + cSigned  // 28-bit SInt

    val sumNeg = sumSigned < 0.S
    // Keep 27 bits (includes potential carry overflow bit at position 26).
    val sumAbs = Mux(sumNeg, (-sumSigned)(26, 0), sumSigned(26, 0))

    // Output sign.
    val finalSign = Wire(UInt(1.W))
    when(s1.prodIsZero && !cIsZero) {
      finalSign := c.sign
    }.elsewhen(!s1.prodIsZero && cIsZero) {
      finalSign := s1.prodSign
    }.elsewhen(sumNeg) {
      finalSign := 1.U
    }.otherwise {
      finalSign := 0.U
    }
    s.sumSign := finalSign

    s.sumBiasedExp := resultBiasedExp
    s.sumMantissa  := sumAbs
    s.isZero       := !s.isNan && !s.isInf && (sumAbs === 0.U) && s1.prodIsZero && cIsZero
    s
  }

  // -------------------------------------------------------------------------
  // Stage 3: normalise and round
  //
  // sumMantissa is a 27-bit value.  The two LSBs are guard bits; the normal
  // mantissa occupies bits[26:2].  Stage 2 sets sumBiasedExp to the biased
  // exponent that is correct when the leading 1 is at bit 25 of the 26-bit
  // "mantissa+guard" part (i.e., at bit 25 with guard bits below).
  //
  // If the leading 1 is at a different position we normalise and adjust the
  // exponent accordingly.
  //
  // Convention (matching Stage 2 output):
  //   sumBiasedExp is correct when leading 1 is at bit 25.
  //   If leading 1 is at bit 26 (carry overflow), exponent needs +1.
  //   If leading 1 is at bit k < 25, exponent needs -(25-k).
  // -------------------------------------------------------------------------
  def FmaStage3(s2: FmaState2): Fp32 = {
    val fp = Wire(new Fp32)

    when(s2.isNan) {
      fp.sign     := 0.U
      fp.exponent := 0xFF.U
      fp.mantissa := (1 << 22).U   // quiet NaN
    }.elsewhen(s2.isInf) {
      fp.sign     := s2.sumSign
      fp.exponent := 0xFF.U
      fp.mantissa := 0.U
    }.elsewhen(s2.isZero || (s2.sumMantissa === 0.U)) {
      fp.sign     := 0.U
      fp.exponent := 0.U
      fp.mantissa := 0.U
    }.otherwise {
      // Find the leading 1 in the 27-bit sumMantissa.
      val mant = s2.sumMantissa  // 27 bits
      val leadPos = Wire(UInt(5.W))
      leadPos := 0.U
      for (k <- 0 until 27) {
        when(mant(k)) { leadPos := k.U }
      }

      // Normalise so that the leading 1 is at bit 25.
      // shift = 25 - leadPos  (positive → shift mant left; negative → shift right)
      val shift = Wire(SInt(6.W))
      shift := 25.S - leadPos.zext

      // Use a 27-bit shifted result.
      val shifted = Wire(UInt(27.W))
      when(shift >= 0.S) {
        shifted := (mant << shift.asUInt)(26, 0)
      }.otherwise {
        shifted := (mant >> (-shift).asUInt)(26, 0)
      }

      // With leading 1 at bit 25:
      //   bit 25 = implicit leading 1 (discarded)
      //   bits [24:2] = 23 stored mantissa bits
      //   bits [1:0]  = guard bits (discarded for now)
      val mantissa23 = shifted(24, 2)

      // Exponent adjustment:
      //   sumBiasedExp is calibrated for leading 1 at bit 25.
      //   Shifting left by N moves leading 1 up → exponent decreases by N.
      //   adjExp = sumBiasedExp - shift
      val adjExp = s2.sumBiasedExp - shift  // SInt

      when(adjExp <= 0.S) {
        fp.sign     := 0.U
        fp.exponent := 0.U
        fp.mantissa := 0.U
      }.elsewhen(adjExp >= 255.S) {
        fp.sign     := s2.sumSign
        fp.exponent := 0xFF.U
        fp.mantissa := 0.U
      }.otherwise {
        fp.sign     := s2.sumSign
        fp.exponent := adjExp.asUInt(7, 0)
        fp.mantissa := mantissa23
      }
    }

    fp
  }
}

/** Top-level Fma module wrapper. */
class Fma extends Module {
  val io = IO(new Bundle {
    val cmd = Input(new FmaCmd)
    val out = Output(new Fp32)
  })

  val s1 = Fma.FmaStage1(io.cmd)
  val s2 = Fma.FmaStage2(s1)
  io.out := Fma.FmaStage3(s2)
}
