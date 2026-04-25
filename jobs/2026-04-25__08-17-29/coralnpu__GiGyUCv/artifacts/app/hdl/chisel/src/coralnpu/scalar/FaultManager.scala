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

/** Fault manager stub: tracks and reports hardware faults. */
class FaultManager(p: Parameters) extends Module {
  val io = IO(new Bundle {
    val fault     = Output(Bool())
    val faultCode = Output(UInt(8.W))
    val report    = Input(Valid(UInt(8.W)))
  })

  val faultReg = RegInit(false.B)
  val codeReg  = RegInit(0.U(8.W))

  when(io.report.valid) {
    faultReg := true.B
    codeReg  := io.report.bits
  }

  io.fault     := faultReg
  io.faultCode := codeReg
}
