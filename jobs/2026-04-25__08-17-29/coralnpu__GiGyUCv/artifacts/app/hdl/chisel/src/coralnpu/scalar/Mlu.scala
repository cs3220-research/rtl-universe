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

object MluOp extends ChiselEnum {
  val MUL, MULH, MULHSU, MULHU = Value
}

class MluRequest(p: Parameters) extends Bundle {
  val addr = UInt(5.W)
  val op   = MluOp()
}

/** Multiply unit.
  *
  * Accepts up to 4 multiply requests per cycle (via Vec(4, Valid(...))).
  * Only the first valid request is processed.  Result is returned through a
  * Decoupled output after a single-cycle latency.
  */
class Mlu(p: Parameters) extends Module {
  val io = IO(new Bundle {
    val req = Input(Vec(4, Valid(new MluRequest(p))))
    val rs1 = Input(Vec(4, new RegSource(p)))
    val rs2 = Input(Vec(4, new RegSource(p)))
    val rd  = Decoupled(new RegData(p))
  })

  // Pick the first valid request (priority encoder)
  val anyValid = io.req.map(_.valid).reduce(_ || _)
  val selIdx   = PriorityEncoder(io.req.map(_.valid))

  val selReq = io.req(selIdx)
  val selRs1 = io.rs1(selIdx)
  val selRs2 = io.rs2(selIdx)

  // 1-cycle pipeline
  val pendingValid = RegNext(anyValid && selRs1.valid && selRs2.valid, false.B)
  val pendingAddr  = RegNext(selReq.bits.addr)
  val pendingOp    = RegNext(selReq.bits.op)
  val pendingA     = RegNext(selRs1.data)
  val pendingB     = RegNext(selRs2.data)

  val a = pendingA
  val b = pendingB

  val mulResult   = (a * b)(31, 0)
  val mulhResult  = ((a.asSInt * b.asSInt).asUInt)(63, 32)
  val mulhuResult = ((a * b))(63, 32)
  val mulhsuResult = ((a.asSInt * b.zext).asUInt)(63, 32)

  val result = MuxLookup(pendingOp, mulResult)(Seq(
    MluOp.MUL    -> mulResult,
    MluOp.MULH   -> mulhResult,
    MluOp.MULHU  -> mulhuResult,
    MluOp.MULHSU -> mulhsuResult,
  ))

  // Output FIFO (depth 1) so we can hold the result until the consumer is ready
  val resultQueue = Module(new Queue(new RegData(p), 1))
  resultQueue.io.enq.valid      := pendingValid
  resultQueue.io.enq.bits.addr  := pendingAddr
  resultQueue.io.enq.bits.data  := result

  io.rd <> resultQueue.io.deq
}
