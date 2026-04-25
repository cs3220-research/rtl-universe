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

/** RISC-V Debug Module stub.
  *
  * Provides a minimal interface placeholder for the debug transport layer.
  * Full RISC-V debug spec (0.13) support is out of scope for the initial
  * implementation.
  */
class Debug(p: Parameters) extends Module {
  val io = IO(new Bundle {
    // Debug halt/resume control
    val haltReq   = Input(Bool())   // request core to halt
    val halted    = Output(Bool())  // core is halted
    val resumeReq = Input(Bool())   // request core to resume
    val running   = Output(Bool())  // core is running

    // Abstract command interface (simple register access)
    val cmdValid  = Input(Bool())
    val cmdWrite  = Input(Bool())
    val cmdAddr   = Input(UInt(16.W))
    val cmdWData  = Input(UInt(32.W))
    val cmdRData  = Output(UInt(32.W))
    val cmdDone   = Output(Bool())
  })

  // Minimal stub implementation
  val haltedReg  = RegInit(false.B)
  val runningReg = RegInit(true.B)

  when(io.haltReq) {
    haltedReg  := true.B
    runningReg := false.B
  }.elsewhen(io.resumeReq) {
    haltedReg  := false.B
    runningReg := true.B
  }

  io.halted   := haltedReg
  io.running  := runningReg
  io.cmdRData := 0.U
  io.cmdDone  := io.cmdValid  // immediate acknowledge (stub)
}
