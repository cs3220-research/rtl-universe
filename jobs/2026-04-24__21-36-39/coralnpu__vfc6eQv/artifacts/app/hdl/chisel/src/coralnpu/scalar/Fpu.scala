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

import common.{Fp32, Fma, FmaCmd}

/** FPU operation type. */
object FpuOptype extends ChiselEnum {
  val FpuAdd = Value  // result = ina + inc
  val FpuSub = Value  // result = ina - inc
  val FpuMul = Value  // result = ina * inb
}

/** Command bundle for the FPU. */
class FpuCmd extends Bundle {
  val optype = FpuOptype()
  val waddr  = UInt(5.W)   // writeback register address
  val ina    = new Fp32
  val inb    = new Fp32
  val inc    = new Fp32
}

/** Output bundle from the FPU. */
class FpuOutput extends Bundle {
  val addr = UInt(5.W)
  val bits = new Fp32
}

/** Two-stage FPU pipeline (FpuAdd, FpuSub, FpuMul).
  *
  * Operations:
  *   FpuAdd : ina + inc  (mapped to FMA: ina * 1.0 + inc)
  *   FpuSub : ina - inc  (mapped to FMA: ina * 1.0 + (-inc))
  *   FpuMul : ina * inb  (mapped to FMA: ina * inb + 0.0)
  *
  * Pipeline: 2-cycle latency.
  *   Cycle N  : cmd valid → stage-1 register samples cmd
  *   Cycle N+1: stage-1 drives FMA → stage-2 register samples result
  *   Cycle N+2: result visible at output (valid=1)
  *
  * Backpressure: stage-2 advances only when the output is free (not valid or
  * being consumed via output.ready).  Stage-1 advances when stage-2 is free.
  */
class Fpu extends Module {
  val io = IO(new Bundle {
    val cmd    = Flipped(Valid(new FpuCmd))
    val output = Decoupled(new FpuOutput)
  })

  // -----------------------------------------------------------------------
  // Constant Fp32 values used for operation mapping
  // -----------------------------------------------------------------------
  val fp_one = Wire(new Fp32)
  fp_one.sign     := false.B
  fp_one.exponent := 127.U   // biased exponent for 1.0
  fp_one.mantissa := 0.U

  val fp_zero = Wire(new Fp32)
  fp_zero.sign     := false.B
  fp_zero.exponent := 0.U
  fp_zero.mantissa := 0.U

  // -----------------------------------------------------------------------
  // Stage 1: register the input command
  // -----------------------------------------------------------------------
  val s1Valid   = RegInit(false.B)
  val s1Optype  = Reg(FpuOptype())
  val s1Waddr   = Reg(UInt(5.W))
  val s1Ina     = Reg(new Fp32)
  val s1Inb     = Reg(new Fp32)
  val s1Inc     = Reg(new Fp32)

  // Stage-2 output register (free when not valid or being consumed)
  val s2Valid   = RegInit(false.B)
  val s2Waddr   = Reg(UInt(5.W))
  val s2Result  = Reg(new Fp32)

  val s2Free = !s2Valid || io.output.ready

  // Stage 1 advances into stage 2 when stage 2 is free
  when(s2Free) {
    s1Valid := io.cmd.valid
    when(io.cmd.valid) {
      s1Optype := io.cmd.bits.optype
      s1Waddr  := io.cmd.bits.waddr
      s1Ina    := io.cmd.bits.ina
      s1Inb    := io.cmd.bits.inb
      s1Inc    := io.cmd.bits.inc
    }
  }

  // -----------------------------------------------------------------------
  // Stage 2: compute FMA from stage-1 registers, register result
  // -----------------------------------------------------------------------

  // Map operation to FMA inputs (ina * inb + inc)
  val fmaCmd = Wire(new FmaCmd)
  fmaCmd.ina := s1Ina
  fmaCmd.inb := fp_one
  fmaCmd.inc := s1Inc  // default: FpuAdd

  switch(s1Optype) {
    is(FpuOptype.FpuSub) {
      // ina - inc = ina * 1.0 + (-inc)
      val negInc = Wire(new Fp32)
      negInc.sign     := !s1Inc.sign
      negInc.exponent := s1Inc.exponent
      negInc.mantissa := s1Inc.mantissa
      fmaCmd.inc := negInc
    }
    is(FpuOptype.FpuMul) {
      // ina * inb + 0
      fmaCmd.inb := s1Inb
      fmaCmd.inc := fp_zero
    }
  }

  val fmaS1     = Fma.FmaStage1(fmaCmd)
  val fmaS2     = Fma.FmaStage2(fmaS1)
  val fmaResult = Fma.FmaStage3(fmaS2)

  // Stage 2 captures when stage 2 is free
  when(s2Free) {
    s2Valid  := s1Valid
    when(s1Valid) {
      s2Waddr  := s1Waddr
      s2Result := fmaResult
    }
  }

  // -----------------------------------------------------------------------
  // Output
  // -----------------------------------------------------------------------
  io.output.valid      := s2Valid
  io.output.bits.addr  := s2Waddr
  io.output.bits.bits  := s2Result
}
