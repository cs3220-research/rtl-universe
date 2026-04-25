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

// ---------------------------------------------------------------------------
// SPI physical IO bundle
// ---------------------------------------------------------------------------

class SpiIO extends Bundle {
  val mosi = Output(Bool())
  val miso = Input(Bool())
  val sclk = Output(Bool())
  val csb  = Output(UInt(1.W)) // chip select (active low)
}

// ---------------------------------------------------------------------------
// SpiMasterCtrl – TL-UL SPI master controller
//
// Register map (byte addresses):
//   0x00 : STATUS   – [0]=busy, [1]=rxEmpty, [2]=txFull (read-only)
//   0x04 : CONTROL  – [0]=enable, [1]=CPOL, [2]=CPHA, [3]=HDRX,
//                      [4]=HDTX, [15:8]=DIV
//   0x08 : TXDATA   – write 8-bit byte to TX FIFO
//   0x0C : RXDATA   – read 8-bit byte from RX FIFO (stalls if empty)
//   0x10 : CSID     – [0]=CS assert when non-zero
//   0x14 : CSMODE   – [0]=manual CS mode
//
// The controller generates SCLK at system_clock / (2*(DIV+1)).
// ---------------------------------------------------------------------------

class SpiMasterCtrl(p: coralnpu.Parameters) extends Module {
  private val tlP = TLULParameters(p)

  val io = IO(new Bundle {
    val tl  = Flipped(new OpenTitanTileLink.Host2Device(tlP))
    val spi = new SpiIO
  })

  // -------------------------------------------------------------------------
  // Configuration registers
  // -------------------------------------------------------------------------
  val ctrl   = RegInit(0.U(32.W))
  val csid   = RegInit(0.U(32.W))
  val csmode = RegInit(0.U(32.W))

  val enable  = ctrl(0)
  val cpol    = ctrl(1)
  val cpha    = ctrl(2)
  val hdrx    = ctrl(3) // half-duplex RX only
  val hdtx    = ctrl(4) // half-duplex TX only
  val divider = ctrl(15, 8)

  // -------------------------------------------------------------------------
  // TX / RX FIFOs (depth = 4)
  // -------------------------------------------------------------------------
  val TX_DEPTH = 4
  val RX_DEPTH = 4

  val txFifo = Module(new Queue(UInt(8.W), TX_DEPTH))
  val rxFifo = Module(new Queue(UInt(8.W), RX_DEPTH))

  val txFull  = !txFifo.io.enq.ready
  val rxEmpty = !rxFifo.io.deq.valid

  // -------------------------------------------------------------------------
  // SPI FSM
  // -------------------------------------------------------------------------
  val sIdle :: sActive :: Nil = Enum(2)
  val state = RegInit(sIdle)

  // Baud-rate divider counter.
  val divCount = RegInit(0.U(8.W))
  val sclkReg  = RegInit(false.B) // current SCLK output level

  // Shift register: 8 bits out, 8 bits in.
  val shiftOut = RegInit(0.U(8.W))
  val shiftIn  = RegInit(0.U(8.W))
  val bitCount = RegInit(0.U(4.W)) // counts half-cycles (0..15 for 8 bits)

  val busy = state === sActive

  // Status word presented to the register interface.
  val statusWord = Cat(0.U(29.W), txFull, rxEmpty, busy)

  // -------------------------------------------------------------------------
  // Chip-select logic
  // -------------------------------------------------------------------------
  val csAsserted = Mux(csmode(0), csid(0), busy).asBool
  io.spi.csb := Mux(csAsserted, 0.U(1.W), 1.U(1.W))

  // -------------------------------------------------------------------------
  // SCLK / data shift FSM
  // -------------------------------------------------------------------------
  // When enabled, a byte transfer begins when the TX FIFO has data
  // (or in HDRX mode, when there's space in the RX FIFO).
  val canStart = enable && (
    (!hdrx && !hdtx && txFifo.io.deq.valid) ||
    (hdrx   && !hdtx && rxFifo.io.enq.ready) ||
    (hdtx   && !hdrx && txFifo.io.deq.valid)
  )

  txFifo.io.deq.ready := false.B
  rxFifo.io.enq.valid := false.B
  rxFifo.io.enq.bits  := 0.U

  switch(state) {
    is(sIdle) {
      sclkReg  := cpol    // idle clock polarity
      bitCount := 0.U
      divCount := 0.U
      when(canStart) {
        state    := sActive
        shiftOut := txFifo.io.deq.bits
        txFifo.io.deq.ready := !hdrx // consume TX byte unless pure RX
        shiftIn  := 0.U
      }
    }
    is(sActive) {
      when(divCount < divider) {
        divCount := divCount + 1.U
      } .otherwise {
        divCount := 0.U
        // Toggle SCLK.
        sclkReg := !sclkReg

        val leadingEdge  = sclkReg === cpol   // transition to active level
        val trailingEdge = sclkReg =/= cpol   // transition back to idle

        // CPHA=0: sample on leading edge, drive on trailing (or start).
        // CPHA=1: drive on leading edge, sample on trailing.
        val sampleEdge = Mux(cpha, trailingEdge, leadingEdge)
        val driveEdge  = Mux(cpha, leadingEdge,  trailingEdge)

        when(sampleEdge && !hdtx) {
          shiftIn := Cat(shiftIn(6, 0), io.spi.miso)
        }
        when(driveEdge) {
          shiftOut := Cat(shiftOut(6, 0), 0.U(1.W))
          bitCount := bitCount + 1.U
          when(bitCount === 15.U) { // 16 half-cycles = 8 bits
            // Byte complete.
            when(!hdtx && rxFifo.io.enq.ready) {
              rxFifo.io.enq.valid := true.B
              rxFifo.io.enq.bits  := Cat(shiftIn(6, 0), io.spi.miso)
            }
            // Start next byte if available.
            when(canStart) {
              shiftOut := txFifo.io.deq.bits
              txFifo.io.deq.ready := !hdrx
              bitCount := 0.U
            } .otherwise {
              state := sIdle
            }
          }
        }
      }
    }
  }

  // MOSI: drive current MSB of shift register.
  io.spi.mosi := shiftOut(7)
  io.spi.sclk := sclkReg

  // -------------------------------------------------------------------------
  // TL-UL register interface
  // -------------------------------------------------------------------------
  // Response tracking (defined before use in ready logic).
  val respPending = RegInit(false.B)
  val respData    = RegInit(0.U(tlP.dataWidth.W))
  val respSrc     = RegInit(0.U(tlP.sourceWidth.W))
  val respOp      = RegInit(TLULOpcodesD.AccessAck)
  val respSz      = RegInit(0.U(tlP.sizeWidth.W))
  val respErr     = RegInit(false.B)

  // Address and direction decode (combinational from request channel).
  val addrA   = io.tl.a.bits.address(7, 0)
  val isWrite = io.tl.a.bits.opcode =/= TLULOpcodesA.Get
  val isRead  = !isWrite

  // Stall conditions (must not prevent tl.a.ready from being driven).
  val stallTx = isWrite && addrA === 0x08.U && txFull
  val stallRx = isRead  && addrA === 0x0C.U && rxEmpty

  io.tl.a.ready := !stallTx && !stallRx && !respPending

  // TX FIFO: enqueue when writing TXDATA and not stalling.
  txFifo.io.enq.valid := io.tl.a.fire && isWrite && addrA === 0x08.U
  txFifo.io.enq.bits  := io.tl.a.bits.data(7, 0)

  // RX FIFO: dequeue when reading RXDATA.
  rxFifo.io.deq.ready := io.tl.a.fire && isRead && addrA === 0x0C.U

  when(io.tl.a.fire) {
    respPending := true.B
    respSrc     := io.tl.a.bits.source
    respSz      := io.tl.a.bits.size

    // Determine error conditions.
    val badAddr   = addrA > 0x14.U
    val writeToRO = isWrite && addrA === 0x00.U
    respErr := badAddr || writeToRO

    when(isWrite) {
      respOp   := TLULOpcodesD.AccessAck
      respData := 0.U
      when(!badAddr && !writeToRO) {
        when(addrA === 0x04.U) { ctrl   := io.tl.a.bits.data }
        when(addrA === 0x10.U) { csid   := io.tl.a.bits.data }
        when(addrA === 0x14.U) { csmode := io.tl.a.bits.data }
        // 0x08 (TXDATA) is handled via txFifo above.
      }
    } .otherwise {
      respOp := TLULOpcodesD.AccessAckData
      respData := MuxLookup(addrA, 0.U)(Seq(
        0x00.U -> statusWord,
        0x04.U -> ctrl,
        0x0C.U -> rxFifo.io.deq.bits, // data already dequeued above
        0x10.U -> csid,
        0x14.U -> csmode
      ))
      when(badAddr) { respErr := true.B }
    }
  }
  when(io.tl.d.fire) { respPending := false.B }

  io.tl.d.valid        := respPending
  io.tl.d.bits.opcode  := respOp
  io.tl.d.bits.param   := 0.U
  io.tl.d.bits.size    := respSz
  io.tl.d.bits.source  := respSrc
  io.tl.d.bits.sink    := 0.U
  io.tl.d.bits.denied  := false.B
  io.tl.d.bits.data    := respData
  io.tl.d.bits.corrupt := false.B
  io.tl.d.bits.error   := respErr
}

// ---------------------------------------------------------------------------
// SpiMaster – top-level wrapper with explicit clock/reset ports
// ---------------------------------------------------------------------------

class SpiMaster(p: coralnpu.Parameters) extends Module {
  private val tlP = TLULParameters(p)

  val io = IO(new Bundle {
    val clk_i     = Input(Clock())
    val rst_ni    = Input(AsyncReset())
    val spi_clk_i = Input(Clock())
    val tl        = Flipped(new OpenTitanTileLink.Host2Device(tlP))
    val spi       = new SpiIO
  })

  // The controller runs in the system clock domain (this module's implicit
  // clock).  The spi_clk_i and clk_i inputs are kept for interface
  // compatibility with the testbench.
  val ctrl = Module(new SpiMasterCtrl(p))
  ctrl.io.tl  <> io.tl
  ctrl.io.spi <> io.spi
}
