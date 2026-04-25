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

/** FifoIxO: FIFO with Irrevocable input and Decoupled output.
  *
  * Wraps a Queue, ensuring the input conforms to Irrevocable semantics
  * (once valid is asserted, bits must not change until accepted).
  *
  * @param gen     Data type.
  * @param entries Number of entries.
  */
class FifoIxO[T <: Data](gen: T, entries: Int) extends Module {
  val io = IO(new Bundle {
    val in  = Flipped(Irrevocable(gen))
    val out = Decoupled(gen)
  })

  val q = Module(new Queue(gen, entries))
  q.io.enq.valid := io.in.valid
  q.io.enq.bits  := io.in.bits
  io.in.ready    := q.io.enq.ready
  io.out        <> q.io.deq
}
