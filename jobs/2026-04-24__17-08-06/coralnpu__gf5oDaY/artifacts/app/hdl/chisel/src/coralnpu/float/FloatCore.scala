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

package coralnpu.float

import chisel3._
import chisel3.util._
import coralnpu.{Parameters, DBusInterface}

/** FloatCore stub - wraps the FPU and floating-point register file. */
class FloatCore(p: Parameters) extends Module {
  val io = IO(new Bundle {
    val cmd    = Flipped(Decoupled(new FloatCoreCmd(p)))
    val result = Valid(new FloatCoreResult(p))
    val dbus   = new DBusInterface(p)
    val halted = Output(Bool())
    val fault  = Output(Bool())
  })

  io.cmd.ready       := false.B
  io.result.valid    := false.B
  io.result.bits     := 0.U.asTypeOf(new FloatCoreResult(p))
  io.dbus.valid      := false.B
  io.dbus.addr       := 0.U
  io.dbus.write      := false.B
  io.dbus.wdata      := 0.U
  io.dbus.wmask      := 0.U
  io.dbus.size       := 4.U
  io.halted          := false.B
  io.fault           := false.B
}
