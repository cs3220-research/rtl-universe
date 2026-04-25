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

/** Fifo: a simple synchronous FIFO using Chisel's Queue.
  *
  * @param gen     Data type of each element.
  * @param entries Number of entries in the FIFO.
  */
class Fifo[T <: Data](gen: T, entries: Int) extends Module {
  val io = IO(new Bundle {
    val in  = Flipped(Decoupled(gen))
    val out = Decoupled(gen)
  })

  val q = Module(new Queue(gen, entries))
  q.io.enq <> io.in
  io.out   <> q.io.deq
}

object Fifo {
  def apply[T <: Data](in: DecoupledIO[T], entries: Int): DecoupledIO[T] = {
    val fifo = Module(new Fifo(chiselTypeOf(in.bits), entries))
    fifo.io.in <> in
    fifo.io.out
  }
}
