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

package coralnpu

import chisel3._
import chisel3.util._

/** Entry in the retirement buffer. */
class RetirementEntry extends Bundle {
  val pc      = UInt(32.W)
  val inst    = UInt(32.W)
  val rd      = UInt(5.W)
  val rdVal   = UInt(32.W)
  val hasRd   = Bool()
  val isTrap  = Bool()
}

/** Retirement buffer (reorder buffer) stub.
  *
  * Holds in-flight instructions until they can be committed in order.
  */
class RetirementBuffer(p: Parameters, depth: Int = 8) extends Module {
  val io = IO(new Bundle {
    val enq   = Flipped(Decoupled(new RetirementEntry))
    val deq   = Decoupled(new RetirementEntry)
    val count = Output(UInt(log2Ceil(depth + 1).W))
    val flush = Input(Bool())
  })

  val buf = Module(new chisel3.util.Queue(new RetirementEntry, depth))

  buf.io.enq <> io.enq
  io.deq     <> buf.io.deq

  when(io.flush) {
    buf.io.deq.ready := true.B
  }

  io.count := buf.io.count
}
