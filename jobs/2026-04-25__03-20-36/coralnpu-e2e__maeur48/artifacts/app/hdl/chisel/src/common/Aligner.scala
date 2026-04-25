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

/** Data aligner: packs valid entries to the front of the output vector.
  *
  * Given an input vector of `n` Valid(UInt) entries, the aligner
  * left-compacts all valid entries so that the output vector has all valid
  * entries at the lowest indices and any unused slots at the top have
  * valid=false.
  *
  * This is a purely combinational module (no registers).  The `T` type
  * parameter controls the element data type; internally everything is
  * operated on as UInt.
  *
  * @param t  The data type of each element (used to determine bit-width).
  * @param n  Number of input/output lanes.
  */
class Aligner[T <: Data](t: T, n: Int) extends Module {
  val width = t.getWidth

  val io = IO(new Bundle {
    val in  = Input(Vec(n, new Bundle {
      val valid = Bool()
      val bits  = UInt(width.W)
    }))
    val out = Output(Vec(n, new Bundle {
      val valid = Bool()
      val bits  = UInt(width.W)
    }))
  })

  // -----------------------------------------------------------------------
  // Prefix-sum based compaction.
  //
  // For each output slot j we want the j-th valid input (0-indexed).
  // We use a Mux tree: out(j).bits = data of the (j+1)-th set bit in the
  // input valid vector.
  //
  // Implementation: for each output index j, we compute a MuxCase where
  // the conditions are "exactly j inputs before index i are valid" for each
  // source i.
  // -----------------------------------------------------------------------

  // Compute prefix counts: prefixCount(i) = number of valid entries in in[0..i-1]
  // (i.e. valid entries strictly before position i).
  // prefixCount is a Seq of UInt wires.
  val prefixCount = Wire(Vec(n, UInt(log2Ceil(n + 1).W)))
  prefixCount(0) := 0.U
  for (i <- 1 until n) {
    prefixCount(i) := prefixCount(i - 1) + io.in(i - 1).valid.asUInt
  }

  for (j <- 0 until n) {
    // Source i maps to output j when prefixCount(i) == j and in(i).valid.
    val candidates: Seq[(Bool, (Bool, UInt))] = (0 until n).map { i =>
      val isSource = io.in(i).valid && (prefixCount(i) === j.U)
      (isSource, (true.B, io.in(i).bits))
    }

    // There is at most one candidate; default is invalid.
    val anySource = candidates.map(_._1).foldLeft(false.B)(_ || _)
    val srcBits   = MuxCase(0.U, candidates.map { case (cond, (_, bits)) => (cond, bits) })

    io.out(j).valid := anySource
    io.out(j).bits  := srcBits
  }
}
