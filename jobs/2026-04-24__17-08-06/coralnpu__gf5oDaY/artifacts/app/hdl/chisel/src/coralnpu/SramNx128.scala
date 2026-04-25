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

/**
 * 128-bit wide SRAM wrapper.
 * Adapts an SRAM to a DataBusPort interface.
 */
class SramNx128(p: Parameters, depth: Int) extends Module {
  val addrBits = log2Ceil(depth)

  val io = IO(new Bundle {
    val port = Flipped(new DataBusPort)
    val busy = Output(Bool())
  })

  val mem = SyncReadMem(depth, UInt(128.W))

  io.port.readDataAddr.ready  := true.B
  io.port.writeDataAddr.ready := true.B

  val rdValid = RegInit(false.B)
  val rdData  = RegInit(0.U(32.W))

  rdValid := io.port.readDataAddr.valid

  when(io.port.readDataAddr.valid) {
    val addr = io.port.readDataAddr.bits(addrBits + 3, 4)  // 16-byte aligned
    rdData := mem.read(addr)(31, 0)
  }

  io.port.readData.valid := rdValid
  io.port.readData.bits  := rdData

  when(io.port.writeDataAddr.valid) {
    val addr = io.port.writeDataAddr.bits(addrBits + 3, 4)
    mem.write(addr, io.port.writeDataBits)
  }

  io.busy := false.B
}
