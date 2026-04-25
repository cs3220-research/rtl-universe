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

// Latches and reports the first fault seen.
// Once a fault is latched it stays asserted until reset.
class FaultManager(p: Parameters) extends Module {
  val io = IO(new Bundle {
    val fault        = Output(Bool())
    val cause        = Output(UInt(32.W))
    val report_fault = Input(Bool())
    val fault_cause  = Input(UInt(32.W))
  })

  val faultReg = RegInit(false.B)
  val causeReg = RegInit(0.U(32.W))

  when (io.report_fault && !faultReg) {
    faultReg := true.B
    causeReg := io.fault_cause
  }

  io.fault := faultReg
  io.cause := causeReg
}
