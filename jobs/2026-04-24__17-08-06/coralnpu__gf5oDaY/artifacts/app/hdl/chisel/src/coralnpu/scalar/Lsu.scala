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

/** Load/Store Unit stub. */
class Lsu(p: Parameters) extends Module {
  val addrWidth = log2Ceil(p.nRegs)

  val io = IO(new Bundle {
    val req = Flipped(Valid(new Bundle {
      val addr   = UInt(addrWidth.W)
      val op     = AluOp()
      val base   = UInt(p.addrBits.W)
      val offset = SInt(12.W)
      val wdata  = UInt(p.xlen.W)
    }))
    val rd    = Valid(new Bundle {
      val addr = UInt(addrWidth.W)
      val data = UInt(p.xlen.W)
    })
    val dbus  = new DBusInterface(p)
    val fault = Output(Bool())
  })

  io.rd.valid      := false.B
  io.rd.bits.addr  := 0.U
  io.rd.bits.data  := 0.U
  io.dbus.valid    := false.B
  io.dbus.addr     := 0.U
  io.dbus.write    := false.B
  io.dbus.wdata    := 0.U
  io.dbus.wmask    := 0.U
  io.dbus.size     := 4.U
  io.fault         := false.B
}
