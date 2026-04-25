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

/** SPI physical IO signals. */
class SpiIO extends Bundle {
  val sclk = Output(Bool())
  val mosi = Output(Bool())
  val miso = Input(Bool())
  val csb  = Output(Bool())  // chip-select bar (active low)
}

/**
  * SPI Master Controller with TL-UL CSR interface.
  *
  * CSR Map:
  *   0x00 : STATUS   (r/o)  - bit 0: busy, bit 1: rx_empty
  *   0x04 : CONTROL  (r/w)  - bit 0: enable, bit 1: cpol, bit 2: cpha,
  *                            bit 3: hdrx (half-duplex RX), bit 4: hdtx (half-duplex TX),
  *                            bits[15:8]: div (baud rate divisor)
  *   0x08 : TXDATA   (w/o)  - write a byte to transmit FIFO
  *   0x0c : RXDATA   (r/o)  - read a byte from receive FIFO (stalls if empty)
  *   0x10 : CSID     (r/w)  - chip select control: 0=deassert, 1=assert (in manual mode)
  *   0x14 : CSMODE   (r/w)  - 0=auto, 1=manual
  *
  * Writes to STATUS (0x00) return error. Reads from TXDATA (0x08) return error.
  * Reads from RXDATA (0x0c) stall channel A until data is available.
  * Writes to TXDATA stall channel A if TX FIFO is full.
  */
class SpiMasterCtrl(p: Parameters) extends Module {
  val tlp = new TLULParameters(p)

  val io = IO(new Bundle {
    val tl  = Flipped(new OpenTitanTileLink.Host2Device(tlp))
    val spi = new SpiIO
  })

  // =========================================================================
  // Configuration registers
  // =========================================================================
  val ctrlReg   = RegInit(0.U(16.W))
  val csidReg   = RegInit(0.U(1.W))
  val csmodeReg = RegInit(0.U(1.W))

  val spiEnable = ctrlReg(0)
  val cpol      = ctrlReg(1)
  val cpha      = ctrlReg(2)
  val hdrx      = ctrlReg(3)   // half-duplex RX
  val hdtx      = ctrlReg(4)   // half-duplex TX
  val divider   = ctrlReg(15, 8)  // baud rate divisor

  // =========================================================================
  // TX and RX FIFOs (depth 4)
  // =========================================================================
  val TX_DEPTH = 4
  val RX_DEPTH = 4

  val txFifo = Module(new Queue(UInt(8.W), TX_DEPTH))
  val rxFifo = Module(new Queue(UInt(8.W), RX_DEPTH))

  // =========================================================================
  // SPI clock generation and bit shifting FSM
  // =========================================================================
  // State machine:
  //   spiIdle: waiting for TX data or HDRX trigger
  //   spiBit:  clocking 8 bits (16 half-periods total)
  //   spiDone: storing received byte and transitioning
  val spiIdle :: spiBit :: spiDone :: Nil = Enum(3)
  val spiState = RegInit(spiIdle)

  val sclkReg  = RegInit(false.B)
  val mosiReg  = RegInit(false.B)
  val shiftReg = RegInit(0.U(8.W))  // TX shift register (MSB first)
  val rxShift  = RegInit(0.U(8.W))  // RX accumulator
  val bitCnt   = RegInit(0.U(3.W))  // 0..7: which bit we're on
  val halfCnt  = RegInit(0.U(8.W))  // half-period counter
  val phase    = RegInit(false.B)   // false=first-half, true=second-half of current bit

  // half-period = divider + 1
  val spiHalfPeriod = WireDefault((divider +& 1.U)(7, 0))

  // CS logic
  val manualCS     = csmodeReg === 1.U
  val autoCSActive = spiState =/= spiIdle && !manualCS

  io.spi.sclk := sclkReg
  io.spi.mosi := mosiReg
  io.spi.csb  := Mux(manualCS, !csidReg.asBool, !autoCSActive)

  val txHasData = txFifo.io.deq.valid
  val rxFull    = !rxFifo.io.enq.ready
  val rxEmpty   = !rxFifo.io.deq.valid

  txFifo.io.enq.valid := false.B
  txFifo.io.enq.bits  := 0.U
  txFifo.io.deq.ready := false.B

  rxFifo.io.enq.valid := false.B
  rxFifo.io.enq.bits  := 0.U
  rxFifo.io.deq.ready := false.B

  val spiBusy  = spiState =/= spiIdle
  val canStart = txHasData || hdrx

  // Helper: load next TX byte (or zeros for HDRX)
  def loadNextByte(): Unit = {
    when(txHasData) {
      txFifo.io.deq.ready := true.B
      shiftReg := txFifo.io.deq.bits
      mosiReg  := txFifo.io.deq.bits(7)
    }.otherwise {
      // HDRX mode: send zeros, clock for RX
      shiftReg := 0.U
      mosiReg  := false.B
    }
    rxShift := 0.U
    bitCnt  := 0.U
    halfCnt := 0.U
    phase   := false.B
  }

  switch(spiState) {
    is(spiIdle) {
      sclkReg := cpol  // idle clock polarity
      when(spiEnable && canStart && !rxFull) {
        loadNextByte()
        spiState := spiBit
      }
    }

    is(spiBit) {
      halfCnt := halfCnt + 1.U
      when(halfCnt >= spiHalfPeriod - 1.U) {
        halfCnt := 0.U

        // Toggle SCLK
        val nextSclk = !sclkReg
        sclkReg := nextSclk

        // Determine if this is a leading or trailing edge
        // Leading edge: CPOL=0 → rising (sclkReg=0→1), CPOL=1 → falling (sclkReg=1→0)
        // sclkReg is the OLD value (before toggle)
        val isLeadingEdge = Mux(cpol, sclkReg, !sclkReg)

        // CPHA=0: sample on leading, shift on trailing
        // CPHA=1: sample on trailing (second), shift on leading (first)
        val sampleOnThisEdge = Mux(cpha, !isLeadingEdge, isLeadingEdge)

        when(sampleOnThisEdge) {
          // Sample MISO, shift into rxShift MSB first
          rxShift := Cat(rxShift(6, 0), io.spi.miso)
          // After sampling all 8 bits
          when(bitCnt === 7.U) {
            spiState := spiDone
          }.otherwise {
            bitCnt := bitCnt + 1.U
          }
        }.otherwise {
          // Shift MOSI: output next bit
          // For CPHA=0: shift happens on trailing edge (after sample), bitCnt already incremented
          // For CPHA=1: shift happens on leading edge (before sample)
          when(bitCnt < 7.U) {
            mosiReg := shiftReg(6.U - bitCnt)
          }
        }
      }
    }

    is(spiDone) {
      sclkReg := cpol
      when(!hdtx) {
        // Store RX byte
        when(rxFifo.io.enq.ready) {
          rxFifo.io.enq.valid := true.B
          rxFifo.io.enq.bits  := rxShift
          when(spiEnable && canStart && !rxFull) {
            loadNextByte()
            spiState := spiBit
          }.otherwise {
            spiState := spiIdle
          }
        }
        // else: stall waiting for RX FIFO space
      }.otherwise {
        // HDTX: discard RX
        when(spiEnable && txHasData) {
          loadNextByte()
          spiState := spiBit
        }.otherwise {
          spiState := spiIdle
        }
      }
    }
  }

  // =========================================================================
  // Status register
  // =========================================================================
  val statusReg = Cat(rxEmpty, spiBusy)

  // =========================================================================
  // TL-UL CSR Slave
  // =========================================================================
  val sTLIdle :: sTLResp :: Nil = Enum(2)
  val tlState = RegInit(sTLIdle)

  val tlRespData   = RegInit(0.U(tlp.dataBits.W))
  val tlRespError  = RegInit(false.B)
  val tlRespSource = RegInit(0.U(tlp.sourceBits.W))
  val tlRespSize   = RegInit(0.U(tlp.sizeBits.W))
  val tlRespOpcode = RegInit(TLULOpcodesD.AccessAck)

  io.tl.a.ready := false.B
  io.tl.d.valid := false.B
  io.tl.d.bits  := 0.U.asTypeOf(new TLULChannelD(tlp))

  // Stall conditions: reading RXDATA when empty or writing TXDATA when full
  val txWriteStall = WireDefault(false.B)
  val rxReadStall  = WireDefault(false.B)

  switch(tlState) {
    is(sTLIdle) {
      val addr    = io.tl.a.bits.address
      val isWrite = (io.tl.a.bits.opcode === TLULOpcodesA.PutFullData) ||
                    (io.tl.a.bits.opcode === TLULOpcodesA.PutPartialData)
      val isGet   = io.tl.a.bits.opcode === TLULOpcodesA.Get

      when(io.tl.a.valid) {
        txWriteStall := isWrite && addr === 0x08.U && !txFifo.io.enq.ready
        rxReadStall  := isGet  && addr === 0x0c.U && rxEmpty
      }

      io.tl.a.ready := io.tl.a.valid && !txWriteStall && !rxReadStall

      when(io.tl.a.valid && !txWriteStall && !rxReadStall) {
        tlRespSource := io.tl.a.bits.source
        tlRespSize   := io.tl.a.bits.size
        tlRespError  := false.B
        tlRespData   := 0.U

        when(isGet) {
          tlRespOpcode := TLULOpcodesD.AccessAckData
          when(addr === 0x00.U) {
            tlRespData := statusReg
          }.elsewhen(addr === 0x04.U) {
            tlRespData := ctrlReg
          }.elsewhen(addr === 0x0c.U) {
            rxFifo.io.deq.ready := true.B
            tlRespData := rxFifo.io.deq.bits
          }.elsewhen(addr === 0x10.U) {
            tlRespData := csidReg
          }.elsewhen(addr === 0x14.U) {
            tlRespData := csmodeReg
          }.otherwise {
            tlRespData  := 0.U
            tlRespError := true.B
          }
        }.elsewhen(isWrite) {
          tlRespOpcode := TLULOpcodesD.AccessAck
          when(addr === 0x00.U) {
            tlRespError := true.B
          }.elsewhen(addr === 0x04.U) {
            ctrlReg := io.tl.a.bits.data(15, 0)
          }.elsewhen(addr === 0x08.U) {
            txFifo.io.enq.valid := true.B
            txFifo.io.enq.bits  := io.tl.a.bits.data(7, 0)
          }.elsewhen(addr === 0x10.U) {
            csidReg := io.tl.a.bits.data(0)
          }.elsewhen(addr === 0x14.U) {
            csmodeReg := io.tl.a.bits.data(0)
          }.otherwise {
            tlRespError := true.B
          }
        }.otherwise {
          tlRespOpcode := TLULOpcodesD.AccessAck
          tlRespError  := true.B
        }

        tlState := sTLResp
      }
    }

    is(sTLResp) {
      io.tl.d.valid        := true.B
      io.tl.d.bits.opcode  := tlRespOpcode
      io.tl.d.bits.param   := 0.U
      io.tl.d.bits.size    := tlRespSize
      io.tl.d.bits.source  := tlRespSource
      io.tl.d.bits.sink    := 0.U
      io.tl.d.bits.denied  := false.B
      io.tl.d.bits.data    := tlRespData
      io.tl.d.bits.corrupt := false.B
      io.tl.d.bits.error   := tlRespError

      when(io.tl.d.ready) {
        tlState := sTLIdle
      }
    }
  }
}

/**
  * SPI Master top-level with explicit clock and reset ports for CDC crossing.
  * Wraps SpiMasterCtrl with explicit clock domain inputs.
  */
class SpiMaster(p: Parameters) extends RawModule {
  val tlp = new TLULParameters(p)

  val io = IO(new Bundle {
    val clk_i     = Input(Clock())
    val rst_ni    = Input(AsyncReset())
    val tl        = Flipped(new OpenTitanTileLink.Host2Device(tlp))
    val spi       = new SpiIO
    val spi_clk_i = Input(Clock())
  })

  withClockAndReset(io.clk_i, io.rst_ni) {
    val ctrl = Module(new SpiMasterCtrl(p))
    ctrl.io.tl  <> io.tl
    io.spi      <> ctrl.io.spi
  }
}
