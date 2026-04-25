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

/** IndexAllocator: manages a pool of free indices.
  *
  * @param capacity Number of indices managed (0 to capacity-1).
  *
  * Interface:
  *   alloc : Decoupled output. valid = pool non-empty; bits = next available index.
  *   free  : Flipped Decoupled input. Caller drives valid+bits to return an index.
  *
  * Behavior:
  *   - alloc.valid  = freeCount > 0 (there is something to allocate)
  *   - free.ready   = nAllocated > 0 (there is something that can be freed)
  *   - alloc fires when alloc.valid && alloc.ready
  *   - free fires when free.valid && free.ready
  *   - Non-simultaneous free: freed index appended at back of FIFO queue.
  *   - Non-simultaneous alloc: pops from front of FIFO queue.
  *   - Simultaneous alloc+free: freed index replaces front (net: old front allocated,
  *     freed index takes its place; positions 1..freeCount-1 unchanged).
  *   - Bypass: when freeCount=0 and free fires, alloc.valid is immediately true
  *     and alloc.bits = free.bits (same cycle forwarding).
  */
abstract class IndexAllocator(val capacity: Int) extends Module {
  val io = IO(new Bundle {
    val alloc = Decoupled(UInt(log2Ceil(capacity).W))
    val free  = Flipped(Decoupled(UInt(log2Ceil(capacity).W)))
  })
}

/** IndexAllocatorShifting: compact shift-register free list implementation. */
class IndexAllocatorShifting(capacity: Int) extends IndexAllocator(capacity) {

  // freeList(0) is the front (next to allocate)
  // Items are pushed at freeList(freeCount) and popped from freeList(0)
  val freeList  = RegInit(VecInit((0 until capacity).map(_.U(log2Ceil(capacity).W))))
  val freeCount = RegInit(capacity.U(log2Ceil(capacity + 1).W))
  val nAllocated = capacity.U - freeCount

  // Combinatorial actions
  val doAlloc = io.alloc.valid && io.alloc.ready
  val doFree  = io.free.valid  && io.free.ready

  // Bypass: when pool is empty but a free is happening, the freed index is
  // immediately visible at alloc (same cycle).
  val bypassActive = (freeCount === 0.U) && io.free.valid && io.free.ready

  // Combinatorial outputs
  io.alloc.valid := (freeCount > 0.U) || bypassActive
  io.alloc.bits  := Mux(bypassActive, io.free.bits, freeList(0))
  io.free.ready  := (nAllocated > 0.U)

  // Register updates on clock edge
  when(doAlloc && !doFree) {
    // Pop from front: shift left
    for (i <- 0 until capacity - 1) {
      freeList(i) := freeList(i + 1)
    }
    freeCount := freeCount - 1.U

  }.elsewhen(!doAlloc && doFree) {
    // Push to back of the used region (FIFO queue semantics)
    for (i <- 0 until capacity) {
      when(i.U === freeCount) {
        freeList(i) := io.free.bits
      }
    }
    freeCount := freeCount + 1.U

  }.elsewhen(doAlloc && doFree) {
    // Simultaneous alloc+free:
    //   - freeList(0) is consumed (allocated)
    //   - freed index goes to front (replaces the consumed slot)
    //   - freeList(1..freeCount-1) unchanged
    //   - freeCount unchanged
    freeList(0) := io.free.bits
  }
}
