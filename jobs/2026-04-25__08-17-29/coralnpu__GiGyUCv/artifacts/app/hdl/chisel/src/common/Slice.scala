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

/** A pipeline slice (register stage) for a Decoupled signal.
  *
  * Inserts one cycle of latency with full handshake support.
  *
  * @param gen  Data type.
  */
class Slice[T <: Data](gen: T) extends Module {
  val io = IO(new Bundle {
    val in  = Flipped(Decoupled(gen))
    val out = Decoupled(gen)
  })

  val q = Module(new Queue(gen, 1, pipe = true, flow = false))
  q.io.enq <> io.in
  io.out   <> q.io.deq
}

object Slice {
  def apply[T <: Data](gen: T): Slice[T] = new Slice(gen)
}
