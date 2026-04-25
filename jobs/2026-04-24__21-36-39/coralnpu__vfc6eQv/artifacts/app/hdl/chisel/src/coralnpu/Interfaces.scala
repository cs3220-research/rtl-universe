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

// ============================================================================
// Memory fabric interfaces
// ============================================================================

/** Enumeration for memory region types. */
object MemoryRegionType extends Enumeration {
  val IMEM, DMEM, Peripheral = Value
}

/** Describes a contiguous memory region.
  *
  * @param base       Base address (byte-granular, physical address).
  * @param size       Region size in bytes.
  * @param regionType One of MemoryRegionType.{IMEM, DMEM, Peripheral}.
  */
class MemoryRegion(val base: Int, val size: Int, val regionType: MemoryRegionType.Value)

/** Internal fabric source-side port (as seen by the DUT).
  *
  * A "source" drives addresses and write-data into the DUT and receives
  * read-data back from the DUT.  From the DUT perspective:
  *   - readDataAddr, writeDataAddr, writeDataBits, writeDataStrb are INPUTS
  *     (the source drives them).
  *   - readData is an OUTPUT (the DUT returns data to the source).
  */
class FabricSourcePort(dataBits: Int = 32, addrBits: Int = 32) extends Bundle {
  /** Read-request address.  valid = request pending; bits = byte address. */
  val readDataAddr  = Flipped(Valid(UInt(addrBits.W)))
  /** Write-request address. valid = request pending; bits = byte address. */
  val writeDataAddr = Flipped(Valid(UInt(addrBits.W)))
  /** Write data (aligned to bus). */
  val writeDataBits = Input(UInt(dataBits.W))
  /** Write byte-enable strobe. */
  val writeDataStrb = Input(UInt((dataBits / 8).W))
  /** Read-data response from the fabric. */
  val readData      = Valid(UInt(dataBits.W))
}

/** Internal fabric output port (towards the memory / peripheral, as seen by DUT).
  *
  * The DUT drives addresses and write-data out, and receives read-data back.
  * From the DUT perspective:
  *   - readDataAddr, writeDataAddr, writeDataBits, writeDataStrb are OUTPUTS.
  *   - readData is an INPUT (the memory/peripheral returns data to the DUT).
  */
class FabricOutPort(dataBits: Int = 32, addrBits: Int = 32) extends Bundle {
  /** Read-request address forwarded to fabric. */
  val readDataAddr  = Valid(UInt(addrBits.W))
  /** Write-request address forwarded to fabric. */
  val writeDataAddr = Valid(UInt(addrBits.W))
  /** Write data forwarded to fabric. */
  val writeDataBits = Output(UInt(dataBits.W))
  /** Write byte-enable strobe forwarded to fabric. */
  val writeDataStrb = Output(UInt((dataBits / 8).W))
  /** Read-data response from fabric. */
  val readData      = Flipped(Valid(UInt(dataBits.W)))
}

// ============================================================================
// Fabric arbitration and multiplexing
// ============================================================================

/** N-to-1 fabric arbiter.
  *
  * Arbitrates between `n` source ports and forwards the highest-priority
  * (lowest index) active request to the single output port.  Read-data
  * from the output port is broadcast to all sources.
  *
  * Priority: source 0 > source 1 > … > source n-1.
  *
  * `fabricBusy(i)` is asserted when a lower-indexed source has an active
  * request, so source `i` must wait.
  */
class FabricArbiter(p: Parameters, n: Int = 2,
                    dataBits: Int = 32, addrBits: Int = 32) extends Module {
  val io = IO(new Bundle {
    val source     = Vec(n, new FabricSourcePort(dataBits, addrBits))
    val port       = new FabricOutPort(dataBits, addrBits)
    val fabricBusy = Output(Vec(n, Bool()))
  })

  // -------------------------------------------------------------------
  // Find the highest-priority (lowest index) active source.
  // -------------------------------------------------------------------
  val activeRead  = VecInit(io.source.map(s => s.readDataAddr.valid))
  val activeWrite = VecInit(io.source.map(s => s.writeDataAddr.valid))
  val activeAny   = VecInit((activeRead zip activeWrite).map { case (r, w) => r || w })

  // Winner index: lowest active source (-1 / "none" encoded as n)
  val winnerIdx = Wire(UInt((log2Ceil(n) + 1).W))
  winnerIdx := n.U
  for (i <- (n - 1) to 0 by -1) {
    when(activeAny(i)) { winnerIdx := i.U }
  }

  // fabricBusy(i): true if any source with index < i has an active request
  for (i <- 0 until n) {
    // Busy if the winner index is lower than i
    io.fabricBusy(i) := (winnerIdx < i.U)
  }

  // -------------------------------------------------------------------
  // Forward winning source → port
  // -------------------------------------------------------------------
  // Defaults (no request)
  io.port.readDataAddr.valid  := false.B
  io.port.readDataAddr.bits   := 0.U
  io.port.writeDataAddr.valid := false.B
  io.port.writeDataAddr.bits  := 0.U
  io.port.writeDataBits       := 0.U
  io.port.writeDataStrb       := 0.U

  for (i <- (n - 1) to 0 by -1) {
    when(winnerIdx === i.U) {
      io.port.readDataAddr.valid  := io.source(i).readDataAddr.valid
      io.port.readDataAddr.bits   := io.source(i).readDataAddr.bits
      io.port.writeDataAddr.valid := io.source(i).writeDataAddr.valid
      io.port.writeDataAddr.bits  := io.source(i).writeDataAddr.bits
      io.port.writeDataBits       := io.source(i).writeDataBits
      io.port.writeDataStrb       := io.source(i).writeDataStrb
    }
  }

  // -------------------------------------------------------------------
  // Broadcast read-data from port → all sources
  // -------------------------------------------------------------------
  for (i <- 0 until n) {
    io.source(i).readData.valid := io.port.readData.valid
    io.source(i).readData.bits  := io.port.readData.bits
  }
}

// ============================================================================
// Fabric address multiplexer (1 source → N ports by address decode)
// ============================================================================

/** 1-to-N fabric address demultiplexer.
  *
  * Routes a single source port to one of N output ports based on the
  * address range of each MemoryRegion.  Addresses within a region are
  * translated to region-relative offsets before forwarding.
  *
  * Read-data from the matching port is forwarded back to the source.
  * `periBusy(i)` is passed through to `fabricBusy`.
  */
class FabricMux(p: Parameters, regions: Seq[MemoryRegion],
                dataBits: Int = 32, addrBits: Int = 32) extends Module {
  val n = regions.length

  val io = IO(new Bundle {
    val source     = new FabricSourcePort(dataBits, addrBits)
    val ports      = Vec(n, new FabricOutPort(dataBits, addrBits))
    val periBusy   = Input(Vec(n, Bool()))
    val fabricBusy = Output(Bool())
  })

  // -------------------------------------------------------------------
  // Address decode: which region does this address fall into?
  // -------------------------------------------------------------------
  def inRegion(addr: UInt, r: MemoryRegion): Bool =
    (addr >= r.base.U) && (addr < (r.base + r.size).U)

  // Defaults
  for (j <- 0 until n) {
    io.ports(j).readDataAddr.valid  := false.B
    io.ports(j).readDataAddr.bits   := 0.U
    io.ports(j).writeDataAddr.valid := false.B
    io.ports(j).writeDataAddr.bits  := 0.U
    io.ports(j).writeDataBits       := 0.U
    io.ports(j).writeDataStrb       := 0.U
  }

  for (j <- 0 until n) {
    val r = regions(j)
    // Read path
    when(io.source.readDataAddr.valid && inRegion(io.source.readDataAddr.bits, r)) {
      io.ports(j).readDataAddr.valid := true.B
      io.ports(j).readDataAddr.bits  := io.source.readDataAddr.bits - r.base.U
    }
    // Write path
    when(io.source.writeDataAddr.valid && inRegion(io.source.writeDataAddr.bits, r)) {
      io.ports(j).writeDataAddr.valid := true.B
      io.ports(j).writeDataAddr.bits  := io.source.writeDataAddr.bits - r.base.U
      io.ports(j).writeDataBits       := io.source.writeDataBits
      io.ports(j).writeDataStrb       := io.source.writeDataStrb
    }
  }

  // -------------------------------------------------------------------
  // Read-data mux: return data from the port whose readData.valid is set
  // -------------------------------------------------------------------
  io.source.readData.valid := io.ports.map(_.readData.valid).reduce(_ || _)
  io.source.readData.bits  := MuxCase(0.U,
    (0 until n).map(j => io.ports(j).readData.valid -> io.ports(j).readData.bits))

  // -------------------------------------------------------------------
  // fabricBusy: OR of all periBusy inputs
  // -------------------------------------------------------------------
  io.fabricBusy := io.periBusy.reduce(_ || _)
}
