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

/** Slice: a single pipeline register stage (Decoupled).
  *
  * Buffers one element of type T. Implements the "cut" between pipeline stages.
  * Provides full throughput with one cycle latency.
  */
class Slice[T <: Data](gen: T) extends Module {
  val io = IO(new Bundle {
    val in  = Flipped(Decoupled(gen))
    val out = Decoupled(gen)
  })

  val reg   = Reg(gen)
  val valid = RegInit(false.B)

  io.in.ready  := !valid || io.out.ready
  io.out.valid := valid
  io.out.bits  := reg

  when(io.out.ready && valid) {
    valid := false.B
  }

  when(io.in.valid && io.in.ready) {
    reg   := io.in.bits
    valid := true.B
  }
}

object Slice {
  def apply[T <: Data](in: DecoupledIO[T]): DecoupledIO[T] = {
    val s = Module(new Slice(chiselTypeOf(in.bits)))
    s.io.in <> in
    s.io.out
  }
}
