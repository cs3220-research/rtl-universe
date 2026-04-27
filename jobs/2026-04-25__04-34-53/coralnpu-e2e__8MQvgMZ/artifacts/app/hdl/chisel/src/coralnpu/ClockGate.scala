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

/** BlackBox referencing the external ClockGate.sv Verilog module.
  *
  * The Verilog implementation supports two modes:
  *   - `USE_GENERIC` defined: latch-based integrated clock gate (simulation).
  *   - otherwise: instantiates vendor library cell CKLNQD10BWP6T20P96CPDLVT.
  */
class ClockGate extends BlackBox {
  val io = IO(new Bundle {
    val clk_i  = Input(Clock())
    val enable = Input(Bool())
    val te     = Input(Bool())
    val clk_o  = Output(Clock())
  })
}
