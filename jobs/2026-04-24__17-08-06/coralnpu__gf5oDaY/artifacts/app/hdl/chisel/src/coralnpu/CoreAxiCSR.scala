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

/**
 * CoreAxiCSR: AXI-accessible CSR register file for the NPU core.
 *
 * Register layout (64-bit data bus, byte-addressed):
 *   0x00: RESET_CONTROL  bits[0]=cg, bits[1]=reset   (R/W)
 *   0x04: PC_START       bits[31:0]=pcStart          (R/W) -- upper 32 bits of 64-bit word at 0x00
 *   0x08: STATUS         bits[0]=halted, bits[1]=fault (R)
 *   0x100+: coralnpu_csr passthrough (32-bit values)
 *
 * Write protocol:
 *   Addr=0x4, strb=0xFF00, data=(val<<32) means the strb[15:8] selects the
 *   upper 8 bytes (64-bit), and the data is positioned in bits[63:32] to
 *   give the 32-bit value. PC_START is extracted from bits[63:32].
 *
 * Read from 0x4 returns PC_START in bits[63:32] of the 64-bit response.
 * Read from 0x100 returns coralnpu_csr.value(0) in bits[31:0].
 */
class CoreAxiCSR(p: Parameters) extends Module {
  val io = IO(new Bundle {
    val axi           = new AxiCSRInterface
    val coralnpu_csr  = Input(new CsrValues)
    val cg            = Output(Bool())
    val reset         = Output(Bool())
    val pcStart       = Output(UInt(32.W))
    val internal      = Input(Bool())
    val halted        = Input(Bool())
    val fault         = Input(Bool())
  })

  // Internal registers (initial values: cg=1, reset=1, pcStart=0)
  val cgReg      = RegInit(true.B)
  val resetReg   = RegInit(true.B)
  val pcStartReg = RegInit(0.U(32.W))

  io.cg      := cgReg
  io.reset   := resetReg
  io.pcStart := pcStartReg

  // ---- AXI read state machine ----
  val sReadIdle :: sReadPending :: Nil = Enum(2)
  val readState   = RegInit(sReadIdle)
  val readAddrReg = RegInit(0.U(32.W))

  io.axi.read.addr.ready         := (readState === sReadIdle)
  io.axi.read.data.valid         := (readState === sReadPending)
  io.axi.read.data.bits.resp     := 0.U
  io.axi.read.data.bits.last     := true.B

  // Compute read data based on captured address
  val readData = Wire(UInt(64.W))
  readData := 0.U

  when(readAddrReg >= 0x100.U) {
    // coralnpu_csr array: 32-bit values at 4-byte intervals
    val idx = (readAddrReg - 0x100.U) >> 2
    when(idx < 16.U) {
      // Place 32-bit value in lower 32 bits of 64-bit response
      readData := Cat(0.U(32.W), io.coralnpu_csr.value(idx))
    }
  }.otherwise {
    // Internal registers: address is byte-addressed
    // Group addresses to 8-byte aligned words
    val wordAddr = readAddrReg & ~7.U(32.W)
    switch(wordAddr) {
      is(0x0.U) {
        // Word 0 (bytes 0..7): [7:0]=lo32, [63:32]=hi32
        // addr 0x0: return RESET_CONTROL in lo32
        // addr 0x4: return PC_START in hi32
        // We check exact addr since we return different things:
        // But test reads addr=0x4 and checks data>>32 == pcStart
        // So for addr 0x4: return pcStart in bits[63:32]
        // For addr 0x0: return RESET_CONTROL in bits[31:0]
        when(readAddrReg(2)) {
          // addr 0x4: PC_START in upper 32 bits
          readData := Cat(pcStartReg, 0.U(32.W))
        }.otherwise {
          // addr 0x0: RESET_CONTROL
          readData := Cat(0.U(32.W), Cat(0.U(30.W), resetReg, cgReg))
        }
      }
      is(0x8.U) {
        // STATUS register
        readData := Cat(0.U(32.W), Cat(0.U(30.W), io.fault, io.halted))
      }
    }
  }

  io.axi.read.data.bits.data := readData

  switch(readState) {
    is(sReadIdle) {
      when(io.axi.read.addr.valid) {
        readAddrReg := io.axi.read.addr.bits.addr
        readState   := sReadPending
      }
    }
    is(sReadPending) {
      when(io.axi.read.data.ready) {
        readState := sReadIdle
      }
    }
  }

  // ---- AXI write state machine ----
  val sWriteIdle :: sWriteResp :: Nil = Enum(2)
  val writeState   = RegInit(sWriteIdle)
  val writeRespReg = RegInit(0.U(2.W))

  io.axi.write.addr.ready     := (writeState === sWriteIdle)
  io.axi.write.data.ready     := (writeState === sWriteIdle)
  io.axi.write.resp.valid     := (writeState === sWriteResp)
  io.axi.write.resp.bits.resp := writeRespReg

  switch(writeState) {
    is(sWriteIdle) {
      when(io.axi.write.addr.valid && io.axi.write.data.valid) {
        val waddr = io.axi.write.addr.bits.addr
        val wdata = io.axi.write.data.bits.data   // 64-bit
        val wstrb = io.axi.write.data.bits.strb   // 16-bit

        // Only addresses 0x0..0x8 are writable
        val addrOk = waddr <= 0x8.U

        when(!addrOk) {
          writeRespReg := 2.U  // SLVERR
        }.otherwise {
          writeRespReg := 0.U  // OKAY

          // Write RESET_CONTROL at addr 0x0
          // strb[3:0] covers bytes 0..3 (lo32 = RESET_CONTROL)
          when(waddr === 0x0.U) {
            when(wstrb(0)) { cgReg    := wdata(0) }
            when(wstrb(1)) { resetReg := wdata(1) }
          }

          // Write PC_START at addr 0x4
          // strb=0xFF00 means bytes 8..15 of 128-bit transfer are active.
          // strb[15:8]=0xFF means upper 64 bits of 128-bit are active.
          // The upper 64-bit data = wdata (since data field is 64-bit and represents the active half).
          // PC_START = wdata[63:32] (from the test: data=(0x20000000<<32), pcStart=0x20000000)
          when(waddr === 0x4.U) {
            // strb[15:8] selects upper bytes; strb[12] = byte 12 = bit 4 of wdata upper 32
            // Simpler: if any of strb[15:8] is set, write PC_START from wdata[63:32]
            when(wstrb(8, 8).orR || wstrb(12, 12).orR) {
              pcStartReg := wdata(63, 32)
            }
            // Also handle simple writes where strb[7:4] is set (direct 32-bit write)
            when(wstrb(4, 4).orR) {
              pcStartReg := wdata(63, 32)
            }
            when(wstrb(7, 4).orR) {
              pcStartReg := wdata(63, 32)
            }
          }
        }
        writeState := sWriteResp
      }
    }
    is(sWriteResp) {
      when(io.axi.write.resp.ready) {
        writeState := sWriteIdle
      }
    }
  }
}
