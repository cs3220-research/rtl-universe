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

/** Round-robin arbiter with N inputs and 1 output.
  *
  * Uses Chisel's ArbiterIO which provides:
  *   io.in:     Vec(n, Flipped(Decoupled(gen)))
  *   io.out:    Decoupled(gen)
  *   io.chosen: UInt(log2Ceil(n).W)
  *
  * Round-robin behavior:
  *   - `lastGrant` register tracks the last granted input (initial=0).
  *   - When multiple inputs are valid, selects the next valid input after lastGrant
  *     in round-robin order: (lastGrant+1) % n, (lastGrant+2) % n, ..., lastGrant.
  *   - On a successful handshake (out.valid && out.ready), updates lastGrant.
  *   - io.in(chosen).ready = io.out.ready when chosen is valid; others = 0.
  */
class CoralNPURRArbiter[T <: Data](gen: T, n: Int) extends Module {
  val io = IO(new ArbiterIO(gen, n))

  // lastGrant: index of the last granted input (initial = 0)
  val lastGrant = RegInit(0.U(log2Ceil(n).W))

  // Determine chosen input using round-robin priority.
  // Scan from (lastGrant+1) % n with priority going to the smallest offset.
  // We iterate k from (n-1) down to 0; k=0 has highest priority.
  // idx(k) = (lastGrant + 1 + k) % n
  // Because Chisel uses "last connect wins", k=0 being last in the loop
  // makes (lastGrant+1)%n the highest priority candidate.
  val idxWire = WireDefault(0.U(log2Ceil(n).W))

  for (k <- (n - 1) to 0 by -1) {
    val idx = (lastGrant + 1.U + k.U) % n.U
    when(io.in(idx).valid) {
      idxWire := idx
    }
  }

  val anyValid = io.in.map(_.valid).reduce(_ || _)
  val chosen   = idxWire

  // Drive outputs
  io.out.valid := anyValid
  io.out.bits  := io.in(chosen).bits
  io.chosen    := chosen

  // Only the chosen input gets ready propagated when it's valid
  for (i <- 0 until n) {
    io.in(i).ready := io.out.ready && anyValid && (chosen === i.U)
  }

  // Update lastGrant on a successful handshake
  when(io.out.valid && io.out.ready) {
    lastGrant := chosen
  }
}
