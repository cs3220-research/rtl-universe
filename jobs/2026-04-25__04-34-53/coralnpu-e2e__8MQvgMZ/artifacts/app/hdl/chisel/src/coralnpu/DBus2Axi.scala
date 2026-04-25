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

/** Data-bus to AXI4 bridge (version 2).
  *
  * Converts a simple valid/ready DBus transaction into an AXI4 master
  * transaction.  Only single-beat (len=0) transfers are generated.
  *
  * Write path:
  *   - AW: addr = aligned address, size = log2(bytes), len = 0, burst = INCR
  *   - W:  data = wdata masked by wmask, strb = wmask, last = 1
  *   - B:  ready when response received; dbus.ready pulses high
  *
  * Read path:
  *   - AR: addr = dbus.addr (not aligned), size = log2(bytes), len = 0
  *   - R:  data forwarded to dbus.rdata; dbus.ready pulses high
  */
class DBus2AxiV2(p: Parameters) extends Module {
  val io = IO(new Bundle {
    val dbus = Flipped(new DBusBundle(p.axi2DataBits, p.addrBits))
    val axi  = new AxiBundle(p.axi2DataBits, p.addrBits, p.axi2IdBits)
  })

  // -------------------------------------------------------------------------
  // State machine
  // -------------------------------------------------------------------------
  private object State extends ChiselEnum {
    val sIdle, sWriteAddr, sWriteData, sWriteResp, sReadAddr, sReadData = Value
  }
  import State._

  val state = RegInit(sIdle)

  // Latch the incoming DBus request
  val addrReg  = Reg(UInt(p.addrBits.W))
  val wdataReg = Reg(UInt(p.axi2DataBits.W))
  val wmaskReg = Reg(UInt((p.axi2DataBits / 8).W))
  val sizeReg  = Reg(UInt(3.W))       // byte count (1,2,4,…)
  val writeReg = Reg(Bool())

  // -------------------------------------------------------------------------
  // AXI default tie-offs
  // -------------------------------------------------------------------------
  io.axi.read.addr.valid        := false.B
  io.axi.read.addr.bits.id     := 0.U
  io.axi.read.addr.bits.addr   := addrReg
  io.axi.read.addr.bits.len    := 0.U
  io.axi.read.addr.bits.size   := Log2(sizeReg)
  io.axi.read.addr.bits.burst  := 1.U  // INCR

  io.axi.read.data.ready       := false.B

  io.axi.write.addr.valid       := false.B
  io.axi.write.addr.bits.id    := 0.U
  io.axi.write.addr.bits.addr  := addrReg & ~(sizeReg - 1.U)  // aligned
  io.axi.write.addr.bits.len   := 0.U
  io.axi.write.addr.bits.size  := Log2(sizeReg)
  io.axi.write.addr.bits.burst := 1.U  // INCR

  io.axi.write.data.valid       := false.B
  io.axi.write.data.bits.data  := wdataReg
  io.axi.write.data.bits.strb  := wmaskReg
  io.axi.write.data.bits.last  := true.B

  io.axi.write.resp.ready      := false.B

  // DBus outputs
  io.dbus.ready := false.B
  io.dbus.rdata := io.axi.read.data.bits.data

  // -------------------------------------------------------------------------
  // State transitions
  // -------------------------------------------------------------------------
  switch(state) {
    is(sIdle) {
      when(io.dbus.valid) {
        addrReg  := io.dbus.addr
        wdataReg := io.dbus.wdata
        wmaskReg := io.dbus.wmask
        sizeReg  := io.dbus.size
        writeReg := io.dbus.write
        state    := Mux(io.dbus.write, sWriteAddr, sReadAddr)
      }
    }

    // ---- Write path --------------------------------------------------------
    is(sWriteAddr) {
      io.axi.write.addr.valid := true.B
      io.axi.write.addr.bits.addr := addrReg & ~(sizeReg - 1.U)
      io.axi.write.addr.bits.size := Log2(sizeReg)
      when(io.axi.write.addr.ready) {
        state := sWriteData
      }
    }

    is(sWriteData) {
      io.axi.write.data.valid := true.B
      // Mask data to only the bytes indicated by wmask
      val maskedData = Wire(UInt(p.axi2DataBits.W))
      maskedData := wdataReg  // strobe already carried separately; data still latched
      io.axi.write.data.bits.data := maskedData
      io.axi.write.data.bits.strb := wmaskReg
      when(io.axi.write.data.ready) {
        state := sWriteResp
      }
    }

    is(sWriteResp) {
      io.axi.write.resp.ready := true.B
      when(io.axi.write.resp.valid) {
        io.dbus.ready := true.B
        state := sIdle
      }
    }

    // ---- Read path ---------------------------------------------------------
    is(sReadAddr) {
      io.axi.read.addr.valid := true.B
      io.axi.read.addr.bits.addr := addrReg
      io.axi.read.addr.bits.size := Log2(sizeReg)
      when(io.axi.read.addr.ready) {
        state := sReadData
      }
    }

    is(sReadData) {
      io.axi.read.data.ready := true.B
      io.dbus.rdata          := io.axi.read.data.bits.data
      when(io.axi.read.data.valid) {
        io.dbus.ready := true.B
        state := sIdle
      }
    }
  }
}

/** Chisel emission entry-point for the DBus2AxiV2 standalone build. */
class EmitDBus2Axi extends App {
  val p = new Parameters
  circt.stage.ChiselStage.emitSystemVerilog(new DBus2AxiV2(p))
}
