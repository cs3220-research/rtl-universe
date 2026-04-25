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

/** Slim TL-D channel for Spi2TLULV2: omits corrupt/resp/last since the SPI
  * bridge only consumes data and error from the D-channel response.
  */
class Spi2TLULV2_D_Channel(p: TLULParameters) extends Bundle {
  val opcode = UInt(3.W)
  val param  = UInt(3.W)
  val size   = UInt(p.sizeWidth.W)
  val source = UInt(p.sourceWidth.W)
  val sink   = UInt(1.W)
  val data   = UInt(p.dataWidth.W)
  val user   = new OpenTitanTileLink_D_User
  val error  = Bool()
}

/** Slim TL-D channel for Spi2TLULV2: omits corrupt/resp/last since the SPI
  * bridge only consumes data and error from the D-channel response.
  */
class Spi2TLULV2_D_Channel(p: TLULParameters) extends Bundle {
  val opcode = UInt(3.W)
  val param  = UInt(3.W)
  val size   = UInt(p.sizeWidth.W)
  val source = UInt(p.sourceWidth.W)
  val sink   = UInt(1.W)
  val data   = UInt(p.dataWidth.W)
  val user   = new OpenTitanTileLink_D_User
  val error  = Bool()
}

/** SPI-to-TileLink-UL V2 bridge.
  *
  * SPI frame format (MSB-first):
  *   Header (7 bytes):
  *     Byte 0:    opcode  0x01=Read 0x02=Write
  *     Bytes 1-4: 32-bit address big-endian
  *     Bytes 5-6: numBeats-1 (0 = single 16-byte beat)
  *
  *   Write: header + numBeats * 16 bytes of payload
  *   Read:  header; MISO returns per-beat: 0xFE + 16 bytes of TL-D response data
  */
class Spi2TLULV2(p: coralnpu.Parameters) extends Module {
  val tlul_p = new TLULParameters(p)

  val io = IO(new Bundle {
    val spi_clk   = Input(Clock())
    val spi_rst_n = Input(Bool())      // 1 = active (de-asserted)

    val q_mosi_pin = Flipped(Valid(Bool()))
    val q_miso_pin = Decoupled(Bool())

    val q_tl_a = Decoupled(new OpenTitanTileLink.A_Channel(tlul_p))
    val q_tl_d = Flipped(Decoupled(new Spi2TLULV2_D_Channel(tlul_p)))
  })

  val spiRst = !io.spi_rst_n

  // -------------------------------------------------------------------------
  // SPI-clock-domain bit accumulator → generates one byte per 8 SPI posedges
  // -------------------------------------------------------------------------
  val spiBitCntRx   = withClockAndReset(io.spi_clk, spiRst.asAsyncReset) { RegInit(0.U(3.W)) }
  val spiByteShift  = withClockAndReset(io.spi_clk, spiRst.asAsyncReset) { RegInit(0.U(8.W)) }
  val spiByteRdy    = withClockAndReset(io.spi_clk, spiRst.asAsyncReset) { RegInit(false.B) }
  val spiByteOut    = withClockAndReset(io.spi_clk, spiRst.asAsyncReset) { RegInit(0.U(8.W)) }

  withClockAndReset(io.spi_clk, spiRst.asAsyncReset) {
    spiByteRdy := false.B
    when(io.q_mosi_pin.valid) {
      val shifted = Cat(spiByteShift(6, 0), io.q_mosi_pin.bits)
      spiByteShift  := shifted
      spiBitCntRx   := spiBitCntRx + 1.U
      when(spiBitCntRx === 7.U) {
        spiByteOut := shifted
        spiByteRdy := true.B
      }
    }
  }

  // 2-FF synchronizer into sys-clock domain
  val rdy1  = RegNext(spiByteRdy, false.B)
  val rdy2  = RegNext(rdy1, false.B)
  val byte2 = RegNext(RegNext(spiByteOut, 0.U), 0.U)
  val rdy2Prev = RegNext(rdy2, false.B)
  val byteEdge = rdy2 && !rdy2Prev

  // -------------------------------------------------------------------------
  // Header / payload parser (sys clock domain)
  // -------------------------------------------------------------------------
  val pHdr = 0.U(4.W)    // byte 0 = opcode
  val pA3  = 1.U(4.W)    // byte 1 = addr[31:24]
  val pA2  = 2.U(4.W)
  val pA1  = 3.U(4.W)
  val pA0  = 4.U(4.W)    // byte 4 = addr[7:0]
  val pL1  = 5.U(4.W)    // byte 5 = len[15:8]
  val pL0  = 6.U(4.W)    // byte 6 = len[7:0] → trigger
  val pPay = 7.U(4.W)    // payload bytes
  val pWait = 8.U(4.W)   // wait for CSB deassert before accepting next frame

  val parseState = RegInit(pHdr)
  val opcode     = RegInit(0.U(8.W))
  val hdrAddr    = RegInit(0.U(32.W))
  val hdrLenHi   = RegInit(0.U(8.W))
  val numBeats   = RegInit(0.U(17.W))  // total beats (fits 16-bit len + 1)
  val beatsDone  = RegInit(0.U(17.W))
  val curAddr    = RegInit(0.U(32.W))
  val payByte    = RegInit(0.U(5.W))   // 0..15
  val payShift   = RegInit(0.U(128.W))

  // TL-A output
  val tlAValReg = RegInit(false.B)
  val tlABits   = RegInit(0.U.asTypeOf(new OpenTitanTileLink.A_Channel(tlul_p)))

  io.q_tl_a.valid := tlAValReg
  io.q_tl_a.bits  := tlABits
  when(io.q_tl_a.valid && io.q_tl_a.ready) { tlAValReg := false.B }

  // -------------------------------------------------------------------------
  // MISO byte queue (sys-clock domain): filled with 0xFE + 16 data bytes per read beat
  // -------------------------------------------------------------------------
  val misoQ = Module(new Queue(UInt(8.W), 256))
  misoQ.io.enq.valid := false.B
  misoQ.io.enq.bits  := 0.U
  misoQ.io.deq.ready := false.B

  // On TL-D response: push 0xFE + 16 data bytes into misoQ
  val loadRspPending = RegInit(false.B)

  // TL-D: only accept a new response when the current one has been fully loaded
  // into misoQ (back-pressure prevents dropping multi-beat TL-D responses).
  io.q_tl_d.ready := !loadRspPending
  val rspData        = RegInit(0.U(128.W))
  val rspByteCnt     = RegInit(0.U(5.W))  // 0..16 (0=sync byte, 1..16=data bytes)

  when(io.q_tl_d.valid && io.q_tl_d.ready && !loadRspPending) {
    rspData        := io.q_tl_d.bits.data
    rspByteCnt     := 0.U
    loadRspPending := true.B
  }

  when(loadRspPending && misoQ.io.enq.ready) {
    misoQ.io.enq.valid := true.B
    when(rspByteCnt === 0.U) {
      misoQ.io.enq.bits := 0xFE.U   // sync byte
    }.otherwise {
      val byteIdx = rspByteCnt - 1.U
      misoQ.io.enq.bits := (rspData >> (byteIdx << 3))(7, 0)
    }
    rspByteCnt := rspByteCnt + 1.U
    when(rspByteCnt === 16.U) {
      loadRspPending := false.B
    }
  }

  // -------------------------------------------------------------------------
  // MISO bit serialiser
  //
  // Two sys-domain byte slots pre-fetched from misoQ.
  // sysSentCnt: total bytes moved into slots (wrapping 3-bit counter).
  //
  // SPI-domain registers:
  //   spiAckCnt : total bytes fully consumed (3-bit, wrapping).
  //   spiBitCntTx: bits remaining in current byte (0 = idle, 8..1 = active).
  //   spiShift   : current shift register.
  //   spiOutBit  : the bit to output THIS posedge (read by test after step).
  //   spiOutVld  : whether the output is valid this posedge.
  //
  // Protocol:
  //   - "load" posedge (spiBitCntTx == 0 && pending):
  //       spiOutBit := byte(7)               -- output bit7 this posedge
  //       spiShift  := {byte[6:0], 0}        -- pre-shift so next posedge bit6 is at [7]
  //       spiBitCntTx := 7                   -- 7 more bits follow
  //   - "shift" posedge (spiBitCntTx > 0):
  //       spiOutBit := spiShift(7)           -- output current MSB
  //       spiShift  := {spiShift[6:0], 0}    -- shift left
  //       spiBitCntTx -= 1
  //       if (spiBitCntTx was 1):
  //         advance spiAckCnt
  //         if next byte ready: spiShift := nextByte (NOT pre-shifted)
  //                             spiOutBit will be correct since spiShift(7)=nextByte(7)
  //                             spiBitCntTx := 8   (re-enters "shift" path next posedge)
  //                                                 outputting nextByte(7) via spiShift(7)
  //         else: spiOutVld := false
  //
  // Note: the "re-enter shift with spiBitCntTx=8 after last-bit" handles the transition.
  // At the last-bit posedge (spiBitCntTx=1→0 externally):
  //   spiOutBit = spiShift(7) = current byte's bit0 (correct)
  //   spiShift  = nextByte  (loaded for next posedge)
  //   spiBitCntTx = 8 (next posedge enters shift path, outputs nextByte[7])
  //
  // At next posedge (spiBitCntTx=8):
  //   spiOutBit = spiShift(7) = nextByte(7)  -- correct first bit of next byte
  //   spiShift  = {nextByte[6:0], 0}
  //   spiBitCntTx = 7
  // -------------------------------------------------------------------------
  val misoSlot   = RegInit(VecInit(Seq.fill(2)(0.U(8.W))))
  val sysSentCnt = RegInit(0.U(3.W))   // # bytes queued to SPI domain (wrapping)

  // SPI-domain state
  val spiAckCnt   = withClockAndReset(io.spi_clk, spiRst.asAsyncReset) { RegInit(0.U(3.W)) }
  val spiBitCntTx = withClockAndReset(io.spi_clk, spiRst.asAsyncReset) { RegInit(0.U(4.W)) }
  val spiOutBit   = withClockAndReset(io.spi_clk, spiRst.asAsyncReset) { RegInit(false.B) }
  val spiOutVld   = withClockAndReset(io.spi_clk, spiRst.asAsyncReset) { RegInit(false.B) }
  val spiShift    = withClockAndReset(io.spi_clk, spiRst.asAsyncReset) { RegInit(0.U(8.W)) }

  // Direct cross-domain read (simulation OK)
  val inFlight = sysSentCnt - spiAckCnt

  // Sys: eagerly fill up to 2 slots ahead
  when(inFlight < 2.U && misoQ.io.deq.valid) {
    misoQ.io.deq.ready      := true.B
    misoSlot(sysSentCnt(0)) := misoQ.io.deq.bits
    sysSentCnt              := sysSentCnt + 1.U
  }

  // SPI domain: serialise bytes from misoSlot, MSB-first
  withClockAndReset(io.spi_clk, spiRst.asAsyncReset) {
    val pending = (sysSentCnt - spiAckCnt) > 0.U  // direct wire from sys domain

    when(spiBitCntTx > 0.U) {
      // --- Shift posedge ---
      // Output the current MSB of spiShift, then shift left.
      spiOutBit   := spiShift(7)
      spiShift    := Cat(spiShift(6, 0), false.B)
      spiBitCntTx := spiBitCntTx - 1.U
      spiOutVld   := true.B

      when(spiBitCntTx === 1.U) {
        // Last bit of current byte. Advance ack counter and check for next byte.
        val nextAck = spiAckCnt + 1.U
        spiAckCnt := nextAck
        val hasNext = (sysSentCnt - nextAck) > 0.U
        when(hasNext) {
          // Pre-load next byte. On the NEXT posedge (spiBitCntTx=8→7), the
          // shift path will output spiShift(7) = nextByte(7). No gap.
          val nb      = misoSlot(nextAck(0))
          spiShift    := nb     // NOT pre-shifted; shift path reads spiShift(7)=nb(7) next
          spiBitCntTx := 8.U   // restart shift counter for next byte
          spiOutVld   := true.B
        }.otherwise {
          spiBitCntTx := 0.U   // go idle
          spiOutVld   := false.B
        }
      }
    }.elsewhen(pending) {
      // --- Load posedge ---
      // Start a new byte from slot[spiAckCnt(0)].
      // Output bit7 directly; pre-shift spiShift so the NEXT posedge (shift path)
      // sees bit6 at spiShift(7).
      val currSlot = misoSlot(spiAckCnt(0))
      spiOutBit   := currSlot(7)
      spiShift    := Cat(currSlot(6, 0), false.B)  // pre-shifted: spiShift(7)=bit6 next cycle
      spiBitCntTx := 7.U                           // 7 more bits after this one
      spiOutVld   := true.B
    }
  }

  io.q_miso_pin.valid := spiOutVld
  io.q_miso_pin.bits  := spiOutBit

  // -------------------------------------------------------------------------
  // Read beat sequencer
  // -------------------------------------------------------------------------
  val rdPending   = RegInit(false.B)
  val rdBeatsLeft = RegInit(0.U(17.W))
  val rdCurAddr   = RegInit(0.U(32.W))

  when(rdPending && !tlAValReg) {
    tlAValReg          := true.B
    tlABits.opcode     := TLULOpcodesA.Get
    tlABits.param      := 0.U
    tlABits.size       := 4.U    // 16 bytes
    tlABits.source     := 0.U
    tlABits.address    := rdCurAddr
    tlABits.mask       := "hffff".U
    tlABits.data       := 0.U
    tlABits.corrupt    := false.B
    tlABits.user       := 0.U.asTypeOf(new OpenTitanTileLink_A_User)
    rdCurAddr          := rdCurAddr + 16.U
    rdBeatsLeft        := rdBeatsLeft - 1.U
    when(rdBeatsLeft === 1.U) { rdPending := false.B }
  }

  // -------------------------------------------------------------------------
  // Byte-edge driven parser
  // -------------------------------------------------------------------------
  when(byteEdge && !spiRst) {
    val b = byte2
    switch(parseState) {
      is(pHdr) { opcode   := b; parseState := pA3 }
      is(pA3)  { hdrAddr  := Cat(b, 0.U(24.W)); parseState := pA2 }
      is(pA2)  { hdrAddr  := Cat(hdrAddr(31,24), b, 0.U(16.W)); parseState := pA1 }
      is(pA1)  { hdrAddr  := Cat(hdrAddr(31,16), b, 0.U(8.W)); parseState := pA0 }
      is(pA0)  { hdrAddr  := Cat(hdrAddr(31,8), b); parseState := pL1 }
      is(pL1)  { hdrLenHi := b; parseState := pL0 }
      is(pL0)  {
        val total = Cat(hdrLenHi, b).asUInt +& 1.U
        numBeats  := total
        beatsDone := 0.U
        curAddr   := hdrAddr
        payByte   := 0.U
        payShift  := 0.U

        when(opcode === 0x01.U) {
          // Read: start issuing Gets; wait for CSB deassert before next frame
          rdPending   := true.B
          rdBeatsLeft := total
          rdCurAddr   := hdrAddr
          parseState  := pWait
        }.otherwise {
          parseState := pPay
        }
      }
      is(pWait) {
        // Frame complete — ignore all incoming bytes until CSB deasserts.
        // The `when(spiRst)` block below will reset parseState to pHdr.
      }
      is(pPay) {
        // Shift bytes into the HIGH end so the first byte (b0) ends up at
        // the lowest bits of the word — matching TileLink's little-endian
        // byte ordering (data[7:0] = first byte received).
        payShift := Cat(b, payShift(127, 8))
        payByte  := payByte + 1.U
        when(payByte === 15.U) {
          // Beat complete: beatData = {b15, b14, ..., b1, b0} with b0 at [7:0]
          val beatData = Cat(b, payShift(127, 8))
          tlAValReg       := true.B
          tlABits.opcode  := TLULOpcodesA.PutFullData
          tlABits.param   := 0.U
          tlABits.size    := 4.U
          tlABits.source  := 0.U
          tlABits.address := curAddr
          tlABits.mask    := "hffff".U
          tlABits.data    := beatData
          tlABits.corrupt := false.B
          tlABits.user    := 0.U.asTypeOf(new OpenTitanTileLink_A_User)
          curAddr         := curAddr + 16.U
          beatsDone       := beatsDone + 1.U
          payByte         := 0.U
          when(beatsDone + 1.U === numBeats) {
            // Last beat: wait for CSB deassert before accepting next frame
            parseState := pWait
          }
        }
      }
    }
  }

  // SPI reset: go back to idle
  when(spiRst) {
    parseState := pHdr
  }
}
