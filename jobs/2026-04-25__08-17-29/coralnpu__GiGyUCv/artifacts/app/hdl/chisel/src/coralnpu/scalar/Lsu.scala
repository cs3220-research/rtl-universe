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

object LsuOp extends ChiselEnum {
  val LB, LH, LW, LBU, LHU = Value
  val SB, SH, SW            = Value
}

class LsuRequest(p: Parameters) extends Bundle {
  val addr = UInt(5.W)
  val op   = LsuOp()
  val imm  = SInt(32.W)
}

/** Load/Store Unit (LSU) stub. */
class Lsu(p: Parameters) extends Module {
  val io = IO(new Bundle {
    val req  = Flipped(Valid(new LsuRequest(p)))
    val rs1  = Input(new RegSource(p))
    val rs2  = Input(new RegSource(p))
    val dbus = new DBusIO(p)
    val rd   = Valid(new RegData(p))
    val busy = Output(Bool())
  })

  io.dbus.valid := false.B
  io.dbus.addr  := 0.U
  io.dbus.write := false.B
  io.dbus.wdata := 0.U
  io.dbus.wmask := 0.U
  io.dbus.size  := 0.U

  io.rd.valid := false.B
  io.rd.bits  := 0.U.asTypeOf(new RegData(p))
  io.busy     := false.B
}
