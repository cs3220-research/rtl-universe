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
import freechips.rocketchip.util._

/**
  * SPI-to-TileLink-UL bridge (version 2).
  *
  * SPI frame format (bytes, MSB-first per byte):
  *   Byte 0     : opcode  (0x01=read, 0x02=write)
  *   Bytes 1..4 : address (big-endian 32-bit)
  *   Bytes 5..6 : beats-1 (big-endian 16-bit)
  *   For write: Bytes 7..end : write data (beats * 16 bytes)
  *
  * Read response on MISO per beat: 0xFE sync byte + 16 data bytes
  * (little-endian: byte[i] = data[8i+7:8i]).
  */
class Spi2TLULV2(p: Parameters) extends Module {
  val tlp        = new TLULParameters(p)
  val BEAT_BYTES = tlp.dataBits / 8  // 16 bytes for 128-bit bus

  val io = IO(new Bundle {
    val spi_clk   = Input(Clock())
    val spi_rst_n = Input(Bool())     // low = reset

    val q_mosi_pin = Flipped(Decoupled(Bool()))
    val q_miso_pin = Decoupled(Bool())

    val q_tl_a = Decoupled(new OpenTitanTileLink.A_Channel(tlp))
    val q_tl_d = Flipped(Decoupled(new OpenTitanTileLink.D_Channel(tlp)))
  })

  class SpiReqDesc extends Bundle {
    val isWrite = Bool()
    val address = UInt(32.W)
    val wdata   = UInt(tlp.dataBits.W)
  }

  val spiReset = !io.spi_rst_n

  // ---------------------------------------------------------------------------
  // Async queues for CDC
  // ---------------------------------------------------------------------------
  val spiToSys = Module(new AsyncQueue(new SpiReqDesc, AsyncQueueParams(depth = 4, safe = false)))
  spiToSys.io.enq_clock := io.spi_clk
  spiToSys.io.enq_reset := spiReset
  spiToSys.io.deq_clock := clock
  spiToSys.io.deq_reset := reset.asBool

  val sysToSpi = Module(new AsyncQueue(UInt(tlp.dataBits.W), AsyncQueueParams(depth = 4, safe = false)))
  sysToSpi.io.enq_clock := clock
  sysToSpi.io.enq_reset := reset.asBool
  sysToSpi.io.deq_clock := io.spi_clk
  sysToSpi.io.deq_reset := spiReset

  // ---------------------------------------------------------------------------
  // SPI clock domain sub-module
  // ---------------------------------------------------------------------------

  /**
    * SpiDomain: all SPI-clocked logic inside a black-box Module so that
    * Chisel sees a clean clock/reset boundary.
    */
  class SpiDomain extends Module {
    val io = IO(new Bundle {
      // MOSI pin
      val mosi_valid = Input(Bool())
      val mosi_bits  = Input(Bool())
      val mosi_ready = Output(Bool())

      // MISO pin
      val miso_valid = Output(Bool())
      val miso_bits  = Output(Bool())
      val miso_ready = Input(Bool())

      // Enqueue to spiToSys
      val enq_valid = Output(Bool())
      val enq_ready = Input(Bool())
      val enq_bits  = Output(new SpiReqDesc)

      // Dequeue from sysToSpi
      val deq_valid = Input(Bool())
      val deq_ready = Output(Bool())
      val deq_bits  = Input(UInt(tlp.dataBits.W))
    })

    // Always accept MOSI
    io.mosi_ready := true.B

    // Default outputs
    io.enq_valid := false.B
    io.enq_bits  := 0.U.asTypeOf(new SpiReqDesc)
    io.deq_ready := false.B
    io.miso_valid := false.B
    io.miso_bits  := false.B

    // Bit accumulator
    val bitCnt = RegInit(0.U(4.W))
    val bitBuf = RegInit(0.U(8.W))

    val byteRdy = WireDefault(false.B)
    val byteVal = WireDefault(0.U(8.W))

    when(io.mosi_valid) {
      val newBuf = Cat(bitBuf(6, 0), io.mosi_bits.asUInt)
      bitBuf := newBuf
      bitCnt := bitCnt + 1.U
      when(bitCnt === 7.U) {
        bitCnt  := 0.U
        byteRdy := true.B
        byteVal := Cat(bitBuf(6, 0), io.mosi_bits.asUInt)
      }
    }

    // Header buffer
    val hdrBuf  = Reg(Vec(7, UInt(8.W)))
    val hdrCnt  = RegInit(0.U(4.W))
    val inHdr   = RegInit(true.B)

    // Payload
    val payBuf  = Reg(Vec(BEAT_BYTES, UInt(8.W)))
    val payCnt  = RegInit(0.U(5.W))

    val isWriteReg  = RegInit(false.B)
    val addrReg     = RegInit(0.U(32.W))
    val numBeatsReg = RegInit(0.U(16.W))
    val beatIdx     = RegInit(0.U(16.W))

    val rdPending = RegInit(false.B)
    val rdBeatIdx = RegInit(0.U(16.W))

    // Byte processing
    when(byteRdy) {
      when(inHdr) {
        hdrBuf(hdrCnt) := byteVal
        hdrCnt := hdrCnt + 1.U
        when(hdrCnt === 6.U) {
          hdrCnt := 0.U
          val op    = hdrBuf(0)
          val addr  = Cat(hdrBuf(1), hdrBuf(2), hdrBuf(3), hdrBuf(4))
          val lf    = Cat(hdrBuf(5), byteVal)
          isWriteReg  := op === 0x02.U
          addrReg     := addr
          numBeatsReg := lf +& 1.U
          beatIdx     := 0.U
          payCnt      := 0.U
          when(op === 0x02.U) {
            inHdr := false.B
          }.otherwise {
            rdPending := true.B
            rdBeatIdx := 0.U
          }
        }
      }.otherwise {
        payBuf(payCnt) := byteVal
        payCnt := payCnt + 1.U
        when(payCnt === (BEAT_BYTES - 1).U) {
          payCnt := 0.U
          val wdata = Cat(
            byteVal,
            payBuf(14), payBuf(13), payBuf(12), payBuf(11),
            payBuf(10), payBuf(9),  payBuf(8),  payBuf(7),
            payBuf(6),  payBuf(5),  payBuf(4),  payBuf(3),
            payBuf(2),  payBuf(1),  payBuf(0)
          )
          val req = Wire(new SpiReqDesc)
          req.isWrite := true.B
          req.address := addrReg + (beatIdx << log2Ceil(BEAT_BYTES).U)
          req.wdata   := wdata
          io.enq_valid := true.B
          io.enq_bits  := req

          beatIdx := beatIdx + 1.U
          when(beatIdx >= numBeatsReg - 1.U) {
            inHdr := true.B
          }
        }
      }
    }

    // Dispatch read requests (only when not processing a byte this cycle)
    when(rdPending && !byteRdy) {
      val req = Wire(new SpiReqDesc)
      req.isWrite := false.B
      req.address := addrReg + (rdBeatIdx << log2Ceil(BEAT_BYTES).U)
      req.wdata   := 0.U
      when(io.enq_ready && !io.enq_valid) {
        io.enq_valid := true.B
        io.enq_bits  := req
        rdBeatIdx := rdBeatIdx + 1.U
        when(rdBeatIdx >= numBeatsReg - 1.U) {
          rdPending := false.B
        }
      }
    }

    // MISO output FSM
    val msIdle :: msSync :: msData :: Nil = Enum(3)
    val misoState   = RegInit(msIdle)
    val misoData    = RegInit(0.U(tlp.dataBits.W))
    val misoByteCnt = RegInit(0.U(5.W))
    val misoBitCnt  = RegInit(0.U(4.W))

    switch(misoState) {
      is(msIdle) {
        io.deq_ready := true.B
        when(io.deq_valid) {
          misoData    := io.deq_bits
          misoBitCnt  := 7.U
          misoByteCnt := 0.U
          misoState   := msSync
        }
      }
      is(msSync) {
        io.miso_valid := true.B
        io.miso_bits  := 0xfe.U(8.W)(misoBitCnt)
        when(io.miso_ready) {
          when(misoBitCnt === 0.U) {
            misoBitCnt  := 7.U
            misoByteCnt := 0.U
            misoState   := msData
          }.otherwise {
            misoBitCnt := misoBitCnt - 1.U
          }
        }
      }
      is(msData) {
        val curByte = (misoData >> (misoByteCnt << 3.U))(7, 0)
        io.miso_valid := true.B
        io.miso_bits  := curByte(misoBitCnt)
        when(io.miso_ready) {
          when(misoBitCnt === 0.U) {
            misoBitCnt := 7.U
            when(misoByteCnt === (BEAT_BYTES - 1).U) {
              misoByteCnt := 0.U
              misoState   := msIdle
            }.otherwise {
              misoByteCnt := misoByteCnt + 1.U
            }
          }.otherwise {
            misoBitCnt := misoBitCnt - 1.U
          }
        }
      }
    }
  }

  // Instantiate SpiDomain with spi_clk and spiReset
  val spiDom = withClockAndReset(io.spi_clk, spiReset) { Module(new SpiDomain) }

  // Wire SpiDomain to module IOs
  io.q_mosi_pin.ready    := spiDom.io.mosi_ready
  spiDom.io.mosi_valid   := io.q_mosi_pin.valid
  spiDom.io.mosi_bits    := io.q_mosi_pin.bits

  io.q_miso_pin.valid    := spiDom.io.miso_valid
  io.q_miso_pin.bits     := spiDom.io.miso_bits
  spiDom.io.miso_ready   := io.q_miso_pin.ready

  // Wire SpiDomain to async queues
  spiToSys.io.enq.valid  := spiDom.io.enq_valid
  spiToSys.io.enq.bits   := spiDom.io.enq_bits
  spiDom.io.enq_ready    := spiToSys.io.enq.ready

  spiDom.io.deq_valid    := sysToSpi.io.deq.valid
  spiDom.io.deq_bits     := sysToSpi.io.deq.bits
  sysToSpi.io.deq.ready  := spiDom.io.deq_ready

  // ---------------------------------------------------------------------------
  // System clock domain: TL-UL transactions
  // ---------------------------------------------------------------------------
  val sIdle :: sIssue :: sWaitD :: Nil = Enum(3)
  val sysState = RegInit(sIdle)

  val pendAddr    = RegInit(0.U(32.W))
  val pendIsWrite = RegInit(false.B)
  val pendWdata   = RegInit(0.U(tlp.dataBits.W))

  io.q_tl_a.valid     := false.B
  io.q_tl_a.bits      := 0.U.asTypeOf(new OpenTitanTileLink.A_Channel(tlp))
  io.q_tl_d.ready     := false.B
  spiToSys.io.deq.ready := false.B
  sysToSpi.io.enq.valid := false.B
  sysToSpi.io.enq.bits  := 0.U

  switch(sysState) {
    is(sIdle) {
      spiToSys.io.deq.ready := true.B
      when(spiToSys.io.deq.valid) {
        pendAddr    := spiToSys.io.deq.bits.address
        pendIsWrite := spiToSys.io.deq.bits.isWrite
        pendWdata   := spiToSys.io.deq.bits.wdata
        sysState    := sIssue
      }
    }

    is(sIssue) {
      io.q_tl_a.valid          := true.B
      io.q_tl_a.bits.opcode    := Mux(pendIsWrite, TLULOpcodesA.PutFullData, TLULOpcodesA.Get)
      io.q_tl_a.bits.param     := 0.U
      io.q_tl_a.bits.size      := log2Ceil(BEAT_BYTES).U
      io.q_tl_a.bits.source    := 0.U
      io.q_tl_a.bits.address   := pendAddr
      io.q_tl_a.bits.mask      := ((BigInt(1) << BEAT_BYTES) - 1).U
      io.q_tl_a.bits.data      := pendWdata
      io.q_tl_a.bits.corrupt   := false.B
      io.q_tl_a.bits.user      := 0.U.asTypeOf(new OpenTitanTileLink_A_User)

      when(io.q_tl_a.ready) {
        sysState := sWaitD
      }
    }

    is(sWaitD) {
      io.q_tl_d.ready := true.B
      when(io.q_tl_d.valid) {
        when(!pendIsWrite) {
          sysToSpi.io.enq.valid := true.B
          sysToSpi.io.enq.bits  := io.q_tl_d.bits.data
        }
        sysState := sIdle
      }
    }
  }
}
