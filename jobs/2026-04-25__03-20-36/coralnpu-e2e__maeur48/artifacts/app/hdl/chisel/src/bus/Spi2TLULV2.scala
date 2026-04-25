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

/** SPI to TileLink-UL bridge, version 2.
  *
  * Supports multi-beat (burst) transfers.  The SPI clock runs in its own clock
  * domain; an [[AsyncQueue]] is used to cross from the SPI domain into the
  * system TL-UL domain and back.
  *
  * === Frame Format ===
  * {{{
  *   Byte 0    : opcode  (0x01=read, 0x02=write)
  *   Bytes 1-4 : base address (big-endian, 32-bit)
  *   Bytes 5-6 : num_beats - 1  (big-endian)
  *   Bytes 7+  : payload (write: num_beats × 16 bytes; read: N/A)
  * }}}
  *
  * === Read response ===
  * Each 128-bit TL-UL response beat is preceded by a 0xFE sync byte on MISO.
  * This allows the host to align its bit stream.
  *
  * @param p  Project-wide parameters (provides dataBits = lsuDataBits).
  */
class Spi2TLULV2(p: coralnpu.Parameters) extends Module {
  val tlulP = new TLULParameters(p)

  val io = IO(new Bundle {
    // SPI clock and reset (from external SPI master)
    val spi_clk   = Input(Clock())
    val spi_rst_n = Input(Bool())   // active-low reset, typically driven by ~CSB

    // MOSI from SPI master — presented as a Decoupled 1-bit stream
    val q_mosi_pin = Flipped(Decoupled(Bool()))
    // MISO to SPI master — driven as a Decoupled 1-bit stream
    val q_miso_pin = Decoupled(Bool())

    // TL-UL host-facing channels (system clock domain)
    val q_tl_a = Decoupled(new OpenTitanTileLink.A_Channel(tlulP))
    val q_tl_d = Flipped(Decoupled(new OpenTitanTileLink.D_Channel(tlulP)))
  })

  // -------------------------------------------------------------------------
  // SPI-domain shift register and frame parser
  // -------------------------------------------------------------------------
  // All shift-register logic runs in the SPI clock domain.
  // MOSI bits arrive as a Decoupled 1-bit stream (handshake with q_mosi_pin).

  val dataBits   = p.lsuDataBits
  val hdrBytes   = 7   // opcode(1) + addr(4) + len(2)
  val payBytes   = dataBits / 8   // bytes per beat

  // --- SPI-domain FIFOs (system-clock side) ---
  // A-channel request FIFO: SPI domain writes; system clock reads via q_tl_a
  val aTxFifo = Module(
    new AsyncQueue(new OpenTitanTileLink.A_Channel(tlulP), AsyncQueueParams(depth = 8, sync = 2))
  )
  // D-channel response FIFO: system clock writes; SPI domain reads for MISO
  val dRxFifo = Module(
    new AsyncQueue(new OpenTitanTileLink.D_Channel(tlulP), AsyncQueueParams(depth = 8, sync = 2))
  )

  // Clocks
  aTxFifo.io.enq_clock := io.spi_clk
  aTxFifo.io.enq_reset := !io.spi_rst_n
  aTxFifo.io.deq_clock := clock
  aTxFifo.io.deq_reset := reset.asBool

  dRxFifo.io.enq_clock := clock
  dRxFifo.io.enq_reset := reset.asBool
  dRxFifo.io.deq_clock := io.spi_clk
  dRxFifo.io.deq_reset := !io.spi_rst_n

  // System-clock side wiring
  io.q_tl_a <> aTxFifo.io.deq
  dRxFifo.io.enq <> io.q_tl_d

  // -------------------------------------------------------------------------
  // SPI-domain logic (withClockAndReset)
  // -------------------------------------------------------------------------
  // All registers here are in the SPI clock domain.
  val spiLogic = withClockAndReset(io.spi_clk, (!io.spi_rst_n).asAsyncReset) {

    // -----------------------------------------------------------------------
    // SPI receive shift register (8-bit byte boundary)
    // -----------------------------------------------------------------------
    val shiftReg   = RegInit(0.U(8.W))
    val bitCnt     = RegInit(0.U(3.W))
    val byteValid  = Wire(Bool())
    byteValid := false.B

    // Consume one bit per SPI clock from q_mosi_pin
    io.q_mosi_pin.ready := true.B
    when(io.q_mosi_pin.valid) {
      shiftReg := Cat(shiftReg(6, 0), io.q_mosi_pin.bits)
      bitCnt   := bitCnt + 1.U
      when(bitCnt === 7.U) {
        byteValid := true.B
      }
    }

    // -----------------------------------------------------------------------
    // Header / payload state machine
    // -----------------------------------------------------------------------
    val sHdr0 :: sHdr1 :: sHdr2 :: sHdr3 :: sHdr4 :: sHdr5 :: sHdr6 ::
        sPayload :: sDone :: Nil = Enum(9)

    val fsmState  = RegInit(sHdr0)
    val spiOpcode = RegInit(0.U(8.W))
    val spiAddr   = RegInit(0.U(32.W))
    val spiLen    = RegInit(0.U(16.W))  // num_beats - 1
    val beatCnt   = RegInit(0.U(16.W))
    // Payload accumulator: collect dataBits/8 bytes then push to TL
    val payBuf    = RegInit(0.U(dataBits.W))
    val payByteCnt = RegInit(0.U(5.W))  // up to 16 bytes per beat

    // FIFO enqueue side (SPI domain → A-channel FIFO)
    aTxFifo.io.enq.valid        := false.B
    aTxFifo.io.enq.bits         := 0.U.asTypeOf(new OpenTitanTileLink.A_Channel(tlulP))

    when(byteValid) {
      switch(fsmState) {
        is(sHdr0) {
          spiOpcode   := shiftReg
          fsmState    := sHdr1
        }
        is(sHdr1) {
          spiAddr     := Cat(0.U(24.W), shiftReg)
          fsmState    := sHdr2
        }
        is(sHdr2) {
          spiAddr     := Cat(spiAddr(7, 0), shiftReg, 0.U(16.W))
          fsmState    := sHdr3
        }
        is(sHdr3) {
          spiAddr     := Cat(spiAddr(15, 0), shiftReg, 0.U(8.W))
          fsmState    := sHdr4
        }
        is(sHdr4) {
          spiAddr     := Cat(spiAddr(23, 0), shiftReg)
          fsmState    := sHdr5
        }
        is(sHdr5) {
          spiLen      := Cat(0.U(8.W), shiftReg)
          fsmState    := sHdr6
        }
        is(sHdr6) {
          spiLen      := Cat(spiLen(7, 0), shiftReg)
          beatCnt     := 0.U
          payByteCnt  := 0.U
          payBuf      := 0.U
          // For reads: push Get requests immediately (no payload)
          when(spiOpcode === 0x01.U) {
            fsmState  := sDone  // read requests pushed separately below
          }.otherwise {
            fsmState  := sPayload
          }
        }
        is(sPayload) {
          // Accumulate bytes into payBuf (LSB-first per byte, big-endian across beats)
          payBuf      := Cat(shiftReg, payBuf(dataBits - 1, 8))
          payByteCnt  := payByteCnt + 1.U
          when(payByteCnt === (payBytes - 1).U) {
            // Full beat accumulated
            val a = Wire(new OpenTitanTileLink.A_Channel(tlulP))
            a.opcode  := TLULOpcodesA.PutFullData.asUInt
            a.param   := 0.U
            a.size    := log2Ceil(payBytes).U
            a.source  := 0.U
            a.address := spiAddr + (beatCnt * payBytes.U)
            a.mask    := Fill(payBytes, 1.U)
            a.data    := Cat(shiftReg, payBuf(dataBits - 1, 8))
            a.user    := 0.U.asTypeOf(new OpenTitanTileLink_A_User)
            a.corrupt := false.B
            aTxFifo.io.enq.valid := true.B
            aTxFifo.io.enq.bits  := a
            payByteCnt := 0.U
            payBuf     := 0.U
            when(beatCnt === spiLen) {
              fsmState := sDone
            }.otherwise {
              beatCnt  := beatCnt + 1.U
            }
          }
        }
        is(sDone) {
          // Frame complete; stay here until spi_rst_n deasserts (next CSB)
        }
      }
    }

    // For reads: push Get requests every beat as long as beatCnt <= spiLen
    val readPending = RegInit(false.B)
    val readBeat    = RegInit(0.U(16.W))

    when(fsmState === sDone && spiOpcode === 0x01.U && readBeat <= spiLen && !readPending) {
      val a = Wire(new OpenTitanTileLink.A_Channel(tlulP))
      a.opcode  := TLULOpcodesA.Get.asUInt
      a.param   := 0.U
      a.size    := log2Ceil(payBytes).U
      a.source  := 0.U
      a.address := spiAddr + (readBeat * payBytes.U)
      a.mask    := Fill(payBytes, 1.U)
      a.data    := 0.U
      a.user    := 0.U.asTypeOf(new OpenTitanTileLink_A_User)
      a.corrupt := false.B
      aTxFifo.io.enq.valid := true.B
      aTxFifo.io.enq.bits  := a
      when(aTxFifo.io.enq.ready) {
        readBeat := readBeat + 1.U
      }
    }

    // Reset read state when frame ends
    when(fsmState === sHdr0) {
      readBeat    := 0.U
      readPending := false.B
    }

    // -----------------------------------------------------------------------
    // MISO output: drain D-channel FIFO; prefix each beat with 0xFE sync
    // -----------------------------------------------------------------------
    // TX shift register
    val misoShift  = RegInit(0.U(dataBits.W))
    val misoBitCnt = RegInit(0.U(8.W))
    val misoSyncSent = RegInit(false.B)

    val misoBeatValid = dRxFifo.io.deq.valid
    dRxFifo.io.deq.ready := false.B

    val misoBit = Wire(Bool())
    misoBit := false.B

    io.q_miso_pin.valid := false.B
    io.q_miso_pin.bits  := false.B

    when(misoBeatValid) {
      io.q_miso_pin.valid := true.B
      when(!misoSyncSent) {
        // Shift out 0xFE sync byte (MSB first)
        io.q_miso_pin.bits := ("hFE".U >> (7.U - misoBitCnt(2, 0)))(0)
        when(io.q_miso_pin.ready) {
          when(misoBitCnt(2, 0) === 7.U) {
            misoSyncSent := true.B
            misoShift    := dRxFifo.io.deq.bits.data
            misoBitCnt   := 0.U
          }.otherwise {
            misoBitCnt := misoBitCnt + 1.U
          }
        }
      }.otherwise {
        // Shift out data beat (MSB-first, byte 0 first)
        io.q_miso_pin.bits := misoShift(dataBits - 1)
        when(io.q_miso_pin.ready) {
          misoShift  := misoShift << 1
          misoBitCnt := misoBitCnt + 1.U
          when(misoBitCnt === (dataBits - 1).U) {
            dRxFifo.io.deq.ready := true.B
            misoSyncSent := false.B
            misoBitCnt   := 0.U
          }
        }
      }
    }

    // Return dummy unit (withClockAndReset requires an expression)
    ()
  }
}
