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

package bus

import chisel3._
import chisel3.util._

/** TL-UL source-ID width adapter.
  *
  * Truncates the A-channel source field to `outIdBits` bits on the way to the
  * device and zero-extends the D-channel source field on the way back to the
  * host.  Useful when connecting a wide-source host to a narrower-ID device
  * without a full arbitration fabric.
  *
  * @param p         host-side TL-UL parameters
  * @param outIdBits device-side source ID width
  */
class TlulIdRemapper(p: TLULParameters, outIdBits: Int) extends Module {
  private val devP = TLULParameters(
    dataWidth   = p.dataWidth,
    addrWidth   = p.addrWidth,
    idWidth     = outIdBits,
    sizeWidth   = p.sizeWidth,
    maskWidth   = p.maskWidth,
    sourceWidth = outIdBits
  )

  val io = IO(new Bundle {
    val tl_h = Flipped(new TLBundleUL(p))
    val tl_d = new TLBundleUL(devP)
  })

  // A channel: host → device (truncate source).
  io.tl_d.a.valid          := io.tl_h.a.valid
  io.tl_d.a.bits.opcode    := io.tl_h.a.bits.opcode
  io.tl_d.a.bits.param     := io.tl_h.a.bits.param
  io.tl_d.a.bits.size      := io.tl_h.a.bits.size
  io.tl_d.a.bits.source    := io.tl_h.a.bits.source(outIdBits - 1, 0)
  io.tl_d.a.bits.address   := io.tl_h.a.bits.address
  io.tl_d.a.bits.mask      := io.tl_h.a.bits.mask
  io.tl_d.a.bits.data      := io.tl_h.a.bits.data
  io.tl_d.a.bits.corrupt   := io.tl_h.a.bits.corrupt
  io.tl_h.a.ready          := io.tl_d.a.ready

  // D channel: device → host (zero-extend source).
  io.tl_h.d.valid          := io.tl_d.d.valid
  io.tl_h.d.bits.opcode    := io.tl_d.d.bits.opcode
  io.tl_h.d.bits.param     := io.tl_d.d.bits.param
  io.tl_h.d.bits.size      := io.tl_d.d.bits.size
  io.tl_h.d.bits.source    := io.tl_d.d.bits.source
  io.tl_h.d.bits.sink      := io.tl_d.d.bits.sink
  io.tl_h.d.bits.denied    := io.tl_d.d.bits.denied
  io.tl_h.d.bits.data      := io.tl_d.d.bits.data
  io.tl_h.d.bits.corrupt   := io.tl_d.d.bits.corrupt
  io.tl_h.d.bits.error     := io.tl_d.d.bits.error
  io.tl_d.d.ready          := io.tl_h.d.ready
}
