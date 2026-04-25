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

/** Hardware Bundle that tracks the state of a multi-enqueue/dequeue FIFO.
  *
  * This is a pure Bundle (not a Module).  It is intended to be held in a
  * register and updated each cycle via the functional methods below.
  *
  * @param capacity Maximum number of elements the FIFO can hold.
  * @param t        The element type.
  */
class FIFOState[T <: Data](capacity: Int, t: T) extends Bundle {
  val data  = Vec(capacity, t)
  // head: index of the oldest (front) element.
  val head  = UInt(log2Ceil(capacity + 1).W)
  // count: number of valid elements currently held.
  val count = UInt(log2Ceil(capacity + 1).W)

  /** Enqueue up to n elements from `newData`.  Only the first `nValid`
    * elements of `newData` are inserted.  No overflow check is done here;
    * callers must ensure nValid <= (capacity - count).
    */
  def enqueue(newData: Vec[T], nValid: UInt): FIFOState[T] = {
    val next = Wire(new FIFOState(capacity, t))
    next.data  := data
    next.head  := head
    next.count := count + nValid

    // tail = (head + count) % capacity
    val tail = (head +& count) % capacity.U

    for (i <- 0 until newData.length) {
      when(i.U < nValid) {
        val idx = (tail +& i.U) % capacity.U
        next.data(idx) := newData(i)
      }
    }
    next
  }

  /** Dequeue `nReady` elements from the front of the FIFO.
    * No underflow check is done here; callers must ensure nReady <= count.
    */
  def dequeue(nReady: UInt): FIFOState[T] = {
    val next = Wire(new FIFOState(capacity, t))
    next.data  := data
    next.count := count - nReady
    next.head  := (head +& nReady) % capacity.U
    next
  }

  /** Return a new FIFOState with all elements cleared (head=0, count=0). */
  def flush(): FIFOState[T] = {
    val next = Wire(new FIFOState(capacity, t))
    next.data  := data   // data contents don't matter after flush
    next.head  := 0.U
    next.count := 0.U
    next
  }

  /** Peek at the first `n` elements from the front of the FIFO.
    * Elements beyond `count` will contain stale data (callers should gate
    * on the returned count).
    */
  def peek(n: Int): Vec[T] = {
    val out = Wire(Vec(n, t))
    for (i <- 0 until n) {
      val idx = (head +& i.U) % capacity.U
      out(i) := data(idx)
    }
    out
  }

  /** Hardware assertion: count must be <= capacity. */
  def invariant(): Bool = count <= capacity.U
}

/** Companion object for FIFOState. */
object FIFOState {
  /** Create an initialised FIFOState register value (count=0, head=0). */
  def init[T <: Data](capacity: Int, t: T): FIFOState[T] = {
    val s = Wire(new FIFOState(capacity, t))
    s.data  := VecInit(Seq.fill(capacity)(0.U.asTypeOf(t)))
    s.head  := 0.U
    s.count := 0.U
    s
  }
}
