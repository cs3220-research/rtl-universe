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

/** RVVI (RISC-V Verification Interface) trace module.
  *
  * Outputs a trace of retired instructions compatible with the RVVI
  * protocol.  When `enableVerification` is false the module is a no-op
  * (all outputs are driven to zero).
  *
  * The actual RVVI protocol implementation is provided by the external
  * RVVI Verilog library (referenced from the BUILD file); this Chisel
  * wrapper provides the connection points used by SCore.
  */
class RvviTrace(p: Parameters) extends Module {

  val io = IO(new Bundle {
    // Retired instruction feed from the CPU pipeline
    val retire = Flipped(Valid(new Bundle {
      val pc   = UInt(32.W)
      val inst = UInt(32.W)
      val trap = Bool()
    }))
    // Register file read-back for RVVI tracing
    val regfile = Vec(32, Input(UInt(32.W)))
    // RVVI trace valid pulse
    val valid   = Output(Bool())
  })

  // No-op stub: RVVI tracing disabled in RTL (enabled externally via DPI)
  io.valid := false.B
}
