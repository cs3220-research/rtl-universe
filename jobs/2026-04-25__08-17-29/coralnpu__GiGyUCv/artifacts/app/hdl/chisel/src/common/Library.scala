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

/** ForceZero: if the Valid is not valid, forces bits to 0. */
object ForceZero {
  def apply(in: Valid[SInt]): Valid[SInt] = {
    val out = Wire(Valid(chiselTypeOf(in.bits)))
    out.valid := in.valid
    out.bits  := Mux(in.valid, in.bits, 0.S)
    out
  }
}

/** Zip32: interleaves elements of a and b at the granularity given by sz.
  *  sz=4 (word):  out = Cat(b[31:0],  a[31:0])
  *  sz=2 (half):  out = Cat(b[31:16], a[31:16], b[15:0], a[15:0])
  *  sz=1 (byte):  out = Cat(b[23:16], a[23:16], b[15:8], a[15:8], b[7:0], a[7:0]) ...
  *                     with remaining upper bytes zero-extended to 64 bits
  */
object Zip32 {
  def apply(sz: UInt, a: UInt, b: UInt): UInt = {
    require(a.getWidth == 32)
    require(b.getWidth == 32)

    val wordResult = Cat(b(31, 0), a(31, 0))

    val halfResult = Cat(
      b(31, 16), a(31, 16),
      b(15,  0), a(15,  0)
    )

    val byteResult = Cat(
      b(31, 24), a(31, 24),
      b(23, 16), a(23, 16),
      b(15,  8), a(15,  8),
      b( 7,  0), a( 7,  0)
    )

    MuxLookup(sz, wordResult)(Seq(
      4.U -> wordResult,
      2.U -> halfResult,
      1.U -> byteResult
    ))
  }
}

/** RotateVectorLeft: rotates the vector left by `shift` positions.
  *  output[i + shift] = input[i]  (with wrap-around)
  */
object RotateVectorLeft {
  def apply[T <: Data](in: Vec[Valid[T]], shift: UInt): Vec[Valid[T]] = {
    val n = in.length
    val out = Wire(Vec(n, Valid(chiselTypeOf(in(0).bits))))
    for (o <- 0 until n) {
      // out(o) = in[(o - shift + n) % n]
      // Build a Mux: for each possible shift t (0..n-1), if shift==t, pick in[(o-t+n)%n]
      val chosen = MuxCase(in(0), (0 until n).map { t =>
        (shift === t.U) -> in((o - t + n) % n)
      })
      out(o) := chosen
    }
    out
  }
}

/** RotateVectorRight: rotates the vector right by `shift` positions.
  *  output[i - shift] = input[i]  (with wrap-around)
  *  i.e. output[(i - shift + n) % n] = input[i]
  *  i.e. output[o] = input[(o + shift) % n]
  */
object RotateVectorRight {
  def apply[T <: Data](in: Vec[Valid[T]], shift: UInt): Vec[Valid[T]] = {
    val n = in.length
    val out = Wire(Vec(n, Valid(chiselTypeOf(in(0).bits))))
    for (o <- 0 until n) {
      // out(o) = in[(o + shift) % n]
      val chosen = MuxCase(in(0), (0 until n).map { t =>
        (shift === t.U) -> in((o + t) % n)
      })
      out(o) := chosen
    }
    out
  }
}

/** ShiftVectorLeft: shifts left by `shift`, filling vacated positions with zero.
  *  output[i + shift] = input[i]  (no wrap; positions that would wrap are zeroed)
  *
  *  From the test:
  *    targetIndex = o + t  (where t = shift, o = input index)
  *    if targetIndex wraps (>= 16), targetIndex -= 16  => targetIndex < o
  *    if targetIndex < o => output is zero
  *    else output[targetIndex] = input[o]
  *
  *  So output[o] = input[o - shift] if o >= shift, else 0.
  */
object ShiftVectorLeft {
  def apply[T <: Data](in: Vec[Valid[T]], shift: UInt): Vec[Valid[T]] = {
    val n = in.length
    val out = Wire(Vec(n, Valid(chiselTypeOf(in(0).bits))))
    val zero = 0.U.asTypeOf(Valid(chiselTypeOf(in(0).bits)))
    for (o <- 0 until n) {
      // out(o) = in[o - shift] if o >= shift, else 0
      val chosen = MuxCase(zero, (0 until n).map { t =>
        // if shift == t: out(o) = in[o-t] if o >= t, else 0
        (shift === t.U) -> (if (o >= t) in(o - t) else zero)
      })
      out(o) := chosen
    }
    out
  }
}

/** ShiftVectorRight: shifts right by `shift`, filling vacated positions with zero.
  *  output[i - shift] = input[i]  (no wrap; positions that would wrap are zeroed)
  *
  *  From the test:
  *    targetIndex = o - t  (where t = shift, o = input index)
  *    if targetIndex < 0, targetIndex += 16  => targetIndex > o
  *    if targetIndex > o => output is zero
  *    else output[targetIndex] = input[o]
  *
  *  So output[o] = input[o + shift] if (o + shift) < n, else 0.
  */
object ShiftVectorRight {
  def apply[T <: Data](in: Vec[Valid[T]], shift: UInt): Vec[Valid[T]] = {
    val n = in.length
    val out = Wire(Vec(n, Valid(chiselTypeOf(in(0).bits))))
    val zero = 0.U.asTypeOf(Valid(chiselTypeOf(in(0).bits)))
    for (o <- 0 until n) {
      // out(o) = in[o + shift] if (o + shift) < n, else 0
      val chosen = MuxCase(zero, (0 until n).map { t =>
        // if shift == t: out(o) = in[o+t] if o+t < n, else 0
        (shift === t.U) -> (if (o + t < n) in(o + t) else zero)
      })
      out(o) := chosen
    }
    out
  }
}
