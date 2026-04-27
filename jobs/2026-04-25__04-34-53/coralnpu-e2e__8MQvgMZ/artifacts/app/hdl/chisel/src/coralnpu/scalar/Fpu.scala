// Copyright 2025 Google LLC
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
import chisel3.experimental.ExtModule
import chisel3.util.HasExtModuleInline
import common.Fp32

// BlackBox FPU implementation using SystemVerilog shortreal arithmetic.
//
// Optype encoding (matches FpuOptype ordinals):
//   0 = FpuAdd  : result = ina + inc
//   1 = FpuSub  : result = ina - inc
//   2 = FpuMul  : result = ina * inb
//   3 = FpuFma  : result = ina * inb + inc
//   4 = FpuFnma : result = -(ina * inb) + inc
//   5 = FpuFms  : result = ina * inb - inc
//   6 = FpuFnms : result = -(ina * inb) - inc
//   others      : result = ina + inc (safe default)
//
// The module registers all outputs on the positive clock edge, providing
// exactly 1 cycle of pipeline latency.
class FpuBB extends ExtModule with HasExtModuleInline {
  val io = IO(new Bundle {
    val clk      = Input(Clock())
    val rst      = Input(Bool())
    val valid_i  = Input(Bool())
    val optype_i = Input(UInt(5.W))
    val waddr_i  = Input(UInt(5.W))
    val ina_i    = Input(UInt(32.W))
    val inb_i    = Input(UInt(32.W))
    val inc_i    = Input(UInt(32.W))
    val valid_o  = Output(Bool())
    val waddr_o  = Output(UInt(5.W))
    val result_o = Output(UInt(32.W))
  })

  setInline("FpuBB.sv",
    """|module FpuBB (
       |  input  logic        clk,
       |  input  logic        rst,
       |  input  logic        valid_i,
       |  input  logic [4:0]  optype_i,
       |  input  logic [4:0]  waddr_i,
       |  input  logic [31:0] ina_i,
       |  input  logic [31:0] inb_i,
       |  input  logic [31:0] inc_i,
       |  output logic        valid_o,
       |  output logic [4:0]  waddr_o,
       |  output logic [31:0] result_o
       |);
       |  shortreal fa, fb, fc, fr;
       |  logic [31:0] result_comb;
       |
       |  always_comb begin
       |    fa = $bitstoshortreal(ina_i);
       |    fb = $bitstoshortreal(inb_i);
       |    fc = $bitstoshortreal(inc_i);
       |    case (optype_i)
       |      5'd0:    fr = fa + fc;           // FpuAdd
       |      5'd1:    fr = fa - fc;           // FpuSub
       |      5'd2:    fr = fa * fb;           // FpuMul
       |      5'd3:    fr = fa * fb + fc;      // FpuFma
       |      5'd4:    fr = -(fa * fb) + fc;   // FpuFnma
       |      5'd5:    fr = fa * fb - fc;      // FpuFms
       |      5'd6:    fr = -(fa * fb) - fc;   // FpuFnms
       |      default: fr = fa + fc;
       |    endcase
       |    result_comb = $shortrealtobits(fr);
       |  end
       |
       |  always_ff @(posedge clk or posedge rst) begin
       |    if (rst) begin
       |      valid_o  <= 1'b0;
       |      waddr_o  <= 5'd0;
       |      result_o <= 32'd0;
       |    end else begin
       |      valid_o  <= valid_i;
       |      waddr_o  <= waddr_i;
       |      result_o <= result_comb;
       |    end
       |  end
       |endmodule
       |""".stripMargin)
}

// 1-cycle pipelined FPU.
//
// Timing:
//   Cycle N:   cmd.valid=1 and cmd.bits driven  →  command accepted
//   Cycle N+1: output.valid=1, output.bits available
//
// Back-pressure (output.ready) is accepted but the pipeline does not stall;
// results are produced every cycle when input is valid.
class Fpu extends Module {
  val io = IO(new Bundle {
    val cmd    = Flipped(Valid(new FpuCommand))
    val output = Decoupled(new FpuResult)
  })

  val bb = Module(new FpuBB)

  // Pack Fp32 fields into IEEE-754 32-bit words
  val inaWord = Cat(io.cmd.bits.ina.sign, io.cmd.bits.ina.exponent, io.cmd.bits.ina.mantissa)
  val inbWord = Cat(io.cmd.bits.inb.sign, io.cmd.bits.inb.exponent, io.cmd.bits.inb.mantissa)
  val incWord = Cat(io.cmd.bits.inc.sign, io.cmd.bits.inc.exponent, io.cmd.bits.inc.mantissa)

  bb.io.clk      := clock
  bb.io.rst      := reset.asBool
  bb.io.valid_i  := io.cmd.valid
  bb.io.optype_i := io.cmd.bits.optype.asUInt(4, 0)
  bb.io.waddr_i  := io.cmd.bits.waddr
  bb.io.ina_i    := inaWord
  bb.io.inb_i    := inbWord
  bb.io.inc_i    := incWord

  // Unpack the result bit-pattern into an Fp32 bundle
  io.output.valid          := bb.io.valid_o
  io.output.bits.addr      := bb.io.waddr_o
  io.output.bits.bits      := Fp32.fromWord(bb.io.result_o)
  io.output.bits.intResult := bb.io.result_o
  io.output.bits.fflags    := 0.U
  io.output.bits.isInt     := false.B
}
