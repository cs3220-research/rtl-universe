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

/**
  * DMA Engine with TileLink-UL host (master) and device (CSR slave) ports.
  *
  * CSR Map (device port, 32-bit registers):
  *   0x00 : CTRL      (r/w)  - bit 0: enable, bit 1: start, bit 2: abort
  *   0x04 : STATUS    (r/o)  - bit 0: busy, bit 1: done, bit 2: error
  *   0x08 : DESC_ADDR (r/w)  - descriptor physical address
  *
  * Descriptor format (32 bytes at DESC_ADDR):
  *   +0x00 : src_addr   (32-bit)
  *   +0x04 : dst_addr   (32-bit)
  *   +0x08 : flags      bits[23:0]:xfer_len, bits[26:24]:xfer_width (log2 bytes/beat),
  *                      bit[27]:src_fixed, bit[28]:dst_fixed, bit[29]:poll_en
  *   +0x0c : next_desc  (32-bit, 0 = no chaining)
  *   +0x10 : poll_addr
  *   +0x14 : poll_mask
  *   +0x18 : poll_value
  *   +0x1c : reserved
  */
class DmaEngine(hostP: Parameters, deviceP: Parameters) extends Module {
  val hostTlp   = new TLULParameters(hostP)
  val deviceTlp = new TLULParameters(deviceP)

  val io = IO(new Bundle {
    val tl_host   = new OpenTitanTileLink.Host2Device(hostTlp)
    val tl_device = Flipped(new OpenTitanTileLink.Host2Device(deviceTlp))
  })

  // =========================================================================
  // CSR Registers
  // =========================================================================
  val ctrlReg     = RegInit(0.U(32.W))
  val statusReg   = RegInit(0.U(32.W))
  val descAddrReg = RegInit(0.U(32.W))

  // =========================================================================
  // Device port TL-UL slave (CSR access)
  // =========================================================================
  val sDevIdle :: sDevResp :: Nil = Enum(2)
  val devState = RegInit(sDevIdle)

  val devRespData   = RegInit(0.U(deviceTlp.dataBits.W))
  val devRespError  = RegInit(false.B)
  val devRespSource = RegInit(0.U(deviceTlp.sourceBits.W))
  val devRespSize   = RegInit(0.U(deviceTlp.sizeBits.W))
  val devRespOpcode = RegInit(TLULOpcodesD.AccessAck)

  io.tl_device.a.ready := false.B
  io.tl_device.d.valid := false.B
  io.tl_device.d.bits  := 0.U.asTypeOf(new TLULChannelD(deviceTlp))

  // Write enable signals decoded this cycle
  val devWriteCtrl     = WireDefault(false.B)
  val devWriteDescAddr = WireDefault(false.B)
  val devWriteData     = WireDefault(0.U(32.W))
  val devAbortReq      = WireDefault(false.B)

  switch(devState) {
    is(sDevIdle) {
      io.tl_device.a.ready := true.B
      when(io.tl_device.a.valid) {
        val addr    = io.tl_device.a.bits.address
        val isWrite = (io.tl_device.a.bits.opcode === TLULOpcodesA.PutFullData) ||
                      (io.tl_device.a.bits.opcode === TLULOpcodesA.PutPartialData)
        val isGet   = io.tl_device.a.bits.opcode === TLULOpcodesA.Get

        devRespSource := io.tl_device.a.bits.source
        devRespSize   := io.tl_device.a.bits.size
        devRespError  := false.B
        devRespData   := 0.U

        when(isGet) {
          devRespOpcode := TLULOpcodesD.AccessAckData
          when(addr === 0x00.U) {
            devRespData := ctrlReg
          }.elsewhen(addr === 0x04.U) {
            devRespData := statusReg
          }.elsewhen(addr === 0x08.U) {
            devRespData := descAddrReg
          }.otherwise {
            devRespData  := 0.U
            devRespError := true.B
          }
        }.elsewhen(isWrite) {
          devRespOpcode := TLULOpcodesD.AccessAck
          when(addr === 0x04.U) {
            // STATUS is read-only
            devRespError := true.B
          }.elsewhen(addr === 0x00.U) {
            devWriteCtrl := true.B
            devWriteData := io.tl_device.a.bits.data(31, 0)
            when(io.tl_device.a.bits.data(2)) {
              devAbortReq := true.B
            }
          }.elsewhen(addr === 0x08.U) {
            devWriteDescAddr := true.B
            devWriteData     := io.tl_device.a.bits.data(31, 0)
          }.otherwise {
            devRespError := true.B
          }
        }.otherwise {
          devRespOpcode := TLULOpcodesD.AccessAck
          devRespError  := true.B
        }

        devState := sDevResp
      }
    }

    is(sDevResp) {
      io.tl_device.d.valid        := true.B
      io.tl_device.d.bits.opcode  := devRespOpcode
      io.tl_device.d.bits.param   := 0.U
      io.tl_device.d.bits.size    := devRespSize
      io.tl_device.d.bits.source  := devRespSource
      io.tl_device.d.bits.sink    := 0.U
      io.tl_device.d.bits.denied  := false.B
      io.tl_device.d.bits.data    := devRespData
      io.tl_device.d.bits.corrupt := false.B
      io.tl_device.d.bits.error   := devRespError

      when(io.tl_device.d.ready) {
        devState := sDevIdle
      }
    }
  }

  // =========================================================================
  // DMA FSM (host port master)
  // =========================================================================
  val dIdle      :: dLoadDesc  :: dWaitDesc  ::
      dXferRead  :: dWaitRead  ::
      dXferWrite :: dWaitWrite ::
      dDone      :: dError     :: Nil = Enum(9)
  val dmaState = RegInit(dIdle)

  // Descriptor fields (latched from loading)
  val descSrcAddr   = RegInit(0.U(32.W))
  val descDstAddr   = RegInit(0.U(32.W))
  val descXferLen   = RegInit(0.U(24.W))
  val descXferWidth = RegInit(0.U(3.W))  // log2 bytes per beat
  val descSrcFixed  = RegInit(false.B)
  val descDstFixed  = RegInit(false.B)
  val descPollEn    = RegInit(false.B)
  val descNextDesc  = RegInit(0.U(32.W))

  // Running transfer state
  val xferSrcAddr  = RegInit(0.U(32.W))
  val xferDstAddr  = RegInit(0.U(32.W))
  val xferRemain   = RegInit(0.U(32.W))  // bytes remaining
  val xferBeatSize = RegInit(4.U(32.W))  // bytes per beat

  // Descriptor word loading (8 x 32-bit words)
  val descLoadIdx = RegInit(0.U(4.W))
  val descWords   = Reg(Vec(8, UInt(32.W)))

  // Pending read data
  val readDataReg = RegInit(0.U(hostTlp.dataBits.W))

  // Status bits
  val busyReg  = RegInit(false.B)
  val doneReg  = RegInit(false.B)
  val errorReg = RegInit(false.B)

  statusReg := Cat(0.U(29.W), errorReg, doneReg, busyReg)

  // Default host port outputs
  io.tl_host.a.valid     := false.B
  io.tl_host.a.bits      := 0.U.asTypeOf(new TLULChannelA(hostTlp))
  io.tl_host.d.ready     := false.B

  // Number of bytes in address offset within bus word
  val busBytesLog2 = log2Ceil(hostTlp.dataBits / 8)

  // Helper: log2 of beat size for TL-UL size field
  // beatSize must be a power-of-2 between 1 and busBytes
  def beatSizeToLog2(beatSize: UInt): UInt = {
    val result = WireDefault(2.U(4.W))
    val checks = Seq(1, 2, 4, 8, 16).map(s => (beatSize === s.U) -> log2Ceil(s).U(4.W))
    result := MuxCase(2.U(4.W), checks)
    result
  }

  val curBeat  = Mux(xferRemain >= xferBeatSize, xferBeatSize, xferRemain)
  val curSizeLog2 = beatSizeToLog2(curBeat)

  switch(dmaState) {
    is(dIdle) {
      when(devWriteCtrl && devWriteData(0) && devWriteData(1) && !busyReg) {
        busyReg   := true.B
        doneReg   := false.B
        errorReg  := false.B
        descLoadIdx := 0.U
        dmaState  := dLoadDesc
      }
    }

    is(dLoadDesc) {
      when(devAbortReq) {
        busyReg  := false.B
        errorReg := true.B
        dmaState := dError
      }.otherwise {
        val loadAddr = descAddrReg + (descLoadIdx << 2.U)
        io.tl_host.a.valid        := true.B
        io.tl_host.a.bits.opcode  := TLULOpcodesA.Get
        io.tl_host.a.bits.param   := 0.U
        io.tl_host.a.bits.size    := 2.U  // 4 bytes
        io.tl_host.a.bits.source  := 0.U
        io.tl_host.a.bits.address := loadAddr
        io.tl_host.a.bits.mask    := 0xf.U << loadAddr(busBytesLog2 - 1, 0)
        io.tl_host.a.bits.data    := 0.U
        io.tl_host.a.bits.corrupt := false.B

        when(io.tl_host.a.ready) {
          dmaState := dWaitDesc
        }
      }
    }

    is(dWaitDesc) {
      io.tl_host.d.ready := true.B
      when(devAbortReq) {
        busyReg  := false.B
        errorReg := true.B
        dmaState := dError
      }.elsewhen(io.tl_host.d.valid) {
        val loadAddr   = descAddrReg + (descLoadIdx << 2.U)
        val byteOff    = loadAddr(busBytesLog2 - 1, 0)
        val bitOff     = byteOff << 3.U
        val word       = (io.tl_host.d.bits.data >> bitOff)(31, 0)
        descWords(descLoadIdx) := word

        when(descLoadIdx === 7.U) {
          // Latch descriptor fields from accumulated words
          // word 2 flags is already from the last write on index 2
          val flags = descWords(2)
          descSrcAddr   := descWords(0)
          descDstAddr   := descWords(1)
          descXferLen   := flags(23, 0)
          descXferWidth := flags(26, 24)
          descSrcFixed  := flags(27)
          descDstFixed  := flags(28)
          descPollEn    := flags(29)
          descNextDesc  := descWords(3)

          xferSrcAddr   := descWords(0)
          xferDstAddr   := descWords(1)
          xferRemain    := flags(23, 0)
          xferBeatSize  := 1.U << flags(26, 24)

          dmaState := dXferRead
        }.otherwise {
          descLoadIdx := descLoadIdx + 1.U
          dmaState    := dLoadDesc
        }
      }
    }

    is(dXferRead) {
      when(devAbortReq) {
        busyReg  := false.B
        errorReg := true.B
        dmaState := dError
      }.elsewhen(xferRemain === 0.U) {
        when(descNextDesc =/= 0.U) {
          descAddrReg := descNextDesc
          descLoadIdx := 0.U
          dmaState    := dLoadDesc
        }.otherwise {
          busyReg  := false.B
          doneReg  := true.B
          dmaState := dDone
        }
      }.otherwise {
        io.tl_host.a.valid        := true.B
        io.tl_host.a.bits.opcode  := TLULOpcodesA.Get
        io.tl_host.a.bits.param   := 0.U
        io.tl_host.a.bits.size    := curSizeLog2
        io.tl_host.a.bits.source  := 1.U
        io.tl_host.a.bits.address := xferSrcAddr
        io.tl_host.a.bits.mask    := ((1.U << curBeat) - 1.U) << xferSrcAddr(busBytesLog2 - 1, 0)
        io.tl_host.a.bits.data    := 0.U
        io.tl_host.a.bits.corrupt := false.B

        when(io.tl_host.a.ready) {
          dmaState := dWaitRead
        }
      }
    }

    is(dWaitRead) {
      io.tl_host.d.ready := true.B
      when(devAbortReq) {
        busyReg  := false.B
        errorReg := true.B
        dmaState := dError
      }.elsewhen(io.tl_host.d.valid) {
        readDataReg := io.tl_host.d.bits.data
        dmaState    := dXferWrite
      }
    }

    is(dXferWrite) {
      when(devAbortReq) {
        busyReg  := false.B
        errorReg := true.B
        dmaState := dError
      }.otherwise {
        // src data is in readDataReg at srcByteOffset within the bus word
        // dst data should be placed at dstByteOffset within the bus word
        val srcByteOff = xferSrcAddr(busBytesLog2 - 1, 0)
        val dstByteOff = xferDstAddr(busBytesLog2 - 1, 0)

        // Extract the relevant bytes from the source bus word and re-place at dst offset
        val byteMask   = ((1.U << curBeat) - 1.U)
        val dstMaskShifted = byteMask << dstByteOff

        // Shift data: extract from src position, place at dst position
        val srcData     = readDataReg >> (srcByteOff << 3.U)
        val dstData     = srcData << (dstByteOff << 3.U)

        io.tl_host.a.valid        := true.B
        io.tl_host.a.bits.opcode  := TLULOpcodesA.PutFullData
        io.tl_host.a.bits.param   := 0.U
        io.tl_host.a.bits.size    := curSizeLog2
        io.tl_host.a.bits.source  := 2.U
        io.tl_host.a.bits.address := xferDstAddr
        io.tl_host.a.bits.mask    := dstMaskShifted
        io.tl_host.a.bits.data    := dstData
        io.tl_host.a.bits.corrupt := false.B

        when(io.tl_host.a.ready) {
          dmaState := dWaitWrite
        }
      }
    }

    is(dWaitWrite) {
      io.tl_host.d.ready := true.B
      when(devAbortReq) {
        busyReg  := false.B
        errorReg := true.B
        dmaState := dError
      }.elsewhen(io.tl_host.d.valid) {
        when(!descSrcFixed) {
          xferSrcAddr := xferSrcAddr + curBeat
        }
        when(!descDstFixed) {
          xferDstAddr := xferDstAddr + curBeat
        }
        xferRemain := xferRemain - curBeat
        dmaState   := dXferRead
      }
    }

    is(dDone) { /* terminal */ }
    is(dError) { /* terminal */ }
  }

  // =========================================================================
  // CSR register writes
  // =========================================================================
  when(devWriteCtrl) {
    ctrlReg := devWriteData
    when(devWriteData(2)) {
      // Abort clears busy, sets error
      busyReg  := false.B
      errorReg := true.B
      dmaState := dError
    }.elsewhen(devWriteData(0) && devWriteData(1) && !busyReg) {
      // enable+start triggers DMA (handled in dIdle state above)
    }
  }
  when(devWriteDescAddr) {
    descAddrReg := devWriteData
  }
}
