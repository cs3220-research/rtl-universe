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

/** A circular buffer that supports enqueueing/dequeueing multiple elements per cycle.
  *
  * @param t        The element type.
  * @param n        Maximum number of elements to enqueue/dequeue per cycle.
  * @param capacity Total capacity of the buffer.
  */
class CircularBufferMulti[T <: Data](t: T, n: Int, capacity: Int) extends Module {
  val io = IO(new Bundle {
    val enqValid  = Input(UInt((log2Ceil(n + 1)).W))    // how many to enqueue this cycle
    val enqData   = Input(Vec(n, t))
    val deqReady  = Input(UInt((log2Ceil(n + 1)).W))    // how many to dequeue this cycle
    val nEnqueued = Output(UInt(log2Ceil(capacity + 1).W))
    val dataOut   = Output(Vec(n, t))
    val flush     = Input(Bool())
  })

  // Internal storage
  val buf   = RegInit(VecInit(Seq.fill(capacity)(0.U.asTypeOf(t))))
  val head  = RegInit(0.U(log2Ceil(capacity).W))    // write pointer (next empty slot)
  val count = RegInit(0.U(log2Ceil(capacity + 1).W))

  // Read pointer (tail): oldest element
  // tail = (head - count + capacity) % capacity
  val tail = Wire(UInt(log2Ceil(capacity).W))
  tail := (head +& (capacity.U - count)) % capacity.U

  // ----- Dequeue: consume deqCount items from front -----
  val deqCount = Mux(io.deqReady > count, count, io.deqReady)
  val newTail  = (tail + deqCount) % capacity.U  // new tail after dequeue (for next enqueue reference)

  // ----- Enqueue: add enqCount items at head -----
  // Space available = capacity - (count - deqCount)
  val countAfterDeq = count - deqCount
  val spaceAvail    = capacity.U - countAfterDeq
  val enqCount      = Mux(io.enqValid > spaceAvail, spaceAvail, io.enqValid)

  // Write incoming data into buffer at positions [head, head+1, ..., head+enqCount-1]
  for (i <- 0 until n) {
    val writeAddr = (head + i.U) % capacity.U
    when(i.U < enqCount) {
      buf(writeAddr) := io.enqData(i)
    }
  }

  val newHead  = (head + enqCount) % capacity.U
  val newCount = countAfterDeq + enqCount

  // Update registers
  when(io.flush) {
    head  := 0.U
    count := 0.U
  }.otherwise {
    head  := newHead
    count := newCount
  }

  // ---- Outputs ----
  // nEnqueued: current count (registered value, reflects state AFTER last clock edge)
  io.nEnqueued := count

  // dataOut: the first n elements starting at the current tail
  for (i <- 0 until n) {
    val readAddr = (tail + i.U) % capacity.U
    io.dataOut(i) := buf(readAddr)
  }
}
