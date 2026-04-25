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

/** Purely combinatorial FIFO state machine implemented as a Chisel Bundle.
  *
  * The buf is a circular buffer with:
  *  - head: write pointer (index of next empty slot)
  *  - count: number of valid elements
  * Tail (read pointer) = (head - count + capacity) % capacity
  *
  * @param capacity  Total capacity of the FIFO.
  * @param gen       Element type (used only for type elaboration; not a hardware field).
  */
class FIFOState[T <: Data](val capacity: Int, gen: T) extends Bundle {
  val buf   = Vec(capacity, gen.cloneType)
  val head  = UInt(log2Ceil(capacity).W)
  val count = UInt(log2Ceil(capacity + 1).W)

  // Derive element type from the already-elaborated buf field.
  private def elemType: T = buf(0).cloneType.asInstanceOf[T]

  /** Returns the index of the tail (oldest element). */
  private def tail: UInt = {
    val t = Wire(UInt(log2Ceil(capacity).W))
    // (head - count + capacity) % capacity, using wide arithmetic to avoid underflow
    val wide = head +& (capacity.U - count)
    t := wide % capacity.U
    t
  }

  /** Returns a new FIFOState after dequeueing n items.
    *
    * Removes n items from the front of the FIFO (combinatorial, no registers).
    */
  def dequeue(n: UInt): FIFOState[T] = {
    val next = Wire(new FIFOState(capacity, elemType))
    val actualDeq = Mux(n > count, count, n)
    // head doesn't change on dequeue; count decreases (tail moves forward implicitly)
    next.head  := head
    next.count := count - actualDeq
    next.buf   := buf
    next
  }

  /** Returns a new FIFOState after enqueueing data(0..n-1).
    *
    * Enqueues n items starting from data(0). Items beyond available space are dropped.
    */
  def enqueue(data: Vec[T], n: UInt): FIFOState[T] = {
    val next = Wire(new FIFOState(capacity, elemType))
    val spaceAvail = capacity.U - count
    val actualEnq = Mux(n > spaceAvail, spaceAvail, n)

    // Write items into buf
    val newBuf = Wire(Vec(capacity, elemType))
    newBuf := buf
    for (i <- 0 until data.length) {
      val writeAddr = (head + i.U) % capacity.U
      when(i.U < actualEnq) {
        newBuf(writeAddr) := data(i)
      }
    }

    next.buf   := newBuf
    next.head  := (head + actualEnq) % capacity.U
    next.count := count + actualEnq
    next
  }

  /** Returns an empty FIFOState (flush). */
  def flush(): FIFOState[T] = {
    val next = Wire(new FIFOState(capacity, elemType))
    next.buf   := buf
    next.head  := 0.U
    next.count := 0.U
    next
  }

  /** Returns true if the state is internally consistent. */
  def invariant(): Bool = {
    count <= capacity.U
  }

  /** Returns the first n items from the front of the FIFO.
    *
    * Items at positions [tail, tail+1, ..., tail+n-1] (mod capacity).
    */
  def peek(n: Int): Vec[T] = {
    val out = Wire(Vec(n, elemType))
    val t = tail
    for (i <- 0 until n) {
      val readAddr = (t + i.U) % capacity.U
      out(i) := buf(readAddr)
    }
    out
  }
}

/** Companion object for FIFOState. */
object FIFOState {
  /** Creates an initial (empty) FIFOState suitable for use with RegInit.
    *
    * Returns a zero-initialized value of FIFOState[T] (head=0, count=0, buf=all zeros).
    * Must be called inside a Chisel Module context.
    *
    * @param capacity Total capacity of the FIFO.
    * @param t        Element type.
    * @return A zero-initialized FIFOState value.
    */
  def init[T <: Data](capacity: Int, t: T): FIFOState[T] = {
    0.U.asTypeOf(new FIFOState(capacity, t))
  }
}
