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

/** Utility functions used across the project. */
object Library {
  /** Returns ceil(log2(n)). Equivalent to chisel3.util.log2Ceil. */
  def log2Up(n: Int): Int = chisel3.util.log2Ceil(n)

  /** Returns floor(log2(n)). Equivalent to chisel3.util.log2Floor. */
  def log2Down(n: Int): Int = chisel3.util.log2Floor(n)

  /** Returns true if n is a power of two. */
  def isPow2(n: Int): Boolean = n > 0 && (n & (n - 1)) == 0

  /** Returns a bitmask with the given number of bits set (all 1s). */
  def mask(bits: Int): UInt = ((BigInt(1) << bits) - 1).U(bits.W)

  /** Truncates data to the specified number of bits. */
  def truncate(data: UInt, bits: Int): UInt = data(bits - 1, 0)

  /** Sign-extends data from fromBits wide to toBits wide. */
  def signExtend(data: UInt, fromBits: Int, toBits: Int): UInt = {
    require(toBits >= fromBits, "toBits must be >= fromBits")
    val signBit = data(fromBits - 1)
    val extended = Wire(UInt(toBits.W))
    extended := Cat(Fill(toBits - fromBits, signBit), data(fromBits - 1, 0))
    extended
  }

  /** Concatenates four UInt values: Cat(a, b, c, d). */
  def Cat4(a: UInt, b: UInt, c: UInt, d: UInt): UInt = Cat(a, b, c, d)

  /** Returns 0 with the same width as data when valid is not asserted,
    * otherwise returns data unchanged.
    * NOTE: This version works on a Valid bundle - zero out the bits when invalid.
    */
  def ForceZero[T <: Data](v: Valid[T]): Valid[T] = {
    val out = Wire(chiselTypeOf(v))
    out.valid := v.valid
    when(v.valid) {
      out.bits := v.bits
    }.otherwise {
      out.bits := 0.U.asTypeOf(v.bits)
    }
    out
  }

  /** Interleave the elements of two 32-bit words according to a size parameter.
    * sz controls the element granularity:
    *   sz=4: word interleave (concatenate a and b, 32-bit units)
    *   sz=2: half-word interleave (16-bit units)
    *   sz=1: byte interleave (8-bit units)
    * Output is 64 bits: interleaved pairs of [b_elem, a_elem].
    */
  def Zip32(sz: UInt, a: UInt, b: UInt): UInt = {
    val words4      = Cat(b(31, 0), a(31, 0))
    val halves4     = Cat(b(31, 16), a(31, 16), b(15, 0), a(15, 0))
    val bytesResult = Cat(b(31, 24), a(31, 24), b(23, 16), a(23, 16),
                          b(15,  8), a(15,  8), b( 7,  0), a( 7,  0))
    MuxLookup(sz, words4)(Seq(
      4.U -> words4,
      2.U -> halves4,
      1.U -> bytesResult,
    ))
  }

  /** Rotate a Vec left by n positions (combinational, n is a hardware UInt).
    *
    * RotateLeft by n: output[j] = input[(j - n + len) % len], equivalently
    * output[(i + n) % len] = input[i].
    */
  def RotateVectorLeft[T <: Data](vec: Vec[T], n: UInt): Vec[T] = {
    val len    = vec.length
    val result = Wire(Vec(len, chiselTypeOf(vec(0))))
    for (j <- 0 until len) {
      // result[j] = vec[i] when (i + n) % len == j, i.e. n == (j - i + len) % len.
      val candidates = (0 until len).map { i =>
        ((((j - i + len) % len)).U -> vec(i))
      }
      result(j) := MuxLookup(n, vec(j))(candidates)
    }
    result
  }

  /** Rotate a Vec right by n positions (combinational, n is a hardware UInt). */
  def RotateVectorRight[T <: Data](vec: Vec[T], n: UInt): Vec[T] = {
    val len = vec.length
    val result = Wire(Vec(len, chiselTypeOf(vec(0))))
    for (j <- 0 until len) {
      val candidates = (0 until len).map { i =>
        // RotateRight by n: result[j] = vec[(j + n) % len]
        // i.e. when n == (i - j + len) % len, result[j] = vec[i]
        ((((i - j + len) % len)).U -> vec(i))
      }
      result(j) := MuxLookup(n, vec(j))(candidates)
    }
    result
  }

  /** Shift a Vec left by n positions (combinational, n is a hardware UInt).
    * Positions that shift out of bounds become zeroed-out default elements.
    * ShiftLeft: output[(i+n)] = input[i] for (i+n) < len; positions below the
    * shifted range are zeroed.
    */
  def ShiftVectorLeft[T <: Data](vec: Vec[T], n: UInt): Vec[T] = {
    val len = vec.length
    val zero = 0.U.asTypeOf(chiselTypeOf(vec(0)))
    val result = Wire(Vec(len, chiselTypeOf(vec(0))))
    for (j <- 0 until len) {
      // result[j] = vec[j - n] if j >= n else zero
      val candidates = (0 until len).map { i =>
        // j - n == i => n == j - i. Valid when j >= i.
        val shift = j - i
        if (shift >= 0) {
          Some((shift.U -> vec(i)))
        } else {
          None
        }
      }.flatten
      // Default to zero when n > j (meaning no valid source)
      result(j) := MuxLookup(n, zero)(candidates)
    }
    result
  }

  /** Shift a Vec right by n positions (combinational, n is a hardware UInt).
    * Positions that shift out of bounds become zeroed-out default elements.
    * ShiftRight: output[(i-n)] = input[i] for (i-n) >= 0; positions above the
    * shifted range are zeroed.
    */
  def ShiftVectorRight[T <: Data](vec: Vec[T], n: UInt): Vec[T] = {
    val len = vec.length
    val zero = 0.U.asTypeOf(chiselTypeOf(vec(0)))
    val result = Wire(Vec(len, chiselTypeOf(vec(0))))
    for (j <- 0 until len) {
      // result[j] = vec[j + n] if (j + n) < len else zero
      val candidates = (0 until len).map { i =>
        // j + n == i => n == i - j. Valid when i >= j.
        val shift = i - j
        if (shift >= 0) {
          Some((shift.U -> vec(i)))
        } else {
          None
        }
      }.flatten
      result(j) := MuxLookup(n, zero)(candidates)
    }
    result
  }
}

// Top-level convenience aliases so callers can use these without the Library. prefix

/** Returns 0 with the same Data type when valid is false; otherwise passes bits through. */
object ForceZero {
  def apply[T <: Data](v: Valid[T]): Valid[T] = Library.ForceZero(v)
}

/** Zip32: interleave elements of two 32-bit words. */
object Zip32 {
  def apply(sz: UInt, a: UInt, b: UInt): UInt = Library.Zip32(sz, a, b)
}

/** RotateVectorLeft: rotate Vec left by n. */
object RotateVectorLeft {
  def apply[T <: Data](vec: Vec[T], n: UInt): Vec[T] = Library.RotateVectorLeft(vec, n)
}

/** RotateVectorRight: rotate Vec right by n. */
object RotateVectorRight {
  def apply[T <: Data](vec: Vec[T], n: UInt): Vec[T] = Library.RotateVectorRight(vec, n)
}

/** ShiftVectorLeft: shift Vec left by n (no wrap, zero fill). */
object ShiftVectorLeft {
  def apply[T <: Data](vec: Vec[T], n: UInt): Vec[T] = Library.ShiftVectorLeft(vec, n)
}

/** ShiftVectorRight: shift Vec right by n (no wrap, zero fill). */
object ShiftVectorRight {
  def apply[T <: Data](vec: Vec[T], n: UInt): Vec[T] = Library.ShiftVectorRight(vec, n)
}
