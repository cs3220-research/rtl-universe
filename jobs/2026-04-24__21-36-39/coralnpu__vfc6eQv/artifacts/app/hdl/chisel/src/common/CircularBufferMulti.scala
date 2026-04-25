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

/** CircularBufferMulti: a multi-element FIFO buffer.
  *
  * @param gen       Data type of each element.
  * @param n         Maximum number of elements that can be enqueued/dequeued per cycle.
  * @param capacity  Total buffer capacity.
  *
  * Interface:
  *   nEnqueued   : current number of elements in the buffer
  *   flush       : when asserted, clears the buffer
  *   enqValid    : number of elements to enqueue this cycle (0..n)
  *   enqData     : the elements to enqueue (first enqValid are used)
  *   deqReady    : number of elements to dequeue this cycle (0..n)
  *   dataOut     : all buffered data, oldest at index 0
  */
class CircularBufferMulti[T <: Data](gen: T, n: Int, capacity: Int) extends Module {
  val io = IO(new Bundle {
    val nEnqueued = Output(UInt(log2Ceil(capacity + 1).W))
    val flush     = Input(Bool())
    val enqValid  = Input(UInt(log2Ceil(n + 1).W))
    val enqData   = Input(Vec(n, gen))
    val deqReady  = Input(UInt(log2Ceil(n + 1).W))
    val dataOut   = Output(Vec(capacity, gen))
  })

  val state = RegInit(FIFOState.init(capacity, gen))

  // Chain: dequeue then enqueue (handles simultaneous ops)
  val deqState = state.dequeue(io.deqReady)
  val enqVec   = Wire(Vec(n, gen))
  for (i <- 0 until n) enqVec(i) := io.enqData(i)
  val enqState = deqState.enqueue(enqVec, io.enqValid)

  state := Mux(io.flush, state.flush(), enqState)

  io.nEnqueued := state.count

  // Output all data in order (oldest first)
  for (i <- 0 until capacity) {
    val idx = (state.head + i.U) % capacity.U
    io.dataOut(i) := state.buffer(idx)
  }
}
