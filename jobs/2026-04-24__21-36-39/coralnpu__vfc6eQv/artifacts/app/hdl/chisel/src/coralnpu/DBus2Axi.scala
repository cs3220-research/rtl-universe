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

/** Simple data-bus to AXI4 adapter.
  *
  * Translates single-beat DBus transactions (valid/ready handshake) into
  * AXI4 single-beat read or write transactions.
  *
  * The `dbus.size` field carries the transfer size in bytes (1, 2, or 4).
  * The AXI `size` field carries log2(bytes).
  *
  * For writes the aligned address is computed as `addr - (addr % size)`.
  * Write data is passed through with strobed bytes zeroed.
  */
class DBus2AxiV2(p: Parameters) extends Module {

  // Number of bytes on the AXI data bus
  val DataBytes = p.axi2DataBits / 8

  val io = IO(new Bundle {
    val dbus = new Bundle {
      val valid = Input(Bool())
      val ready = Output(Bool())
      val write = Input(Bool())
      val addr  = Input(UInt(p.axi2AddrBits.W))
      val size  = Input(UInt(3.W))               // transfer bytes (1/2/4/…)
      val wdata = Input(UInt(p.axi2DataBits.W))
      val wmask = Input(UInt(DataBytes.W))
    }
    val axi = new AxiMasterIO(p.axi2AddrBits, p.axi2DataBits, p.axi2IdBits)
  })

  // -------------------------------------------------------------------------
  // State machine
  // -------------------------------------------------------------------------
  val sIdle      = 0.U(3.W)
  val sWriteAddr = 1.U(3.W)
  val sWriteData = 2.U(3.W)
  val sWriteResp = 3.U(3.W)
  val sReadAddr  = 4.U(3.W)
  val sReadData  = 5.U(3.W)

  val state = RegInit(sIdle)

  // Captured request fields
  val regAddr  = Reg(UInt(p.axi2AddrBits.W))
  val regSize  = Reg(UInt(3.W))  // bytes (1, 2, 4, …)
  val regWdata = Reg(UInt(p.axi2DataBits.W))
  val regWmask = Reg(UInt(DataBytes.W))
  val regWrite = Reg(Bool())

  // -------------------------------------------------------------------------
  // Aligned write address: addr - (addr % size)
  // For size in {1,2,4,8,…}: mask = size - 1; alignedAddr = addr & ~mask
  // -------------------------------------------------------------------------
  val alignedAddr = regAddr & ~(regSize - 1.U)

  // -------------------------------------------------------------------------
  // AXI size field: log2(bytes).
  // Compute combinationally from regSize.
  // -------------------------------------------------------------------------
  val axiSize = log2Ceil(1) // placeholder, overridden by MuxLookup below

  // Use PriorityMux / MuxLookup for the size encoding
  val axiSizeField = MuxLookup(regSize, 0.U(3.W))(Seq(
    1.U  -> 0.U(3.W),
    2.U  -> 1.U(3.W),
    4.U  -> 2.U(3.W),
    8.U  -> 3.U(3.W),
    16.U -> 4.U(3.W),
    32.U -> 5.U(3.W),
  ))

  // -------------------------------------------------------------------------
  // Masked write data: zero out bytes where strobe = 0
  // -------------------------------------------------------------------------
  val maskedWdata = Wire(UInt(p.axi2DataBits.W))
  val wdataBytes  = Wire(Vec(DataBytes, UInt(8.W)))
  for (i <- 0 until DataBytes) {
    wdataBytes(i) := Mux(regWmask(i), regWdata(i * 8 + 7, i * 8), 0.U(8.W))
  }
  maskedWdata := wdataBytes.asUInt

  // -------------------------------------------------------------------------
  // Default / constant AXI field values
  // -------------------------------------------------------------------------
  val axiId    = 0.U(p.axi2IdBits.W)
  val axiBurst = 1.U(2.W)  // INCR
  val axiLock  = 0.U(1.W)
  val axiCache = 0.U(4.W)
  val axiProt  = 0.U(3.W)
  val axiQos   = 0.U(4.W)
  val axiLen   = 0.U(8.W)  // single beat

  // -------------------------------------------------------------------------
  // Default assignments (all channels idle unless stated otherwise)
  // -------------------------------------------------------------------------
  // Write address channel
  io.axi.write.addr.valid      := false.B
  io.axi.write.addr.bits.addr  := alignedAddr
  io.axi.write.addr.bits.size  := axiSizeField
  io.axi.write.addr.bits.len   := axiLen
  io.axi.write.addr.bits.id    := axiId
  io.axi.write.addr.bits.burst := axiBurst
  io.axi.write.addr.bits.lock  := axiLock
  io.axi.write.addr.bits.cache := axiCache
  io.axi.write.addr.bits.prot  := axiProt
  io.axi.write.addr.bits.qos   := axiQos

  // Write data channel
  io.axi.write.data.valid      := false.B
  io.axi.write.data.bits.data  := maskedWdata
  io.axi.write.data.bits.strb  := regWmask
  io.axi.write.data.bits.last  := true.B

  // Write response channel
  io.axi.write.resp.ready := false.B

  // Read address channel
  io.axi.read.addr.valid      := false.B
  io.axi.read.addr.bits.addr  := regAddr
  io.axi.read.addr.bits.size  := axiSizeField
  io.axi.read.addr.bits.len   := axiLen
  io.axi.read.addr.bits.id    := axiId
  io.axi.read.addr.bits.burst := axiBurst
  io.axi.read.addr.bits.lock  := axiLock
  io.axi.read.addr.bits.cache := axiCache
  io.axi.read.addr.bits.prot  := axiProt
  io.axi.read.addr.bits.qos   := axiQos

  // Read data channel: always ready to accept data when in READ_DATA state
  io.axi.read.data.ready := (state === sReadData)

  // DBus ready: asserted when the transaction completes
  io.dbus.ready := false.B

  // -------------------------------------------------------------------------
  // State machine transitions
  // -------------------------------------------------------------------------
  switch(state) {
    is(sIdle) {
      when(io.dbus.valid) {
        regAddr  := io.dbus.addr
        regSize  := io.dbus.size
        regWdata := io.dbus.wdata
        regWmask := io.dbus.wmask
        regWrite := io.dbus.write
        state    := Mux(io.dbus.write, sWriteAddr, sReadAddr)
      }
    }

    is(sWriteAddr) {
      io.axi.write.addr.valid := true.B
      when(io.axi.write.addr.ready) {
        state := sWriteData
      }
    }

    is(sWriteData) {
      io.axi.write.data.valid := true.B
      when(io.axi.write.data.ready) {
        state := sWriteResp
      }
    }

    is(sWriteResp) {
      io.axi.write.resp.ready := true.B
      when(io.axi.write.resp.valid) {
        io.dbus.ready := true.B
        state         := sIdle
      }
    }

    is(sReadAddr) {
      io.axi.read.addr.valid := true.B
      when(io.axi.read.addr.ready) {
        state := sReadData
      }
    }

    is(sReadData) {
      io.axi.read.data.ready := true.B
      when(io.axi.read.data.valid) {
        io.dbus.ready := true.B
        when(io.axi.read.data.bits.last) {
          state := sIdle
        }
      }
    }
  }
}

object EmitDBus2Axi extends App {
  import circt.stage.ChiselStage
  ChiselStage.emitSystemVerilog(new DBus2AxiV2(new Parameters), args)
}
