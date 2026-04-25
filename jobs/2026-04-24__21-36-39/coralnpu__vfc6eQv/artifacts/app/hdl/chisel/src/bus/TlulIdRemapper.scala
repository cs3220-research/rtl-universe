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
import coralnpu.Parameters

/**
  * TileLink-UL ID remapper.
  *
  * Remaps source IDs from a large host-side ID space to a smaller device-side
  * ID space. Maintains a table mapping allocated device IDs back to original
  * host source IDs.
  *
  * @param p          Parameters for the data bus
  * @param nEntries   Number of in-flight transactions supported
  */
class TlulIdRemapper(p: Parameters, nEntries: Int = 8) extends Module {
  val hostTlp   = new TLULParameters(p)

  // Device-side uses fewer source bits
  val devSourceBits = log2Ceil(nEntries)

  class DevTLULParams extends TLULParameters(p) {
    override val sourceBits: Int = devSourceBits
  }
  val devTlp = new DevTLULParams

  val io = IO(new Bundle {
    val host   = Flipped(new OpenTitanTileLink.Host2Device(hostTlp))
    val device = new OpenTitanTileLink.Host2Device(devTlp)
  })

  // ID mapping table: device_id → original host source
  val srcTable  = Reg(Vec(nEntries, UInt(hostTlp.sourceBits.W)))
  val validVec  = RegInit(VecInit(Seq.fill(nEntries)(false.B)))

  // Find a free entry
  val freeEntry = PriorityEncoder(validVec.map(!_))
  val hasFree   = validVec.map(!_).reduce(_ || _)

  // A channel
  io.host.a.ready        := hasFree && io.device.a.ready
  io.device.a.valid      := io.host.a.valid && hasFree
  io.device.a.bits       := 0.U.asTypeOf(new TLULChannelA(devTlp))
  io.device.a.bits.opcode  := io.host.a.bits.opcode
  io.device.a.bits.param   := io.host.a.bits.param
  io.device.a.bits.size    := io.host.a.bits.size
  io.device.a.bits.source  := freeEntry
  io.device.a.bits.address := io.host.a.bits.address
  io.device.a.bits.mask    := io.host.a.bits.mask
  io.device.a.bits.data    := io.host.a.bits.data
  io.device.a.bits.corrupt := io.host.a.bits.corrupt

  // Allocate entry on A channel fire
  val aFire = io.host.a.valid && hasFree && io.device.a.ready
  when(aFire) {
    srcTable(freeEntry)  := io.host.a.bits.source
    validVec(freeEntry)  := true.B
  }

  // D channel: remap source back to host source
  val devSrc = io.device.d.bits.source(devSourceBits - 1, 0)
  io.host.d.valid        := io.device.d.valid
  io.host.d.bits         := 0.U.asTypeOf(new TLULChannelD(hostTlp))
  io.host.d.bits.opcode  := io.device.d.bits.opcode
  io.host.d.bits.param   := io.device.d.bits.param
  io.host.d.bits.size    := io.device.d.bits.size
  io.host.d.bits.source  := srcTable(devSrc)
  io.host.d.bits.sink    := io.device.d.bits.sink
  io.host.d.bits.denied  := io.device.d.bits.denied
  io.host.d.bits.data    := io.device.d.bits.data
  io.host.d.bits.corrupt := io.device.d.bits.corrupt
  io.host.d.bits.error   := io.device.d.bits.error
  io.device.d.ready      := io.host.d.ready

  // Free entry on D channel fire
  val dFire = io.device.d.valid && io.host.d.ready
  when(dFire) {
    validVec(devSrc) := false.B
  }
}
