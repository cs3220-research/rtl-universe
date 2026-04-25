// Copyright 2025 Google LLC
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

/** Aligner: compresses valid inputs to the left.
  *
  * Given n inputs, produces n outputs where all valid entries are packed to
  * the left (lower indices), preserving their relative order. Invalid entries
  * become zeros.
  *
  * The io uses UInt(gen.getWidth.W) for bits to support arbitrary Bundle types
  * (callers cast bits via asTypeOf).
  */
class Aligner[T <: Data](gen: T, n: Int) extends Module {
  val io = IO(new Bundle {
    val in  = Input(Vec(n, new Bundle {
      val valid = Bool()
      val bits  = UInt(gen.getWidth.W)
    }))
    val out = Output(Vec(n, new Bundle {
      val valid = Bool()
      val bits  = UInt(gen.getWidth.W)
    }))
  })

  // Build a list of (valid, bits) pairs compacted to the left.
  // Use a simple O(n^2) implementation with combinational muxes.

  // For each output position j, find the j-th valid input.
  // outIdx(j) = the index in `in` of the j-th valid element.

  // Default outputs to zero/invalid
  for (j <- 0 until n) {
    io.out(j).valid := false.B
    io.out(j).bits  := 0.U
  }

  // Count how many valid inputs precede each index
  // Then route each valid input to its compacted position
  for (i <- 0 until n) {
    // How many valid inputs are there among in[0..i-1]?
    val priorValidCount = if (i == 0) 0.U else PopCount(VecInit((0 until i).map(k => io.in(k).valid)))
    // The destination index for in[i] is priorValidCount
    when(io.in(i).valid) {
      // Use a demux: write to the appropriate output slot
      for (j <- 0 until n) {
        when(priorValidCount === j.U) {
          io.out(j).valid := true.B
          io.out(j).bits  := io.in(i).bits
        }
      }
    }
  }
}
