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

/** Multi-port circular buffer.
  *
  * Supports enqueuing and dequeuing up to `n` elements per cycle.
  * Non-power-of-2 depths are supported.
  *
  * @param gen    Element type.
  * @param n      Maximum number of elements that can be enqueued or dequeued per cycle.
  * @param depth  Total buffer capacity.
  */
class CircularBufferMulti[T <: Data](gen: T, n: Int, depth: Int) extends Module {
  require(depth >= 1)
  require(n >= 1)

  private val cntBits = log2Ceil(n + 1)
  private val nEnqBits = log2Ceil(depth + 1)

  val io = IO(new Bundle {
    /** Number of elements to enqueue this cycle (0..n). */
    val enqValid  = Input(UInt(cntBits.W))
    /** Data to enqueue. */
    val enqData   = Input(Vec(n, gen))
    /** Number of elements to dequeue / consume this cycle (0..n). */
    val deqReady  = Input(UInt(cntBits.W))
    /** Peek at the front n elements (oldest first). */
    val dataOut   = Output(Vec(n, gen))
    /** Current number of valid elements in the buffer. */
    val nEnqueued = Output(UInt(nEnqBits.W))
    /** Reset the buffer to empty. */
    val flush     = Input(Bool())
    /** Number of free slots. */
    val nSpace    = Output(UInt(nEnqBits.W))
  })

  // -----------------------------------------------------------------------
  // Storage: a flat Vec of depth entries.
  // head  = index of oldest element (read pointer).
  // count = number of valid entries.
  // tail  = (head + count) % depth  (derived, not registered).
  // -----------------------------------------------------------------------
  val mem   = Reg(Vec(depth, gen))
  val head  = RegInit(0.U(log2Ceil(depth).W))
  val count = RegInit(0.U(nEnqBits.W))

  val tail = (head +& count) % depth.U

  // -----------------------------------------------------------------------
  // Combinational outputs
  // -----------------------------------------------------------------------
  io.nEnqueued := count
  io.nSpace    := (depth.U - count)

  // Peek: expose the front n elements starting at head.
  for (i <- 0 until n) {
    val idx = (head +& i.U) % depth.U
    io.dataOut(i) := mem(idx)
  }

  // -----------------------------------------------------------------------
  // Sequential update
  // -----------------------------------------------------------------------
  // The "dequeue then enqueue" ordering means the tail for new writes is
  // computed from the post-dequeue head/count.

  when(io.flush) {
    head  := 0.U
    count := 0.U
  }.otherwise {
    // --- Dequeue (advance head, decrease count) ---
    val deqN = io.deqReady
    val newHead  = (head  +& deqN) % depth.U
    val newCount = count - deqN

    // --- Enqueue (write starting at new tail) ---
    val enqN   = io.enqValid
    val newTail = (newHead +& newCount) % depth.U

    for (i <- 0 until n) {
      when(i.U < enqN) {
        val writeIdx = (newTail +& i.U) % depth.U
        mem(writeIdx) := io.enqData(i)
      }
    }

    head  := newHead
    count := newCount + enqN
  }
}
