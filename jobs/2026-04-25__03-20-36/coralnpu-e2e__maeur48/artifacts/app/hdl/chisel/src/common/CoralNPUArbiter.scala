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

/** Fixed-priority arbiter.
  *
  * Selects the lowest-index asserted request.
  *
  * @param n  Number of request inputs.
  *
  * IO:
  *   in:     Vec[Bool]  – request signals (one per input)
  *   grant:  Vec[Bool]  – one-hot grant output
  *   chosen: UInt       – index of the granted input (valid only when any grant is asserted)
  */
class CoralNPUArbiter(n: Int) extends Module {
  val io = IO(new Bundle {
    val in     = Input(Vec(n, Bool()))
    val grant  = Output(Vec(n, Bool()))
    val chosen = Output(UInt(log2Ceil(n).W))
  })

  // Fixed priority: lowest index wins.
  val found   = Wire(Bool())
  val chosenW = Wire(UInt(log2Ceil(n).W))
  found   := false.B
  chosenW := 0.U

  for (i <- (n - 1) to 0 by -1) {
    when(io.in(i)) {
      found   := true.B
      chosenW := i.U
    }
  }

  io.chosen := chosenW
  for (i <- 0 until n) {
    io.grant(i) := found && (chosenW === i.U)
  }
}

/** Round-robin arbiter with Decoupled (ready/valid) IO.
  *
  * Grants to one requester per cycle in round-robin order.  The grant
  * rotates after each accepted transaction (out.fire).
  *
  * @param gen  The data type carried by each input/output channel.
  * @param n    Number of input channels.
  *
  * IO:
  *   in:     Vec[Decoupled(gen)]  – input channels (Flipped inside the Vec)
  *   out:    Decoupled(gen)       – single output channel
  *   chosen: UInt                 – index of the currently selected input
  */
class CoralNPURRArbiter[T <: Data](gen: T, n: Int) extends Module {
  val io = IO(new Bundle {
    val in     = Vec(n, Flipped(Decoupled(gen)))
    val out    = Decoupled(gen)
    val chosen = Output(UInt(log2Ceil(n).W))
  })

  // lastGrant register: tracks which input was last granted.
  // Round-robin starts from the input *after* the last grant.
  val lastGrant = RegInit(0.U(log2Ceil(n).W))

  // Build a priority-encoded arbiter that starts scanning from
  // (lastGrant + 1) mod n, wrapping around.
  val chosenWire = Wire(UInt(log2Ceil(n).W))
  val anyValid   = Wire(Bool())

  // Two-pass priority: look for a valid input starting from
  // (lastGrant + 1), wrapping around to lastGrant (inclusive of lastGrant
  // only if no one else is valid).
  //
  // Implement with a flat priority scan over the 2n-element conceptual
  // ring.  For each rotated position p, the actual index is
  // (lastGrant + 1 + p) % n.  We pick the lowest p that has valid.

  anyValid   := false.B
  chosenWire := 0.U

  // Scan in reverse so the lowest-rotation-distance wins.
  for (p <- (n - 1) to 0 by -1) {
    val idx = (lastGrant +& (p + 1).U) % n.U
    when(io.in(idx).valid) {
      anyValid   := true.B
      chosenWire := idx
    }
  }

  io.chosen   := chosenWire
  io.out.valid := anyValid
  io.out.bits  := io.in(chosenWire).bits

  // back-pressure: only the chosen input sees ready
  for (i <- 0 until n) {
    io.in(i).ready := anyValid && io.out.ready && (chosenWire === i.U)
  }

  // Advance lastGrant only when a transaction completes.
  when(io.out.fire) {
    lastGrant := chosenWire
  }
}
