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

/** Input command to FMA: computes ina * inb + inc */
class FmaCmd extends Bundle {
  val ina = new Fp32
  val inb = new Fp32
  val inc = new Fp32
}

/** Intermediate state after stage 1. */
class FmaState1 extends Bundle {
  val prodSign   = Bool()
  val prodExp    = SInt(10.W)   // biased exponent: expA + expB - 127
  val prodMant   = UInt(48.W)   // 24x24 product (with implicit 1s)
  val isNaN      = Bool()
  val isInf      = Bool()
  val isZero     = Bool()
  val nanPayload = UInt(23.W)
  val cSign      = Bool()
  val cExp       = UInt(8.W)
  val cMant      = UInt(23.W)
  val cIsZero    = Bool()
  val cIsInf     = Bool()
  val cIsNaN     = Bool()
}

/** Intermediate state after stage 2. */
class FmaState2 extends Bundle {
  val sign       = Bool()
  val exp        = SInt(10.W)   // see below
  val mant       = UInt(98.W)   // working mantissa
  val isNaN      = Bool()
  val isInf      = Bool()
  val isZero     = Bool()
  val nanPayload = UInt(23.W)
}

object Fma {

  def FmaStage1(cmd: FmaCmd): FmaState1 = {
    val s1 = Wire(new FmaState1)

    val aIsNaN  = cmd.ina.isNan()
    val bIsNaN  = cmd.inb.isNan()
    val aIsInf  = cmd.ina.isInf()
    val bIsInf  = cmd.inb.isInf()
    val aIsZero = cmd.ina.isZero()
    val bIsZero = cmd.inb.isZero()

    s1.prodSign := cmd.ina.sign ^ cmd.inb.sign
    val rawExp = cmd.ina.exponent.zext +& cmd.inb.exponent.zext - 127.S
    s1.prodExp := rawExp(9, 0).asSInt

    val aMantFull = Cat(!aIsZero && (cmd.ina.exponent =/= 0.U), cmd.ina.mantissa)
    val bMantFull = Cat(!bIsZero && (cmd.inb.exponent =/= 0.U), cmd.inb.mantissa)
    s1.prodMant := aMantFull * bMantFull

    val mulNaN = aIsNaN || bIsNaN || (aIsZero && bIsInf) || (bIsZero && aIsInf)
    s1.isNaN := mulNaN
    s1.isInf := !mulNaN && (aIsInf || bIsInf)
    s1.isZero := !mulNaN && !aIsInf && !bIsInf && (aIsZero || bIsZero)
    s1.nanPayload := Mux(aIsNaN, cmd.ina.mantissa,
                    Mux(bIsNaN, cmd.inb.mantissa, (1 << 22).U))

    s1.cSign   := cmd.inc.sign
    s1.cExp    := cmd.inc.exponent
    s1.cMant   := cmd.inc.mantissa
    s1.cIsZero := cmd.inc.isZero()
    s1.cIsInf  := cmd.inc.isInf()
    s1.cIsNaN  := cmd.inc.isNan()

    s1
  }

  /** Stage 2: Align addend to product and compute sum.
    *
    * Working space: 98 bits.
    *
    * Product placement:
    *   prodMant (48 bits) at bits 73:26 of working (shifted left 26 bits for guard room).
    *   Bit k of working = bit(k-26) of prodMant represents 2^(k - 26 + prodExp - 173)
    *                    = 2^(k + prodExp - 199).
    *   Bit 0 represents 2^(prodExp - 199).
    *
    * Addend placement:
    *   cMantFull (24 bits) bit 0 should represent 2^(cExpS - 150).
    *   This should go to working bit k where 2^(k + prodExp - 199) = 2^(cExpS - 150).
    *   => k = cExpS - prodExp + 49.
    *
    *   addendShift = cExpS - prodExp + 49.
    *   When addendShift = 49 + 24 = 73: addend MSB at bit 73 (same as product MSB). Sum can carry to bit 74.
    *   When addendShift = 73: addend MSB at bit 73+23 = 96. Max index = 96 < 97.
    *   98-bit space ensures no overflow for addend shifts up to 73 + 23 < 98.
    *
    * Clamping: addendShift ∈ [-73, 73]. Beyond ±73, the addend has no effect on the 23-bit result.
    */
  def FmaStage2(s1: FmaState1): FmaState2 = {
    val s2 = Wire(new FmaState2)

    val infMinusInf = s1.isInf && s1.cIsInf && (s1.prodSign =/= s1.cSign)
    val resultNaN   = s1.isNaN || s1.cIsNaN || infMinusInf
    val resultInf   = !resultNaN && (s1.isInf || s1.cIsInf)
    val resultZero  = !resultNaN && !resultInf && s1.isZero && s1.cIsZero

    s2.isNaN      := resultNaN
    s2.isInf      := resultInf
    s2.isZero     := resultZero
    s2.nanPayload := Mux(s1.isNaN, s1.nanPayload,
                    Mux(s1.cIsNaN, s1.cMant, (1 << 22).U))

    val cExpS     = s1.cExp.zext  // zero-extend UInt(8.W) to SInt(9.W) (0..255)
    val cIsNorm   = s1.cExp =/= 0.U
    val cMantFull = Cat(cIsNorm, s1.cMant)  // 24 bits

    // Product in 98-bit working space: bits 73:26 (shifted up 26 bits).
    val prodWorking98 = Cat(s1.prodMant, 0.U(26.W)).pad(98)  // 98 bits

    // Addend shift = cExpS - prodExp + 49
    val addendShiftRaw = (cExpS - s1.prodExp) + 49.S  // ~10-bit SInt
    val MAXSH = 73
    val addendShift = Mux(addendShiftRaw > MAXSH.S, MAXSH.S,
                     Mux(addendShiftRaw < (-MAXSH).S, (-MAXSH).S,
                         addendShiftRaw))

    // Barrel shift cMantFull into 98-bit space
    val cMant98 = cMantFull.pad(98)

    val LSHIFT = MAXSH  // max left shift
    val RSHIFT = MAXSH  // max right shift

    val cMantL = Wire(Vec(LSHIFT + 1, UInt(98.W)))
    for (i <- 0 to LSHIFT) { cMantL(i) := (cMant98 << i)(97, 0) }

    val cMantR = Wire(Vec(RSHIFT + 1, UInt(98.W)))
    for (i <- 0 to RSHIFT) { cMantR(i) := cMant98 >> i.U }

    val cWorking98 = Mux(s1.cIsZero, 0.U(98.W),
      Mux(addendShift >= 0.S,
        MuxCase(cMant98, (0 to LSHIFT).map(i => (addendShift === i.S) -> cMantL(i))),
        MuxCase(cMant98, (0 to RSHIFT).map(i => (addendShift === (-i).S) -> cMantR(i)))
      )
    )

    val sameSign     = !(s1.prodSign ^ s1.cSign)
    val sumAbs98     = (prodWorking98 +& cWorking98)(97, 0)  // keep 98 bits
    val diffPA98     = (prodWorking98 -% cWorking98)(97, 0)
    val diffAP98     = (cWorking98 -% prodWorking98)(97, 0)
    val addendLarger = cWorking98 > prodWorking98

    val resultMant98 = WireDefault(0.U(98.W))
    val resultSign   = WireDefault(false.B)

    when (s1.isZero) {
      resultSign   := s1.cSign
      resultMant98 := cWorking98
    } .elsewhen (s1.cIsZero) {
      resultSign   := s1.prodSign
      resultMant98 := prodWorking98
    } .elsewhen (sameSign) {
      resultSign   := s1.prodSign
      resultMant98 := sumAbs98
    } .otherwise {
      when (addendLarger) {
        resultSign   := s1.cSign
        resultMant98 := diffAP98
      } .otherwise {
        resultSign   := s1.prodSign
        resultMant98 := diffPA98
      }
    }

    // Base exponent: bit 0 of working = 2^(prodExp - 199).
    // We store prodExp - 199 + 127 = prodExp - 72 as s2.exp.
    // In stage 3: biased fp32 exponent of result = leadPos + s2.exp.
    s2.exp  := s1.prodExp - 72.S
    s2.sign := resultSign
    s2.mant := resultMant98

    s2
  }

  /** Stage 3: Find MSB of 98-bit working mantissa, normalize, round. */
  def FmaStage3(s2: FmaState2): Fp32 = {
    val out = Wire(new Fp32)

    when (s2.isNaN) {
      out.sign     := 0.U
      out.exponent := 0xFF.U
      out.mantissa := (s2.nanPayload | (1.U << 22))(22, 0)
    } .elsewhen (s2.isInf) {
      out.sign     := s2.sign
      out.exponent := 0xFF.U
      out.mantissa := 0.U
    } .elsewhen (s2.isZero || s2.mant === 0.U) {
      out.sign     := 0.U
      out.exponent := 0.U
      out.mantissa := 0.U
    } .otherwise {
      val mant98 = s2.mant  // 98 bits

      // Find position of MSB (0..97)
      val penc    = PriorityEncoder(Reverse(mant98))    // 7-bit UInt (log2Ceil(98)=7)
      // leadPos = 97 - penc (MSB position)
      val leadPos = (97.U(8.W) - penc.pad(8))(6, 0)    // 7-bit UInt, 0..97
      val leadPosS = Cat(0.U(2.W), leadPos).asSInt      // 9-bit SInt (always positive)

      // Biased fp32 exponent = leadPos + s2.exp
      val expNorm = s2.exp + leadPosS  // 11-bit SInt

      // Normalize: shift mant98 left so MSB is at bit 97
      val shiftLeft = 97.U - leadPos  // 7-bit UInt, 0..97

      val NSHIFT = 97
      val mantShifted = Wire(Vec(NSHIFT + 1, UInt(98.W)))
      for (i <- 0 to NSHIFT) {
        mantShifted(i) := (mant98 << i)(97, 0)
      }
      val mantNorm = MuxCase(mant98,
        (0 to NSHIFT).map(i => (shiftLeft === i.U) -> mantShifted(i)))

      // mantNorm: leading 1 at bit 97.
      // Mantissa bits [96:74] = 23 bits.
      // Round bit = bit 73, sticky = bits 72:0.
      val mantissa23 = mantNorm(96, 74)
      val roundBit   = mantNorm(73)
      val stickyBits = mantNorm(72, 0) =/= 0.U
      val roundUp    = roundBit && (stickyBits || mantissa23(0))
      val mantRnded  = mantissa23 +& roundUp   // 24 bits
      val mantFinal  = mantRnded(22, 0)
      val expCarry   = Mux(mantRnded(23), 1.S, 0.S)
      val expFinal   = expNorm + expCarry

      when (expFinal >= 255.S) {
        out.sign     := s2.sign
        out.exponent := 0xFF.U
        out.mantissa := 0.U
      } .elsewhen (expFinal <= 0.S) {
        out.sign     := 0.U
        out.exponent := 0.U
        out.mantissa := 0.U
      } .otherwise {
        out.sign     := s2.sign
        out.exponent := expFinal(7, 0).asUInt
        out.mantissa := mantFinal
      }
    }

    out
  }
}
