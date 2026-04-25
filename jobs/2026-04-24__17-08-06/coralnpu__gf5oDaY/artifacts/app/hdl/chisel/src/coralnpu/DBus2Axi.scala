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
 * DBus to AXI bridge (version 2).
 *
 * Translates DBus transactions to AXI4 read/write transactions.
 * - Write: sends AW, W, waits for B (write response).
 * - Read:  sends AR, waits for R (read data).
 * DBus ready is asserted when the AXI transaction completes.
 */
class DBus2AxiV2(p: Parameters) extends Module {
  val io = IO(new Bundle {
    val dbus = new DBusInterface(p)
    val axi  = new AxiDataInterface(p)
  })

  // State machine
  val sIdle :: sWriteAddr :: sWriteData :: sWriteResp :: sReadAddr :: sReadData :: Nil = Enum(6)
  val state = RegInit(sIdle)

  // Latch the DBus transaction
  val addrReg  = RegInit(0.U(p.addrBits.W))
  val writeReg = RegInit(false.B)
  val wdataReg = RegInit(0.U(p.lsuDataBits.W))
  val wmaskReg = RegInit(0.U((p.lsuDataBits / 8).W))
  val sizeReg  = RegInit(0.U(4.W))

  // Default outputs
  io.dbus.ready := false.B

  io.axi.read.addr.valid := false.B
  io.axi.read.addr.bits.id   := 0.U
  io.axi.read.addr.bits.addr := addrReg
  io.axi.read.addr.bits.len  := 0.U
  io.axi.read.addr.bits.size := 0.U
  io.axi.read.data.ready := true.B

  io.axi.write.addr.valid := false.B
  io.axi.write.addr.bits.id   := 0.U
  io.axi.write.addr.bits.addr := 0.U
  io.axi.write.addr.bits.len  := 0.U
  io.axi.write.addr.bits.size := 0.U
  io.axi.write.data.valid := false.B
  io.axi.write.data.bits.data := 0.U
  io.axi.write.data.bits.strb := 0.U
  io.axi.write.data.bits.last := true.B
  io.axi.write.resp.ready := false.B

  // Aligned address and AXI size encoding
  val sizeBits  = sizeReg  // number of bytes
  val axiSize   = MuxLookup(sizeBits, 0.U)(Seq(
    1.U -> 0.U,  // 1 byte  -> size=0
    2.U -> 1.U,  // 2 bytes -> size=1
    4.U -> 2.U,  // 4 bytes -> size=2
    8.U -> 3.U,  // 8 bytes -> size=3
    16.U -> 4.U,
  ))
  val alignedAddr = addrReg & (~((sizeBits - 1.U).pad(p.addrBits)))

  // FSM
  switch(state) {
    is(sIdle) {
      when(io.dbus.valid) {
        addrReg  := io.dbus.addr
        writeReg := io.dbus.write
        wdataReg := io.dbus.wdata
        wmaskReg := io.dbus.wmask
        sizeReg  := io.dbus.size
        state    := Mux(io.dbus.write, sWriteAddr, sReadAddr)
      }
    }
    is(sWriteAddr) {
      val alignedAddrW = addrReg & (~((sizeReg - 1.U).pad(p.addrBits)))
      io.axi.write.addr.valid       := true.B
      io.axi.write.addr.bits.addr   := alignedAddrW
      io.axi.write.addr.bits.len    := 0.U
      io.axi.write.addr.bits.size   := MuxLookup(sizeReg, 0.U)(Seq(
        1.U -> 0.U, 2.U -> 1.U, 4.U -> 2.U, 8.U -> 3.U, 16.U -> 4.U,
      ))
      when(io.axi.write.addr.ready) {
        state := sWriteData
      }
    }
    is(sWriteData) {
      // Mask out bytes not in wmask using a pure combinational expression
      val maskBits = (0 until (p.lsuDataBits / 8)).map { i =>
        Mux(wmaskReg(i), (BigInt(0xFF) << (i * 8)).U(p.lsuDataBits.W), 0.U(p.lsuDataBits.W))
      }.reduce(_ | _)
      val maskedData = wdataReg & maskBits

      io.axi.write.data.valid       := true.B
      io.axi.write.data.bits.data   := maskedData
      io.axi.write.data.bits.strb   := wmaskReg
      io.axi.write.data.bits.last   := true.B
      when(io.axi.write.data.ready) {
        state := sWriteResp
      }
    }
    is(sWriteResp) {
      io.axi.write.resp.ready := true.B
      io.dbus.ready           := io.axi.write.resp.valid
      when(io.axi.write.resp.valid) {
        state := sIdle
      }
    }
    is(sReadAddr) {
      io.axi.read.addr.valid       := true.B
      io.axi.read.addr.bits.addr   := addrReg
      io.axi.read.addr.bits.len    := 0.U
      io.axi.read.addr.bits.size   := MuxLookup(sizeReg, 0.U)(Seq(
        1.U -> 0.U, 2.U -> 1.U, 4.U -> 2.U, 8.U -> 3.U, 16.U -> 4.U,
      ))
      when(io.axi.read.addr.ready) {
        state := sReadData
      }
    }
    is(sReadData) {
      io.dbus.ready := io.axi.read.data.valid
      when(io.axi.read.data.valid) {
        state := sIdle
      }
    }
  }
}

/** Emitter object for DBus2AxiV2. */
object EmitDBus2Axi extends App {
  val p = new Parameters
  _root_.circt.stage.ChiselStage.emitSystemVerilog(new DBus2AxiV2(p))
}
