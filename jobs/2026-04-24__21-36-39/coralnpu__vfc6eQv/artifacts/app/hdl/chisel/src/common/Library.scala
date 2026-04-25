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

/** ForceZero: if Valid.valid is false, sets bits to 0; otherwise passes through. */
object ForceZero {
  def apply[T <: Data](in: Valid[T]): Valid[T] = {
    val out = Wire(chiselTypeOf(in))
    out.valid := in.valid
    out.bits  := Mux(in.valid, in.bits, 0.U.asTypeOf(in.bits))
    out
  }
}

/** Zip32: interleaves elements of a and b based on sz (element size in bytes: 1, 2, 4).
  *
  * sz=4 (word):  out[63:32]=b[31:0],  out[31:0]=a[31:0]
  * sz=2 (half):  out = b[31:16]##a[31:16]##b[15:0]##a[15:0]
  * sz=1 (byte):  out = b[23:16]##a[23:16]##b[15:8]##a[15:8]##b[7:0]##a[7:0]  (upper 16 bits = 0)
  */
object Zip32 {
  def apply(sz: UInt, a: UInt, b: UInt): UInt = {
    val wordResult = Cat(b(31, 0), a(31, 0))
    val halfResult = Cat(b(31, 16), a(31, 16), b(15, 0), a(15, 0))
    val byteResult = Cat(0.U(16.W), b(23, 16), a(23, 16), b(15, 8), a(15, 8), b(7, 0), a(7, 0))
    MuxCase(wordResult, Seq(
      (sz === 4.U) -> wordResult,
      (sz === 2.U) -> halfResult,
      (sz === 1.U) -> byteResult,
    ))
  }
}

/** RotateVectorLeft: rotates vector left by shift positions.
  * out[(o + shift) % n] = in[o]
  * Equivalently: out[d] = in[(d - shift + n) % n]
  */
object RotateVectorLeft {
  def apply[T <: Data](in: Vec[Valid[T]], shift: UInt): Vec[Valid[T]] = {
    val n   = in.length
    val out = Wire(chiselTypeOf(in))
    for (d <- 0 until n) {
      // source index = (d - shift + n) % n
      val src = ((d.U +& n.U) - shift) % n.U
      out(d) := in(src)
    }
    out
  }
}

/** RotateVectorRight: rotates vector right by shift positions.
  * out[(o - shift + n) % n] = in[o]
  * Equivalently: out[d] = in[(d + shift) % n]
  */
object RotateVectorRight {
  def apply[T <: Data](in: Vec[Valid[T]], shift: UInt): Vec[Valid[T]] = {
    val n   = in.length
    val out = Wire(chiselTypeOf(in))
    for (d <- 0 until n) {
      // source index = (d + shift) % n
      val src = (d.U + shift) % n.U
      out(d) := in(src)
    }
    out
  }
}

/** ShiftVectorLeft: shifts vector left (fills right with zeros), non-wrapping.
  * in[o] goes to out[o + shift] if (o + shift) < n, otherwise zero.
  * Positions 0 .. shift-1 are zero.
  * Equivalently: out[d] = in[d - shift] if d >= shift, else 0.
  */
object ShiftVectorLeft {
  def apply[T <: Data](in: Vec[Valid[T]], shift: UInt): Vec[Valid[T]] = {
    val n    = in.length
    val zero = 0.U.asTypeOf(chiselTypeOf(in(0)))
    val out  = Wire(chiselTypeOf(in))
    for (d <- 0 until n) {
      // Source index = d - shift (valid only when d >= shift)
      val src = (d.U - shift)(log2Ceil(n) - 1, 0)
      out(d) := Mux(d.U >= shift, in(src), zero)
    }
    out
  }
}

/** ShiftVectorRight: shifts vector right (fills left with zeros), non-wrapping.
  * in[o] goes to out[o - shift] if (o - shift) >= 0, otherwise zero.
  * Positions n-shift .. n-1 are zero.
  * Equivalently: out[d] = in[d + shift] if d + shift < n, else 0.
  */
object ShiftVectorRight {
  def apply[T <: Data](in: Vec[Valid[T]], shift: UInt): Vec[Valid[T]] = {
    val n    = in.length
    val zero = 0.U.asTypeOf(chiselTypeOf(in(0)))
    val out  = Wire(chiselTypeOf(in))
    for (d <- 0 until n) {
      // Source index = d + shift (valid only when d + shift < n)
      // Use +& for carry-preserving addition to avoid overflow
      val rawSrc = d.U +& shift
      val src    = (rawSrc % n.U)(log2Ceil(n) - 1, 0)
      out(d) := Mux(rawSrc < n.U, in(src), zero)
    }
    out
  }
}
