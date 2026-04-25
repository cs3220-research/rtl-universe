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

/** Instruction buffer: wraps a CircularBufferMulti with a streaming-style interface.
  *
  * feedIn: up to n items per cycle from a producer.
  * out: a window of up to n items exposed as Valid outputs (consumers pop from the front).
  *
  * @param gen    Element type.
  * @param n      Number of items per cycle in/out.
  * @param window Total buffer capacity.
  */
class InstructionBuffer[T <: Data](gen: T, n: Int, window: Int) extends Module {
  val io = IO(new Bundle {
    val feedIn = new Bundle {
      val nValid = Input(UInt(log2Ceil(n + 1).W))
      val bits   = Input(Vec(n, gen))
      val nReady = Output(UInt(log2Ceil(n + 1).W))
    }
    val out       = Vec(n, Decoupled(gen))
    val flush     = Input(Bool())
    val nEnqueued = Output(UInt(log2Ceil(window + 1).W))
    val nSpace    = Output(UInt(log2Ceil(window + 1).W))
  })

  val buf = Module(new CircularBufferMulti(gen, n, window))

  // How many items are being consumed this cycle (out(i).ready && out(i).valid, contiguous from front)
  // Items must be consumed in order (contiguous from 0).
  val consumeCount = WireDefault(0.U(log2Ceil(n + 1).W))
  // Count contiguous ready outputs
  var cnt = 0.U(log2Ceil(n + 1).W)
  for (i <- 0 until n) {
    val isValid = buf.io.nEnqueued > i.U
    cnt = cnt + (isValid && io.out(i).ready).asUInt
  }
  consumeCount := cnt

  buf.io.enqValid  := io.feedIn.nValid
  buf.io.enqData   := io.feedIn.bits
  buf.io.deqReady  := consumeCount
  buf.io.flush     := io.flush

  // Outputs: expose first n items in buffer as Valid signals
  for (i <- 0 until n) {
    io.out(i).valid := buf.io.nEnqueued > i.U
    io.out(i).bits  := buf.io.dataOut(i)
  }

  // nReady: how many items can we accept? = free space in buffer
  val freeSpace = window.U - buf.io.nEnqueued
  io.feedIn.nReady := Mux(freeSpace > n.U, n.U, freeSpace)

  io.nEnqueued := buf.io.nEnqueued
  io.nSpace    := window.U - buf.io.nEnqueued
}
