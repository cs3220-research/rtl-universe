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
// Extended D-channel bundle for Spi2TLULV2
//
// The V2 bridge uses an extended TL-D encoding that includes explicit user
// and error fields on top of the standard TLChannelD fields.
// ---------------------------------------------------------------------------

/** Extended D-channel response bundle used by Spi2TLULV2.
  *
  * Mirrors the OpenTitan full-integrity TL-D encoding:
  *   opcode(3) + param(3) + size(sizeWidth) + source(sourceWidth) +
  *   sink(1) + data(dataWidth) + user(14) + error(1)
  */
class Spi2TLULV2_D_Channel(p: TLULParameters) extends Bundle {
  val opcode  = UInt(3.W)
  val param   = UInt(3.W)
  val size    = UInt(p.sizeWidth.W)
  val source  = UInt(p.sourceWidth.W)
  val sink    = UInt(1.W)
  val data    = UInt(p.dataWidth.W)
  val user    = new OpenTitanTileLink_D_User
  val error   = UInt(1.W)
}

/** SPI-to-TL-UL bridge (v2).
  *
  * Converts SPI frame transactions into TL-UL A-channel requests and returns
  * TL-UL D-channel responses as MISO bit-stream data.
  *
  * SPI frame format (MSB first):
  *   Byte 0       : opcode (0x01 = read, 0x02 = write)
  *   Bytes 1–4    : 32-bit address (big-endian)
  *   Bytes 5–6    : number of 16-byte beats minus 1 (big-endian)
  *   Bytes 7+     : write payload (for write frames; 16 bytes per beat)
  *
  * MISO response (read frames): per beat, a 0xFE sync byte followed by
  * 16 bytes of TL-D response data.
  *
  * The queued IO (`q_mosi_pin`, `q_miso_pin`, `q_tl_a`, `q_tl_d`) are all in
  * the system clock domain.  The SPI clock and reset are provided as separate
  * inputs for instantiation into a CDC bridge; for simulation purposes the
  * receive logic runs in the system domain.
  *
  * @param p coralnpu system parameters (drives data-width, etc.)
  */
class Spi2TLULV2(p: coralnpu.Parameters) extends Module {
  private val tlP = TLULParameters(p)

  val io = IO(new Bundle {
    // SPI physical signals (async to system clock)
    val spi_clk   = Input(Clock())
    val spi_rst_n = Input(Bool())

    // Queued MOSI / MISO bit streams (system clock domain)
    val q_mosi_pin = Flipped(Decoupled(Bool()))
    val q_miso_pin = Decoupled(Bool())

    // Queued TL-UL channels (system clock domain)
    val q_tl_a = Decoupled(new TLChannelA(tlP))
    val q_tl_d = Flipped(Decoupled(new Spi2TLULV2_D_Channel(tlP)))
  })

  // -------------------------------------------------------------------------
  // Constants
  // -------------------------------------------------------------------------
  val HEADER_BITS  = 56           // 7 header bytes × 8 bits
  val BEAT_BITS    = tlP.dataWidth // 128 bits = 16 bytes
  val BEAT_BYTES   = BEAT_BITS / 8
  // MISO: 1 sync byte (0xFE) + BEAT_BYTES data bytes, all shifted MSB-first
  val MISO_BITS_PER_BEAT = 8 + BEAT_BITS

  // -------------------------------------------------------------------------
  // Receiver state machine (runs in system clock domain)
  //
  // In a full implementation the SPI clock domain would be used; here we
  // model it as edge-triggered in the system domain using the MOSI queue.
  // -------------------------------------------------------------------------
  val sHdr :: sData :: sRespond :: Nil = Enum(3)
  val state = RegInit(sHdr)

  // Header shift register (56 bits accumulate MSB-first).
  val hdrSreg  = RegInit(0.U(HEADER_BITS.W))
  val bitCount = RegInit(0.U(8.W))

  // Decoded and latched header fields.
  val hdrOp   = RegInit(0.U(8.W))   // 0x01=read, 0x02=write
  val hdrAddr = RegInit(0.U(32.W))
  val hdrLen  = RegInit(0.U(16.W))  // number of beats - 1

  // Write data accumulator.
  val dataSreg   = RegInit(0.U(BEAT_BITS.W))
  val dataBitCnt = RegInit(0.U(8.W))
  val beatIdx    = RegInit(0.U(16.W))

  // MISO shift register for read responses.
  val misoSreg   = RegInit(0.U(MISO_BITS_PER_BEAT.W))
  val misoBits   = RegInit(0.U(9.W))   // bits remaining to send
  val misoBeatIdx = RegInit(0.U(16.W)) // beats output so far

  // Outgoing TL-A request latch.
  val tlAValid = RegInit(false.B)
  val tlABits  = RegInit(0.U.asTypeOf(new TLChannelA(tlP)))

  // Pending TL-D response.
  val tlDPending = RegInit(false.B)
  val tlDData    = RegInit(0.U(BEAT_BITS.W))

  // -------------------------------------------------------------------------
  // Reset on frame boundary (spi_rst_n low = frame inactive)
  // -------------------------------------------------------------------------
  when(!io.spi_rst_n) {
    state      := sHdr
    bitCount   := 0.U
    dataBitCnt := 0.U
    beatIdx    := 0.U
    hdrSreg    := 0.U
    dataSreg   := 0.U
    tlDPending := false.B
    misoBits   := 0.U
    misoBeatIdx := 0.U
  }

  // -------------------------------------------------------------------------
  // Bit receive: consume one MOSI bit per system cycle when active.
  // -------------------------------------------------------------------------
  io.q_mosi_pin.ready := io.spi_rst_n && (state === sHdr || state === sData)

  when(io.q_mosi_pin.fire) {
    val bit = io.q_mosi_pin.bits

    when(state === sHdr) {
      hdrSreg  := Cat(hdrSreg(HEADER_BITS - 2, 0), bit)
      bitCount := bitCount + 1.U
      when(bitCount === (HEADER_BITS - 1).U) {
        val hdr = Cat(hdrSreg(HEADER_BITS - 2, 0), bit)
        hdrOp   := hdr(HEADER_BITS - 1, HEADER_BITS - 8)
        hdrAddr := hdr(HEADER_BITS - 9, HEADER_BITS - 40)
        hdrLen  := hdr(15, 0)
        bitCount   := 0.U
        dataBitCnt := 0.U
        beatIdx    := 0.U
        dataSreg   := 0.U

        when(hdr(HEADER_BITS - 1, HEADER_BITS - 8) === 0x01.U) {
          // Read frame: emit first TL-A Get immediately after header.
          tlAValid       := true.B
          tlABits.opcode  := TLULOpcodesA.Get
          tlABits.param   := 0.U
          tlABits.size    := 4.U
          tlABits.source  := 0.U
          tlABits.address := hdr(HEADER_BITS - 9, HEADER_BITS - 40)
          tlABits.mask    := ~0.U(tlP.maskWidth.W)
          tlABits.data    := 0.U
          tlABits.corrupt := false.B
          misoBeatIdx := 0.U
          state := sRespond
        } .otherwise {
          state := sData
        }
      }
    } .elsewhen(state === sData) {
      dataSreg   := Cat(dataSreg(BEAT_BITS - 2, 0), bit)
      dataBitCnt := dataBitCnt + 1.U
      when(dataBitCnt === (BEAT_BITS - 1).U) {
        // Full 128-bit beat received (big-endian SPI → little-endian TL-UL).
        val rawBeat = Cat(dataSreg(BEAT_BITS - 2, 0), bit)
        // Reverse byte order for little-endian.
        val leBytes = Wire(Vec(BEAT_BYTES, UInt(8.W)))
        for (i <- 0 until BEAT_BYTES) {
          leBytes(i) := rawBeat(BEAT_BITS - 1 - i * 8, BEAT_BITS - 8 - i * 8)
        }
        tlAValid       := true.B
        tlABits.opcode  := TLULOpcodesA.PutFullData
        tlABits.param   := 0.U
        tlABits.size    := 4.U
        tlABits.source  := 0.U
        tlABits.address := hdrAddr + Cat(beatIdx, 0.U(4.W))
        tlABits.mask    := ~0.U(tlP.maskWidth.W)
        tlABits.data    := Cat(leBytes.reverse)
        tlABits.corrupt := false.B
        dataBitCnt := 0.U
        dataSreg   := 0.U
        beatIdx    := beatIdx + 1.U
      }
    }
  }

  // -------------------------------------------------------------------------
  // TL-A output
  // -------------------------------------------------------------------------
  io.q_tl_a.valid := tlAValid
  io.q_tl_a.bits  := tlABits
  when(io.q_tl_a.fire) { tlAValid := false.B }

  // -------------------------------------------------------------------------
  // TL-D input (for read responses)
  // -------------------------------------------------------------------------
  io.q_tl_d.ready := !tlDPending

  when(io.q_tl_d.fire) {
    // Reverse to big-endian for MISO output.
    val d = io.q_tl_d.bits.data
    val beBytes = Wire(Vec(BEAT_BYTES, UInt(8.W)))
    for (i <- 0 until BEAT_BYTES) {
      beBytes(i) := d(i * 8 + 7, i * 8)
    }
    // Prepend 0xFE sync byte then load into shift register.
    misoSreg   := Cat(0xfe.U(8.W), Cat(beBytes.reverse))
    misoBits   := MISO_BITS_PER_BEAT.U
    tlDPending := true.B  // keep pending until all bits are shifted out
  }

  // -------------------------------------------------------------------------
  // MISO output: shift out bits MSB first when tlDPending.
  // -------------------------------------------------------------------------
  io.q_miso_pin.valid := tlDPending
  io.q_miso_pin.bits  := misoSreg(MISO_BITS_PER_BEAT - 1)

  when(io.q_miso_pin.fire) {
    misoSreg := Cat(misoSreg(MISO_BITS_PER_BEAT - 2, 0), false.B)
    misoBits := misoBits - 1.U
    when(misoBits <= 1.U) {
      tlDPending  := false.B
      misoBeatIdx := misoBeatIdx + 1.U
      // For multi-beat reads: trigger next Get.
      when(hdrOp === 0x01.U && misoBeatIdx < hdrLen) {
        tlAValid       := true.B
        tlABits.opcode  := TLULOpcodesA.Get
        tlABits.param   := 0.U
        tlABits.size    := 4.U
        tlABits.source  := 0.U
        tlABits.address := hdrAddr + Cat((misoBeatIdx + 1.U), 0.U(4.W))
        tlABits.mask    := ~0.U(tlP.maskWidth.W)
        tlABits.data    := 0.U
        tlABits.corrupt := false.B
      }
    }
  }
}
