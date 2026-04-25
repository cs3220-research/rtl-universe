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

/** DMA engine with TL-UL host and device ports.
  *
  * Device port (tl_device): CSR register interface.
  *
  *   CSR map (byte addresses):
  *     0x00 : CTRL     – [0]=enable, [1]=start, [2]=abort  (read/write)
  *     0x04 : STATUS   – [0]=busy,   [1]=done,  [2]=error  (read-only)
  *     0x08 : DESC_ADDR– descriptor base address            (read/write)
  *
  *   All other addresses return an error response.
  *   Writes to STATUS (0x04) return an error response.
  *
  * Host port (tl_host): the DMA engine drives memory transactions.
  *
  * Descriptor format (32 bytes at DESC_ADDR):
  *   +0x00 : src_addr  (32-bit)
  *   +0x04 : dst_addr  (32-bit)
  *   +0x08 : flags = len[23:0] | xferWidth[26:24] | srcFixed[27] | dstFixed[28] | pollEn[29]
  *   +0x0C : next_desc (32-bit; 0 = end of chain)
  *   +0x10–+0x1C : poll fields (reserved in this implementation)
  *
  * @param hostP   coralnpu parameters for the host (memory) port
  * @param deviceP coralnpu parameters for the device (CSR) port
  */
class DmaEngine(hostP: coralnpu.Parameters, deviceP: coralnpu.Parameters) extends Module {
  private val hostTlP   = TLULParameters(hostP)
  private val deviceTlP = TLULParameters(deviceP)

  val io = IO(new Bundle {
    val tl_host   = new TLBundleUL(hostTlP)
    val tl_device = Flipped(new OpenTitanTileLink.Host2Device(deviceTlP))
  })

  // =========================================================================
  // CSR registers
  // =========================================================================
  val ctrl     = RegInit(0.U(32.W))
  val status   = RegInit(0.U(32.W))
  val descAddr = RegInit(0.U(32.W))

  val ctrlEnable = ctrl(0)
  val ctrlStart  = ctrl(1)
  val ctrlAbort  = ctrl(2)

  val statusBusy  = status(0)

  // =========================================================================
  // Device (CSR) port
  // =========================================================================
  io.tl_device.a.ready := true.B

  val devRespValid = RegInit(false.B)
  val devRespData  = RegInit(0.U(deviceTlP.dataWidth.W))
  val devRespSrc   = RegInit(0.U(deviceTlP.sourceWidth.W))
  val devRespOp    = RegInit(TLULOpcodesD.AccessAck)
  val devRespSz    = RegInit(0.U(deviceTlP.sizeWidth.W))
  val devRespErr   = RegInit(false.B)

  when(io.tl_device.a.fire) {
    devRespValid := true.B
    devRespSrc   := io.tl_device.a.bits.source
    devRespSz    := io.tl_device.a.bits.size
    val addr    = io.tl_device.a.bits.address(7, 0)
    val isWrite = io.tl_device.a.bits.opcode =/= TLULOpcodesA.Get

    val badAddr   = addr > 0x08.U
    val writeToRO = isWrite && addr === 0x04.U
    val isErr     = badAddr || writeToRO
    devRespErr := isErr

    when(isWrite && !isErr) {
      devRespOp   := TLULOpcodesD.AccessAck
      devRespData := 0.U
      when(addr === 0x00.U) { ctrl     := io.tl_device.a.bits.data }
      when(addr === 0x08.U) { descAddr := io.tl_device.a.bits.data }
    } .otherwise {
      devRespOp   := TLULOpcodesD.AccessAckData
      devRespData := MuxLookup(addr, 0.U)(Seq(
        0x00.U -> ctrl,
        0x04.U -> status,
        0x08.U -> descAddr
      ))
    }
  }
  when(io.tl_device.d.fire) { devRespValid := false.B }

  io.tl_device.d.valid        := devRespValid
  io.tl_device.d.bits.opcode  := devRespOp
  io.tl_device.d.bits.param   := 0.U
  io.tl_device.d.bits.size    := devRespSz
  io.tl_device.d.bits.source  := devRespSrc
  io.tl_device.d.bits.sink    := 0.U
  io.tl_device.d.bits.denied  := false.B
  io.tl_device.d.bits.data    := devRespData
  io.tl_device.d.bits.corrupt := false.B
  io.tl_device.d.bits.error   := devRespErr

  // =========================================================================
  // DMA FSM states
  // =========================================================================
  // sIdle:      waiting for start command
  // sReqDesc:   issuing read request for next descriptor word
  // sWaitDesc:  waiting for descriptor read response
  // sReqRead:   issuing read from source
  // sWaitRead:  waiting for read response
  // sReqWrite:  issuing write to destination
  // sWaitWrite: waiting for write response
  // sDone:      transfer complete, waiting for re-start
  val sIdle :: sReqDesc :: sWaitDesc :: sReqRead :: sWaitRead :: sReqWrite :: sWaitWrite :: sDone :: Nil = Enum(8)
  val dmaState = RegInit(sIdle)

  // -------------------------------------------------------------------------
  // Descriptor registers (loaded in sLoadDesc sub-states)
  // -------------------------------------------------------------------------
  val descSrcAddr  = RegInit(0.U(32.W))
  val descDstAddr  = RegInit(0.U(32.W))
  val descLen      = RegInit(0.U(24.W))
  val descXferW    = RegInit(2.U(3.W))  // default 4-byte beats
  val descSrcFixed = RegInit(false.B)
  val descDstFixed = RegInit(false.B)
  val descNextDesc = RegInit(0.U(32.W))

  val descWordIdx  = RegInit(0.U(3.W))
  val DESC_WORDS   = 4 // words 0,1,2,3 = src,dst,flags,nextDesc

  // -------------------------------------------------------------------------
  // Transfer state
  // -------------------------------------------------------------------------
  val bytesRemain = RegInit(0.U(24.W))
  val srcPtr      = RegInit(0.U(32.W))
  val dstPtr      = RegInit(0.U(32.W))
  val readData    = RegInit(0.U(hostTlP.dataWidth.W))

  // -------------------------------------------------------------------------
  // Abort (combinational, highest priority)
  // -------------------------------------------------------------------------
  val abortNow = ctrlAbort && statusBusy
  when(abortNow) {
    status   := "b100".U  // error=1, done=0, busy=0
    dmaState := sIdle
    ctrl     := ctrl & ~4.U(32.W) // clear abort bit
  }

  // -------------------------------------------------------------------------
  // Start edge detection
  // -------------------------------------------------------------------------
  val startNow = ctrlEnable && ctrlStart && !statusBusy && dmaState === sIdle
  when(startNow) {
    status      := 1.U           // busy=1
    descWordIdx := 0.U
    dmaState    := sReqDesc
    ctrl        := ctrl & ~2.U(32.W) // auto-clear start
  }

  // -------------------------------------------------------------------------
  // Host port default outputs
  // -------------------------------------------------------------------------
  io.tl_host.a.valid        := false.B
  io.tl_host.a.bits         := 0.U.asTypeOf(new TLChannelA(hostTlP))
  io.tl_host.d.ready        := false.B

  // -------------------------------------------------------------------------
  // FSM body
  // -------------------------------------------------------------------------
  when(!abortNow) {
    switch(dmaState) {
      is(sIdle)  { /* wait */ }
      is(sDone)  { /* wait for re-start */ }

      // -----------------------------------------------------------------------
      // Descriptor load: read word descWordIdx
      // -----------------------------------------------------------------------
      is(sReqDesc) {
        io.tl_host.a.valid          := true.B
        io.tl_host.a.bits.opcode    := TLULOpcodesA.Get
        io.tl_host.a.bits.param     := 0.U
        io.tl_host.a.bits.size      := 2.U   // 4 bytes
        io.tl_host.a.bits.source    := 0.U
        io.tl_host.a.bits.address   := descAddr + (descWordIdx ## 0.U(2.W))
        io.tl_host.a.bits.mask      := 0xf.U
        io.tl_host.a.bits.data      := 0.U
        io.tl_host.a.bits.corrupt   := false.B
        when(io.tl_host.a.fire) { dmaState := sWaitDesc }
      }

      is(sWaitDesc) {
        io.tl_host.d.ready := true.B
        when(io.tl_host.d.fire) {
          val w = io.tl_host.d.bits.data(31, 0)
          switch(descWordIdx) {
            is(0.U) { descSrcAddr  := w }
            is(1.U) { descDstAddr  := w }
            is(2.U) {
              descLen      := w(23, 0)
              descXferW    := w(26, 24)
              descSrcFixed := w(27)
              descDstFixed := w(28)
            }
            is(3.U) { descNextDesc := w }
          }
          val nextWord = descWordIdx + 1.U
          descWordIdx := nextWord
          when(nextWord === DESC_WORDS.U) {
            // All descriptor words loaded. Start transfer.
            srcPtr      := descSrcAddr
            dstPtr      := descDstAddr
            bytesRemain := descLen
            dmaState    := sReqRead
          } .otherwise {
            dmaState := sReqDesc
          }
        }
      }

      // -----------------------------------------------------------------------
      // Transfer: read from source
      // -----------------------------------------------------------------------
      is(sReqRead) {
        when(bytesRemain === 0.U) {
          // Transfer complete for this descriptor.
          when(descNextDesc =/= 0.U) {
            // Chain to next descriptor.
            descAddr    := descNextDesc
            descWordIdx := 0.U
            dmaState    := sReqDesc
          } .otherwise {
            status   := 2.U   // done=1, busy=0
            dmaState := sDone
          }
        } .otherwise {
          io.tl_host.a.valid          := true.B
          io.tl_host.a.bits.opcode    := TLULOpcodesA.Get
          io.tl_host.a.bits.param     := 0.U
          io.tl_host.a.bits.size      := descXferW
          io.tl_host.a.bits.source    := 0.U
          io.tl_host.a.bits.address   := srcPtr
          io.tl_host.a.bits.mask      := ~0.U(hostTlP.maskWidth.W)
          io.tl_host.a.bits.data      := 0.U
          io.tl_host.a.bits.corrupt   := false.B
          when(io.tl_host.a.fire) { dmaState := sWaitRead }
        }
      }

      is(sWaitRead) {
        io.tl_host.d.ready := true.B
        when(io.tl_host.d.fire) {
          readData := io.tl_host.d.bits.data
          dmaState := sReqWrite
          when(!descSrcFixed) { srcPtr := srcPtr + (1.U << descXferW) }
        }
      }

      // -----------------------------------------------------------------------
      // Transfer: write to destination
      // -----------------------------------------------------------------------
      is(sReqWrite) {
        io.tl_host.a.valid          := true.B
        io.tl_host.a.bits.opcode    := TLULOpcodesA.PutFullData
        io.tl_host.a.bits.param     := 0.U
        io.tl_host.a.bits.size      := descXferW
        io.tl_host.a.bits.source    := 0.U
        io.tl_host.a.bits.address   := dstPtr
        io.tl_host.a.bits.mask      := ~0.U(hostTlP.maskWidth.W)
        io.tl_host.a.bits.data      := readData
        io.tl_host.a.bits.corrupt   := false.B
        when(io.tl_host.a.fire) {
          dmaState    := sWaitWrite
          when(!descDstFixed) { dstPtr := dstPtr + (1.U << descXferW) }
          bytesRemain := bytesRemain - (1.U << descXferW)
        }
      }

      is(sWaitWrite) {
        io.tl_host.d.ready := true.B
        when(io.tl_host.d.fire) { dmaState := sReqRead }
      }
    }
  }
}
