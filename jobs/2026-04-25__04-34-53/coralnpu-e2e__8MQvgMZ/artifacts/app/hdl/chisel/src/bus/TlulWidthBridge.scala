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

/** TL-UL data-width bridge.
  *
  * Connects a wider host (inP) to a narrower device (outP) by truncating data
  * and mask on the A channel and zero-extending on the D channel.
  *
  * @param inP  host-side TL-UL parameters
  * @param outP device-side TL-UL parameters (narrower data/mask width)
  */
class TlulWidthBridge(inP: TLULParameters, outP: TLULParameters) extends Module {
  val io = IO(new Bundle {
    val tl_h = Flipped(new TLBundleUL(inP))
    val tl_d = new TLBundleUL(outP)
  })

  // A channel: host → device (truncate data and mask to device width).
  io.tl_d.a.valid        := io.tl_h.a.valid
  io.tl_d.a.bits.opcode  := io.tl_h.a.bits.opcode
  io.tl_d.a.bits.param   := io.tl_h.a.bits.param
  io.tl_d.a.bits.size    := io.tl_h.a.bits.size
  io.tl_d.a.bits.source  := io.tl_h.a.bits.source
  io.tl_d.a.bits.address := io.tl_h.a.bits.address
  io.tl_d.a.bits.mask    := io.tl_h.a.bits.mask(outP.maskWidth - 1, 0)
  io.tl_d.a.bits.data    := io.tl_h.a.bits.data(outP.dataWidth - 1, 0)
  io.tl_d.a.bits.corrupt := io.tl_h.a.bits.corrupt
  io.tl_h.a.ready        := io.tl_d.a.ready

  // D channel: device → host (pass through; host must accept narrower data).
  io.tl_h.d <> io.tl_d.d
}
