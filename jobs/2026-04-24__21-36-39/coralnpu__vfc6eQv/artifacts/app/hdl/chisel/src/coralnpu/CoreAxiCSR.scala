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

package coralnpu

import chisel3._
import chisel3.util._

/** AXI4 slave CSR module for CoralNPU.
  *
  * Exposes a small register map over a 128-bit AXI4 slave interface:
  *
  *   0x004  - PC-start register (read/write). AXI data[63:32] = pcStart.
  *   0x100  - Status register (read-only). AXI data[31:0] = coralnpu_csr.value(0).
  *
  * Writes to any other address return SLVERR (resp=2).
  */
class CoreAxiCSR(p: Parameters) extends Module {
  val io = IO(new Bundle {
    val axi          = Flipped(new bus.AxiMasterIO(32, 128, p.axi2IdBits))
    val internal     = Input(Bool())
    val halted       = Input(Bool())
    val fault        = Input(Bool())
    val coralnpu_csr = Input(new CoralNPUCsr)
    val cg           = Output(Bool())    // clock gate, initially 1
    val reset        = Output(Bool())    // reset signal, initially 1
    val pcStart      = Output(UInt(32.W))
  })

  // -------------------------------------------------------------------------
  // Registers
  // -------------------------------------------------------------------------
  val cgReg      = RegInit(true.B)
  val resetReg   = RegInit(true.B)
  val pcStartReg = RegInit(0.U(32.W))

  io.cg      := cgReg
  io.reset   := resetReg
  io.pcStart := pcStartReg

  // -------------------------------------------------------------------------
  // AXI address/data offsets
  // -------------------------------------------------------------------------
  val ADDR_PC_START = 0x4.U(32.W)
  val ADDR_STATUS   = 0x100.U(32.W)

  // -------------------------------------------------------------------------
  // Write path
  // -------------------------------------------------------------------------
  // State: pending write (address and data both captured)
  val wrPending = RegInit(false.B)
  val wrAddr    = RegInit(0.U(32.W))
  val wrData    = RegInit(0.U(128.W))
  val wrId      = RegInit(0.U(p.axi2IdBits.W))
  val wrResp    = RegInit(0.U(2.W))

  // Accept new write when addr.valid AND data.valid AND (not pending OR resp
  // is being consumed this cycle).
  val wrRespConsumed = wrPending && io.axi.write.resp.valid && io.axi.write.resp.ready
  val wrAccept = io.axi.write.addr.valid && io.axi.write.data.valid &&
                 (!wrPending || wrRespConsumed)

  io.axi.write.addr.ready := !wrPending || wrRespConsumed
  io.axi.write.data.ready := !wrPending || wrRespConsumed

  when (wrAccept) {
    wrPending := true.B
    wrAddr    := io.axi.write.addr.bits.addr
    wrData    := io.axi.write.data.bits.data
    wrId      := io.axi.write.addr.bits.id

    // Determine response code and perform register update combinatorially
    // on the latched values below.
    val ispcStart = io.axi.write.addr.bits.addr === ADDR_PC_START
    when (ispcStart) {
      // pcStart lives in data[63:32]
      pcStartReg := io.axi.write.data.bits.data(63, 32)
      wrResp     := 0.U
    }.otherwise {
      wrResp := 2.U  // SLVERR
    }
  }.elsewhen (wrRespConsumed) {
    wrPending := false.B
  }

  io.axi.write.resp.valid      := wrPending
  io.axi.write.resp.bits.resp  := wrResp
  io.axi.write.resp.bits.id    := wrId

  // -------------------------------------------------------------------------
  // Read path
  // -------------------------------------------------------------------------
  val rdPending = RegInit(false.B)
  val rdAddr    = RegInit(0.U(32.W))
  val rdId      = RegInit(0.U(p.axi2IdBits.W))

  val rdDataConsumed = rdPending && io.axi.read.data.valid && io.axi.read.data.ready
  val rdAccept = io.axi.read.addr.valid && (!rdPending || rdDataConsumed)

  io.axi.read.addr.ready := !rdPending || rdDataConsumed

  when (rdAccept) {
    rdPending := true.B
    rdAddr    := io.axi.read.addr.bits.addr
    rdId      := io.axi.read.addr.bits.id
  }.elsewhen (rdDataConsumed) {
    rdPending := false.B
  }

  // Build read data based on registered address
  val rdData = Wire(UInt(128.W))
  rdData := 0.U
  when (rdAddr === ADDR_PC_START) {
    // pcStart at bits[63:32], zeros elsewhere
    rdData := Cat(0.U(64.W), pcStartReg, 0.U(32.W))
  }.elsewhen (rdAddr === ADDR_STATUS) {
    // coralnpu_csr.value(0) at bits[31:0]
    rdData := io.coralnpu_csr.value(0)
  }

  io.axi.read.data.valid      := rdPending
  io.axi.read.data.bits.data  := rdData
  io.axi.read.data.bits.resp  := 0.U
  io.axi.read.data.bits.last  := true.B
  io.axi.read.data.bits.id    := rdId
}
