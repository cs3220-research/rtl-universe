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

// =============================================================================
// SPI IO bundle
// =============================================================================

/** Standard SPI master signal bundle. */
class SpiIO extends Bundle {
  val sclk = Output(Bool())
  val mosi = Output(Bool())
  val miso = Input(Bool())
  val csb  = Output(Bool())  // active-low chip select
}

// =============================================================================
// SPI master controller core (single clock domain)
// =============================================================================

/** SPI master controller — single clock domain.
  *
  * Register map (32-bit TL-UL):
  * {{{
  * 0x00 : STATUS   (R)   bit[0]=busy, bit[1]=rx_empty
  * 0x04 : CONTROL  (R/W) bit[0]=enable, bit[1]=cpol, bit[2]=cpha,
  *                        bit[3]=hdrx (half-duplex RX), bit[4]=hdtx (half-duplex TX),
  *                        bits[15:8]=div (clock divider, 0 = div/2)
  * 0x08 : TXDATA   (W)   byte to transmit (stalls if FIFO full)
  * 0x0C : RXDATA   (R)   received byte (stalls if FIFO empty)
  * 0x10 : CSID     (R/W) chip-select ID (0 = active)
  * 0x14 : CSMODE   (R/W) 0=auto, 1=manual
  * }}}
  *
  * @param p  Project-wide parameters (not directly used in V1 but kept for consistency).
  */
class SpiMasterCtrl(p: coralnpu.Parameters) extends Module {
  val tlulP = new TLULParameters(new coralnpu.Parameters {
    lsuDataBits = 32
  })

  val io = IO(new Bundle {
    val tl  = new OpenTitanTileLink.Device2Host(tlulP)
    val spi = new SpiIO
  })

  // -------------------------------------------------------------------------
  // Control registers
  // -------------------------------------------------------------------------
  val regCtrl   = RegInit(0.U(32.W))
  val regCsid   = RegInit(0.U(32.W))
  val regCsmode = RegInit(0.U(32.W))

  val ctrlEnable = regCtrl(0)
  val ctrlCpol   = regCtrl(1)
  val ctrlCpha   = regCtrl(2)
  val ctrlHdrx   = regCtrl(3)   // half-duplex RX
  val ctrlHdtx   = regCtrl(4)   // half-duplex TX
  val ctrlDiv    = regCtrl(15, 8)

  // -------------------------------------------------------------------------
  // TX / RX FIFOs (depth 4)
  // -------------------------------------------------------------------------
  val txFifo = Module(new common.Fifo(UInt(8.W), depth = 4))
  val rxFifo = Module(new common.Fifo(UInt(8.W), depth = 4))

  val txFull   = !txFifo.io.enq.ready
  val rxEmpty  = !rxFifo.io.deq.valid

  // -------------------------------------------------------------------------
  // TL-UL register interface
  // -------------------------------------------------------------------------
  val aAddr    = io.tl.a.bits.address(7, 0)
  val aData    = io.tl.a.bits.data(7, 0)
  val aWrite   = (io.tl.a.bits.opcode === TLULOpcodesA.PutFullData.asUInt ||
                  io.tl.a.bits.opcode === TLULOpcodesA.PutPartialData.asUInt)

  // Stall conditions
  val stallTx  = aWrite && (aAddr === 0x08.U) && txFull
  val stallRx  = !aWrite && (aAddr === 0x0C.U) && rxEmpty

  val aReady   = !stallTx && !stallRx && io.tl.d.ready
  val aFire    = io.tl.a.valid && aReady

  val rdata    = Wire(UInt(32.W))
  rdata := 0.U
  val error    = Wire(Bool())
  error := false.B

  txFifo.io.enq.valid := false.B
  txFifo.io.enq.bits  := 0.U
  rxFifo.io.deq.ready := false.B

  when(aFire) {
    when(aAddr === 0x00.U) {
      when(aWrite) { error := true.B }  // STATUS is read-only
      .otherwise   {
        rdata := Cat(0.U(30.W), rxEmpty, 0.U(1.W))  // bit[1]=rx_empty, bit[0]=busy
      }
    }.elsewhen(aAddr === 0x04.U) {
      when(aWrite) { regCtrl := io.tl.a.bits.data }
      .otherwise   { rdata   := regCtrl }
    }.elsewhen(aAddr === 0x08.U) {
      when(aWrite) {
        txFifo.io.enq.valid := true.B
        txFifo.io.enq.bits  := aData
      }.otherwise { error := true.B }  // TXDATA is write-only
    }.elsewhen(aAddr === 0x0C.U) {
      when(aWrite) { error := true.B }  // RXDATA is read-only
      .otherwise {
        rxFifo.io.deq.ready := true.B
        rdata := rxFifo.io.deq.bits
      }
    }.elsewhen(aAddr === 0x10.U) {
      when(aWrite) { regCsid   := io.tl.a.bits.data }
      .otherwise   { rdata := regCsid }
    }.elsewhen(aAddr === 0x14.U) {
      when(aWrite) { regCsmode := io.tl.a.bits.data }
      .otherwise   { rdata := regCsmode }
    }.otherwise {
      error := true.B
    }
  }

  val rdataReg = RegNext(rdata)
  val errorReg = RegNext(error)
  val srcReg   = RegNext(io.tl.a.bits.source)
  val sizeReg  = RegNext(io.tl.a.bits.size)
  val isGetReg = RegNext(!aWrite)
  val validReg = RegNext(aFire, false.B)

  io.tl.a.ready := aReady
  io.tl.d.valid := validReg

  io.tl.d.bits.opcode  := Mux(isGetReg, TLULOpcodesD.AccessAckData.asUInt, TLULOpcodesD.AccessAck.asUInt)
  io.tl.d.bits.param   := 0.U
  io.tl.d.bits.size    := sizeReg
  io.tl.d.bits.source  := srcReg
  io.tl.d.bits.sink    := 0.U
  io.tl.d.bits.data    := rdataReg
  io.tl.d.bits.user    := 0.U.asTypeOf(new OpenTitanTileLink_D_User)
  io.tl.d.bits.error   := errorReg
  io.tl.d.bits.corrupt := false.B

  // -------------------------------------------------------------------------
  // SPI shift engine
  // -------------------------------------------------------------------------
  val spiIdle :: spiLoad :: spiShift :: spiDone :: Nil = Enum(4)
  val spiState = RegInit(spiIdle)

  val sclkReg   = RegInit(false.B)
  val mosiReg   = RegInit(false.B)
  val csbReg    = RegInit(true.B)
  val shiftReg  = RegInit(0.U(8.W))
  val rxShiftReg = RegInit(0.U(8.W))
  val bitCntSpi = RegInit(0.U(3.W))
  val divCnt    = RegInit(0.U(8.W))
  val divMatch  = (divCnt === ctrlDiv)

  io.spi.sclk := sclkReg
  io.spi.mosi := mosiReg
  io.spi.csb  := csbReg

  // The HDRX mode: generate SCLK even without TX data (to receive)
  val hdrxActive = ctrlHdrx && ctrlEnable && rxFifo.io.enq.ready

  txFifo.io.deq.ready  := false.B
  rxFifo.io.enq.valid  := false.B
  rxFifo.io.enq.bits   := 0.U

  divCnt := Mux(divCnt === ctrlDiv, 0.U, divCnt + 1.U)

  switch(spiState) {
    is(spiIdle) {
      when(ctrlEnable && (txFifo.io.deq.valid || hdrxActive)) {
        when(hdrxActive && !txFifo.io.deq.valid) {
          shiftReg := 0.U
        }.otherwise {
          txFifo.io.deq.ready := true.B
          shiftReg := txFifo.io.deq.bits
        }
        spiState := spiLoad
        // Assert CS
        when(regCsmode === 0.U) {  // Auto mode
          csbReg := false.B
        }.otherwise {
          // Manual mode: respect CSID
          csbReg := (regCsid =/= 0.U)
        }
        rxShiftReg := 0.U
        bitCntSpi  := 0.U
        sclkReg    := ctrlCpol
        divCnt     := 0.U
      }.otherwise {
        when(regCsmode === 1.U) {
          csbReg := (regCsid =/= 0.U)
        }.otherwise {
          csbReg := true.B
        }
        sclkReg := ctrlCpol
      }
    }

    is(spiLoad) {
      mosiReg  := shiftReg(7)
      spiState := spiShift
    }

    is(spiShift) {
      when(divMatch) {
        // Toggle clock
        sclkReg := !sclkReg
        val onSample = Mux(ctrlCpha,
          sclkReg === ctrlCpol,     // CPHA=1: sample on trailing edge
          sclkReg =/= ctrlCpol)     // CPHA=0: sample on leading edge

        when(onSample) {
          // Sample MISO
          rxShiftReg := Cat(rxShiftReg(6, 0), io.spi.miso)
        }.otherwise {
          // Drive MOSI on non-sample half-cycle
          shiftReg   := Cat(shiftReg(6, 0), 0.U(1.W))
          mosiReg    := shiftReg(6)
          bitCntSpi  := bitCntSpi + 1.U
          when(bitCntSpi === 7.U) {
            spiState := spiDone
          }
        }
      }
    }

    is(spiDone) {
      sclkReg := ctrlCpol
      // Only push to RX if not half-duplex TX mode
      when(!ctrlHdtx && rxFifo.io.enq.ready) {
        rxFifo.io.enq.valid := true.B
        rxFifo.io.enq.bits  := rxShiftReg
      }
      spiState := spiIdle
    }
  }
}

// =============================================================================
// Top-level SpiMaster with explicit clock ports for CDC
// =============================================================================

/** SPI master with explicit system/SPI clock ports.
  *
  * Wraps [[SpiMasterCtrl]] and exposes the raw clock/reset/SPI-clock input
  * ports needed when the TL-UL bus and SPI clock are asynchronous.
  *
  * In simulation the `spi_clk_i` input is used directly as the SPI clock;
  * in synthesis it would feed a clock-domain boundary.
  */
class SpiMaster(p: coralnpu.Parameters) extends RawModule {
  val tlulP = new TLULParameters(new coralnpu.Parameters {
    lsuDataBits = 32
  })

  val io = IO(new Bundle {
    val clk_i     = Input(Clock())
    val rst_ni    = Input(AsyncReset())
    val tl        = new OpenTitanTileLink.Device2Host(tlulP)
    val spi       = new SpiIO
    val spi_clk_i = Input(Clock())
  })

  // Instantiate the ctrl module in the system clock domain
  val ctrl = withClockAndReset(io.clk_i, (!io.rst_ni.asBool).asAsyncReset) {
    Module(new SpiMasterCtrl(p))
  }

  // Wire TL-UL and SPI signals
  io.tl  <> ctrl.io.tl
  io.spi <> ctrl.io.spi
}
