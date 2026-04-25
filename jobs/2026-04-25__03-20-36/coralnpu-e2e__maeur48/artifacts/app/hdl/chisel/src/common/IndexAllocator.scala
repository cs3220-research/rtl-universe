// Copyright 2024 Google LLC
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
  * Subclasses must call `buildIO(capacity)` in their constructor body to
  * construct the IO bundle.  Using `lazy val io` in Chisel is unsafe; instead
  * we pass capacity as a concrete argument.
  */
abstract class IndexAllocator(val capacity: Int) extends Module {
  val io = IO(new Bundle {
    /** Allocate an index.  valid indicates one is available; bits holds it.
      * ready from the consumer acknowledges the allocation (handshake). */
    val alloc = Decoupled(UInt(log2Ceil(capacity).W))
    /** Free a previously-allocated index.
      * valid: caller asserts to release an index.
      * bits:  the index to release.
      * ready: always false (no pipeline buffering needed). */
    val free  = Flipped(Decoupled(UInt(log2Ceil(capacity).W)))
  })
}

/** A shifting-based free-list allocator with combinational free bypass.
  *
  * Maintains a shift-register queue of free indices.  On reset all indices
  * 0..n-1 are enqueued in order.
  *
  * Key behaviours:
  *   - A freed index is visible to the alloc port COMBINATIONALLY in the same
  *     cycle as io.free.valid is asserted, without requiring a clock edge.
  *   - Free requests are always accepted (io.free.ready = false.B).
  *   - The "out of order free" test expects freed indices returned in FIFO
  *     order of freeing.
  *   - When alloc and free happen simultaneously, the freed index becomes the
  *     next index to be allocated.
  */
class IndexAllocatorShifting(n: Int) extends IndexAllocator(n) {

  private val cntBits = log2Ceil(n + 1)
  private val idxBits = log2Ceil(n)

  // -----------------------------------------------------------------
  // Registers: `entries` is the free-index queue; `cnt` is its fill level.
  // -----------------------------------------------------------------
  val entries = RegInit(VecInit((0 until n).map(_.U(idxBits.W))))
  val cnt     = RegInit(n.U(cntBits.W))

  // -----------------------------------------------------------------
  // Combinational "effective" state: incorporates the free bypass.
  //
  // If io.free.valid is asserted, the freed index is virtually available
  // at position 0 (or appended if cnt > 0).  This allows alloc to see
  // the freed index combinationally.
  // -----------------------------------------------------------------
  val doAlloc = io.alloc.valid && io.alloc.ready
  val doFree  = io.free.valid

  // Effective count (with free bypass).
  val effCnt = Wire(UInt(cntBits.W))
  effCnt := cnt + doFree.asUInt

  // Effective front entry: if cnt == 0 and freeing, expose the freed index.
  val effEntry0 = Wire(UInt(idxBits.W))
  when(cnt === 0.U && doFree) {
    effEntry0 := io.free.bits
  }.otherwise {
    effEntry0 := entries(0)
  }

  // -----------------------------------------------------------------
  // Outputs (combinational on effective state)
  // -----------------------------------------------------------------
  io.alloc.valid := effCnt > 0.U
  io.alloc.bits  := effEntry0

  // Free is always accepted immediately; no pipeline buffering.
  io.free.ready  := false.B

  // Free is always accepted immediately; no pipeline buffering.
  io.free.ready  := false.B

  // -----------------------------------------------------------------
  // Sequential update
  // -----------------------------------------------------------------
  when(doAlloc && !doFree) {
    // Consume from the front; shift remaining entries down.
    for (i <- 0 until n - 1) {
      entries(i) := entries(i + 1)
    }
    cnt := cnt - 1.U
  }.elsewhen(!doAlloc && doFree) {
    // Append freed index at the tail (position cnt).
    entries(cnt) := io.free.bits
    cnt          := cnt + 1.U
  }.elsewhen(doAlloc && doFree) {
    // Simultaneous alloc+free:
    // - Alloc consumes entry[0] (effEntry0, which is entries[0] since cnt > 0
    //   when doAlloc is true).
    // - The freed index goes to position 0, making it immediately available
    //   for the next alloc.  entries[1..cnt-1] are unchanged.
    entries(0) := io.free.bits
    // cnt unchanged.
  }
}
