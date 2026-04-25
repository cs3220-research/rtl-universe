// Copyright 2024 Google LLC
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

/** Instruction buffer holding a sliding window of instructions.
  *
  * Instructions are fed in n at a time via `feedIn` and consumed from the
  * front via individual ready signals on `out`.
  *
  * @param gen    The instruction data type.
  * @param n      Maximum number of instructions fed in or presented per cycle.
  * @param window Total buffer capacity.
  */
class InstructionBuffer[T <: Data](gen: T, n: Int, window: Int) extends Module {
  require(window >= n, "window must be >= n")

  private val cntBits = log2Ceil(n + 1)
  private val winBits = log2Ceil(window + 1)

  /** Feed-in interface bundle. */
  class FeedInBundle extends Bundle {
    /** Number of valid instructions being fed in this cycle (0..n). */
    val nValid = Input(UInt(cntBits.W))
    /** The instruction data.  Only the first nValid entries are meaningful. */
    val bits   = Input(Vec(n, gen))
    /** Number of slots accepted this cycle (back-pressure signal). */
    val nReady = Output(UInt(cntBits.W))
  }

  val io = IO(new Bundle {
    val feedIn   = new FeedInBundle
    /** The front n instructions; valid bit set when the slot holds data. */
    val out      = Vec(n, Decoupled(gen))
    /** Current fill level. */
    val nEnqueued = Output(UInt(winBits.W))
    /** Free space remaining. */
    val nSpace   = Output(UInt(winBits.W))
    /** Flush the buffer. */
    val flush    = Input(Bool())
  })

  // Delegate storage to CircularBufferMulti.
  val buf = Module(new CircularBufferMulti(gen, n, window))

  buf.io.flush    := io.flush
  io.nEnqueued    := buf.io.nEnqueued
  io.nSpace       := buf.io.nSpace

  // -----------------------------------------------------------------------
  // Dequeue side: count how many outputs have ready asserted.
  // We only allow in-order retirement (FIFO), so we count the prefix of
  // contiguous ready signals.
  // -----------------------------------------------------------------------
  val readyCount = Wire(UInt(cntBits.W))
  val prefix     = Wire(Vec(n, Bool()))

  // Build a prefix-AND: output i is considered consumed only if outputs
  // 0..i-1 have also been consumed.
  prefix(0) := io.out(0).ready
  for (i <- 1 until n) {
    prefix(i) := prefix(i - 1) && io.out(i).ready
  }

  // readyCount = number of contiguous true entries from the front
  // Use PopCount on the prefix vector clamped by nEnqueued.
  readyCount := PopCount(prefix)

  // Clamp to actual available entries.
  val deqN = Mux(readyCount > buf.io.nEnqueued, buf.io.nEnqueued, readyCount)

  buf.io.deqReady := deqN

  // -----------------------------------------------------------------------
  // Enqueue side: accept up to nSpace entries.
  // -----------------------------------------------------------------------
  val spaceAvail = Mux(buf.io.nSpace > n.U, n.U, buf.io.nSpace)
  val enqN       = Mux(io.feedIn.nValid > spaceAvail, spaceAvail, io.feedIn.nValid)

  buf.io.enqValid := enqN
  buf.io.enqData  := io.feedIn.bits
  io.feedIn.nReady := enqN

  // -----------------------------------------------------------------------
  // Output: expose front n entries.
  // -----------------------------------------------------------------------
  for (i <- 0 until n) {
    io.out(i).valid := (i.U < buf.io.nEnqueued)
    io.out(i).bits  := buf.io.dataOut(i)
  }
}
