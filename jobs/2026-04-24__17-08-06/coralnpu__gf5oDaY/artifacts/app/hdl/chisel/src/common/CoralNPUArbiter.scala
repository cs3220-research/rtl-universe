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

/** Round-robin arbiter with n inputs and 1 output.
  *
  * Keeps track of `lastGrant` register. When multiple inputs are valid,
  * it picks the one that was NOT the last granted (round-robin).
  *
  * @param t Data type.
  * @param n Number of input channels.
  */
class CoralNPURRArbiter[T <: Data](t: T, n: Int) extends Module {
  val io = IO(new Bundle {
    val in     = Vec(n, Flipped(Decoupled(t)))
    val out    = Decoupled(t)
    val chosen = Output(UInt(log2Ceil(n).W))
  })

  // Track the last granted input
  val lastGrant = RegInit(0.U(log2Ceil(n).W))

  // Count valid inputs
  val anyValid = io.in.map(_.valid).reduce(_ || _)

  // Determine which input to choose using round-robin:
  // Priority: the input AFTER lastGrant (circular) among valid inputs.
  // If only one is valid, pick that one regardless.

  // Build priority: starting from (lastGrant + 1) % n, rotating
  // For n=2: if lastGrant=0, priority order = [1, 0]; if lastGrant=1, priority order = [0, 1]
  val chosen = Wire(UInt(log2Ceil(n).W))
  chosen := 0.U

  // Default: pick first valid in round-robin order
  // We iterate in priority order starting from (lastGrant + 1) % n
  // Use a for loop from n-1 down to 0 to set chosen (last write wins = highest priority)
  for (offset <- (n - 1) to 0 by -1) {
    val idx = Wire(UInt(log2Ceil(n).W))
    idx := (lastGrant + offset.U + 1.U) % n.U
    when(io.in(idx).valid) {
      chosen := idx
    }
  }

  // When no inputs are valid, chosen doesn't matter but out.valid = 0
  io.chosen := chosen
  io.out.valid := anyValid
  io.out.bits  := io.in(chosen).bits

  // Only assert ready to the chosen input when out is also ready
  for (i <- 0 until n) {
    io.in(i).ready := io.out.ready && anyValid && (chosen === i.U)
  }

  // Update lastGrant when a transaction completes
  when(io.out.valid && io.out.ready) {
    lastGrant := chosen
  }
}
