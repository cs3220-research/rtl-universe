// Copyright 2026 Google LLC
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

/** Abstract base class for index allocators.
  *
  * Provides free indices for use as resource tags. Freed indices become
  * available for re-allocation (with combinatorial forwarding).
  */
abstract class IndexAllocator extends Module {
  def capacity: Int
  val io = IO(new Bundle {
    val alloc = Decoupled(UInt(log2Ceil(capacity).W))   // provides next free index
    val free  = Flipped(Decoupled(UInt(log2Ceil(capacity).W))) // accept freed index
  })
}

/** IndexAllocator implemented using a shift register of free indices.
  *
  * Behavior:
  *  - Initially presents indices 0, 1, 2, ... in order.
  *  - When alloc.ready, the current index is consumed.
  *  - When free.valid, the freed index is accepted and queued.
  *  - Freed indices are immediately available combinatorially (bypass path).
  *  - When alloc and free happen on the same cycle, the freed item occupies
  *    the newly vacated front slot (stays at front of queue).
  *
  * @param capacity Total number of indices to manage.
  */
class IndexAllocatorShifting(val capacity: Int) extends IndexAllocator {

  // Shift register of free indices, packed from index 0
  val buf   = RegInit(VecInit((0 until capacity).map(_.U(log2Ceil(capacity).W))))
  val count = RegInit(capacity.U(log2Ceil(capacity + 1).W))

  // --- Combinatorial outputs ---

  // free.ready: there is room to store a freed index
  // (when count == capacity, the queue is full; can only accept if alloc also happens)
  io.free.ready := count < capacity.U

  // alloc.valid: there is a free index available (either in queue or via bypass)
  val hasBufItem = count > 0.U
  io.alloc.valid := hasBufItem || io.free.valid

  // alloc.bits: the next free index (buf[0] if available, else bypass from free)
  io.alloc.bits := Mux(hasBufItem, buf(0), io.free.bits)

  // --- Next-state logic ---

  val doAlloc = io.alloc.ready && io.alloc.valid
  val doFree  = io.free.valid && io.free.ready

  // Both alloc and free on same cycle (and there's an item in the buffer):
  // The freed item goes to buf[0]; other items shift left normally (net: no count change)
  val bothWithBuf = doAlloc && doFree && hasBufItem

  // Only alloc (with buffer item):
  val onlyAlloc = doAlloc && !doFree && hasBufItem

  // Only free (no alloc, or alloc with empty buffer = bypass):
  val onlyFree  = doFree && !(doAlloc && hasBufItem)

  // Bypass case: alloc and free both happen but buffer is empty (direct bypass)
  // -> no state change (count stays 0)

  val nextBuf   = Wire(Vec(capacity, UInt(log2Ceil(capacity).W)))
  val nextCount = Wire(UInt(log2Ceil(capacity + 1).W))

  nextBuf   := buf
  nextCount := count

  when(bothWithBuf) {
    // Alloc consumed buf[0]; freed item takes the vacated buf[0] slot.
    // buf[1..] remains unchanged. Count is net unchanged.
    nextBuf(0) := io.free.bits
    for (i <- 1 until capacity) {
      nextBuf(i) := buf(i)
    }
    nextCount := count  // net: -1 (alloc) + 1 (free) = 0
  }.elsewhen(onlyAlloc) {
    // Shift left by 1, decrement count
    for (i <- 0 until capacity - 1) {
      nextBuf(i) := buf(i + 1)
    }
    nextBuf(capacity - 1) := 0.U
    nextCount := count - 1.U
  }.elsewhen(onlyFree) {
    // Append to end, increment count
    for (i <- 0 until capacity) {
      when(i.U === count) {
        nextBuf(i) := io.free.bits
      }
    }
    nextCount := count + 1.U
  }

  buf   := nextBuf
  count := nextCount
}
