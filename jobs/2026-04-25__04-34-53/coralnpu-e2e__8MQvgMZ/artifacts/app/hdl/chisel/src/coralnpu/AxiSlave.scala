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

package coralnpu

import chisel3._
import chisel3.util._
import bus._

/** AXI slave — routes host transactions to ITCM, DTCM or CoreAxiCSR.
  *
  * Address map (byte addresses on the AXI slave port):
  * {{{
  *   0x00000000 .. itcmSizeBytes-1  → ITCM back-door
  *   0x00010000 .. 0x10000+dtcm-1  → DTCM back-door
  *   0x00030000 .. 0x30000+0x200-1 → CSR (forwarded to CoreAxiCSR)
  * }}}
  *
  * The host AXI port is 128-bit wide.  ITCM/DTCM writes are forwarded as
  * 128-bit back-door writes; reads return 128-bit words.
  *
  * CSR transactions are routed transparently to the `csrAxi` port which
  * connects to a `CoreAxiCSR` instance in the parent module.
  */
class AxiSlave(p: Parameters) extends Module {

  // Host-facing AXI is always 128-bit.
  private val axiBits  = 128
  private val axiBytes = axiBits / 8

  // Address boundaries
  private val ITCM_BASE: Long = 0x00000000L
  private val DTCM_BASE: Long = 0x00010000L
  private val CSR_BASE:  Long = 0x00030000L
  private val CSR_SIZE:  Long = 0x00001000L

  val io = IO(new Bundle {
    // Host AXI slave
    val axi    = Flipped(new AxiBundle(axiBits, p.addrBits, p.axiIdBits))
    // ITCM back-door (128-bit wide, word-addressed)
    val itcm = new Bundle {
      val en    = Output(Bool())
      val we    = Output(Bool())
      val addr  = Output(UInt(log2Ceil(p.itcmSizeBytes / axiBytes).W))
      val wdata = Output(UInt(axiBits.W))
      val wmask = Output(UInt(axiBytes.W))
      val rdata = Input(UInt(axiBits.W))
    }
    // DTCM back-door (128-bit wide, word-addressed)
    val dtcm = new Bundle {
      val en    = Output(Bool())
      val we    = Output(Bool())
      val addr  = Output(UInt(log2Ceil(p.dtcmSizeBytes / axiBytes).W))
      val wdata = Output(UInt(axiBits.W))
      val wmask = Output(UInt(axiBytes.W))
      val rdata = Input(UInt(axiBits.W))
    }
    // CSR AXI passthrough (forwarded to CoreAxiCSR)
    val csrAxi = new AxiBundle(axiBits, p.addrBits, p.axiIdBits)
  })

  // ---------------------------------------------------------------------------
  // Address decode helpers
  // ---------------------------------------------------------------------------
  def inITCM(addr: UInt): Bool = addr < p.itcmSizeBytes.U
  def inDTCM(addr: UInt): Bool = (addr >= DTCM_BASE.U) && (addr < (DTCM_BASE + p.dtcmSizeBytes).U)
  def inCSR (addr: UInt): Bool = (addr >= CSR_BASE.U)  && (addr < (CSR_BASE  + CSR_SIZE).U)

  // ---------------------------------------------------------------------------
  // FSM
  // ---------------------------------------------------------------------------
  private object State extends ChiselEnum {
    val sIdle, sReadITCM, sReadITCMWait, sReadDTCM, sReadDTCMWait,
        sReadCSR, sReadCSRWait,
        sWriteITCM, sWriteDTCM, sWriteCSR,
        sReadResp, sWriteResp = Value
  }
  import State._

  val state    = RegInit(sIdle)
  val idReg    = Reg(UInt(p.axiIdBits.W))
  val addrReg  = Reg(UInt(p.addrBits.W))
  val rdataReg = Reg(UInt(axiBits.W))
  val wdataReg = Reg(UInt(axiBits.W))
  val wmaskReg = Reg(UInt(axiBytes.W))

  // Default drives
  io.itcm.en    := false.B
  io.itcm.we    := false.B
  io.itcm.addr  := addrReg(log2Ceil(p.itcmSizeBytes) - 1, log2Ceil(axiBytes))
  io.itcm.wdata := wdataReg
  io.itcm.wmask := wmaskReg

  io.dtcm.en    := false.B
  io.dtcm.we    := false.B
  io.dtcm.addr  := (addrReg - DTCM_BASE.U)(log2Ceil(p.dtcmSizeBytes) - 1, log2Ceil(axiBytes))
  io.dtcm.wdata := wdataReg
  io.dtcm.wmask := wmaskReg

  // CSR AXI: default tie-offs
  io.csrAxi.read.addr.valid        := false.B
  io.csrAxi.read.addr.bits.id     := idReg
  io.csrAxi.read.addr.bits.addr   := addrReg - CSR_BASE.U
  io.csrAxi.read.addr.bits.len    := 0.U
  io.csrAxi.read.addr.bits.size   := 2.U
  io.csrAxi.read.addr.bits.burst  := 1.U
  io.csrAxi.read.addr.bits.prot   := 0.U
  io.csrAxi.read.addr.bits.lock   := 0.U
  io.csrAxi.read.addr.bits.cache  := 0.U
  io.csrAxi.read.addr.bits.qos    := 0.U
  io.csrAxi.read.addr.bits.region := 0.U
  io.csrAxi.read.data.ready       := false.B
  io.csrAxi.write.addr.valid       := false.B
  io.csrAxi.write.addr.bits.id    := idReg
  io.csrAxi.write.addr.bits.addr  := addrReg - CSR_BASE.U
  io.csrAxi.write.addr.bits.len   := 0.U
  io.csrAxi.write.addr.bits.size  := 2.U
  io.csrAxi.write.addr.bits.burst := 1.U
  io.csrAxi.write.addr.bits.prot   := 0.U
  io.csrAxi.write.addr.bits.lock   := 0.U
  io.csrAxi.write.addr.bits.cache  := 0.U
  io.csrAxi.write.addr.bits.qos    := 0.U
  io.csrAxi.write.addr.bits.region := 0.U
  io.csrAxi.write.data.valid      := false.B
  io.csrAxi.write.data.bits.data := wdataReg
  io.csrAxi.write.data.bits.strb := wmaskReg
  io.csrAxi.write.data.bits.last := true.B
  io.csrAxi.write.resp.ready     := false.B

  // AXI slave default back-pressure
  io.axi.read.addr.ready  := (state === sIdle)
  io.axi.write.addr.ready := (state === sIdle)
  io.axi.write.data.ready := (state === sIdle)

  io.axi.read.data.valid       := (state === sReadResp)
  io.axi.read.data.bits.id    := idReg
  io.axi.read.data.bits.data  := rdataReg
  io.axi.read.data.bits.resp  := 0.U
  io.axi.read.data.bits.last  := true.B

  io.axi.write.resp.valid      := (state === sWriteResp)
  io.axi.write.resp.bits.id   := idReg
  io.axi.write.resp.bits.resp := 0.U

  switch(state) {
    is(sIdle) {
      // ---- Read ----
      when(io.axi.read.addr.valid && resetDone) {
        idReg   := io.axi.read.addr.bits.id
        addrReg := io.axi.read.addr.bits.addr
        state := MuxCase(sReadResp, Seq(
          inITCM(io.axi.read.addr.bits.addr) -> sReadITCM,
          inDTCM(io.axi.read.addr.bits.addr) -> sReadDTCM,
          inCSR (io.axi.read.addr.bits.addr) -> sReadCSR,
        ))
      }
      // ---- Write ----
      .elsewhen(io.axi.write.addr.valid && io.axi.write.data.valid) {
        idReg    := io.axi.write.addr.bits.id
        addrReg  := io.axi.write.addr.bits.addr
        wdataReg := io.axi.write.data.bits.data
        wmaskReg := io.axi.write.data.bits.strb
        state := MuxCase(sWriteResp, Seq(
          inITCM(io.axi.write.addr.bits.addr) -> sWriteITCM,
          inDTCM(io.axi.write.addr.bits.addr) -> sWriteDTCM,
          inCSR (io.axi.write.addr.bits.addr) -> sWriteCSR,
        ))
      }
    }

    // ---- ITCM read --------------------------------------------------------
    // Cycle 0: assert enable; SRAM latches the address.
    is(sReadITCM) {
      io.itcm.en   := true.B
      io.itcm.we   := false.B
      io.itcm.addr := addrReg(log2Ceil(p.itcmSizeBytes) - 1, log2Ceil(axiBytes))
      state        := sReadITCMWait
    }

    // Cycle 1: read data is now valid on io.itcm.rdata.
    is(sReadITCMWait) {
      rdataReg := io.itcm.rdata
      state    := sReadResp
    }

    // ---- DTCM read --------------------------------------------------------
    // Cycle 0: assert enable; SRAM latches the address.
    is(sReadDTCM) {
      io.dtcm.en   := true.B
      io.dtcm.we   := false.B
      io.dtcm.addr := (addrReg - DTCM_BASE.U)(log2Ceil(p.dtcmSizeBytes) - 1, log2Ceil(axiBytes))
      state        := sReadDTCMWait
    }

    // Cycle 1: read data is now valid on io.dtcm.rdata.
    is(sReadDTCMWait) {
      rdataReg := io.dtcm.rdata
      state    := sReadResp
    }

    // ---- CSR read ---------------------------------------------------------
    // Present the AR beat; once accepted, wait for R beat from CoreAxiCSR.
    is(sReadCSR) {
      io.csrAxi.read.addr.valid      := true.B
      io.csrAxi.read.addr.bits.addr  := addrReg - CSR_BASE.U
      io.csrAxi.read.addr.bits.id    := idReg
      when(io.csrAxi.read.addr.ready) {
        state := sReadCSRWait
      }
    }

    // Wait for the R beat from CoreAxiCSR, capture data, then send to host.
    is(sReadCSRWait) {
      io.csrAxi.read.data.ready := true.B
      when(io.csrAxi.read.data.valid) {
        rdataReg := io.csrAxi.read.data.bits.data
        state    := sReadResp
      }
    }

    // ---- ITCM write -------------------------------------------------------
    is(sWriteITCM) {
      io.itcm.en    := true.B
      io.itcm.we    := true.B
      io.itcm.addr  := addrReg(log2Ceil(p.itcmSizeBytes) - 1, log2Ceil(axiBytes))
      io.itcm.wdata := wdataReg
      io.itcm.wmask := wmaskReg
      state         := sWriteResp
    }

    // ---- DTCM write -------------------------------------------------------
    is(sWriteDTCM) {
      io.dtcm.en    := true.B
      io.dtcm.we    := true.B
      io.dtcm.addr  := (addrReg - DTCM_BASE.U)(log2Ceil(p.dtcmSizeBytes) - 1, log2Ceil(axiBytes))
      io.dtcm.wdata := wdataReg
      io.dtcm.wmask := wmaskReg
      state         := sWriteResp
    }

    // ---- CSR write --------------------------------------------------------
    is(sWriteCSR) {
      io.csrAxi.write.addr.valid      := true.B
      io.csrAxi.write.addr.bits.addr  := addrReg - CSR_BASE.U
      io.csrAxi.write.addr.bits.id    := idReg
      io.csrAxi.write.data.valid      := true.B
      io.csrAxi.write.data.bits.data  := wdataReg
      io.csrAxi.write.data.bits.strb  := wmaskReg
      when(io.csrAxi.write.addr.ready && io.csrAxi.write.data.ready) {
        state := sWriteResp
      }
    }

    // ---- Read response ----------------------------------------------------
    // rdataReg is populated by sReadITCMWait / sReadDTCMWait / sReadCSRWait.
    is(sReadResp) {
      io.axi.read.data.valid := true.B
      when(io.axi.read.data.ready) {
        state := sIdle
      }
    }

    // ---- Write response ---------------------------------------------------
    is(sWriteResp) {
      // Relay CSR write response when applicable
      io.csrAxi.write.resp.ready := true.B
      io.axi.write.resp.valid    := true.B
      when(io.axi.write.resp.ready) {
        state := sIdle
      }
    }
  }
}
