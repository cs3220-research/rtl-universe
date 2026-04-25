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
import chisel3.util.HasBlackBoxResource

/** Reset synchronizer (BlackBox wrapper for RstSync.sv).
  *
  * Synchronises an asynchronous reset to the rising edge of clk_i using a
  * two-flop synchroniser with a clock-gated enable.
  */
class RstSync extends BlackBox with HasBlackBoxResource {
  val io = IO(new Bundle {
    val clk_i  = Input(Clock())
    val rst_ni = Input(AsyncReset())
    val en_i   = Input(Bool())
    val rst_no = Output(AsyncReset())
  })

  addResource("RstSync.sv")
}
