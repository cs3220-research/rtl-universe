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

// ============================================================
// FMA Implementation: ina * inb + inc  (IEEE 754 FP32)
// ============================================================
//
// Representation convention used throughout:
//   A 48-bit integer `mantissa` at scale 2^E means the value is
//   mantissa * 2^E.
//
// Product:  sigA * sigB (48-bit) at scale 2^(prodExp - 173)
//   where prodExp = biased_expA + biased_expB - 127
//   (since sigA, sigB are 24-bit with leading 1, each at scale 2^(biasedExp-150))
//
// Addend:   sigC (24-bit) at scale 2^(addExp - 150)
//
// We align both to a common 100-bit fixed-point space with the product in
// bits [71:24] (after left-shifting prodMantissa by 24) and the addend at
// the appropriate offset.
//
// Common scale: accumulator bit `i` represents value * 2^(prodExp - 197 + i)
//   (same as prodMantissa << 24, placing its bit 0 at accumulator bit 24)
//
// Position of addend leading bit in accumulator:
//   prodExp - 197 + pos = addExp - 150  =>  pos = addExp - prodExp + 47
//   addSig (24-bit) should go at positions [pos+23 : pos]
//   We need pos in [-24, 75] for the 100-bit accumulator, otherwise clamp.

class FmaCmd extends Bundle {
  val ina = new Fp32; val inb = new Fp32; val inc = new Fp32
}

class FmaState1 extends Bundle {
  val prodSign     = Bool()
  val prodExp      = SInt(10.W)
  val prodMantissa = UInt(48.W)
  val isNaN        = Bool()
  val isInf        = Bool()
  val addSign      = Bool()
  val addExp       = UInt(8.W)
  val addMantissa  = UInt(23.W)
}

class FmaState2 extends Bundle {
  val prodSign     = Bool()
  val prodExp      = SInt(10.W)
  val prodAligned  = UInt(100.W)
  val addAligned   = UInt(100.W)
  val addSign      = Bool()
  val isNaN        = Bool()
  val isInf        = Bool()
}

object Fma {

  def FmaStage1(cmd: FmaCmd): FmaState1 = {
    val s1 = Wire(new FmaState1)
    val a = cmd.ina; val b = cmd.inb; val c = cmd.inc

    s1.prodSign := a.sign ^ b.sign
    val sigA = Cat(1.U(1.W), a.mantissa)   // 24-bit
    val sigB = Cat(1.U(1.W), b.mantissa)   // 24-bit
    s1.prodMantissa := sigA * sigB          // 48-bit
    s1.prodExp := Cat(0.U(1.W), a.exponent +& b.exponent).asSInt - 127.S

    val aZ = a.isZero(); val bZ = b.isZero()
    val aI = a.isInf();  val bI = b.isInf()
    val aN = a.isNan();  val bN = b.isNan();  val cN = c.isNan()
    s1.isNaN := aN || bN || cN || (aZ && bI) || (bZ && aI)
    s1.isInf := !s1.isNaN && (aI || bI)

    when(aZ || bZ) {
      s1.prodMantissa := 0.U; s1.prodExp := (-200).S
    }
    s1.addSign := c.sign; s1.addExp := c.exponent; s1.addMantissa := c.mantissa
    s1
  }

  def FmaStage2(s1: FmaState1): FmaState2 = {
    val s2 = Wire(new FmaState2)
    s2.isNaN := s1.isNaN; s2.isInf := s1.isInf
    s2.prodSign := s1.prodSign; s2.prodExp := s1.prodExp

    // Product in 100-bit accumulator: prodMantissa << 24 -> bits [71:24]
    s2.prodAligned := Cat(0.U(28.W), s1.prodMantissa, 0.U(24.W))  // 100-bit

    // Addend: pos = addExp - prodExp + 47
    val addSig   = Cat(1.U(1.W), s1.addMantissa)   // 24-bit
    val addSig100 = Cat(0.U(76.W), addSig)           // 100-bit, sig in bits [23:0]

    val addIsZero = (s1.addExp === 0.U) && (s1.addMantissa === 0.U)
    val pos       = Cat(0.U(1.W), s1.addExp).asSInt - s1.prodExp + 47.S  // position in accumulator

    val addAligned = Wire(UInt(100.W))
    when(addIsZero) {
      addAligned := 0.U
    }.elsewhen(pos >= 100.S) {
      // Addend way off to the left: clamp (product rounds to zero relative to addend)
      // We can't represent this faithfully in 100 bits, but we do our best:
      // Place addend at top of accumulator and zero out product
      s2.prodAligned := 0.U
      addAligned := addSig100 << 76.U  // put leading 1 at bit 99
    }.elsewhen(pos >= 0.S) {
      // Normal left-shift
      val shiftAmt = pos(6, 0)
      addAligned := (addSig100 << shiftAmt)(99, 0)
    }.elsewhen(pos > (-76).S) {
      // Right-shift (product larger)
      val shiftAmt = (-pos)(6, 0)
      addAligned := (addSig100 >> shiftAmt)(99, 0)
    }.otherwise {
      // Addend tiny relative to product: round to zero
      addAligned := 0.U
    }

    s2.addAligned := addAligned
    s2.addSign    := s1.addSign
    s2
  }

  def FmaStage3(s2: FmaState2): Fp32 = {
    val result = Wire(new Fp32)

    when(s2.isNaN) {
      result.sign := false.B; result.exponent := 0xFF.U; result.mantissa := (1 << 22).U
    }.elsewhen(s2.isInf) {
      result.sign := s2.prodSign; result.exponent := 0xFF.U; result.mantissa := 0.U
    }.otherwise {
      // Sign-magnitude addition of 100-bit aligned values
      val sameSign = (s2.prodSign === s2.addSign)
      val sumMag   = Wire(UInt(101.W))
      val sumSign  = Wire(Bool())

      when(sameSign) {
        sumMag  := (s2.prodAligned +& s2.addAligned)(100, 0)
        sumSign := s2.prodSign
      }.otherwise {
        val pBig = (s2.prodAligned >= s2.addAligned)
        when(pBig) {
          sumMag  := s2.prodAligned - s2.addAligned
          sumSign := s2.prodSign
        }.otherwise {
          sumMag  := s2.addAligned - s2.prodAligned
          sumSign := s2.addSign
        }
      }

      // Leading 1 position (0-indexed from LSB).
      // PriorityEncoder(Reverse(x)) for 101-bit x = 100 - MSB_position(x).
      // We compute msbPos = 100 - PriorityEncoder(Reverse(sumMag)) = true MSB position.
      val peReverse = PriorityEncoder(Reverse(sumMag))         // 100 - MSB_pos (7-bit)
      val msbPos    = Cat(0.U(1.W), 100.U - peReverse)        // true MSB position (0-indexed from LSB), 8-bit unsigned

      // biasedExp: accumulator bit i represents value * 2^(prodExp - 197 + i)
      // The leading bit at msbPos represents 2^(prodExp - 197 + msbPos)
      // For FP32: result = 1.xxx * 2^(biasedExp - 127)
      // biasedExp - 127 = prodExp - 197 + msbPos  =>  biasedExp = msbPos + prodExp - 70
      val rawExp = msbPos.asSInt + s2.prodExp - 70.S

      // Extract 23-bit mantissa by right-shifting so that the leading 1 lands at bit 23
      // shiftAmt = msbPos - 23 when msbPos >= 23, else 0 (left-shift not needed; sumMag is 0)
      val shiftAmt  = Mux(msbPos >= 23.U, (msbPos - 23.U)(6, 0), 0.U(7.W))
      val shifted   = (sumMag >> shiftAmt)(23, 0)
      val mantRaw   = shifted(22, 0)

      // Round-to-nearest-even
      // The round bit is at position (msbPos - 24) in sumMag when msbPos >= 24
      val rPos  = Mux(msbPos >= 24.U, (msbPos - 24.U)(6, 0), 0.U(7.W))
      val rBit  = Mux(msbPos >= 24.U, (sumMag >> rPos)(0), 0.U)
      val sMask = Mux(msbPos >= 24.U, (~((~0.U(101.W)) << rPos))(100, 0), 0.U(101.W))
      val sBit  = (sumMag & sMask).orR
      val doRnd = rBit.asBool && (sBit || mantRaw(0))
      val mRnd  = (mantRaw +& Mux(doRnd, 1.U, 0.U))(23, 0)
      val mOvf  = doRnd && mRnd(23)
      val fMant = Mux(mOvf, 0.U(23.W), mRnd(22, 0))
      val fExp  = Mux(mOvf, rawExp + 1.S, rawExp)

      when(sumMag === 0.U) {
        result.sign := false.B; result.exponent := 0.U; result.mantissa := 0.U
      }.elsewhen(fExp >= 255.S) {
        result.sign := sumSign; result.exponent := 0xFF.U; result.mantissa := 0.U
      }.elsewhen(fExp <= 0.S || msbPos < 23.U) {
        result.sign := sumSign; result.exponent := 0.U; result.mantissa := 0.U
      }.otherwise {
        result.sign := sumSign; result.exponent := fExp(7, 0).asUInt; result.mantissa := fMant
      }
    }
    result
  }
}
