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

/** FIFO with extended flow-control.  Functionally identical to [[Fifo]] in this
  * implementation; subclasses may add extra bypass or lookahead logic.
  */
class FifoX[T <: Data](gen: T, depth: Int) extends Module {
  require(depth >= 1, "FifoX depth must be at least 1")

  private val addrBits = log2Ceil(depth)
  private val cntBits  = log2Ceil(depth + 1)

  val io = IO(new Bundle {
    val enq   = Flipped(Decoupled(gen))
    val deq   = Decoupled(gen)
    val count = Output(UInt(cntBits.W))
  })

  val mem   = Mem(depth, gen)
  val head  = RegInit(0.U(addrBits.W))
  val tail  = RegInit(0.U(addrBits.W))
  val count = RegInit(0.U(cntBits.W))

  val full  = count === depth.U
  val empty = count === 0.U

  io.enq.ready := !full
  io.deq.valid := !empty
  io.deq.bits  := mem(head)
  io.count     := count

  when(io.enq.fire && !io.deq.fire) {
    mem(tail) := io.enq.bits
    tail      := Mux(tail === (depth - 1).U, 0.U, tail + 1.U)
    count     := count + 1.U
  }.elsewhen(!io.enq.fire && io.deq.fire) {
    head  := Mux(head === (depth - 1).U, 0.U, head + 1.U)
    count := count - 1.U
  }.elsewhen(io.enq.fire && io.deq.fire) {
    mem(tail) := io.enq.bits
    tail      := Mux(tail === (depth - 1).U, 0.U, tail + 1.U)
    head      := Mux(head === (depth - 1).U, 0.U, head + 1.U)
  }
}
