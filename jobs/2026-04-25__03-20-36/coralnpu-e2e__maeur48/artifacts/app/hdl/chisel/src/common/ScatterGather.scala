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

/** Gather operation: `out(i) = data(indices(i))`. */
object Gather {
  def apply(indices: Vec[UInt], data: Vec[UInt]): Vec[UInt] = {
    val n   = indices.length
    val out = Wire(Vec(n, chiselTypeOf(data(0))))
    for (i <- 0 until n) {
      out(i) := data(indices(i))
    }
    out
  }
}

/** Scatter operation.
  *
  * For each destination slot `d`, finds the lowest-index source `s` such that
  * `indicesValid(s)` and `indices(s) == d`.  That source "wins" the slot:
  *   - `writeMask(d)` is set
  *   - `result(d)` is set to `data(s)`
  *   - `indicesSelected(s)` is set
  *
  * Sources that are invalid or lose a conflict are not selected.
  *
  * @return (result, writeMask, indicesSelected)
  */
object Scatter {
  def apply(
    indicesValid: Vec[Bool],
    indices:      Vec[UInt],
    data:         Vec[UInt]
  ): (Vec[UInt], Vec[Bool], Vec[Bool]) = {
    val nSrc  = indices.length
    val nDest = nSrc

    // -----------------------------------------------------------------------
    // Step 1: for each source s, compute whether it is the winner for its
    // destination.  Source s wins iff it is valid and no lower-index source
    // is valid with the same destination index.
    // -----------------------------------------------------------------------
    val isWinner = Wire(Vec(nSrc, Bool()))
    for (s <- 0 until nSrc) {
      // Compute OR of "valid source with same dest, lower index than s"
      val lowerConflict: Seq[Bool] = (0 until s).map { sp =>
        indicesValid(sp) && (indices(sp) === indices(s))
      }
      val anyLower = lowerConflict.foldLeft(false.B)(_ || _)
      isWinner(s) := indicesValid(s) && !anyLower
    }

    // indicesSelected(s) = true iff s is the winner
    val indicesSelected = isWinner

    // -----------------------------------------------------------------------
    // Step 2: for each destination d, check if any winner targets it.
    // -----------------------------------------------------------------------
    val writeMask = Wire(Vec(nDest, Bool()))
    val result    = Wire(Vec(nDest, chiselTypeOf(data(0))))

    for (d <- 0 until nDest) {
      // Collect all winner sources that target destination d.
      // At most one such source exists (by construction of isWinner).
      val winnerData: Seq[(Bool, UInt)] = (0 until nSrc).map { s =>
        (isWinner(s) && (indices(s) === d.U), data(s))
      }
      val anyWinner = winnerData.map(_._1).foldLeft(false.B)(_ || _)
      writeMask(d) := anyWinner

      // MuxCase: pick the winner's data (only one can be true).
      result(d) := MuxCase(0.U, winnerData)
    }

    (result, writeMask, indicesSelected)
  }
}
