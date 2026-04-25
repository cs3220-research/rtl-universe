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

/** FabricArbiter: n-to-1 priority arbiter for the memory fabric.
  *
  * source(0) has highest priority; source(n-1) has lowest priority.
  * The winning source's request is forwarded to `port`.
  * fabricBusy(i) = true when a higher-priority source (0..i-1) holds the bus.
  * Read responses are broadcast to all sources (each source can filter by
  * checking its own request state).
  */
class FabricArbiter(p: Parameters, n: Int = 2) extends Module {
  val io = IO(new Bundle {
    val source      = Vec(n, Flipped(new FabricPort(p)))
    val port        = new FabricPort(p)
    val fabricBusy  = Output(Vec(n, Bool()))
  })

  // ------------------------------------------------------------------
  // Priority encode: highest-priority active source wins
  // ------------------------------------------------------------------
  // A source is "requesting" if it has a valid read or write address.
  val requesting = VecInit(io.source.map(s =>
    s.readDataAddr.valid || s.writeDataAddr.valid
  ))

  // Winner = lowest-index requesting source
  val winnerIdx = PriorityEncoder(requesting)
  val anyReq    = requesting.reduce(_ || _)

  // fabricBusy(i): true if a source j < i is requesting
  for (i <- 0 until n) {
    io.fabricBusy(i) := (0 until i).map(j => requesting(j)).fold(false.B)(_ || _)
  }

  // ------------------------------------------------------------------
  // Forward winner's request to port
  // ------------------------------------------------------------------
  // Read address
  io.port.readDataAddr.valid := anyReq && io.source(winnerIdx).readDataAddr.valid
  io.port.readDataAddr.bits  :=
    Mux(anyReq, io.source(winnerIdx).readDataAddr.bits, 0.U)

  // Port ready back-pressure (not used by sources in this simple model)
  for (i <- 0 until n) {
    io.source(i).readDataAddr.ready  := true.B
    io.source(i).writeDataAddr.ready := true.B
  }

  // Write address
  io.port.writeDataAddr.valid := anyReq && io.source(winnerIdx).writeDataAddr.valid
  io.port.writeDataAddr.bits  :=
    Mux(anyReq, io.source(winnerIdx).writeDataAddr.bits, 0.U)

  // Write data
  io.port.writeDataBits  := Mux(anyReq, io.source(winnerIdx).writeDataBits,  0.U)
  io.port.writeDataStrb  := Mux(anyReq, io.source(winnerIdx).writeDataStrb,  0.U)

  // port.readDataAddr.ready not used upstream; port drives readData back
  io.port.readDataAddr.ready := true.B

  // ------------------------------------------------------------------
  // Broadcast read response to all sources
  // ------------------------------------------------------------------
  for (i <- 0 until n) {
    io.source(i).readData.valid := io.port.readData.valid
    io.source(i).readData.bits  := io.port.readData.bits
  }

  // Write response (not needed for this simple fabric)
  io.port.writeDataAddr.ready := true.B
}

/** FabricMux: 1-to-n address-based demultiplexer for the memory fabric.
  *
  * Routes a single source's read/write request to the appropriate downstream
  * port based on the memory region map.  The output address is translated to
  * a region-relative (offset) address.
  */
class FabricMux(p: Parameters, regions: Seq[MemoryRegion]) extends Module {
  private val n = regions.length

  val io = IO(new Bundle {
    val source     = Flipped(new FabricPort(p))
    val ports      = Vec(n, new FabricPort(p))
    val periBusy   = Input(Vec(n, Bool()))
    val fabricBusy = Output(Bool())
  })

  // ------------------------------------------------------------------
  // Address decode
  // ------------------------------------------------------------------
  // Which region contains the read address?
  val rdAddr = io.source.readDataAddr.bits
  val wrAddr = io.source.writeDataAddr.bits

  val rdMatch = VecInit(regions.zipWithIndex.map { case (r, _) =>
    rdAddr >= r.base.U && rdAddr < (r.base + r.size).U
  })
  val wrMatch = VecInit(regions.zipWithIndex.map { case (r, _) =>
    wrAddr >= r.base.U && wrAddr < (r.base + r.size).U
  })

  // Translated (region-relative) addresses
  val rdOffsets = VecInit(regions.zipWithIndex.map { case (r, _) =>
    rdAddr - r.base.U
  })
  val wrOffsets = VecInit(regions.zipWithIndex.map { case (r, _) =>
    wrAddr - r.base.U
  })

  val rdHit = rdMatch.reduce(_ || _)
  val wrHit = wrMatch.reduce(_ || _)

  // ------------------------------------------------------------------
  // Forward to ports
  // ------------------------------------------------------------------
  for (i <- 0 until n) {
    // Read
    io.ports(i).readDataAddr.valid := io.source.readDataAddr.valid && rdMatch(i) && rdHit
    io.ports(i).readDataAddr.bits  := rdOffsets(i)
    // Write
    io.ports(i).writeDataAddr.valid := io.source.writeDataAddr.valid && wrMatch(i) && wrHit
    io.ports(i).writeDataAddr.bits  := wrOffsets(i)
    io.ports(i).writeDataBits       := io.source.writeDataBits
    io.ports(i).writeDataStrb       := io.source.writeDataStrb
    // Back-pressure
    io.ports(i).readDataAddr.ready  := true.B
    io.ports(i).writeDataAddr.ready := true.B
  }

  io.source.readDataAddr.ready  := true.B
  io.source.writeDataAddr.ready := true.B

  // ------------------------------------------------------------------
  // Read response mux: pick the port that was addressed
  // ------------------------------------------------------------------
  // We store which port was addressed one cycle ago
  val rdMatchReg = RegNext(rdMatch, VecInit(Seq.fill(n)(false.B)))

  val rdDataValid = VecInit((0 until n).map(i =>
    io.ports(i).readData.valid && rdMatchReg(i)
  )).reduce(_ || _)

  val rdDataBits = MuxCase(0.U, (0 until n).map(i =>
    (io.ports(i).readData.valid && rdMatchReg(i)) -> io.ports(i).readData.bits
  ))

  io.source.readData.valid := rdDataValid
  io.source.readData.bits  := rdDataBits

  // ------------------------------------------------------------------
  // fabricBusy: any periBusy active for the current addressed port
  // ------------------------------------------------------------------
  val rdBusy = VecInit((0 until n).map(i =>
    io.source.readDataAddr.valid && rdMatch(i) && io.periBusy(i)
  )).reduce(_ || _)
  val wrBusy = VecInit((0 until n).map(i =>
    io.source.writeDataAddr.valid && wrMatch(i) && io.periBusy(i)
  )).reduce(_ || _)

  io.fabricBusy := rdBusy || wrBusy
}
