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

/** InstructionBuffer: a windowed instruction FIFO.
  *
  * @param gen    Data type of each element.
  * @param n      Number of elements that can be fed in per cycle.
  * @param window Visible window size (how many outputs are shown).
  *
  * Interface:
  *   feedIn.nValid  : number of valid elements being provided this cycle
  *   feedIn.nReady  : number of new elements accepted per cycle (= n)
  *   feedIn.bits    : Vec(n) of input elements
  *   out            : Vec(window, Valid(gen)) - visible window of buffered data
  *   nEnqueued      : total number of elements currently buffered
  *   nSpace         : total space remaining (capacity - nEnqueued)
  *   nDequeued      : number to remove from the front each cycle
  *   flush          : clears the buffer
  *
  * The out(i).ready signals are used to dequeue: the number of ready=true
  * outputs from the beginning of out are dequeued.
  * (out(0).ready=1, out(1).ready=1 dequeues 2 from the front)
  */
class InstructionBuffer[T <: Data](gen: T, n: Int, window: Int) extends Module {
  // window is the total buffer capacity; n is the max elements per cycle
  val capacity = window

  val io = IO(new Bundle {
    val feedIn = new Bundle {
      val nValid = Input(UInt(log2Ceil(n + 1).W))
      val nReady = Output(UInt(log2Ceil(n + 1).W))
      val bits   = Input(Vec(n, gen))
    }
    val out       = Vec(n, new DecoupledIO(gen))
    val nEnqueued = Output(UInt(log2Ceil(capacity + 1).W))
    val nSpace    = Output(UInt(log2Ceil(capacity + 1).W))
    val flush     = Input(Bool())
  })

  val state = RegInit(FIFOState.init(capacity, gen))

  // Count how many consecutive out().ready signals are set from the front
  // Only contiguous ready signals from index 0 count for dequeue
  val nDeqVec = Wire(Vec(n + 1, Bool()))
  nDeqVec(0) := true.B
  for (i <- 0 until n) {
    nDeqVec(i + 1) := nDeqVec(i) && io.out(i).ready
  }
  // Count how many consecutive readys from front
  val nDeqCount = Wire(UInt(log2Ceil(n + 1).W))
  nDeqCount := 0.U
  for (i <- 0 until n) {
    when(nDeqVec(i + 1)) {
      nDeqCount := (i + 1).U
    }
  }

  // Also cap by nEnqueued
  val actualDeq = Mux(nDeqCount > state.count, state.count, nDeqCount)

  // Chain: dequeue then enqueue
  val deqState = state.dequeue(actualDeq)
  val enqVec   = Wire(Vec(n, gen))
  for (i <- 0 until n) enqVec(i) := io.feedIn.bits(i)
  val enqState = deqState.enqueue(enqVec, io.feedIn.nValid)

  state := Mux(io.flush, state.flush(), enqState)

  // Outputs
  io.nEnqueued      := state.count
  io.nSpace         := (capacity.U - state.count)
  io.feedIn.nReady  := n.U  // always accept n per cycle

  // Out window: show front n elements of buffered data
  val peeked = state.peek(n)
  for (i <- 0 until n) {
    io.out(i).valid := (i.U < state.count)
    io.out(i).bits  := peeked(i)
  }
}
