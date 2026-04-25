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

/** Aligner: compacts valid inputs to the left.
  *
  * Given n inputs (valid/bits pairs), produces n outputs where the valid
  * inputs are packed to the beginning in their original order. Non-valid
  * inputs are ignored; output slots beyond the number of valid inputs have
  * valid=false.
  *
  * The internal data path uses UInt (bit-width = widthOf(t)). The test
  * wrapper converts T ↔ UInt externally.
  *
  * @param t   The element type (used only to determine bit width).
  * @param n   Number of input/output slots.
  */
class Aligner[T <: Data](t: T, n: Int) extends Module {
  val w = t.getWidth

  val io = IO(new Bundle {
    val in  = Input(Vec(n, Valid(UInt(w.W))))
    val out = Output(Vec(n, Valid(UInt(w.W))))
  })

  // For each output slot j, find which input goes there.
  // output(j) = the j-th valid input (0-indexed).
  //
  // count_before(i) = number of valid inputs at positions < i.
  // If valid(i) && count_before(i) == j, then output(j) = input(i).
  //
  // Implement combinatorially using Scala-time loop.

  // Compute prefix counts at each position (number of valid inputs before position i).
  // This is a Chisel carry-chain.
  val prefixCount = Wire(Vec(n + 1, UInt(log2Ceil(n + 1).W)))
  prefixCount(0) := 0.U
  for (i <- 0 until n) {
    prefixCount(i + 1) := prefixCount(i) + io.in(i).valid.asUInt
  }

  for (j <- 0 until n) {
    // Find the input whose prefix count equals j and is valid.
    val candidates = Seq.tabulate(n) { i =>
      // Input i contributes to output j if prefixCount(i) == j and valid(i).
      (i, io.in(i).valid && (prefixCount(i) === j.U))
    }
    // At most one candidate will be true. Use MuxCase.
    val matchBits = MuxCase(0.U(w.W), candidates.map { case (i, cond) =>
      (cond, io.in(i).bits)
    })
    val matchValid = candidates.foldLeft(false.B) { case (acc, (_, cond)) => acc || cond }

    io.out(j).valid := matchValid
    io.out(j).bits  := matchBits
  }
}
