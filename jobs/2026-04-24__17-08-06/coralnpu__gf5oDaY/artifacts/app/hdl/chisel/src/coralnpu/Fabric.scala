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
 * FabricArbiter: n-to-1 arbiter for the data bus fabric.
 *
 * Priority: source(0) has highest priority, source(n-1) has lowest.
 * fabricBusy(i) = true when a higher-priority source is using the bus.
 *
 * Read responses are broadcast to all sources.
 */
class FabricArbiter(p: Parameters, n: Int = 2) extends Module {
  val io = IO(new Bundle {
    val source      = Vec(n, Flipped(new DataBusPort))
    val port        = new DataBusPort
    val fabricBusy  = Output(Vec(n, Bool()))
  })

  // Determine which source wins (priority: lower index = higher priority)
  // A source is "active" if it has a read or write request.
  val sourceActive = VecInit(io.source.map(s => s.readDataAddr.valid || s.writeDataAddr.valid))

  // Winner: highest priority active source
  val winner = Wire(UInt(log2Ceil(n + 1).W))
  winner := n.U  // no winner
  for (i <- (n - 1) to 0 by -1) {
    when(sourceActive(i)) {
      winner := i.U
    }
  }
  val anyWinner = winner < n.U

  // fabricBusy(i) = a higher priority source (lower index) is active
  for (i <- 0 until n) {
    val higherPriorityActive = (0 until i).map(j => sourceActive(j)).foldLeft(false.B)(_ || _)
    io.fabricBusy(i) := higherPriorityActive
  }

  // Mux the winning source to the port
  // Defaults
  io.port.readDataAddr.valid  := false.B
  io.port.readDataAddr.bits   := 0.U
  io.port.writeDataAddr.valid := false.B
  io.port.writeDataAddr.bits  := 0.U
  io.port.writeDataBits       := 0.U
  io.port.writeDataStrb       := 0.U

  // Source ready signals: always ready (combinational pass-through)
  for (i <- 0 until n) {
    io.source(i).readDataAddr.ready  := (winner === i.U) && io.port.readDataAddr.ready
    io.source(i).writeDataAddr.ready := (winner === i.U) && io.port.writeDataAddr.ready
  }

  when(anyWinner) {
    val sel = winner
    // Forward the winning source's request to the port
    io.port.readDataAddr.valid  := io.source(sel).readDataAddr.valid
    io.port.readDataAddr.bits   := io.source(sel).readDataAddr.bits
    io.port.writeDataAddr.valid := io.source(sel).writeDataAddr.valid
    io.port.writeDataAddr.bits  := io.source(sel).writeDataAddr.bits
    io.port.writeDataBits       := io.source(sel).writeDataBits
    io.port.writeDataStrb       := io.source(sel).writeDataStrb
  }

  // Broadcast read response to all sources
  for (i <- 0 until n) {
    io.source(i).readData.valid := io.port.readData.valid
    io.source(i).readData.bits  := io.port.readData.bits
  }

}


/**
 * FabricMux: 1-to-n demultiplexer.
 *
 * Routes a single DataBusPort to one of several output ports based on address.
 * The io.source is the Flipped (from the perspective of the slave).
 * io.ports are the output DataBusPorts (to each memory region).
 */
class FabricMux(p: Parameters, memoryRegions: Seq[MemoryRegion]) extends Module {
  val nRegions = memoryRegions.length

  val io = IO(new Bundle {
    val source      = Flipped(new DataBusPort)
    val ports       = Vec(nRegions, new DataBusPort)
    val periBusy    = Input(Vec(nRegions, Bool()))
    val fabricBusy  = Output(Bool())
  })

  // Find which region the address belongs to
  def findRegion(addr: UInt): (Vec[Bool], Vec[UInt]) = {
    val matches = VecInit(memoryRegions.map { r =>
      (addr >= r.base.U) && (addr < (r.base + r.size).U)
    })
    val offsets = VecInit(memoryRegions.map { r =>
      addr - r.base.U
    })
    (matches, offsets)
  }

  // Default outputs
  io.fabricBusy := io.periBusy.reduce(_ || _)

  for (j <- 0 until nRegions) {
    io.ports(j).readDataAddr.valid  := false.B
    io.ports(j).readDataAddr.bits   := 0.U
    io.ports(j).writeDataAddr.valid := false.B
    io.ports(j).writeDataAddr.bits  := 0.U
    io.ports(j).writeDataBits       := 0.U
    io.ports(j).writeDataStrb       := 0.U
  }

  // Source ready signals
  io.source.readDataAddr.ready  := true.B
  io.source.writeDataAddr.ready := true.B

  // Route write
  when(io.source.writeDataAddr.valid) {
    val (matches, offsets) = findRegion(io.source.writeDataAddr.bits)
    for (j <- 0 until nRegions) {
      when(matches(j)) {
        io.ports(j).writeDataAddr.valid := true.B
        io.ports(j).writeDataAddr.bits  := offsets(j)
        io.ports(j).writeDataBits       := io.source.writeDataBits
        io.ports(j).writeDataStrb       := io.source.writeDataStrb
      }
    }
  }

  // Route read request
  when(io.source.readDataAddr.valid) {
    val (matches, offsets) = findRegion(io.source.readDataAddr.bits)
    for (j <- 0 until nRegions) {
      when(matches(j)) {
        io.ports(j).readDataAddr.valid := true.B
        io.ports(j).readDataAddr.bits  := offsets(j)
      }
    }
  }

  // Mux read response: pick whichever port has a valid response
  io.source.readData.valid := false.B
  io.source.readData.bits  := 0.U
  for (j <- 0 until nRegions) {
    when(io.ports(j).readData.valid) {
      io.source.readData.valid := true.B
      io.source.readData.bits  := io.ports(j).readData.bits
    }
  }

}
