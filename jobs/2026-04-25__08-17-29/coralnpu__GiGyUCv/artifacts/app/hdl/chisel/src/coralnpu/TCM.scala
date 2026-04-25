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

/** Tightly Coupled Memory (TCM) stub.
  *
  * A simple synchronous memory connected to the Fabric port interface.
  */
class ITCM(p: Parameters) extends Module {
  val io = IO(Flipped(new FabricPort(p)))

  val depth = p.itcmSizeBytes / (p.lsuDataBits / 8)
  val mem   = SyncReadMem(depth, UInt(p.lsuDataBits.W))

  io.readDataAddr.ready  := true.B
  io.writeDataAddr.ready := true.B
  io.readData.valid      := RegNext(io.readDataAddr.valid, false.B)
  io.readData.bits       := mem.read(io.readDataAddr.bits >> log2Ceil(p.lsuDataBits / 8),
                                     io.readDataAddr.valid)

  when(io.writeDataAddr.valid) {
    mem.write(io.writeDataAddr.bits >> log2Ceil(p.lsuDataBits / 8), io.writeDataBits)
  }
}

class DTCM(p: Parameters) extends Module {
  val io = IO(Flipped(new FabricPort(p)))

  val depth = p.dtcmSizeBytes / (p.lsuDataBits / 8)
  val mem   = SyncReadMem(depth, UInt(p.lsuDataBits.W))

  io.readDataAddr.ready  := true.B
  io.writeDataAddr.ready := true.B
  io.readData.valid      := RegNext(io.readDataAddr.valid, false.B)
  io.readData.bits       := mem.read(io.readDataAddr.bits >> log2Ceil(p.lsuDataBits / 8),
                                     io.readDataAddr.valid)

  when(io.writeDataAddr.valid) {
    mem.write(io.writeDataAddr.bits >> log2Ceil(p.lsuDataBits / 8), io.writeDataBits)
  }
}
