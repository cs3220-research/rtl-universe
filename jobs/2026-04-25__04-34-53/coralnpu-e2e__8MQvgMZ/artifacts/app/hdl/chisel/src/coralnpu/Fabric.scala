// Copyright 2025 Google LLC
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

/** N-to-1 priority arbiter for the internal fabric.
  *
  * source(0) has the highest priority.  fabricBusy(i) is asserted when any
  * source with a lower index (higher priority) has an active read or write
  * request.
  *
  * Read response data (port.readData) is broadcast to *all* sources regardless
  * of which one issued the request.
  *
  * @param p  Design parameters.
  * @param n  Number of requester ports (default 2).
  */
class FabricArbiter(p: Parameters, n: Int = 2) extends Module {
  val io = IO(new Bundle {
    val source     = Vec(n, Flipped(new FabricPort(p.lsuDataBits)))
    val port       = new FabricPort(p.lsuDataBits)
    val fabricBusy = Vec(n, Output(Bool()))
  })

  // -------------------------------------------------------------------------
  // Determine which source is active (read or write)
  // -------------------------------------------------------------------------
  val sourceActive = Wire(Vec(n, Bool()))
  for (i <- 0 until n) {
    sourceActive(i) := io.source(i).readDataAddr.valid || io.source(i).writeDataAddr.valid
  }

  // Priority select: find the highest-priority (lowest index) active source.
  // Expressed as a one-hot vector for clarity.
  val grant = Wire(Vec(n, Bool()))
  for (i <- 0 until n) {
    val higherActive = (0 until i).map(j => sourceActive(j)).foldLeft(false.B)(_ || _)
    grant(i) := sourceActive(i) && !higherActive
  }

  // fabricBusy(i) = any higher-priority source is active
  for (i <- 0 until n) {
    val higherActive = (0 until i).map(j => sourceActive(j)).foldLeft(false.B)(_ || _)
    io.fabricBusy(i) := higherActive
  }

  // -------------------------------------------------------------------------
  // Mux the winning source onto the output port
  // -------------------------------------------------------------------------
  // Default: no request
  io.port.readDataAddr.valid        := false.B
  io.port.readDataAddr.bits         := 0.U
  io.port.writeDataAddr.valid       := false.B
  io.port.writeDataAddr.bits        := 0.U
  io.port.writeDataBits             := 0.U
  io.port.writeDataStrb             := 0.U

  for (i <- n - 1 to 0 by -1) {  // lower index overwrites higher (priority)
    when(grant(i)) {
      io.port.readDataAddr.valid  := io.source(i).readDataAddr.valid
      io.port.readDataAddr.bits   := io.source(i).readDataAddr.bits
      io.port.writeDataAddr.valid := io.source(i).writeDataAddr.valid
      io.port.writeDataAddr.bits  := io.source(i).writeDataAddr.bits
      io.port.writeDataBits       := io.source(i).writeDataBits
      io.port.writeDataStrb       := io.source(i).writeDataStrb
    }
  }

  // -------------------------------------------------------------------------
  // Back-pressure: source readDataAddr.ready / writeDataAddr.ready
  // -------------------------------------------------------------------------
  for (i <- 0 until n) {
    io.source(i).readDataAddr.ready  := io.port.readDataAddr.ready  && grant(i)
    io.source(i).writeDataAddr.ready := io.port.writeDataAddr.ready && grant(i)
  }

  // -------------------------------------------------------------------------
  // Broadcast read-response data to all sources
  // -------------------------------------------------------------------------
  for (i <- 0 until n) {
    io.source(i).readData.valid := io.port.readData.valid
    io.source(i).readData.bits  := io.port.readData.bits
  }
}

/** 1-to-N address-decode mux for the internal fabric.
  *
  * Decodes the incoming address against a list of `MemoryRegion` descriptors
  * and routes the transaction to the matching output port.  The address
  * forwarded on the output port has the region base subtracted so that each
  * downstream peripheral sees a zero-based address.
  *
  * fabricBusy is the OR of the `periBusy` inputs for any currently selected
  * region.
  *
  * @param p        Design parameters.
  * @param regions  Sequence of memory-region descriptors (in any order).
  */
class FabricMux(p: Parameters, regions: Seq[MemoryRegion]) extends Module {
  val nRegions = regions.length

  val io = IO(new Bundle {
    val source     = Flipped(new FabricPort(p.lsuDataBits))
    val ports      = Vec(nRegions, new FabricPort(p.lsuDataBits))
    val periBusy   = Vec(nRegions, Input(Bool()))
    val fabricBusy = Output(Bool())
  })

  // -------------------------------------------------------------------------
  // Address decode
  // -------------------------------------------------------------------------
  val readAddr  = io.source.readDataAddr.bits
  val writeAddr = io.source.writeDataAddr.bits

  // For each region: does the incoming address fall inside it?
  val readHit  = Wire(Vec(nRegions, Bool()))
  val writeHit = Wire(Vec(nRegions, Bool()))
  for (i <- 0 until nRegions) {
    val base = regions(i).base.U(p.addrBits.W)
    val size = regions(i).size.U(p.addrBits.W)
    readHit(i)  := io.source.readDataAddr.valid  && (readAddr  >= base) && (readAddr  < base + size)
    writeHit(i) := io.source.writeDataAddr.valid && (writeAddr >= base) && (writeAddr < base + size)
  }

  // -------------------------------------------------------------------------
  // Route requests to the matching port
  // -------------------------------------------------------------------------
  // Default tie-offs for all output ports
  for (i <- 0 until nRegions) {
    io.ports(i).readDataAddr.valid  := false.B
    io.ports(i).readDataAddr.bits   := 0.U
    io.ports(i).writeDataAddr.valid := false.B
    io.ports(i).writeDataAddr.bits  := 0.U
    io.ports(i).writeDataBits       := io.source.writeDataBits
    io.ports(i).writeDataStrb       := io.source.writeDataStrb
  }

  for (i <- 0 until nRegions) {
    val base = regions(i).base.U(p.addrBits.W)
    when(readHit(i)) {
      io.ports(i).readDataAddr.valid := true.B
      io.ports(i).readDataAddr.bits  := readAddr - base
    }
    when(writeHit(i)) {
      io.ports(i).writeDataAddr.valid := true.B
      io.ports(i).writeDataAddr.bits  := writeAddr - base
    }
  }

  // -------------------------------------------------------------------------
  // Back-pressure from ports toward source
  // -------------------------------------------------------------------------
  val readReadyVec  = Wire(Vec(nRegions, Bool()))
  val writeReadyVec = Wire(Vec(nRegions, Bool()))
  for (i <- 0 until nRegions) {
    readReadyVec(i)  := io.ports(i).readDataAddr.ready  && readHit(i)
    writeReadyVec(i) := io.ports(i).writeDataAddr.ready && writeHit(i)
  }
  io.source.readDataAddr.ready  := readReadyVec.reduce(_ || _)
  io.source.writeDataAddr.ready := writeReadyVec.reduce(_ || _)

  // -------------------------------------------------------------------------
  // Return read data: mux the valid response back to source
  // -------------------------------------------------------------------------
  io.source.readData.valid := false.B
  io.source.readData.bits  := 0.U
  for (i <- 0 until nRegions) {
    when(io.ports(i).readData.valid) {
      io.source.readData.valid := true.B
      io.source.readData.bits  := io.ports(i).readData.bits
    }
  }

  // -------------------------------------------------------------------------
  // fabricBusy: OR of periBusy for any currently-addressed region
  // -------------------------------------------------------------------------
  val periBusyActive = Wire(Vec(nRegions, Bool()))
  for (i <- 0 until nRegions) {
    periBusyActive(i) := io.periBusy(i) && (readHit(i) || writeHit(i))
  }
  io.fabricBusy := periBusyActive.reduce(_ || _)
}
