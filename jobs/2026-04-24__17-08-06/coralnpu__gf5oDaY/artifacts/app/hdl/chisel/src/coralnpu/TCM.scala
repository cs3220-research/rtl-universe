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
 * Tightly Coupled Memory (TCM) stub.
 *
 * Provides a simple DataBusPort interface to an internal SRAM.
 */
class TCM(p: Parameters, sizeKBytes: Int) extends Module {
  val sizeBytes = sizeKBytes * 1024
  val depth     = sizeBytes / 16  // 128-bit (16-byte) words
  val addrBits  = log2Ceil(sizeBytes)

  val io = IO(new Bundle {
    val port   = Flipped(new DataBusPort)
    val busy   = Output(Bool())
  })

  // Simple memory stub
  val mem = SyncReadMem(depth, UInt(128.W))

  io.port.readDataAddr.ready  := true.B
  io.port.writeDataAddr.ready := true.B

  // Read
  val rdValid = RegInit(false.B)
  val rdData  = RegInit(0.U(32.W))

  rdValid := io.port.readDataAddr.valid
  when(io.port.readDataAddr.valid) {
    rdData := mem.read(io.port.readDataAddr.bits >> 4)(31, 0)
  }

  io.port.readData.valid := rdValid
  io.port.readData.bits  := rdData

  // Write
  when(io.port.writeDataAddr.valid) {
    mem.write(io.port.writeDataAddr.bits >> 4, io.port.writeDataBits)
  }

  io.busy := false.B
}

/** Instruction TCM (ITCM) stub. */
class ITCM(p: Parameters) extends Module {
  val io = IO(new Bundle {
    val port  = Flipped(new DataBusPort)
    val ibus  = Flipped(new IBusInterface(p))
    val busy  = Output(Bool())
  })

  val sizeBytes = p.itcmSizeKBytes * 1024
  val depth     = sizeBytes / 16
  val mem       = SyncReadMem(depth, UInt(128.W))

  io.port.readDataAddr.ready  := true.B
  io.port.writeDataAddr.ready := true.B

  val rdValid = RegInit(false.B)
  rdValid := io.port.readDataAddr.valid
  io.port.readData.valid := rdValid
  io.port.readData.bits  := 0.U

  when(io.port.writeDataAddr.valid) {
    mem.write(io.port.writeDataAddr.bits >> 4, io.port.writeDataBits)
  }

  // IBus interface
  val ibusRdValid = RegInit(false.B)
  ibusRdValid := io.ibus.valid
  io.ibus.ready := ibusRdValid
  io.ibus.rdata := mem.read(io.ibus.addr >> 4)

  io.busy := false.B
}

/** Data TCM (DTCM) stub. */
class DTCM(p: Parameters) extends Module {
  val io = IO(new Bundle {
    val port = Flipped(new DataBusPort)
    val busy = Output(Bool())
  })

  val sizeBytes = p.dtcmSizeKBytes * 1024
  val depth     = sizeBytes / 16
  val mem       = SyncReadMem(depth, UInt(128.W))

  io.port.readDataAddr.ready  := true.B
  io.port.writeDataAddr.ready := true.B

  val rdValid = RegInit(false.B)
  rdValid := io.port.readDataAddr.valid
  io.port.readData.valid := rdValid
  io.port.readData.bits  := 0.U

  when(io.port.writeDataAddr.valid) {
    mem.write(io.port.writeDataAddr.bits >> 4, io.port.writeDataBits)
  }

  io.busy := false.B
}
