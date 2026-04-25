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

object LsuOp extends ChiselEnum {
  val LOAD, STORE, LOAD_FLOAT, STORE_FLOAT = Value
}

// Load/Store Unit.
// For a 32-bit bus this is a simple pass-through of load/store requests.
// For a wider bus the unit aligns the address and extracts the correct bytes.
class Lsu(p: Parameters) extends Module {
  val dataBits  = p.lsuDataBits
  val dataBytes = dataBits / 8

  val io = IO(new Bundle {
    val req = Flipped(Valid(new Bundle {
      val addr    = UInt(32.W)
      val rdAddr  = UInt(5.W)
      val op      = LsuOp()
      val wdata   = UInt(32.W)
      val size    = UInt(2.W)   // 0=byte, 1=half, 2=word
      val signExt = Bool()
    }))
    val dbus  = new DBusBundle(dataBits)
    val rd    = Valid(new Bundle {
      val addr = UInt(5.W)
      val data = UInt(32.W)
    })
    val busy  = Output(Bool())
  })

  // ── State machine ──────────────────────────────────────────────────────────
  val sIdle :: sWait :: Nil = Enum(2)
  val state = RegInit(sIdle)

  // Latched copy of request for the WAIT state
  val rAddr    = RegInit(0.U(32.W))
  val rRdAddr  = RegInit(0.U(5.W))
  val rOp      = RegInit(LsuOp.LOAD)
  val rWdata   = RegInit(0.U(32.W))
  val rSize    = RegInit(0.U(2.W))
  val rSignExt = RegInit(false.B)

  // ── Byte offset within the bus word ───────────────────────────────────────
  private def byteOff(addr: UInt): UInt =
    if (dataBytes == 1) 0.U
    else addr(log2Ceil(dataBytes)-1, 0)

  // ── Aligned bus address ───────────────────────────────────────────────────
  private def alignAddr(addr: UInt): UInt =
    if (dataBytes == 1) addr
    else Cat(addr(31, log2Ceil(dataBytes)), 0.U(log2Ceil(dataBytes).W))

  // ── Write data replication ─────────────────────────────────────────────────
  // For 32-bit bus: wdata is the word; for wider bus replicate across all lanes
  private def mkWdata(wdata32: UInt): UInt =
    if (dataBits == 32) wdata32
    else {
      // replicate 32-bit word to fill bus width
      val nReps = dataBytes / 4
      VecInit(Seq.fill(nReps)(wdata32)).asUInt
    }

  // ── Write mask ────────────────────────────────────────────────────────────
  private def mkWmask(addr: UInt, size: UInt): UInt = {
    val base = Wire(UInt(4.W))
    base := MuxCase("b1111".U, Seq(
      (size === 0.U) -> "b0001".U,
      (size === 1.U) -> "b0011".U
    ))
    if (dataBytes == 4) base
    else {
      // shift the 4-bit mask to the right byte lane
      val off   = byteOff(addr)
      val wide  = (base << off)(dataBytes-1, 0)
      wide
    }
  }

  // ── Determine active request ──────────────────────────────────────────────
  val useReq = state === sIdle && io.req.valid
  val activeAddr    = Mux(useReq, io.req.bits.addr,    rAddr)
  val activeRdAddr  = Mux(useReq, io.req.bits.rdAddr,  rRdAddr)
  val activeOp      = Mux(useReq, io.req.bits.op,      rOp)
  val activeWdata   = Mux(useReq, io.req.bits.wdata,   rWdata)
  val activeSize    = Mux(useReq, io.req.bits.size,     rSize)
  val activeSignExt = Mux(useReq, io.req.bits.signExt, rSignExt)

  val isLoad  = (activeOp === LsuOp.LOAD)  || (activeOp === LsuOp.LOAD_FLOAT)
  val isStore = (activeOp === LsuOp.STORE) || (activeOp === LsuOp.STORE_FLOAT)

  // ── DBus drive ───────────────────────────────────────────────────────────
  io.dbus.valid := (state === sIdle && io.req.valid) || state === sWait
  io.dbus.addr  := alignAddr(activeAddr)
  io.dbus.write := isStore
  io.dbus.wdata := mkWdata(activeWdata)
  io.dbus.wmask := mkWmask(activeAddr, activeSize)
  io.dbus.size  := activeSize

  // ── Busy output ──────────────────────────────────────────────────────────
  io.busy := (state === sWait)

  // ── FSM transitions ──────────────────────────────────────────────────────
  switch (state) {
    is (sIdle) {
      when (io.req.valid && !io.dbus.ready) {
        // Bus stall: latch request and wait
        rAddr    := io.req.bits.addr
        rRdAddr  := io.req.bits.rdAddr
        rOp      := io.req.bits.op
        rWdata   := io.req.bits.wdata
        rSize    := io.req.bits.size
        rSignExt := io.req.bits.signExt
        state    := sWait
      }
    }
    is (sWait) {
      when (io.dbus.ready) {
        state := sIdle
      }
    }
  }

  // ── Read data extraction ──────────────────────────────────────────────────
  // One cycle after the bus fires for a load, extract the result
  val loadFired  = io.dbus.valid && io.dbus.ready && !io.dbus.write
  val ldMetaAddr    = RegNext(activeAddr)
  val ldMetaRdAddr  = RegNext(activeRdAddr)
  val ldMetaSize    = RegNext(activeSize)
  val ldMetaSignExt = RegNext(activeSignExt)
  val ldValid       = RegNext(loadFired, false.B)

  // Extract 32-bit sub-word from bus return data
  val rawData = io.dbus.rdata
  val shift   = if (dataBytes == 4) 0.U else
                  (ldMetaAddr(log2Ceil(dataBytes)-1, 0) ## 0.U(3.W))(4,0)  // byte offset * 8
  val shifted32 = (rawData >> shift)(31,0)

  val rdByte = Cat(Fill(24, Mux(ldMetaSignExt, shifted32(7),  false.B)), shifted32(7,0))
  val rdHalf = Cat(Fill(16, Mux(ldMetaSignExt, shifted32(15), false.B)), shifted32(15,0))
  val rdWord = shifted32

  val rdData = MuxCase(rdWord, Seq(
    (ldMetaSize === 0.U) -> rdByte,
    (ldMetaSize === 1.U) -> rdHalf
  ))

  io.rd.valid      := ldValid
  io.rd.bits.addr  := ldMetaRdAddr
  io.rd.bits.data  := rdData
}
