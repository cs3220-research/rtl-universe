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

/** DBus-to-AXI bridge.
  *
  * Converts the internal DBus (single-cycle request with byte-enable) into
  * AXI4 read/write transactions.
  *
  * The AXI data width matches p.axi2DataBits (256-bit by default).
  * Byte strobe width = axi2DataBits / 8 (32 bytes by default).
  */
class DBus2AxiIO(p: Parameters) extends Bundle {
  val dbus = new DBusIO(p)
  val axi  = new AxiMasterBundle(p.axiAddrBits, p.axi2DataBits, p.axi2IdBits)
}

class DBus2AxiV2(p: Parameters) extends Module {
  val io = IO(new DBus2AxiIO(p))

  // -----------------------------------------------------------------------
  // State machine
  // -----------------------------------------------------------------------
  object State extends ChiselEnum {
    val sIdle, sWriteAddr, sWriteData, sWriteResp, sReadAddr, sReadData = Value
  }
  import State._

  val state = RegInit(sIdle)

  // Captured request
  val reqAddr  = Reg(UInt(p.axiAddrBits.W))
  val reqWrite = Reg(Bool())
  val reqWdata = Reg(UInt(p.axi2DataBits.W))
  val reqWmask = Reg(UInt((p.axi2DataBits / 8).W))
  val reqSize  = Reg(UInt(log2Ceil(p.axi2DataBits / 8 + 1).W))

  // Default outputs
  io.dbus.ready := false.B
  io.dbus.rdata := 0.U

  // AXI write address channel
  io.axi.write.addr.valid        := false.B
  io.axi.write.addr.bits.id      := 0.U
  io.axi.write.addr.bits.addr    := 0.U
  io.axi.write.addr.bits.len     := 0.U
  io.axi.write.addr.bits.size    := 0.U
  io.axi.write.addr.bits.burst   := 1.U

  // AXI write data channel
  io.axi.write.data.valid        := false.B
  io.axi.write.data.bits.data    := 0.U
  io.axi.write.data.bits.strb    := 0.U
  io.axi.write.data.bits.last    := true.B

  // AXI write response channel
  io.axi.write.resp.ready        := false.B

  // AXI read address channel
  io.axi.read.addr.valid         := false.B
  io.axi.read.addr.bits.id       := 0.U
  io.axi.read.addr.bits.addr     := 0.U
  io.axi.read.addr.bits.len      := 0.U
  io.axi.read.addr.bits.size     := 0.U
  io.axi.read.addr.bits.burst    := 1.U

  // AXI read data channel
  io.axi.read.data.ready         := false.B

  switch(state) {
    is(sIdle) {
      when(io.dbus.valid) {
        reqAddr  := io.dbus.addr
        reqWrite := io.dbus.write
        reqWdata := io.dbus.wdata
        reqWmask := io.dbus.wmask
        reqSize  := io.dbus.size
        state    := Mux(io.dbus.write, sWriteAddr, sReadAddr)
      }
    }

    is(sWriteAddr) {
      // Aligned address
      val alignedAddr = reqAddr & ~((reqSize - 1.U)(p.axiAddrBits - 1, 0))
      io.axi.write.addr.valid      := true.B
      io.axi.write.addr.bits.addr  := alignedAddr
      io.axi.write.addr.bits.len   := 0.U
      io.axi.write.addr.bits.size  := Log2(reqSize)
      io.axi.write.addr.bits.burst := 1.U
      when(io.axi.write.addr.ready) {
        state := sWriteData
      }
    }

    is(sWriteData) {
      // Expand byte strobe to bit mask: each strobe bit => 8 output bits
      val strobeBytes = reqWmask.asBools  // LSB = byte 0
      val strobeMask  = Cat(strobeBytes.reverse.map(b => Mux(b, 0xFF.U(8.W), 0.U(8.W))))
      io.axi.write.data.valid      := true.B
      io.axi.write.data.bits.data  := reqWdata & strobeMask
      io.axi.write.data.bits.strb  := reqWmask
      io.axi.write.data.bits.last  := true.B
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

    is(sReadAddr) {
      io.axi.read.addr.valid       := true.B
      io.axi.read.addr.bits.addr   := reqAddr
      io.axi.read.addr.bits.len    := 0.U
      io.axi.read.addr.bits.size   := Log2(reqSize)
      io.axi.read.addr.bits.burst  := 1.U
      io.dbus.ready := true.B  // Signal ready when read addr accepted
      when(io.axi.read.addr.ready) {
        state := sReadData
      }
    }

    is(sReadData) {
      io.axi.read.data.ready := true.B
      io.dbus.ready := true.B
      when(io.axi.read.data.valid) {
        io.dbus.rdata := io.axi.read.data.bits.data
        state := sIdle
      }
    }
  }
}

object EmitDBus2Axi extends App {
  import circt.stage.ChiselStage
  val p = new Parameters
  ChiselStage.emitSystemVerilog(new DBus2AxiV2(p))
}
