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

/** Descriptor-based DMA engine.
  *
  * ===CSR Register Map (32-bit, device TL-UL port)===
  * {{{
  * 0x00 : CTRL   (R/W)  bit[0]=enable, bit[1]=start, bit[2]=abort
  * 0x04 : STATUS (R)    bit[0]=busy, bit[1]=done, bit[2]=error
  * 0x08 : DESC_ADDR (R/W) — address of first descriptor in memory
  * 0x0C : (reserved)
  * }}}
  *
  * ===Descriptor layout (32 bytes, read via host TL-UL port)===
  * {{{
  * +0x00 : src_addr    (32-bit)
  * +0x04 : dst_addr    (32-bit)
  * +0x08 : flags       — bits[23:0]=xfer_len (bytes), bits[26:24]=xfer_width (log2 bytes per beat)
  *                        bit[27]=src_fixed, bit[28]=dst_fixed, bit[29]=poll_en
  * +0x0C : next_desc   (32-bit, 0 = end of chain)
  * +0x10 : poll_addr   (32-bit)
  * +0x14 : poll_mask   (32-bit)
  * +0x18 : poll_value  (32-bit)
  * +0x1C : (reserved)
  * }}}
  *
  * @param hostP    Parameters for the host (memory-access) TL-UL port.
  * @param deviceP  Parameters for the device (CSR-access) TL-UL port.
  */
class DmaEngine(hostP: coralnpu.Parameters, deviceP: coralnpu.Parameters) extends Module {
  val hostTlulP   = new TLULParameters(hostP)
  val deviceTlulP = new TLULParameters(new coralnpu.Parameters {
    lsuDataBits = 32
  })

  val io = IO(new Bundle {
    // Host port: DMA reads/writes memory via this TL-UL host channel
    val tl_host   = new OpenTitanTileLink.Host2Device(hostTlulP)
    // Device port: CPU programs the DMA engine via this TL-UL device channel
    val tl_device = new OpenTitanTileLink.Device2Host(deviceTlulP)
  })

  // -------------------------------------------------------------------------
  // CSR registers
  // -------------------------------------------------------------------------
  val regCtrl     = RegInit(0.U(32.W))
  val regStatus   = RegInit(0.U(32.W))
  val regDescAddr = RegInit(0.U(32.W))

  val ctrlEnable = regCtrl(0)
  val ctrlStart  = regCtrl(1)
  val ctrlAbort  = regCtrl(2)

  // -------------------------------------------------------------------------
  // CSR TL-UL device interface
  // -------------------------------------------------------------------------
  val csrFire  = io.tl_device.a.valid && io.tl_device.d.ready
  val csrAddr  = io.tl_device.a.bits.address(7, 0)
  val csrData  = io.tl_device.a.bits.data
  val csrWrite = (io.tl_device.a.bits.opcode === TLULOpcodesA.PutFullData.asUInt ||
                  io.tl_device.a.bits.opcode === TLULOpcodesA.PutPartialData.asUInt)

  val csrRdata = Wire(UInt(32.W))
  csrRdata := 0.U
  val csrError = Wire(Bool())
  csrError := false.B

  when(csrFire) {
    when(csrAddr === 0x00.U) {
      when(csrWrite) { regCtrl := csrData }
      .otherwise     { csrRdata := regCtrl }
    }.elsewhen(csrAddr === 0x04.U) {
      when(csrWrite) { csrError := true.B } // STATUS is read-only
      .otherwise     { csrRdata := regStatus }
    }.elsewhen(csrAddr === 0x08.U) {
      when(csrWrite) { regDescAddr := csrData }
      .otherwise     { csrRdata := regDescAddr }
    }.otherwise {
      csrError := true.B
    }
  }

  val csrRdataReg = RegNext(csrRdata)
  val csrErrorReg = RegNext(csrError)
  val csrSrcReg   = RegNext(io.tl_device.a.bits.source)
  val csrSizeReg  = RegNext(io.tl_device.a.bits.size)
  val csrIsGetReg = RegNext(!csrWrite)
  val csrValidReg = RegNext(csrFire, false.B)

  io.tl_device.a.ready := io.tl_device.d.ready
  io.tl_device.d.valid := csrValidReg

  io.tl_device.d.bits.opcode  := Mux(csrIsGetReg, TLULOpcodesD.AccessAckData.asUInt, TLULOpcodesD.AccessAck.asUInt)
  io.tl_device.d.bits.param   := 0.U
  io.tl_device.d.bits.size    := csrSizeReg
  io.tl_device.d.bits.source  := csrSrcReg
  io.tl_device.d.bits.sink    := 0.U
  io.tl_device.d.bits.data    := csrRdataReg
  io.tl_device.d.bits.user    := 0.U.asTypeOf(new OpenTitanTileLink_D_User)
  io.tl_device.d.bits.error   := csrErrorReg
  io.tl_device.d.bits.corrupt := false.B

  // -------------------------------------------------------------------------
  // DMA state machine
  // -------------------------------------------------------------------------
  // States
  val sDmaIdle :: sDmaFetchDesc :: sDmaFetchDescWait :: sDmaPollRead ::
    sDmaPollWait :: sDmaRead :: sDmaReadWait :: sDmaWrite :: sDmaWriteWait ::
    sDmaDone :: Nil = Enum(10)

  val dmaState = RegInit(sDmaIdle)

  // Descriptor fields (latched after fetch)
  val descSrcAddr  = RegInit(0.U(32.W))
  val descDstAddr  = RegInit(0.U(32.W))
  val descFlags    = RegInit(0.U(32.W))
  val descNextDesc = RegInit(0.U(32.W))
  val descPollAddr = RegInit(0.U(32.W))
  val descPollMask = RegInit(0.U(32.W))
  val descPollVal  = RegInit(0.U(32.W))

  val xferLen    = descFlags(23, 0)
  val xferWidth  = descFlags(26, 24)   // log2(bytes per beat)
  val srcFixed   = descFlags(27)
  val dstFixed   = descFlags(28)
  val pollEn     = descFlags(29)

  val bytesPerBeat = Wire(UInt(32.W))
  bytesPerBeat := 1.U << xferWidth

  // Transfer state
  val curSrcAddr   = RegInit(0.U(32.W))
  val curDstAddr   = RegInit(0.U(32.W))
  val bytesLeft    = RegInit(0.U(24.W))
  val readBuf      = RegInit(0.U(hostP.lsuDataBits.W))

  // Descriptor fetch counter (fetches 8 words × 4 bytes = 32 bytes)
  val descFetchCnt = RegInit(0.U(4.W))
  val descFetchBuf = Reg(Vec(8, UInt(32.W)))

  // Current descriptor address
  val curDescAddr  = RegInit(0.U(32.W))

  // Host TL-UL port
  io.tl_host.a.valid    := false.B
  io.tl_host.a.bits     := 0.U.asTypeOf(new OpenTitanTileLink.A_Channel(hostTlulP))
  io.tl_host.d.ready    := false.B

  // Status update helper
  def setBusy():  Unit = { regStatus := "b001".U }
  def setDone():  Unit = { regStatus := "b010".U }
  def setError(): Unit = { regStatus := "b100".U }

  // -------------------------------------------------------------------------
  // DMA engine activation: triggered by CTRL.start (bit 1)
  // -------------------------------------------------------------------------
  when(ctrlStart && (dmaState === sDmaIdle) && !regStatus(0)) {
    // Clear done/error, set busy
    regStatus    := "b001".U
    curDescAddr  := regDescAddr
    descFetchCnt := 0.U
    dmaState     := sDmaFetchDesc
  }

  // Abort handling
  when(ctrlAbort && (dmaState =/= sDmaIdle)) {
    dmaState  := sDmaIdle
    setError()
    regCtrl   := regCtrl & ~(1.U << 2)  // clear abort bit
  }

  // -------------------------------------------------------------------------
  // Descriptor fetch: issue 8 sequential 32-bit TL-UL Get requests
  // -------------------------------------------------------------------------
  switch(dmaState) {
    is(sDmaFetchDesc) {
      // Issue a 32-bit read for descriptor word descFetchCnt
      val addr = curDescAddr + (descFetchCnt << 2)
      io.tl_host.a.valid          := true.B
      io.tl_host.a.bits.opcode    := TLULOpcodesA.Get.asUInt
      io.tl_host.a.bits.param     := 0.U
      io.tl_host.a.bits.size      := 2.U  // 4 bytes
      io.tl_host.a.bits.source    := 0.U
      io.tl_host.a.bits.address   := addr(31, 0)
      io.tl_host.a.bits.mask      := 0xf.U.pad(hostTlulP.maskBits)
      io.tl_host.a.bits.data      := 0.U
      io.tl_host.a.bits.user      := 0.U.asTypeOf(new OpenTitanTileLink_A_User)
      io.tl_host.a.bits.corrupt   := false.B
      when(io.tl_host.a.ready) {
        dmaState := sDmaFetchDescWait
      }
    }

    is(sDmaFetchDescWait) {
      io.tl_host.d.ready := true.B
      when(io.tl_host.d.valid) {
        when(io.tl_host.d.bits.error) {
          setError()
          dmaState := sDmaIdle
        }.otherwise {
          // Extract 32 LSBs of the response (bus may be wider)
          val wordData = io.tl_host.d.bits.data(31, 0)
          descFetchBuf(descFetchCnt) := wordData
          when(descFetchCnt === 7.U) {
            // All 8 words fetched — latch descriptor fields
            descSrcAddr  := descFetchBuf(0)
            descDstAddr  := descFetchBuf(1)
            descFlags    := descFetchBuf(2)
            descNextDesc := descFetchBuf(3)
            descPollAddr := descFetchBuf(4)
            descPollMask := descFetchBuf(5)
            descPollVal  := descFetchBuf(6)
            // Latch the last word (index 7) which was just received
            // (descFetchBuf(7) will be set by assignment above on next cycle;
            //  use wordData directly for word 7)
            curSrcAddr   := descFetchBuf(0)
            curDstAddr   := descFetchBuf(1)
            bytesLeft    := descFetchBuf(2)(23, 0)
            descFetchCnt := 0.U
            dmaState     := Mux(descFetchBuf(2)(29), sDmaPollRead, sDmaRead)
          }.otherwise {
            descFetchCnt := descFetchCnt + 1.U
            dmaState     := sDmaFetchDesc
          }
        }
      }
    }

    is(sDmaPollRead) {
      // Read from poll_addr (32-bit)
      io.tl_host.a.valid          := true.B
      io.tl_host.a.bits.opcode    := TLULOpcodesA.Get.asUInt
      io.tl_host.a.bits.param     := 0.U
      io.tl_host.a.bits.size      := 2.U
      io.tl_host.a.bits.source    := 0.U
      io.tl_host.a.bits.address   := descPollAddr
      io.tl_host.a.bits.mask      := 0xf.U.pad(hostTlulP.maskBits)
      io.tl_host.a.bits.data      := 0.U
      io.tl_host.a.bits.user      := 0.U.asTypeOf(new OpenTitanTileLink_A_User)
      io.tl_host.a.bits.corrupt   := false.B
      when(io.tl_host.a.ready) {
        dmaState := sDmaPollWait
      }
    }

    is(sDmaPollWait) {
      io.tl_host.d.ready := true.B
      when(io.tl_host.d.valid) {
        val pollResult = io.tl_host.d.bits.data(31, 0) & descPollMask
        when(pollResult === (descPollVal & descPollMask)) {
          dmaState := sDmaRead
        }.otherwise {
          dmaState := sDmaPollRead  // retry
        }
      }
    }

    is(sDmaRead) {
      when(bytesLeft === 0.U) {
        // Transfer complete for this descriptor
        when(descNextDesc =/= 0.U) {
          // Chain to next descriptor
          curDescAddr  := descNextDesc
          descFetchCnt := 0.U
          dmaState     := sDmaFetchDesc
        }.otherwise {
          setDone()
          dmaState := sDmaIdle
        }
      }.otherwise {
        // Issue a read of beatWidth bytes from source
        val beatSize = xferWidth
        io.tl_host.a.valid          := true.B
        io.tl_host.a.bits.opcode    := TLULOpcodesA.Get.asUInt
        io.tl_host.a.bits.param     := 0.U
        io.tl_host.a.bits.size      := beatSize
        io.tl_host.a.bits.source    := 0.U
        io.tl_host.a.bits.address   := curSrcAddr
        io.tl_host.a.bits.mask      := Fill(hostTlulP.maskBits, 1.U)
        io.tl_host.a.bits.data      := 0.U
        io.tl_host.a.bits.user      := 0.U.asTypeOf(new OpenTitanTileLink_A_User)
        io.tl_host.a.bits.corrupt   := false.B
        when(io.tl_host.a.ready) {
          dmaState := sDmaReadWait
        }
      }
    }

    is(sDmaReadWait) {
      io.tl_host.d.ready := true.B
      when(io.tl_host.d.valid) {
        when(io.tl_host.d.bits.error) {
          setError()
          dmaState := sDmaIdle
        }.otherwise {
          readBuf  := io.tl_host.d.bits.data
          // Advance source address (unless fixed)
          when(!srcFixed) {
            curSrcAddr := curSrcAddr + bytesPerBeat
          }
          dmaState := sDmaWrite
        }
      }
    }

    is(sDmaWrite) {
      // Issue write to destination
      val beatSize = xferWidth
      io.tl_host.a.valid          := true.B
      io.tl_host.a.bits.opcode    := TLULOpcodesA.PutFullData.asUInt
      io.tl_host.a.bits.param     := 0.U
      io.tl_host.a.bits.size      := beatSize
      io.tl_host.a.bits.source    := 0.U
      io.tl_host.a.bits.address   := curDstAddr
      io.tl_host.a.bits.mask      := Fill(hostTlulP.maskBits, 1.U)
      io.tl_host.a.bits.data      := readBuf
      io.tl_host.a.bits.user      := 0.U.asTypeOf(new OpenTitanTileLink_A_User)
      io.tl_host.a.bits.corrupt   := false.B
      when(io.tl_host.a.ready) {
        dmaState := sDmaWriteWait
      }
    }

    is(sDmaWriteWait) {
      io.tl_host.d.ready := true.B
      when(io.tl_host.d.valid) {
        when(io.tl_host.d.bits.error) {
          setError()
          dmaState := sDmaIdle
        }.otherwise {
          // Advance destination address (unless fixed)
          when(!dstFixed) {
            curDstAddr := curDstAddr + bytesPerBeat
          }
          // Decrement bytes remaining (saturate at 0)
          val dec = bytesPerBeat(23, 0)
          bytesLeft := Mux(bytesLeft > dec, bytesLeft - dec, 0.U)
          dmaState  := sDmaRead
        }
      }
    }
  }
}
