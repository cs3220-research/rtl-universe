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

/** Gather: out(i) = data(indices(i))
  *
  * For each output position i, reads from the data vector at the index
  * specified by indices(i).
  */
object Gather {
  def apply[T <: Data](indices: Vec[UInt], data: Vec[T]): Vec[T] = {
    val n   = indices.length
    val out = Wire(Vec(n, chiselTypeOf(data(0))))
    for (i <- 0 until n) {
      out(i) := data(indices(i))
    }
    out
  }
}

/** Scatter: distributes data to target positions, resolving conflicts.
  *
  * For each source i with indicesValid(i)=true, attempts to write data(i) to
  * outData(indices(i)). If multiple sources target the same destination, only
  * the first (lowest index) is selected.
  *
  * Returns:
  *   (outData, writeMask, indicesSelected)
  *   - outData:         The scattered data (undefined at positions not written).
  *   - writeMask:       Vec[Bool] indicating which output positions were written.
  *   - indicesSelected: Vec[Bool] indicating which source indices were selected.
  */
object Scatter {
  def apply[T <: Data](
      indicesValid: Vec[Bool],
      indices:      Vec[UInt],
      data:         Vec[T]
  ): (Vec[T], Vec[Bool], Vec[Bool]) = {
    val n       = indices.length
    val outData = Wire(Vec(n, chiselTypeOf(data(0))))
    val writeMask       = Wire(Vec(n, Bool()))
    val indicesSelected = Wire(Vec(n, Bool()))

    // Initialize defaults
    for (i <- 0 until n) {
      outData(i)          := 0.U.asTypeOf(chiselTypeOf(data(0)))
      writeMask(i)        := false.B
      indicesSelected(i)  := false.B
    }

    // Track which output destinations have already been claimed
    // Process sources in order (lower index = higher priority)
    // Use a "claimed" vector built up combinationally
    val claimed = Wire(Vec(n, Bool()))
    for (d <- 0 until n) {
      // A destination is claimed if any prior source (lower index) with valid
      // wrote to it.
      claimed(d) := VecInit(
        (0 until n).map { i =>
          // source i claims destination d if:
          //   - it is valid
          //   - its index points to d
          //   - no source j < i also claims d
          // We'll compute this below using indicesSelected
          false.B
        }
      ).reduce(_ || _)
    }
    // The above won't synthesize cleanly as a circular reference.
    // Use a sequential priority approach instead.

    // For each source i, determine if it is selected:
    // selected(i) = valid(i) && forall j < i: !(valid(j) && indices(j) == indices(i))
    for (i <- 0 until n) {
      val conflictWithPrior = if (i == 0) {
        false.B
      } else {
        VecInit(
          (0 until i).map { j =>
            indicesValid(j) && (indices(j) === indices(i))
          }
        ).asUInt.orR
      }
      indicesSelected(i) := indicesValid(i) && !conflictWithPrior
    }

    // Build write mask and output data from selected sources
    for (i <- 0 until n) {
      when(indicesSelected(i)) {
        writeMask(indices(i))  := true.B
        outData(indices(i))    := data(i)
      }
    }

    (outData, writeMask, indicesSelected)
  }
}
