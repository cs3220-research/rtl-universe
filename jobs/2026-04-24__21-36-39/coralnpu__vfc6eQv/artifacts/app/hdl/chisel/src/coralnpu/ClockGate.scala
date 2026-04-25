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
import chisel3.experimental._

/** BlackBox that wraps ClockGate.sv.
  *
  * Port names match the SystemVerilog cell exactly: `clk_i`, `en_i`,
  * `test_en_i`, `clk_o`.
  */
private class ClockGateBB extends BlackBox with HasBlackBoxPath {
  val io = IO(new Bundle {
    val clk_i     = Input(Clock())
    val en_i      = Input(Bool())
    val test_en_i = Input(Bool())
    val clk_o     = Output(Clock())
  })
}

/** Chisel Module wrapper that exposes the friendly port names used by the
  * rest of the design: `clk_i`, `enable`, `te`, `clk_o`.
  *
  * Instantiation:
  * {{{
  *   val cg = Module(new ClockGate())
  *   cg.io.clk_i  := clock
  *   cg.io.enable := someEnable
  *   cg.io.te     := false.B
  *   withClock(cg.io.clk_o) { ... }
  * }}}
  */
class ClockGate extends Module {
  val io = IO(new Bundle {
    val clk_i  = Input(Clock())
    val enable = Input(Bool())
    val te     = Input(Bool())
    val clk_o  = Output(Clock())
  })

  val bb = Module(new ClockGateBB)
  bb.io.clk_i     := io.clk_i
  bb.io.en_i      := io.enable
  bb.io.test_en_i := io.te
  io.clk_o        := bb.io.clk_o
}
