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

package coralnpu

import chisel3._
import chisel3.util._

/** Retirement buffer — tracks in-flight instructions from dispatch to commit.
  *
  * Stores (pc, inst) pairs indexed by a small circular buffer, allowing
  * out-of-order execution units to commit results in order.
  *
  * @param p    Design parameters.
  * @param size Number of entries (default 8).
  */
class RetirementBuffer(p: Parameters, size: Int = RetirementBufferConfig.size)
    extends Module {

  private val idxWidth = log2Ceil(size) + 1

  val io = IO(new Bundle {
    // Allocate a slot on instruction dispatch
    val alloc = Flipped(Valid(new Bundle {
      val pc   = UInt(32.W)
      val inst = UInt(32.W)
    }))
    // Index assigned to the allocated slot
    val allocIdx = Output(UInt(idxWidth.W))

    // Commit (retire) an instruction
    val commit = Flipped(Valid(new Bundle {
      val idx  = UInt(idxWidth.W)
      val data = UInt(32.W)
      val trap = Bool()
    }))

    // Debug visibility
    val entries = Vec(size, Output(new RetirementBufferDebugEntry))
    val full    = Output(Bool())
    val empty   = Output(Bool())
  })

  // Circular buffer of entries
  val pcMem   = Reg(Vec(size, UInt(32.W)))
  val instMem = Reg(Vec(size, UInt(32.W)))
  val valid   = RegInit(VecInit(Seq.fill(size)(false.B)))

  val headPtr = RegInit(0.U(idxWidth.W))
  val tailPtr = RegInit(0.U(idxWidth.W))

  private def ptr(p: UInt) = p(log2Ceil(size) - 1, 0)

  io.full  := (headPtr(log2Ceil(size)) =/= tailPtr(log2Ceil(size))) &&
              (ptr(headPtr) === ptr(tailPtr))
  io.empty := headPtr === tailPtr

  io.allocIdx := headPtr

  when(io.alloc.valid && !io.full) {
    pcMem(ptr(headPtr))   := io.alloc.bits.pc
    instMem(ptr(headPtr)) := io.alloc.bits.inst
    valid(ptr(headPtr))   := true.B
    headPtr               := headPtr + 1.U
  }

  when(io.commit.valid) {
    valid(ptr(io.commit.bits.idx)) := false.B
    tailPtr := tailPtr + 1.U
  }

  for (i <- 0 until size) {
    io.entries(i).valid := valid(i)
    io.entries(i).pc   := pcMem(i)
    io.entries(i).inst := instMem(i)
    io.entries(i).idx  := i.U
    io.entries(i).data := 0.U
    io.entries(i).trap := false.B
  }
}
