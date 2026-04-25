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

/** Gather: for each output i, return data(indices(i)).
  *
  * @param indices Vec of 16 indices (4-bit each, 0..15)
  * @param data    Vec of 16 data elements
  * @return        Vec of 16 data elements, gathered by indices
  */
object Gather {
  def apply[T <: Data](indices: Vec[UInt], data: Vec[T]): Vec[T] = {
    val n = data.length
    val out = Wire(Vec(n, chiselTypeOf(data(0))))
    for (i <- 0 until n) {
      out(i) := data(indices(i))
    }
    out
  }
}

/** Scatter: scatter data to positions given by indices.
  *
  * For each valid index (indicesValid(i) = true), the first valid occurrence for
  * each output position is used (earlier inputs have priority).
  *
  * @param indicesValid Vec of 16 Bool indicating which inputs are active
  * @param indices      Vec of 16 UInt(4.W) destination indices
  * @param data         Vec of 16 data elements
  * @return (result, writeMask, indicesSelected) where:
  *         result          = Vec of 16 data elements at their scattered positions
  *         writeMask       = Vec of 16 Bool: which output positions were written
  *         indicesSelected = Vec of 16 Bool: which inputs were selected (contributed to output)
  */
object Scatter {
  def apply[T <: Data](
    indicesValid: Vec[Bool],
    indices:      Vec[UInt],
    data:         Vec[T]
  ): (Vec[T], Vec[Bool], Vec[Bool]) = {
    val n = data.length

    val result         = Wire(Vec(n, chiselTypeOf(data(0))))
    val writeMask      = Wire(Vec(n, Bool()))
    val indicesSelected = Wire(Vec(n, Bool()))

    // Track which output positions have been claimed
    // For each input i (in order 0..n-1): if valid and output position not yet claimed,
    // write data(i) to result(indices(i)) and mark position as claimed.

    // Build per-output: was any earlier valid input pointing to this position?
    // We do this combinationally via a priority chain.

    // For each output position j: find the lowest-priority i such that:
    //   indicesValid(i) && indices(i) == j
    //   AND no earlier i' < i with indicesValid(i') && indices(i') == j

    // claimedByEarlier(i) = true if some i' < i has indicesValid(i') && indices(i') == indices(i)
    // We compute this iteratively.

    // claimedPositions(j) = has position j been claimed by any input i so far (scanning 0..n-1)?
    // We build this as a sequence of Bool Wires.

    // For each output position j, has it been written?
    val positionClaimed = Wire(Vec(n, Bool()))
    for (j <- 0 until n) {
      positionClaimed(j) := false.B
    }

    // We process inputs in order 0..n-1.
    // For each input i, it is selected if valid AND its target position is not yet claimed.
    // After processing input i, mark its target position as claimed if selected.

    // Since we need combinational logic, we use a vector of accumulated claim flags.
    // claimedAfter(i)(j) = true if position j is claimed by any input 0..i that was selected.

    // Initialize: nothing claimed before input 0
    var claimed = Seq.fill(n)(false.B)

    val selected = Wire(Vec(n, Bool()))
    val selectedDest = Wire(Vec(n, UInt(indices(0).getWidth.W)))

    for (i <- 0 until n) {
      val dest = indices(i)
      val destClaimed = MuxCase(false.B, (0 until n).map { j =>
        (dest === j.U) -> claimed(j)
      })
      val sel = indicesValid(i) && !destClaimed
      selected(i) := sel
      selectedDest(i) := dest

      // Update claimed: after this input, if sel, then position `dest` is claimed
      claimed = (0 until n).map { j =>
        claimed(j) || (sel && (dest === j.U))
      }
    }

    // Build result, writeMask
    for (j <- 0 until n) {
      // Find which input (if any) was selected for output position j
      val hit = (0 until n).map { i =>
        selected(i) && (selectedDest(i) === j.U)
      }
      val anyHit = hit.reduce(_ || _)
      val hitData = MuxCase(data(0), hit.zipWithIndex.map { case (h, i) => (h, data(i)) })
      result(j)    := hitData
      writeMask(j) := anyHit
    }

    indicesSelected := selected

    (result, writeMask, indicesSelected)
  }
}
