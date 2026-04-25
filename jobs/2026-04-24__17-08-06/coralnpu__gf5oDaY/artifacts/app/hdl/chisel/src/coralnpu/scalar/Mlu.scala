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

/** Multiply/Divide unit operation codes. */
object MluOp extends ChiselEnum {
  val MUL, MULH, MULHU, MULHSU, DIV, DIVU, REM, REMU = Value
}

/**
 * Multiply/Divide Unit.
 *
 * Has 4 request slots (Vec of 4). Only the first valid slot is processed
 * (priority encoder). The result is output via Decoupled rd.
 *
 * Latency: 1 cycle for multiply, variable for divide.
 */
class Mlu(p: Parameters) extends Module {
  val addrWidth = log2Ceil(p.nRegs)
  val nSlots = 4

  val io = IO(new Bundle {
    val req = Input(Vec(nSlots, Valid(new Bundle {
      val addr = UInt(addrWidth.W)
      val op   = MluOp()
    })))
    val rs1 = Input(Vec(nSlots, new Bundle {
      val valid = Bool()
      val data  = UInt(32.W)
    }))
    val rs2 = Input(Vec(nSlots, new Bundle {
      val valid = Bool()
      val data  = UInt(32.W)
    }))
    val rd = Decoupled(new Bundle {
      val addr = UInt(addrWidth.W)
      val data = UInt(32.W)
    })
  })

  // Pick the first valid request slot (priority encoder)
  val activeIdx = PriorityEncoder(io.req.map(_.valid))
  val anyValid  = io.req.map(_.valid).reduce(_ || _)

  val activeReq = io.req(activeIdx)
  val activeRs1 = io.rs1(activeIdx)
  val activeRs2 = io.rs2(activeIdx)

  // Pipeline register (1 cycle latency)
  val pipeValid = RegInit(false.B)
  val pipeAddr  = RegInit(0.U(addrWidth.W))
  val pipeData  = RegInit(0.U(32.W))

  // Output FIFO: hold result until consumed
  val outFifo = Module(new Queue(new Bundle {
    val addr = UInt(addrWidth.W)
    val data = UInt(32.W)
  }, 2))

  // Compute result combinatorially
  val rs1 = activeRs1.data
  val rs2 = activeRs2.data
  val result = Wire(UInt(32.W))
  result := 0.U

  switch(activeReq.bits.op) {
    is(MluOp.MUL) {
      result := (rs1 * rs2)(31, 0)
    }
    is(MluOp.MULH) {
      result := ((rs1.asSInt * rs2.asSInt).asUInt)(63, 32)
    }
    is(MluOp.MULHU) {
      result := (rs1 * rs2)(63, 32)
    }
    is(MluOp.MULHSU) {
      result := ((rs1.asSInt * rs2).asUInt)(63, 32)
    }
    is(MluOp.DIV) {
      result := Mux(rs2 === 0.U, "hFFFFFFFF".U,
                  (rs1.asSInt / rs2.asSInt).asUInt)
    }
    is(MluOp.DIVU) {
      result := Mux(rs2 === 0.U, "hFFFFFFFF".U, rs1 / rs2)
    }
    is(MluOp.REM) {
      result := Mux(rs2 === 0.U, rs1,
                  (rs1.asSInt % rs2.asSInt).asUInt)
    }
    is(MluOp.REMU) {
      result := Mux(rs2 === 0.U, rs1, rs1 % rs2)
    }
  }

  // Stage 1: latch
  pipeValid := anyValid && outFifo.io.enq.ready
  pipeAddr  := activeReq.bits.addr
  pipeData  := result

  // Enqueue to fifo when pipeline output is valid
  outFifo.io.enq.valid      := pipeValid
  outFifo.io.enq.bits.addr  := pipeAddr
  outFifo.io.enq.bits.data  := pipeData

  // Connect output
  io.rd <> outFifo.io.deq
}

/** Emitter for MLU. */
object EmitMlu extends App {
  val p = new Parameters
  _root_.circt.stage.ChiselStage.emitSystemVerilog(new Mlu(p))
}
