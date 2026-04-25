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

/** Gather operation: out(i) = data(indices(i)).
  *
  * @param indices Vec of indices (each < n).
  * @param data    Vec of data elements.
  * @return        Vec where each element is data looked up by the corresponding index.
  */
object Gather {
  def apply[T <: Data](indices: Vec[UInt], data: Vec[T]): Vec[T] = {
    val n = data.length
    val out = Wire(Vec(n, chiselTypeOf(data(0))))
    for (i <- 0 until n) {
      // MuxLookup over all possible index values
      val candidates = Seq.tabulate(n) { j => (j.U -> data(j)) }
      out(i) := MuxLookup(indices(i), data(0))(candidates)
    }
    out
  }
}

/** Scatter operation: for each valid input, writes data(i) to result(indices(i))
  * if that output slot hasn't been written yet (first-come first-served, lowest
  * input index wins conflicts).
  *
  * @param indicesValid  Vec of valid flags.
  * @param indices       Vec of target indices.
  * @param data          Vec of data to scatter.
  * @return              (result, writeMask, indicesSelected)
  *   - result(j): data written to output slot j (meaningful only if writeMask(j)).
  *   - writeMask(j): true if slot j was written.
  *   - indicesSelected(i): true if input i was selected (valid and index not yet used).
  */
object Scatter {
  def apply[T <: Data](
      indicesValid: Vec[Bool],
      indices:      Vec[UInt],
      data:         Vec[T]
  ): (Vec[T], Vec[Bool], Vec[Bool]) = {
    val n = indices.length
    val result         = Wire(Vec(n, chiselTypeOf(data(0))))
    val writeMask      = Wire(Vec(n, Bool()))
    val indicesSelected = Wire(Vec(n, Bool()))

    // Initialize outputs to zero / false
    for (j <- 0 until n) {
      result(j)    := 0.U.asTypeOf(data(0))
      writeMask(j) := false.B
    }
    for (i <- 0 until n) {
      indicesSelected(i) := false.B
    }

    // Process inputs in order; lower index wins conflicts.
    // We build a "claimed" mask combinatorially.
    // claimed(j) is true if any earlier input i' < i has indices(i') == j and indicesValid(i').
    // We compute this per output slot using a priority tree.

    // For each input i (in order), select it if:
    //   1. indicesValid(i) is true
    //   2. No earlier selected input j < i has indices(j) == indices(i)
    //
    // We compute per-input whether it's selected using a chain.
    // claimed(j) = OR over all i' < i where indicesSelected(i') && indices(i') == j

    // Use a sequential scan with Scala-time unrolling.
    // claimedSoFar(j) after processing i inputs = OR(i' < i, indicesSelected(i') && indices(i') == j)

    val claimedSoFar = Array.fill(n)(false.B)

    for (i <- 0 until n) {
      // Is this slot's target already claimed?
      val candidates = Seq.tabulate(n) { j => (j.U -> claimedSoFar(j)) }
      val targetClaimed = MuxLookup(indices(i), false.B)(candidates)

      val selected = indicesValid(i) && !targetClaimed
      indicesSelected(i) := selected

      // Update result and writeMask for this input's target
      for (j <- 0 until n) {
        val isTarget = indices(i) === j.U
        when(selected && isTarget) {
          result(j)    := data(i)
          writeMask(j) := true.B
        }
      }

      // Update claimedSoFar for next iteration
      for (j <- 0 until n) {
        claimedSoFar(j) = claimedSoFar(j) || (selected && (indices(i) === j.U))
      }
    }

    (result, writeMask, indicesSelected)
  }
}
