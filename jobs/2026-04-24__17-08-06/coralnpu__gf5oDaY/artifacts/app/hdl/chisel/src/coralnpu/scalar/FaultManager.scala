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

/** Fault manager stub. */
class FaultManager(p: Parameters) extends Module {
  val io = IO(new Bundle {
    val fault    = Output(Bool())
    val faultPC  = Output(UInt(p.addrBits.W))
    val trigger  = Input(Bool())
    val pc       = Input(UInt(p.addrBits.W))
  })

  val faultReg  = RegInit(false.B)
  val faultPCReg = RegInit(0.U(p.addrBits.W))

  when(io.trigger && !faultReg) {
    faultReg   := true.B
    faultPCReg := io.pc
  }

  io.fault   := faultReg
  io.faultPC := faultPCReg
}
