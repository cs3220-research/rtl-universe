// Copyright 2026 Google LLC
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

package bus

import chisel3._
import chisel3.util._

/** TileLink-UL source-ID remapper.
  *
  * When multiple TL-UL hosts are multiplexed onto a single device (e.g. via
  * [[TlulSocketM1]]), each host's source IDs need to be remapped to a distinct,
  * non-overlapping range so that responses can be routed back to the correct host.
  *
  * This module dynamically allocates IDs from a pool of size [[maxId]] and
  * stores the original (host-side) source ID for each outstanding transaction.
  * On the return path it restores the original source ID.
  *
  * @param sourceBits  Width of the source-ID field.
  * @param maxId       Number of entries in the ID allocation table.
  */
class TlulIdRemapper(sourceBits: Int, maxId: Int) extends Module {
  require(maxId >= 1)
  require(maxId <= (1 << sourceBits))

  val tlulP = new TLULParameters(new coralnpu.Parameters {
    lsuDataBits = 32
  })

  val io = IO(new Bundle {
    // Upstream (host-facing) TL-UL port
    val upstream = new OpenTitanTileLink.Device2Host(tlulP)
    // Downstream (device-facing) TL-UL port
    val downstream = new OpenTitanTileLink.Host2Device(tlulP)
  })

  // -------------------------------------------------------------------------
  // ID allocation table
  // -------------------------------------------------------------------------
  // For each slot [0..maxId-1] we store the original upstream source ID.
  val idValid = RegInit(VecInit(Seq.fill(maxId)(false.B)))
  val idTable = Reg(Vec(maxId, UInt(sourceBits.W)))

  // Find a free slot (simple priority encoder)
  val freeSlot  = Wire(UInt(log2Ceil(maxId).W))
  val hasSlot   = Wire(Bool())
  freeSlot := 0.U
  hasSlot  := false.B
  for (i <- maxId - 1 to 0 by -1) {
    when(!idValid(i.U)) {
      freeSlot := i.U
      hasSlot  := true.B
    }
  }

  // -------------------------------------------------------------------------
  // A-channel: upstream → downstream (remap source ID)
  // -------------------------------------------------------------------------
  io.upstream.a.ready    := io.downstream.a.ready && hasSlot
  io.downstream.a.valid  := io.upstream.a.valid && hasSlot
  io.downstream.a.bits   := io.upstream.a.bits
  io.downstream.a.bits.source := freeSlot

  when(io.upstream.a.valid && io.downstream.a.ready && hasSlot) {
    idValid(freeSlot) := true.B
    idTable(freeSlot) := io.upstream.a.bits.source
  }

  // -------------------------------------------------------------------------
  // D-channel: downstream → upstream (restore original source ID)
  // -------------------------------------------------------------------------
  val dSlot = io.downstream.d.bits.source(log2Ceil(maxId) - 1, 0)

  io.downstream.d.ready  := io.upstream.d.ready
  io.upstream.d.valid    := io.downstream.d.valid
  io.upstream.d.bits     := io.downstream.d.bits
  io.upstream.d.bits.source := Mux(
    io.downstream.d.valid && idValid(dSlot),
    idTable(dSlot),
    io.downstream.d.bits.source
  )

  when(io.downstream.d.valid && io.upstream.d.ready) {
    idValid(dSlot) := false.B
  }
}
