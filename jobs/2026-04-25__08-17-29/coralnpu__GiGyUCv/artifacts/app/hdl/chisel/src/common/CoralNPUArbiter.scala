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

/** Round-robin arbiter with n inputs and one output.
  *
  * When both inputs are valid, gives priority to the one NOT last granted.
  * The lastGrant register updates on each clock edge when a transaction fires.
  *
  * @param gen  The data type.
  * @param n    Number of inputs.
  */
class CoralNPURRArbiter[T <: Data](gen: T, n: Int) extends Module {
  val io = IO(new Bundle {
    val in     = Vec(n, Flipped(Decoupled(gen)))
    val out    = Decoupled(gen)
    val chosen = Output(UInt(log2Ceil(n).W))
  })

  // Tracks the last granted input index.
  // Initialise to 0 so that with both inputs valid initially, input 1 wins first (round-robin).
  val lastGrant = RegInit(0.U(log2Ceil(n).W))

  // Round-robin arbitration:
  // Among valid inputs, pick the one that is NOT lastGrant; if none, pick lastGrant.
  // For n=2 this gives strict alternation.
  //
  // General policy: scan from (lastGrant+1) mod n, wrapping around, and take the
  // first valid input found.

  // Build a Vec of valid flags for easy indexing.
  val valids = VecInit(io.in.map(_.valid))

  // Candidate: starting from lastGrant+1, find the first valid input.
  // We use a two-round scan (indices lastGrant+1 .. lastGrant+n) to implement wrap-around.
  val chosen = WireDefault((n - 1).U(log2Ceil(n).W))
  val anyValid = valids.asUInt =/= 0.U

  // Priority scan: try indices in order (lastGrant+1) mod n, (lastGrant+2) mod n, ...
  // Build priority chain from highest-priority (next after lastGrant) to lowest.
  when (anyValid) {
    chosen := lastGrant  // fallback: keep lastGrant if it's the only valid
    // Scan in reverse priority so that the highest priority (lastGrant+1) wins last (highest priority Mux)
    for (offset <- (n - 1) to 1 by -1) {
      val idx = ((lastGrant +& offset.U) % n.U)(log2Ceil(n) - 1, 0)
      when (valids(idx)) {
        chosen := idx
      }
    }
    // The input just after lastGrant has highest priority; override with it if valid
    val nextIdx = ((lastGrant +& 1.U) % n.U)(log2Ceil(n) - 1, 0)
    when (valids(nextIdx)) {
      chosen := nextIdx
    }
  }

  io.chosen := chosen

  // Output: mux to chosen input
  io.out.valid := anyValid
  io.out.bits  := io.in(chosen).bits

  // Ready signals: only the chosen input gets ready when out is ready
  for (i <- 0 until n) {
    io.in(i).ready := io.out.ready && anyValid && (chosen === i.U)
  }

  // Update lastGrant on a successful handshake
  when (io.out.valid && io.out.ready) {
    lastGrant := chosen
  }
}
