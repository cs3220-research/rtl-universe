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

/** FifoXe: FIFO with extra status signals and flush support.
  *
  * Extends FifoX with a flush input that clears all entries.
  *
  * @param gen     Data type.
  * @param entries Number of entries.
  */
class FifoXe[T <: Data](gen: T, entries: Int) extends Module {
  val io = IO(new Bundle {
    val in    = Flipped(Decoupled(gen))
    val out   = Decoupled(gen)
    val count = Output(UInt(log2Ceil(entries + 1).W))
    val full  = Output(Bool())
    val flush = Input(Bool())
  })

  val q = Module(new Queue(gen, entries, hasFlush = true))
  q.io.enq   <> io.in
  io.out     <> q.io.deq
  io.count   := q.io.count
  io.full    := (q.io.count === entries.U)
  q.io.flush.get := io.flush
}
