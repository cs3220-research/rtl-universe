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

/** FeedIn interface: n items can be fed in per cycle.
  *
  * @param n    Number of items per cycle.
  * @param gen  Element type.
  */
class FeedIn[T <: Data](n: Int, gen: T) extends Bundle {
  val nValid  = Input(UInt(log2Ceil(n + 1).W))
  val bits    = Input(Vec(n, gen))
  val nReady  = Output(UInt(log2Ceil(n + 1).W))
}

/** Instruction buffer backed by CircularBufferMulti.
  *
  * Accepts up to n items per cycle via feedIn, exposes a sliding window
  * of up to n items at the head via DecoupledIO outputs.
  *
  * @param gen     Element type.
  * @param n       Number of items per cycle (feedIn width, window width).
  * @param window  Total buffer capacity.
  */
class InstructionBuffer[T <: Data](gen: T, n: Int, window: Int) extends Module {
  val io = IO(new Bundle {
    val feedIn    = new FeedIn(n, gen)
    val out       = Vec(n, Decoupled(gen))
    val nEnqueued = Output(UInt(log2Ceil(window + 1).W))
    val nSpace    = Output(UInt(log2Ceil(window + 1).W))
    val flush     = Input(Bool())
  })

  // Underlying circular buffer
  val buf = Module(new CircularBufferMulti(gen, n, window))

  // Flush
  buf.io.flush := io.flush

  // Count how many outputs are being consumed this cycle (in order, from slot 0)
  val deqCount = Wire(UInt(log2Ceil(n + 1).W))
  val deqCountReg = RegInit(0.U(log2Ceil(n + 1).W))

  // Determine dequeue count: outputs are consumed in order; consumer must take
  // from slot 0 onwards. Count consecutive ready+valid from slot 0.
  var deqAcc = 0.U(log2Ceil(n + 1).W)
  var allPrevReady = true.B
  for (i <- 0 until n) {
    val thisReady = io.out(i).ready && io.out(i).valid && allPrevReady
    deqAcc = deqAcc + thisReady.asUInt
    allPrevReady = allPrevReady && thisReady
  }
  deqCount := deqAcc

  // Connect to circular buffer
  buf.io.enqData    := io.feedIn.bits
  buf.io.enqValid   := Mux(io.flush, 0.U, io.feedIn.nValid)
  buf.io.deqReady   := deqCount

  // feedIn.nReady: how many input slots are free (up to n)
  val space = window.U - buf.io.nEnqueued
  io.feedIn.nReady := Mux(space >= n.U, n.U, space)

  // Outputs: expose the head of the circular buffer
  for (i <- 0 until n) {
    io.out(i).valid := (i.U < buf.io.nEnqueued)
    io.out(i).bits  := buf.io.dataOut(i)
  }

  io.nEnqueued := buf.io.nEnqueued
  io.nSpace    := window.U - buf.io.nEnqueued
}
