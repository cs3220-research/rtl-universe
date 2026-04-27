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

/** AXI CSR slave for the CoralNPU core.
  *
  * Manages the host-visible control/status registers:
  *
  * {{{
  * Offset  Register          Width  Description
  * 0x000   RESET_CONTROL      32    bit[0]=reset (1=held in reset), bit[1]=cg (1=clock gated)
  * 0x004   PC_START           32    Boot program-counter (sent to CPU as pcStart)
  * 0x100   STATUS             32    Read-only status word
  * }}}
  *
  * The AXI data bus is 128 bits wide.  Register values are placed/extracted at
  * the 32-bit lane selected by `(addr >> 2) & 3` within the 128-bit bus word.
  * Writes to undefined offsets return AXI SLVERR (resp=2).
  *
  * Initial values:  RESET_CONTROL=0x3 (reset=1, cg=1), PC_START=0.
  */
class CoreAxiCSR(p: Parameters) extends Module {

  // The host-facing AXI slave interface is always 128-bit wide.
  private val axiBits  = 128
  private val axiBytes = axiBits / 8

  val io = IO(new Bundle {
    val axi          = Flipped(new AxiBundle(axiBits, p.addrBits, p.axiIdBits))
    val internal     = Input(Bool())
    val halted       = Input(Bool())
    val fault        = Input(Bool())
    val coralnpu_csr = Input(new CsrReadPort)
    val cg           = Output(Bool())
    val reset        = Output(Bool())
    val pcStart      = Output(UInt(32.W))
  })

  // ---------------------------------------------------------------------------
  // Registers
  // ---------------------------------------------------------------------------
  // RESET_CONTROL: bit0=reset, bit1=cg. Initial=0x3 (both asserted).
  val resetReg  = RegInit(true.B)   // io.reset
  val cgReg     = RegInit(true.B)   // io.cg
  val pcStartReg = RegInit(0.U(32.W))

  io.reset   := resetReg
  io.cg      := cgReg
  io.pcStart := pcStartReg

  // ---------------------------------------------------------------------------
  // STATUS register (read-only): returns coralnpu_csr.value(0) directly.
  // Bits [2:0] also carry: [0]=internal, [1]=halted, [2]=fault.
  // ---------------------------------------------------------------------------
  val statusReg = io.coralnpu_csr.value(0)

  // ---------------------------------------------------------------------------
  // AXI slave FSM
  // ---------------------------------------------------------------------------
  // Valid register addresses (word-aligned byte addresses)
  private val ADDR_RESET_CTRL = 0x000
  private val ADDR_PC_START   = 0x004
  private val ADDR_STATUS     = 0x100

  private object State extends ChiselEnum {
    val sIdle, sReadResp, sWriteResp = Value
  }
  import State._

  val state      = RegInit(sIdle)
  val readDataReg = Reg(UInt(axiBits.W))
  val idReg      = Reg(UInt(p.axiIdBits.W))
  val respErrReg = RegInit(false.B)

  // Always accept addresses when idle
  io.axi.read.addr.ready  := (state === sIdle)
  io.axi.write.addr.ready := (state === sIdle)
  io.axi.write.data.ready := (state === sIdle)

  // Read data channel
  io.axi.read.data.valid       := (state === sReadResp)
  io.axi.read.data.bits.id    := idReg
  io.axi.read.data.bits.data  := readDataReg
  io.axi.read.data.bits.resp  := 0.U
  io.axi.read.data.bits.last  := true.B

  // Write response channel
  io.axi.write.resp.valid      := (state === sWriteResp)
  io.axi.write.resp.bits.id   := idReg
  io.axi.write.resp.bits.resp := Mux(respErrReg, 2.U, 0.U)  // SLVERR or OKAY

  // Helper: extract the 32-bit lane from a 128-bit value based on byte addr
  def laneData(data: UInt, addr: UInt): UInt = {
    val lane = (addr >> 2)(1, 0)
    val shifted = (data >> (lane * 32.U))(31, 0)
    shifted
  }

  // Helper: build a 128-bit word with a 32-bit value placed at the correct lane
  def buildWord(data32: UInt, addr: UInt): UInt = {
    val lane   = (addr >> 2)(1, 0)
    val shift  = lane * 32.U
    (data32.asTypeOf(UInt(axiBits.W))) << shift
  }

  switch(state) {
    is(sIdle) {
      // ---- Read ----
      when(io.axi.read.addr.valid) {
        idReg := io.axi.read.addr.bits.id
        val addr = io.axi.read.addr.bits.addr

        // Build the 128-bit reply word; place the 32-bit register at the
        // appropriate lane.
        val regData = Wire(UInt(32.W))
        regData := 0.U

        when(addr(11, 2) === (ADDR_RESET_CTRL >> 2).U) {
          regData := Cat(0.U(30.W), cgReg, resetReg)
        }.elsewhen(addr(11, 2) === (ADDR_PC_START >> 2).U) {
          regData := pcStartReg
        }.elsewhen(addr(11, 2) === (ADDR_STATUS >> 2).U) {
          regData := statusReg
        }.otherwise {
          regData := io.coralnpu_csr.value(0)
        }

        // Place the 32-bit value at the bus lane corresponding to addr[3:2]
        val lane = addr(3, 2)
        readDataReg := regData.asTypeOf(UInt(axiBits.W)) << (lane * 32.U)
        state := sReadResp
      }

      // ---- Write (addr and data arrive together or addr first) ----
      .elsewhen(io.axi.write.addr.valid && io.axi.write.data.valid) {
        idReg := io.axi.write.addr.bits.id
        val addr = io.axi.write.addr.bits.addr
        val data = io.axi.write.data.bits.data

        // Extract 32-bit value from the lane indicated by the address
        val lane   = addr(3, 2)
        val data32 = (data >> (lane * 32.U))(31, 0)

        respErrReg := false.B

        when(addr(11, 2) === (ADDR_RESET_CTRL >> 2).U) {
          resetReg := data32(0)
          cgReg    := data32(1)
        }.elsewhen(addr(11, 2) === (ADDR_PC_START >> 2).U) {
          pcStartReg := data32
        }.otherwise {
          // Write to undefined/read-only address → SLVERR, no register update
          respErrReg := true.B
        }

        state := sWriteResp
      }
    }

    is(sReadResp) {
      when(io.axi.read.data.ready) {
        state := sIdle
      }
    }

    is(sWriteResp) {
      when(io.axi.write.resp.ready) {
        state := sIdle
      }
    }
  }
}
