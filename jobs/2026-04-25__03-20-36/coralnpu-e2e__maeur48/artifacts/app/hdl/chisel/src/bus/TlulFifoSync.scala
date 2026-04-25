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

/** TileLink-UL synchronous FIFO.
  *
  * Provides registered cut-through buffering for both the A-channel
  * (host → device) and D-channel (device → host).  Both sides share the
  * same clock domain.  A [[Queue]] of depth 4 is used for each direction.
  *
  * @param addrBits  Address width.
  * @param dataBits  Data width in bits.
  */
class TlulFifoSync(addrBits: Int, dataBits: Int) extends Module {
  val tlulP = new TLULParameters(new coralnpu.Parameters {
    lsuDataBits = dataBits
  })

  val io = IO(new Bundle {
    val host   = new OpenTitanTileLink.Host2Device(tlulP)
    val device = new OpenTitanTileLink.Device2Host(tlulP)
  })

  // -------------------------------------------------------------------------
  // A-channel: host → device with a 4-entry queue
  // -------------------------------------------------------------------------
  val aFifo = Module(new Queue(new OpenTitanTileLink.A_Channel(tlulP), entries = 4))
  aFifo.io.enq <> io.host.a
  io.device.a  <> aFifo.io.deq

  // -------------------------------------------------------------------------
  // D-channel: device → host with a 4-entry queue
  // -------------------------------------------------------------------------
  val dFifo = Module(new Queue(new OpenTitanTileLink.D_Channel(tlulP), entries = 4))
  dFifo.io.enq <> io.device.d
  io.host.d    <> dFifo.io.deq
}

import _root_.circt.stage.ChiselStage
import scala.annotation.nowarn

@nowarn
object TlulFifoSyncEmitter extends App {
  ChiselStage.emitSystemVerilogFile(
    new TlulFifoSync(addrBits = 32, dataBits = 128),
    args,
    firtoolOpts = Array("--lowering-options=disallowLocalVariables")
  )
}
