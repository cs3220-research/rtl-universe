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

/** Extended FIFO with multiple inputs and multiple outputs, plus flush and full/empty flags.
  *
  * @param gen    Data type.
  * @param n      Number of ports per cycle.
  * @param depth  Buffer capacity.
  */
class FifoXe[T <: Data](gen: T, n: Int, depth: Int) extends Module {
  val io = IO(new Bundle {
    val enqValid = Input(UInt(log2Ceil(n + 1).W))
    val enqData  = Input(Vec(n, gen))
    val deqReady = Input(UInt(log2Ceil(n + 1).W))
    val flush    = Input(Bool())
    val dataOut  = Output(Vec(n, gen))
    val count    = Output(UInt(log2Ceil(depth + 1).W))
    val full     = Output(Bool())
    val empty    = Output(Bool())
  })

  val mem   = Reg(Vec(depth, gen))
  val head  = RegInit(0.U(log2Ceil(depth + 1).W))
  val tail  = RegInit(0.U(log2Ceil(depth + 1).W))
  val count = RegInit(0.U(log2Ceil(depth + 1).W))

  val deqN = Mux(io.deqReady > count, count, io.deqReady)
  val spaceAfterDeq = depth.U - (count - deqN)
  val enqN = Mux(io.enqValid > spaceAfterDeq, spaceAfterDeq, io.enqValid)

  for (i <- 0 until n) {
    when (io.enqValid > i.U) {
      mem((tail + i.U) % depth.U) := io.enqData(i)
    }
  }

  when (io.flush) {
    head  := 0.U
    tail  := 0.U
    count := 0.U
  } .otherwise {
    head  := (head + deqN) % depth.U
    tail  := (tail + enqN) % depth.U
    count := count - deqN + enqN
  }

  for (i <- 0 until n) {
    io.dataOut(i) := mem((head + i.U) % depth.U)
  }
  io.count := count
  io.full  := count === depth.U
  io.empty := count === 0.U
}
