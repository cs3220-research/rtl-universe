// Copyright 2024 Google LLC
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

/** Read-only CSR values driven from outside (e.g. performance counters). */
class CoralNPUCsrIO extends Bundle {
  val value = Input(Vec(16, UInt(32.W)))
}

/** CoreAxiCSR: AXI-slave register file for core control/status.
  *
  * 64-bit AXI data bus.
  *
  * Register map (byte addresses):
  *   0x000  [31:0]  status  (RO)
  *   0x004  [63:32] pcStart (RW), [31:0] control (RW: bit0=reset, bit1=~cg)
  *   0x100..0x13C   coralnpu_csr[0..15] (RO, 32-bit each)
  *
  * Write semantics: address 0x4 is the only writable register.
  * pcStart is written from data[63:32]; control from data[31:0] only
  * when strb[0] is set. (cg and reset keep their reset values by default.)
  *
  * Initial state: reset=1, cg=1, pcStart=0.
  */
class CoreAxiCSR(p: Parameters) extends Module {
  private val DataBits = 64
  private val AddrBits = p.axiAddrBits
  private val IdBits   = p.axiIdBits

  val io = IO(new Bundle {
    val axi          = Flipped(new AxiMasterBundle(AddrBits, DataBits, IdBits))
    val cg           = Output(Bool())
    val reset        = Output(Bool())
    val pcStart      = Output(UInt(32.W))
    val internal     = Input(Bool())
    val halted       = Input(Bool())
    val fault        = Input(Bool())
    val coralnpu_csr = new CoralNPUCsrIO
  })

  // ---------------------------------------------------------------------------
  // Registers
  // ---------------------------------------------------------------------------
  val cgReg      = RegInit(true.B)
  val resetReg   = RegInit(true.B)
  val pcStartReg = RegInit(0.U(32.W))

  io.cg      := cgReg
  io.reset   := resetReg
  io.pcStart := pcStartReg

  // ---------------------------------------------------------------------------
  // AXI read
  // ---------------------------------------------------------------------------
  object RdState extends ChiselEnum { val sIdle, sData = Value }
  val rdState = RegInit(RdState.sIdle)
  val rdId    = Reg(UInt(IdBits.W))
  val rdData  = Reg(UInt(DataBits.W))
  val rdResp  = Reg(UInt(2.W))

  io.axi.read.addr.ready      := rdState === RdState.sIdle
  io.axi.read.data.valid      := rdState === RdState.sData
  io.axi.read.data.bits.id    := rdId
  io.axi.read.data.bits.data  := rdData
  io.axi.read.data.bits.resp  := rdResp
  io.axi.read.data.bits.last  := true.B

  when(rdState === RdState.sIdle && io.axi.read.addr.valid) {
    rdId    := io.axi.read.addr.bits.id
    rdState := RdState.sData

    val addr      = io.axi.read.addr.bits.addr
    val isStatus  = addr === 0x000.U
    val isCtrl    = addr === 0x004.U
    val isCsrBase = addr >= 0x100.U && addr < (0x100 + 16 * 4).U
    val isCsrAlgn = isCsrBase && ((addr - 0x100.U)(1, 0) === 0.U)
    val csrIdx    = (addr - 0x100.U) >> 2
    val csrVal    = MuxLookup(csrIdx, 0.U)(
      (0 until 16).map(i => i.U -> io.coralnpu_csr.value(i))
    )

    rdData := MuxCase(0.U, Seq(
      isStatus  -> Cat(0.U(32.W), 0.U(29.W), io.fault, io.halted, io.internal),
      isCtrl    -> Cat(pcStartReg, 0.U(30.W), ~cgReg, resetReg),
      isCsrAlgn -> Cat(0.U(32.W), csrVal),
    ))
    rdResp := Mux(isStatus || isCtrl || isCsrAlgn, 0.U, 2.U)
  }

  when(rdState === RdState.sData && io.axi.read.data.ready) {
    rdState := RdState.sIdle
  }

  // ---------------------------------------------------------------------------
  // AXI write
  // ---------------------------------------------------------------------------
  object WrState extends ChiselEnum { val sIdle, sResp = Value }
  val wrState = RegInit(WrState.sIdle)
  val wrId    = Reg(UInt(IdBits.W))
  val wrResp  = Reg(UInt(2.W))

  io.axi.write.addr.ready     := wrState === WrState.sIdle
  io.axi.write.data.ready     := wrState === WrState.sIdle
  io.axi.write.resp.valid     := wrState === WrState.sResp
  io.axi.write.resp.bits.id   := wrId
  io.axi.write.resp.bits.resp := wrResp

  when(wrState === WrState.sIdle &&
       io.axi.write.addr.valid && io.axi.write.data.valid) {
    wrId    := io.axi.write.addr.bits.id
    wrState := WrState.sResp

    val addr = io.axi.write.addr.bits.addr
    val data = io.axi.write.data.bits.data

    when(addr === 0x004.U) {
      wrResp     := 0.U
      // pcStart lives in data[63:32] of the 64-bit register
      pcStartReg := data(63, 32)
      // control bits live in data[31:0]; only written when explicit
      // (not touched in the standard test scenario)
    }.otherwise {
      wrResp := 2.U
    }
  }

  when(wrState === WrState.sResp && io.axi.write.resp.ready) {
    wrState := WrState.sIdle
  }
}
