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

/** Multiply unit operation encoding. */
object MluOp extends ChiselEnum {
  val MUL    = Value  // 32x32 -> low 32-bit result
  val MULH   = Value  // signed x signed -> high 32 bits
  val MULHU  = Value  // unsigned x unsigned -> high 32 bits
  val MULHSU = Value  // signed x unsigned -> high 32 bits
}

/** Request bundle for one multiply lane. */
class MluRequest extends Bundle {
  val addr = UInt(5.W)   // destination register address
  val op   = MluOp()
}

/** Result bundle from the multiply unit. */
class MluResult extends Bundle {
  val addr = UInt(5.W)
  val data = UInt(32.W)
}

/** Multiply operand with validity. */
class MluOperand extends Bundle {
  val valid = Bool()
  val data  = UInt(32.W)
}

/** 4-wide pipelined multiply unit.
  *
  * Each of the four lanes can accept an independent multiply request.
  * Results are drained one at a time through the single Decoupled `rd` port.
  *
  * Pipeline (2 cycles from req valid to result visible):
  *   Cycle 0: req valid + operands sampled → stage-1 register captures req/operands
  *   Cycle 1: stage-1 multiply executes → stage-2 register captures result
  *   Cycle 2: result visible at rd.valid/rd.bits; consumed when rd.ready=1
  */
class Mlu(p: Parameters) extends Module {
  val numLanes = 4

  val io = IO(new Bundle {
    val req = Input(Vec(numLanes, Valid(new MluRequest)))
    val rs1 = Input(Vec(numLanes, new MluOperand))
    val rs2 = Input(Vec(numLanes, new MluOperand))
    val rd  = Decoupled(new MluResult)
  })

  // -----------------------------------------------------------------------
  // Stage 1: sample the first active request and its operands
  // -----------------------------------------------------------------------
  val s1Valid  = RegInit(false.B)
  val s1Addr   = Reg(UInt(5.W))
  val s1Op     = Reg(MluOp())
  val s1Rs1    = Reg(UInt(32.W))
  val s1Rs2    = Reg(UInt(32.W))

  val anyValid  = io.req.map(_.valid).reduce(_ || _)
  val firstLane = PriorityEncoder(VecInit(io.req.map(_.valid)))

  // Stage 1 is ready to accept a new request whenever the output stage is free
  // (i.e., there is no pending output, or the pending output is being consumed)
  val s2Free = !io.rd.valid || io.rd.ready

  s1Valid := anyValid && s2Free
  when(anyValid && s2Free) {
    s1Addr := io.req(firstLane).bits.addr
    s1Op   := io.req(firstLane).bits.op
    s1Rs1  := io.rs1(firstLane).data
    s1Rs2  := io.rs2(firstLane).data
  }

  // -----------------------------------------------------------------------
  // Stage 2: compute the multiply and register the result
  // -----------------------------------------------------------------------
  val s2Valid = RegInit(false.B)
  val s2Addr  = Reg(UInt(5.W))
  val s2Data  = Reg(UInt(32.W))

  // Multiply result (combinational from stage-1 registers)
  val mulResult = Wire(UInt(32.W))
  mulResult := MuxLookup(s1Op, (s1Rs1 * s1Rs2)(31, 0))(Seq(
    MluOp.MUL    -> (s1Rs1 * s1Rs2)(31, 0),
    MluOp.MULH   -> (s1Rs1.asSInt * s1Rs2.asSInt).asUInt(63, 32),
    MluOp.MULHU  -> (s1Rs1 * s1Rs2)(63, 32),
    MluOp.MULHSU -> (s1Rs1.asSInt * s1Rs2.asUInt).asUInt(63, 32),
  ))

  // Update stage-2 when stage-1 has a valid result and stage-2 is free
  when(s2Free) {
    s2Valid := s1Valid
    when(s1Valid) {
      s2Addr := s1Addr
      s2Data := mulResult
    }
  }

  // -----------------------------------------------------------------------
  // Output
  // -----------------------------------------------------------------------
  io.rd.valid      := s2Valid
  io.rd.bits.addr  := s2Addr
  io.rd.bits.data  := s2Data
}

/** Emit helper used by the build system. */
object EmitMlu extends App {
  import circt.stage.ChiselStage
  ChiselStage.emitSystemVerilog(new Mlu(new Parameters), args)
}
