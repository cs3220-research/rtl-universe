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

/** FIFOState: a purely functional circular FIFO state bundle.
  *
  * Stores `capacity` elements in a circular buffer. Supports:
  *   - enqueue(data, nValid): add up to nValid elements from data
  *   - dequeue(nReady): remove up to nReady elements from the front
  *   - flush(): reset to empty state
  *   - peek(n): return first n elements as a Vec
  *   - count: current number of elements
  *   - invariant(): combinational check (count <= capacity)
  *
  * All methods are combinational (return new Wire-based FIFOState values).
  *
  * Note: `genType` is stored as `val` so the Chisel plugin can process
  * this Bundle, but it acts purely as a type template.
  */
class FIFOState[T <: Data](val genType: T, val capacity: Int) extends Bundle {
  val buffer = Vec(capacity, genType.cloneType)
  val head   = UInt(log2Ceil(capacity + 1).W)
  val count  = UInt(log2Ceil(capacity + 1).W)

  /** Dequeue up to nDeq elements from the front. */
  def dequeue(nDeq: UInt): FIFOState[T] = {
    val next = Wire(new FIFOState(genType.cloneType, capacity))
    next.genType := genType
    next.buffer := buffer

    val actual = Mux(nDeq > count, count, nDeq)
    next.count := count - actual
    next.head  := (head +& actual) % capacity.U

    next
  }

  /** Enqueue up to nEnq elements from the front of data. */
  def enqueue(data: Vec[T], nEnq: UInt): FIFOState[T] = {
    val next = Wire(new FIFOState(genType.cloneType, capacity))
    next.genType := genType
    next.buffer := buffer
    next.head   := head

    val tailBase = (head +& count) % capacity.U

    for (i <- 0 until data.length) {
      when(i.U < nEnq) {
        val writeIdx = (tailBase +& i.U) % capacity.U
        next.buffer(writeIdx) := data(i)
      }
    }

    next.count := count + nEnq

    next
  }

  /** Return a flushed (empty) FIFOState. */
  def flush(): FIFOState[T] = {
    val next = Wire(new FIFOState(genType.cloneType, capacity))
    next.genType := genType
    next.buffer := buffer
    next.head   := 0.U
    next.count  := 0.U
    next
  }

  /** Return first n elements (oldest first) as a Vec of n elements. */
  def peek(n: Int): Vec[T] = {
    val out = Wire(Vec(n, genType.cloneType))
    for (i <- 0 until n) {
      val idx = (head +& i.U) % capacity.U
      out(i) := buffer(idx)
    }
    out
  }

  /** Invariant: count <= capacity (combinational). */
  def invariant(): Bool = count <= capacity.U
}

object FIFOState {
  /** Create an initial (empty) FIFOState literal for use with RegInit.
    *
    * Returns a zero-valued hardware value: head=0, count=0, buffer=all-zeros.
    * All fields are zero, which corresponds to an empty FIFO.
    */
  def init[T <: Data](capacity: Int, gen: T): FIFOState[T] = {
    0.U.asTypeOf(new FIFOState(gen, capacity))
  }
}
