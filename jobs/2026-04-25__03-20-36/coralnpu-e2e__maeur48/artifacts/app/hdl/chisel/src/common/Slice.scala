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

/** Single-register pipeline slice with full back-pressure (cut-through stall).
  *
  * Implements the classic "elastic pipeline register" pattern:
  *   - When the output is ready, data flows through.
  *   - When the output is not ready, the register holds its value and the
  *     input is stalled (in.ready = false).
  *
  * @param gen  The data type to register.
  */
class Slice[T <: Data](gen: T) extends Module {
  val io = IO(new Bundle {
    val in  = Flipped(Decoupled(gen))
    val out = Decoupled(gen)
  })

  // Registered data and valid.
  val dataReg  = Reg(gen)
  val validReg = RegInit(false.B)

  io.out.valid := validReg
  io.out.bits  := dataReg

  // Input is ready when the register is empty, or when the downstream is
  // consuming this cycle (the register will be freed).
  io.in.ready := !validReg || io.out.ready

  when(io.in.fire) {
    dataReg  := io.in.bits
    validReg := true.B
  }.elsewhen(io.out.fire) {
    validReg := false.B
  }
}
