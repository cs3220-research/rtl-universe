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
import freechips.rocketchip.util._

/** TileLink-UL asynchronous FIFO — bridges two clock domains.
  *
  * The host side (TL-A/D, driven by a host) may run in one clock domain and
  * the device side in another.  In this simplified implementation a single
  * module clock is used; the async-clock ports are wired to the module clock.
  * An [[AsyncQueue]] is used to transfer the A-channel and D-channel messages
  * between the two sides.
  *
  * @param addrBits  Address width (must match the surrounding TL-UL network).
  * @param dataBits  Data width in bits.
  */
class TlulFifoAsync(addrBits: Int, dataBits: Int) extends Module {
  val tlulP = new TLULParameters(new coralnpu.Parameters {
    lsuDataBits = dataBits
  })

  val io = IO(new Bundle {
    // Host-side TL-UL port (host drives A, receives D)
    val host = new OpenTitanTileLink.Host2Device(tlulP)
    // Device-side TL-UL port (device receives A, drives D)
    val device = new OpenTitanTileLink.Device2Host(tlulP)

    // Optional clock/reset ports for the two sides (for true CDC use)
    val host_clock  = Input(Clock())
    val host_reset  = Input(Bool())
    val dev_clock   = Input(Clock())
    val dev_reset   = Input(Bool())
  })

  // -------------------------------------------------------------------------
  // A-channel FIFO: host → device
  // -------------------------------------------------------------------------
  val aQueue = Module(
    new AsyncQueue(new OpenTitanTileLink.A_Channel(tlulP), AsyncQueueParams(depth = 4, sync = 2))
  )
  aQueue.io.enq_clock := io.host_clock
  aQueue.io.enq_reset := io.host_reset
  aQueue.io.deq_clock := io.dev_clock
  aQueue.io.deq_reset := io.dev_reset

  aQueue.io.enq  <> io.host.a
  io.device.a    <> aQueue.io.deq

  // -------------------------------------------------------------------------
  // D-channel FIFO: device → host
  // -------------------------------------------------------------------------
  val dQueue = Module(
    new AsyncQueue(new OpenTitanTileLink.D_Channel(tlulP), AsyncQueueParams(depth = 4, sync = 2))
  )
  dQueue.io.enq_clock := io.dev_clock
  dQueue.io.enq_reset := io.dev_reset
  dQueue.io.deq_clock := io.host_clock
  dQueue.io.deq_reset := io.host_reset

  dQueue.io.enq  <> io.device.d
  io.host.d      <> dQueue.io.deq
}

/** Convenience subclass with 128-bit data width. */
class TlulFifoAsync128 extends TlulFifoAsync(addrBits = 32, dataBits = 128)

import _root_.circt.stage.ChiselStage
import scala.annotation.nowarn

@nowarn
object TlulFifoAsync128Emitter extends App {
  ChiselStage.emitSystemVerilogFile(
    new TlulFifoAsync128,
    args,
    firtoolOpts = Array("--lowering-options=disallowLocalVariables")
  )
}
