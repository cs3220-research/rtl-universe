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

/**
 * AXI Slave: converts AXI transactions to DataBusPort transactions.
 */
class AxiSlave(p: Parameters) extends Module {
  val io = IO(new Bundle {
    val axi  = Flipped(new AxiDataInterface(p))
    val port = new DataBusPort
    val busy = Input(Bool())
  })

  // Read path
  io.port.readDataAddr.valid := io.axi.read.addr.valid
  io.port.readDataAddr.bits  := io.axi.read.addr.bits.addr
  io.axi.read.addr.ready     := io.port.readDataAddr.ready

  io.axi.read.data.valid         := io.port.readData.valid
  io.axi.read.data.bits.id       := 0.U
  io.axi.read.data.bits.data     := io.port.readData.bits
  io.axi.read.data.bits.resp     := 0.U
  io.axi.read.data.bits.last     := true.B
  io.port.readData.valid         := false.B  // readData is input to port
  io.port.readData.bits          := 0.U

  // Write path
  io.port.writeDataAddr.valid := io.axi.write.addr.valid
  io.port.writeDataAddr.bits  := io.axi.write.addr.bits.addr
  io.axi.write.addr.ready     := io.port.writeDataAddr.ready

  io.port.writeDataBits := io.axi.write.data.bits.data
  io.port.writeDataStrb := io.axi.write.data.bits.strb

  io.axi.write.data.ready := true.B

  // Write response
  io.axi.write.resp.valid     := RegNext(io.axi.write.addr.valid && io.axi.write.data.valid)
  io.axi.write.resp.bits.id   := 0.U
  io.axi.write.resp.bits.resp := 0.U
}
