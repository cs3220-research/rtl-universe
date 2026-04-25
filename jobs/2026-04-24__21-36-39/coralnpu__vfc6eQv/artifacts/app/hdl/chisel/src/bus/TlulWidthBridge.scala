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
  * TileLink-UL width conversion bridge.
  *
  * Converts between two TL-UL buses of different data widths.
  * The narrow side is the host (requestor) and the wide side is the device.
  *
  * This implementation handles the common case of 2:1 width ratio by
  * splitting wide writes and merging narrow reads.
  *
  * @param narrowP Parameters for the narrow (host) side
  * @param wideP   Parameters for the wide (device) side
  */
class TlulWidthBridge(narrowP: Parameters, wideP: Parameters) extends Module {
  val narrowTlp = new TLULParameters(narrowP)
  val wideTlp   = new TLULParameters(wideP)

  require(wideTlp.dataBits >= narrowTlp.dataBits, "Wide bus must be >= narrow bus")
  val ratio = wideTlp.dataBits / narrowTlp.dataBits

  val io = IO(new Bundle {
    val narrow = Flipped(new OpenTitanTileLink.Host2Device(narrowTlp))  // host side
    val wide   = new OpenTitanTileLink.Host2Device(wideTlp)              // device side
  })

  if (ratio == 1) {
    // Pass-through with type conversion
    io.wide.a.valid          := io.narrow.a.valid
    io.wide.a.bits.opcode    := io.narrow.a.bits.opcode
    io.wide.a.bits.param     := io.narrow.a.bits.param
    io.wide.a.bits.size      := io.narrow.a.bits.size
    io.wide.a.bits.source    := io.narrow.a.bits.source
    io.wide.a.bits.address   := io.narrow.a.bits.address
    io.wide.a.bits.mask      := io.narrow.a.bits.mask
    io.wide.a.bits.data      := io.narrow.a.bits.data
    io.wide.a.bits.corrupt   := io.narrow.a.bits.corrupt
    io.narrow.a.ready        := io.wide.a.ready

    io.narrow.d.valid        := io.wide.d.valid
    io.narrow.d.bits.opcode  := io.wide.d.bits.opcode
    io.narrow.d.bits.param   := io.wide.d.bits.param
    io.narrow.d.bits.size    := io.wide.d.bits.size
    io.narrow.d.bits.source  := io.wide.d.bits.source
    io.narrow.d.bits.sink    := io.wide.d.bits.sink
    io.narrow.d.bits.denied  := io.wide.d.bits.denied
    io.narrow.d.bits.data    := io.wide.d.bits.data(narrowTlp.dataBits - 1, 0)
    io.narrow.d.bits.corrupt := io.wide.d.bits.corrupt
    io.narrow.d.bits.error   := io.wide.d.bits.error
    io.wide.d.ready          := io.narrow.d.ready
  } else {
    // Simple width extension: zero-extend data and mask
    io.wide.a.valid          := io.narrow.a.valid
    io.wide.a.bits.opcode    := io.narrow.a.bits.opcode
    io.wide.a.bits.param     := io.narrow.a.bits.param
    io.wide.a.bits.size      := io.narrow.a.bits.size
    io.wide.a.bits.source    := io.narrow.a.bits.source
    io.wide.a.bits.address   := io.narrow.a.bits.address
    io.wide.a.bits.mask      := io.narrow.a.bits.mask.pad(wideTlp.dataBits / 8)
    io.wide.a.bits.data      := io.narrow.a.bits.data.pad(wideTlp.dataBits)
    io.wide.a.bits.corrupt   := io.narrow.a.bits.corrupt
    io.narrow.a.ready        := io.wide.a.ready

    io.narrow.d.valid        := io.wide.d.valid
    io.narrow.d.bits.opcode  := io.wide.d.bits.opcode
    io.narrow.d.bits.param   := io.wide.d.bits.param
    io.narrow.d.bits.size    := io.wide.d.bits.size
    io.narrow.d.bits.source  := io.wide.d.bits.source
    io.narrow.d.bits.sink    := io.wide.d.bits.sink
    io.narrow.d.bits.denied  := io.wide.d.bits.denied
    io.narrow.d.bits.data    := io.wide.d.bits.data(narrowTlp.dataBits - 1, 0)
    io.narrow.d.bits.corrupt := io.wide.d.bits.corrupt
    io.narrow.d.bits.error   := io.wide.d.bits.error
    io.wide.d.ready          := io.narrow.d.ready
  }
}
