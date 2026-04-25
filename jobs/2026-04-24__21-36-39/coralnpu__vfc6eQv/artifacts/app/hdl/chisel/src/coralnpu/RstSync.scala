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

/** BlackBox wrapper for RstSync.sv.
  *
  * The SystemVerilog cell signature:
  * {{{
  *   module RstSync #(parameter int STAGES = 2) (
  *     input  logic clk_i,
  *     input  logic rst_ni,
  *     output logic rst_no
  *   );
  * }}}
  *
  * Usage:
  * {{{
  *   val rst = Module(new RstSync)
  *   rst.io.clk_i  := clock
  *   rst.io.rst_ni := asyncRstN
  *   val syncRstN  = rst.io.rst_no
  * }}}
  */
class RstSync extends BlackBox with HasBlackBoxPath {
  val io = IO(new Bundle {
    val clk_i  = Input(Clock())
    val rst_ni = Input(Bool())
    val rst_no = Output(Bool())
  })
}
