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

/** Circular buffer supporting multiple enqueue/dequeue operations per cycle.
  *
  * @param gen      Element type.
  * @param n        Maximum number of items enqueued/dequeued per cycle.
  * @param capacity Total buffer capacity (number of elements).
  */
class CircularBufferMulti[T <: Data](gen: T, n: Int, capacity: Int) extends Module {
  val addrW = log2Ceil(capacity)

  val io = IO(new Bundle {
    /** Number of items to enqueue this cycle (0..n). */
    val enqValid  = Input(UInt(log2Ceil(n + 1).W))
    /** Data to enqueue; first enqValid elements are used. */
    val enqData   = Input(Vec(n, gen))
    /** Number of items to dequeue this cycle (0..n). */
    val deqReady  = Input(UInt(log2Ceil(n + 1).W))
    /** Flush all contents. */
    val flush     = Input(Bool())
    /** Current count of items in the buffer. */
    val nEnqueued = Output(UInt(log2Ceil(capacity + 1).W))
    /** First n items from the head of the buffer. */
    val dataOut   = Output(Vec(n, gen))
  })

  // Backing storage
  val mem   = Reg(Vec(capacity, gen))
  // head: read pointer (index of oldest item)
  // tail: write pointer (next free slot)
  // count: number of items stored
  val head  = RegInit(0.U(log2Ceil(capacity + 1).W))
  val tail  = RegInit(0.U(log2Ceil(capacity + 1).W))
  val count = RegInit(0.U(log2Ceil(capacity + 1).W))

  // Dequeue: remove deqReady items from the head
  val deqN   = Mux(io.deqReady > count, count, io.deqReady)
  val newHead = (head + deqN) % capacity.U

  // Enqueue: add enqValid items at the tail
  val spaceAfterDeq = capacity.U - (count - deqN)
  val enqN   = Mux(io.enqValid > spaceAfterDeq, spaceAfterDeq, io.enqValid)

  // Write new items
  for (i <- 0 until n) {
    when (io.enqValid > i.U) {
      val writeAddr = (tail + i.U) % capacity.U
      mem(writeAddr) := io.enqData(i)
    }
  }

  val newTail  = (tail + enqN) % capacity.U
  val newCount = count - deqN + enqN

  when (io.flush) {
    head  := 0.U
    tail  := 0.U
    count := 0.U
  } .otherwise {
    head  := newHead
    tail  := newTail
    count := newCount
  }

  io.nEnqueued := count

  // Output first n items from the head
  for (i <- 0 until n) {
    val readAddr = (head + i.U) % capacity.U
    io.dataOut(i) := mem(readAddr)
  }
}
