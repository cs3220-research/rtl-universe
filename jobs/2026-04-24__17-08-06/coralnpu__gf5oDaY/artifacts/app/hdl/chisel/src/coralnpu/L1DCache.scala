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

/** Stub L1 Data Cache module. */
class L1DCache(p: Parameters) extends Module {
  val io = IO(new Bundle {
    val dbus  = new DBusInterface(p)
    val port  = new DataBusPort
    val flush = Input(Bool())
    val busy  = Output(Bool())
  })

  // Pass-through stub: always ready, no caching
  io.dbus.ready := false.B
  io.port.readDataAddr.valid := false.B
  io.port.readDataAddr.bits  := 0.U
  io.port.writeDataAddr.valid := false.B
  io.port.writeDataAddr.bits  := 0.U
  io.port.writeDataBits := 0.U
  io.port.writeDataStrb := 0.U
  io.busy := false.B
}

/** Stub L1 Data Cache Bank module. */
class L1DCacheBank(p: Parameters) extends Module {
  val io = IO(new Bundle {
    val dbus  = new DBusInterface(p)
    val port  = new DataBusPort
    val flush = Input(Bool())
    val busy  = Output(Bool())
  })

  io.dbus.ready := false.B
  io.port.readDataAddr.valid := false.B
  io.port.readDataAddr.bits  := 0.U
  io.port.writeDataAddr.valid := false.B
  io.port.writeDataAddr.bits  := 0.U
  io.port.writeDataBits := 0.U
  io.port.writeDataStrb := 0.U
  io.busy := false.B
}
