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

/** Returns v but with data zeroed if !valid. */
object ForceZero {
  def apply[T <: Data](v: Valid[T]): Valid[T] = {
    val result = Wire(chiselTypeOf(v))
    result.valid := v.valid
    when(v.valid) {
      result.bits := v.bits
    }.otherwise {
      result.bits := 0.U.asTypeOf(v.bits)
    }
    result
  }
}

/** Interleave two 32-bit values at granularity sz.
  *  sz=4 (words):     out = b(31:0) ## a(31:0)
  *  sz=2 (halfwords): out = b(31:16) ## a(31:16) ## b(15:0) ## a(15:0)
  *  sz=1 (bytes):     out = b(23:16) ## a(23:16) ## b(15:8) ## a(15:8) ## b(7:0) ## a(7:0)
  * From the test:
  *   sz=1: out = (b[byte2]<<40) | (a[byte2]<<32) | (b[byte1]<<24) | (a[byte1]<<16) | (b[byte0]<<8) | a[byte0]
  */
object Zip32 {
  def apply(sz: UInt, a: UInt, b: UInt): UInt = {
    val words   = Cat(b(31, 0), a(31, 0))
    val halves  = Cat(b(31, 16), a(31, 16), b(15, 0), a(15, 0))
    val bytes   = Cat(b(23, 16), a(23, 16), b(15, 8), a(15, 8), b(7, 0), a(7, 0))
    // Pad bytes result to 64 bits (it's 48 bits wide)
    val bytes64 = Cat(0.U(16.W), bytes)
    MuxLookup(sz, words)(Seq(
      4.U -> words,
      2.U -> halves,
      1.U -> bytes64,
    ))
  }
}

/** Rotate vector left: output[(i + shift) % n] = input[i]
  *
  * Implemented by computing each output slot as a Mux over all possible sources.
  * output[j] = input[(j - shift + n) % n]
  */
object RotateVectorLeft {
  def apply[T <: Data](v: Vec[T], shift: UInt): Vec[T] = {
    val n = v.length
    // For output[j]: find input[o] such that (o + shift) % n == j
    // i.e., o = (j - shift + n) % n
    // Use MuxLookup-style: for each output j, select the right input.
    VecInit(Seq.tabulate(n) { j =>
      // output[j] = input[(j - shift + n) % n]
      // Use a Mux chain over all possible shift values
      val candidates = Seq.tabulate(n) { o =>
        // If shift == (j - o + n) % n, then output[j] = input[o]
        (((j - o + n) % n).U -> v(o))
      }
      MuxLookup(shift, v(0))(candidates)
    })
  }
}

/** Rotate vector right: output[(i - shift + n) % n] = input[i]
  *
  * output[j] = input[(j + shift) % n]
  */
object RotateVectorRight {
  def apply[T <: Data](v: Vec[T], shift: UInt): Vec[T] = {
    val n = v.length
    VecInit(Seq.tabulate(n) { j =>
      // output[j] = input[(j + shift) % n]
      val candidates = Seq.tabulate(n) { s =>
        (s.U -> v((j + s) % n))
      }
      MuxLookup(shift, v(0))(candidates)
    })
  }
}

/** Shift vector left: output[(i + shift) % n] = input[i] if no wrap, else zero.
  * "No wrap" means targetIndex >= i.
  *
  * output[j] = input[o] where o = (j - shift + n) % n, if j >= o (no wrap), else 0
  */
object ShiftVectorLeft {
  def apply[T <: Data](v: Vec[T], shift: UInt): Vec[T] = {
    val n = v.length
    val zero = 0.U.asTypeOf(v(0))
    VecInit(Seq.tabulate(n) { j =>
      // For output[j]: source is o = (j - shift + n) % n
      // Valid if j >= o (no wrap-around occurred)
      val candidates = Seq.tabulate(n) { s =>
        // If shift == s, then o = (j - s + n) % n
        val src = (j - s + n) % n
        val valid = j >= src  // no wrap: j >= src
        (s.U -> Mux(valid.B, v(src), zero))
      }
      MuxLookup(shift, zero)(candidates)
    })
  }
}

/** Shift vector right: output[(i - shift + n) % n] = input[i] if no wrap, else zero.
  * "No wrap" means targetIndex <= i.
  *
  * output[j] = input[o] where o = (j + shift) % n, if j <= o (no wrap), else 0
  */
object ShiftVectorRight {
  def apply[T <: Data](v: Vec[T], shift: UInt): Vec[T] = {
    val n = v.length
    val zero = 0.U.asTypeOf(v(0))
    VecInit(Seq.tabulate(n) { j =>
      // For output[j]: source is o = (j + shift) % n
      // Valid if j <= o (no wrap-around occurred)
      val candidates = Seq.tabulate(n) { s =>
        // If shift == s, then o = (j + s) % n
        val src = (j + s) % n
        val valid = j <= src  // no wrap: j <= src
        (s.U -> Mux(valid.B, v(src), zero))
      }
      MuxLookup(shift, zero)(candidates)
    })
  }
}
