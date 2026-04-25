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

/** Abstract index allocator.
  *
  * alloc: Decoupled output — provides an available index to the consumer.
  * free:  Flipped Decoupled input — accepts a freed index from the consumer.
  */
abstract class IndexAllocator extends Module {
  val io = IO(new Bundle {
    val alloc = Decoupled(UInt(32.W))
    val free  = Flipped(Decoupled(UInt(32.W)))
  })
}

/** Shift-register based index allocator.
  *
  * Maintains two pools:
  *  1. Initial pool: indices 0, 1, ..., capacity-1 (allocated sequentially via initHead pointer).
  *  2. Free pool: a FIFO queue of returned indices (freed items are enqueued here).
  *
  * Allocation priority: free pool first (returned indices), then initial pool,
  * then forwarded free (bypass: if a free is presented and no other source available,
  * the freed index is immediately forwarded to alloc without entering the free pool).
  *
  * This gives the behavior:
  *  - Initial alloc: 0, 1, 2, 3, ...
  *  - After freeing index i (when initial pool is exhausted): i becomes immediately available.
  *  - "Alloc and free on same cycle": freed index goes to free pool front, returned before
  *    remaining initial indices.
  *  - "Flow" (all allocated, free presented): freed index forwarded directly to alloc output.
  *
  * free.ready: true when there are allocated indices outstanding (some have been allocated
  *             but not yet freed), so accepting a freed index is meaningful.
  *             = initHead > freePool.size  (i.e., count_allocated > 0)
  *             = numAllocated > 0
  *
  * alloc.valid: true when there is an available index (free pool non-empty OR initial pool
  *              non-empty OR free is being presented — forwarding bypass).
  *
  * "No pipe" (free.ready=false at start): initially numAllocated=0, so free.ready=false. ✓
  * "Flow" (free fires immediately when all allocated): numAllocated=capacity>0, free.ready=true. ✓
  */
class IndexAllocatorShifting(capacity: Int) extends IndexAllocator {
  val indexW = log2Ceil(capacity + 1)

  // Free pool: FIFO queue of freed indices. Implemented as a circular buffer.
  // Capacity = `capacity` entries.
  val freePoolData  = Reg(Vec(capacity, UInt(indexW.W)))
  val freePoolHead  = RegInit(0.U(log2Ceil(capacity).W))
  val freePoolCount = RegInit(0.U(log2Ceil(capacity + 1).W))

  // Initial pool: indices not yet given out for the first time.
  // initHead is the next index to give out (starts at 0, goes up to capacity-1).
  val initHead = RegInit(0.U(indexW.W))

  // Number of allocated (currently in-use) indices.
  val numAllocated = initHead - freePoolCount  // (# given out from initial) - (# returned)

  // Free pool tail (write pointer)
  val freePoolTail = (freePoolHead + freePoolCount) % capacity.U

  // Is the free pool non-empty? (has returned indices available)
  val freePoolNonEmpty = freePoolCount > 0.U

  // Is the initial pool non-empty? (has fresh indices available)
  val initPoolNonEmpty = initHead < capacity.U

  // free.ready: there are allocated indices that can be returned.
  // Condition: numAllocated > 0.
  // numAllocated = initHead - freePoolCount.
  io.free.ready := (numAllocated > 0.U)

  // Forwarding: a free is presented (and ready) but no pool has items — bypass directly.
  val freePresented = io.free.valid && io.free.ready

  // alloc.valid: either pool has something, or forwarding from free
  io.alloc.valid := freePoolNonEmpty || initPoolNonEmpty || freePresented

  // alloc.bits: prefer free pool, then initial pool, then forwarded free
  io.alloc.bits := Mux(freePoolNonEmpty,
                       freePoolData(freePoolHead),
                   Mux(initPoolNonEmpty,
                       initHead,
                       io.free.bits))

  // Handshake signals
  val allocFire = io.alloc.valid && io.alloc.ready
  val freeFire  = io.free.valid  && io.free.ready

  // Whether alloc is served from the forwarded free (bypass path).
  // This is only true when freePool and initPool are both empty.
  val allocFromForward = allocFire && !freePoolNonEmpty && !initPoolNonEmpty && freeFire

  // Update state on clock edge
  when (allocFire && freeFire && !allocFromForward) {
    // Simultaneously allocate and free (alloc served from a pool, not bypass).
    // Allocate: consume from free pool (or init pool) — free pool takes priority.
    // Free: enqueue returned index into free pool.
    when (freePoolNonEmpty) {
      // Dequeue from free pool head; enqueue freed index at tail.
      freePoolHead  := (freePoolHead + 1.U) % capacity.U
      // freePoolCount stays same (removed 1, added 1)
    } .otherwise {
      // Consume from initial pool; enqueue freed index into free pool.
      initHead      := initHead + 1.U
      freePoolCount := freePoolCount + 1.U
    }
    // Enqueue freed index at tail of free pool (when not bypass)
    freePoolData(freePoolTail) := io.free.bits

  } .elsewhen (allocFire && !freeFire) {
    // Allocate only (no simultaneous free).
    when (freePoolNonEmpty) {
      freePoolHead  := (freePoolHead + 1.U) % capacity.U
      freePoolCount := freePoolCount - 1.U
    } .otherwise {
      // Allocating from initPool (or forwarded free — but allocFromForward requires freeFire,
      // so this branch only runs when freePool is used or initPool is used).
      initHead := initHead + 1.U
    }
  } .elsewhen (!allocFire && freeFire) {
    // Free only: enqueue returned index.
    freePoolData(freePoolTail) := io.free.bits
    freePoolCount := freePoolCount + 1.U
  }
  // When allocFromForward: alloc and free fire simultaneously via bypass.
  // The freed index goes directly to alloc output; no pool state changes.
  // numAllocated remains the same (freed one, re-allocated one via bypass).
}
