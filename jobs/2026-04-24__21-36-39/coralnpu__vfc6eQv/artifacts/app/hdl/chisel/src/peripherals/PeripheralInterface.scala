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

package peripheral

import chisel3._
import chisel3.util._
import bus._

/**
  * ConnectAxiRead: helper to create a combinational AXI read slave from a register map.
  *
  * @param addrBits  Number of address bits in the AXI read channel
  * @param readMap   Map from register name to (byte_offset, data_signal)
  * @return          A wire bundle compatible with AxiReadIO (from the slave perspective)
  *
  * Usage:
  *   io.axi.read <> ConnectAxiRead(addrBits, readMap)
  *
  * where io.axi = Flipped(AxiMasterIO(...)) so io.axi.read receives addr from master
  * and drives data back to master.
  */
object ConnectAxiRead {
  def apply(addrBits: Int, readMap: Map[String, (Int, UInt)]): AxiReadIO = {
    // Create a wire of the master-facing read type
    // (the caller will use <> to connect the flipped slave version)
    val dataBits = 32
    val idBits   = addrBits  // reuse addrBits as a proxy for id bits width

    val wire = Wire(Flipped(new AxiReadIO(32, dataBits, idBits)))

    // Always ready to accept read addresses
    wire.addr.ready := true.B

    // Combinational read response (1-cycle latency)
    // When addr is valid, compute data based on address
    val addrVal  = wire.addr.bits.addr
    val respData = Wire(UInt(dataBits.W))
    val respResp = Wire(UInt(2.W))
    respData := 0.U
    respResp := 2.U  // SLVERR by default (invalid address)

    for ((name, (offset, data)) <- readMap) {
      when(addrVal === offset.U) {
        respData := data
        respResp := 0.U  // OKAY
      }
    }

    // Register the response for 1-cycle latency
    val dataValid = RegInit(false.B)
    val dataReg   = RegInit(0.U(dataBits.W))
    val respReg   = RegInit(0.U(2.W))
    val idReg     = RegInit(0.U(idBits.W))
    val lastReg   = RegInit(false.B)

    when(wire.addr.valid) {
      dataValid := true.B
      dataReg   := respData
      respReg   := respResp
      idReg     := wire.addr.bits.id
      lastReg   := true.B
    }.elsewhen(wire.data.ready && dataValid) {
      dataValid := false.B
    }

    wire.data.valid       := dataValid
    wire.data.bits.data   := dataReg
    wire.data.bits.resp   := respReg
    wire.data.bits.id     := idReg
    wire.data.bits.last   := lastReg

    // Return the non-flipped version for <> connection
    // The caller does: io.axi.read <> ConnectAxiRead(...)
    // io.axi.read is Flipped(AxiReadIO) so it's the slave side
    // wire is Flipped(AxiReadIO) so it matches
    wire
  }
}

/**
  * ConnectAxiWrite: helper to create a combinational AXI write slave from a register map.
  *
  * @param addrBits  Number of address bits
  * @param writeMap  Map from register name to byte_offset
  * @param axiBus    The AXI write IO (from slave perspective = Flipped AxiWriteIO)
  * @return          (writes: Map[String, Bool], writeData: UInt(32.W))
  *                  writes("name") is true for one cycle when that register is written
  *                  writeData is the data word being written
  *
  * Usage:
  *   val (writes, writeData) = ConnectAxiWrite(addrBits, writeMap, io.axi.write)
  */
object ConnectAxiWrite {
  def apply(
      addrBits: Int,
      writeMap: Map[String, Int],
      axiBus: AxiWriteIO
  ): (Map[String, Bool], UInt) = {
    val dataBits = 32
    val idBits   = addrBits

    // Accept addr and data simultaneously
    val bothValid = axiBus.addr.valid && axiBus.data.valid

    // Always ready when both addr and data are present
    axiBus.addr.ready := bothValid
    axiBus.data.ready := bothValid

    // Decode address to register name
    val addrVal  = axiBus.addr.bits.addr
    val dataVal  = axiBus.data.bits.data(dataBits - 1, 0)
    val isValid  = bothValid

    // Build write strobes
    val writesMap = writeMap.map { case (name, offset) =>
      name -> (isValid && addrVal === offset.U)
    }

    // Write response (1-cycle registered)
    val respValid = RegInit(false.B)
    val respResp  = RegInit(0.U(2.W))
    val respId    = RegInit(0.U(idBits.W))

    val addrMatch = Wire(Bool())
    addrMatch := false.B
    for ((_, offset) <- writeMap) {
      when(addrVal === offset.U) { addrMatch := true.B }
    }

    when(bothValid) {
      respValid := true.B
      respResp  := Mux(addrMatch, 0.U, 2.U)  // OKAY or SLVERR
      respId    := axiBus.addr.bits.id
    }.elsewhen(axiBus.resp.ready && respValid) {
      respValid := false.B
    }

    axiBus.resp.valid       := respValid
    axiBus.resp.bits.resp   := respResp
    axiBus.resp.bits.id     := respId

    (writesMap, dataVal)
  }
}
