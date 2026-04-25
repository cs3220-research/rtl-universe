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

/** Aligner: packs valid inputs to the left (lower indices).
  *
  * Takes n Valid inputs; produces n Valid outputs where valid elements are
  * compacted to output positions 0, 1, 2, ... in input order.
  *
  * IO ports use UInt for bits to remain generic.
  */
class AlignerIO(width: Int, n: Int) extends Bundle {
  val in  = Input(Vec(n, new Bundle {
    val valid = Bool()
    val bits  = UInt(width.W)
  }))
  val out = Output(Vec(n, new Bundle {
    val valid = Bool()
    val bits  = UInt(width.W)
  }))
}

class Aligner[T <: Data](t: T, n: Int) extends Module {
  val width = t.getWidth.max(1)

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

  // For each output position o, we want the (o+1)-th valid input (0-indexed: the o-th valid input).
  // We compute prefix sums of validity to find which input maps to each output.
  for (o <- 0 until n) {
    // Count valid inputs seen so far; pick the input whose cumulative count == o+1.
    val selected = Wire(new Bundle {
      val valid = Bool()
      val bits  = UInt(width.W)
    })
    selected.valid := false.B
    selected.bits  := 0.U

    // Accumulate count of valids as we scan inputs 0..n-1
    // The o-th output gets the (o+1)-th valid input.
    // We do this by: for each input i, it goes to output = (number of valids in [0..i-1]).
    // Equivalently, output[o] = input[i] where i is the smallest with sum(valid[0..i]) == o+1.
    // Build a priority mux:
    var countSoFar = 0.U(log2Ceil(n + 1).W)
    val cases = for (i <- 0 until n) yield {
      val prevCount = countSoFar
      countSoFar = countSoFar + io.in(i).valid.asUInt
      // This input i maps to output o if: valid && prevCount == o
      val sel = io.in(i).valid && (prevCount === o.U)
      (sel, io.in(i))
    }

    // Use priority: first match wins
    val validAny = cases.map(_._1).reduce(_ || _)
    val bits = MuxCase(0.U, cases.map { case (cond, inp) => (cond, inp.bits) })
    selected.valid := validAny
    selected.bits  := bits

    io.out(o).valid := selected.valid
    io.out(o).bits  := selected.bits
  }
}
