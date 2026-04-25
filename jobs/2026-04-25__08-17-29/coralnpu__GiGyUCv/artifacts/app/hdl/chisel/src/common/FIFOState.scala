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

/** Functional FIFO state as a hardware Bundle.
  *
  * Holds a circular buffer of `capacity` elements of type `T`.
  * All operations return new state (functional style).
  *
  * The wrapper module uses:
  *   val state = RegInit(FIFOState.init(capacity, t))
  *   val deqState = state.dequeue(io.deqReady)
  *   val enqState = deqState.enqueue(io.enqData, io.enqValid)
  *   state := Mux(io.flush, state.flush(), enqState)
  */
class FIFOState[T <: Data](gen: T, capacity: Int) extends Bundle {
  val data  = Vec(capacity, gen)
  val head  = UInt(log2Ceil(capacity + 1).W)   // read pointer
  val tail  = UInt(log2Ceil(capacity + 1).W)   // write pointer
  val count = UInt(log2Ceil(capacity + 1).W)   // number of items stored

  /** Remove n items from the front (head). Returns new state. */
  def dequeue(n: UInt): FIFOState[T] = {
    val next = Wire(new FIFOState(gen, capacity))
    val deqN = Mux(n > count, count, n)
    next.data  := data
    next.head  := (head + deqN) % capacity.U
    next.tail  := tail
    next.count := count - deqN
    next
  }

  /** Add nValid items from `newData` at the tail. Returns new state. */
  def enqueue(newData: Vec[_ <: T], nValid: UInt): FIFOState[T] = {
    val n      = newData.length
    val next   = Wire(new FIFOState(gen, capacity))
    val space  = (capacity.U - count)
    val enqN   = Mux(nValid > space, space, nValid)

    // Write items into next.data
    val nextData = Wire(Vec(capacity, gen))
    nextData := data
    for (i <- 0 until n) {
      when (i.U < enqN) {
        nextData((tail + i.U) % capacity.U) := newData(i)
      }
    }
    next.data  := nextData
    next.head  := head
    next.tail  := (tail + enqN) % capacity.U
    next.count := count + enqN
    next
  }

  /** Return empty state (head=0, tail=0, count=0). */
  def flush(): FIFOState[T] = {
    val next = Wire(new FIFOState(gen, capacity))
    next.data  := data   // data contents don't matter after flush
    next.head  := 0.U
    next.tail  := 0.U
    next.count := 0.U
    next
  }

  /** Return the first n items from the head as a Vec. */
  def peek(n: Int): Vec[T] = {
    val out = Wire(Vec(n, gen))
    for (i <- 0 until n) {
      out(i) := data((head + i.U) % capacity.U)
    }
    out
  }

  /** Invariant: count <= capacity. */
  def invariant(): Bool = {
    count <= capacity.U
  }
}

object FIFOState {
  /** Create an initial (empty) FIFOState suitable for use with RegInit.
    *
    * @param capacity Buffer capacity.
    * @param gen      Element type.
    */
  def init[T <: Data](capacity: Int, gen: T): FIFOState[T] = {
    val state = Wire(new FIFOState(gen, capacity))
    state.data  := 0.U.asTypeOf(Vec(capacity, gen))
    state.head  := 0.U
    state.tail  := 0.U
    state.count := 0.U
    state
  }
}
