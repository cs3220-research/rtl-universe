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

package peripheral

import chisel3._
import chisel3.util._
import bus._

/** Internal AXI read slave handshake module.
  *
  * Exposes two IO bundles:
  *  - `read`  for `<>`-connecting to an AXI master's read channel
  *  - `ctrl`  for injecting read-data and receiving the latched address
  */
class AxiReadSlave(idBits: Int) extends Module {
  val read = IO(new Bundle {
    val addr = Flipped(Decoupled(new AxiReadAddrBits(32, idBits)))
    val data = Decoupled(new AxiReadDataBits(32, idBits))
  })
  val ctrl = IO(new Bundle {
    val latchedAddr = Output(UInt(32.W))
    val dataIn      = Input(UInt(32.W))
    val respIn      = Input(UInt(2.W))
  })

  val addrReg = RegInit(0.U(32.W))
  val idReg   = RegInit(0.U(idBits.W))
  val pending = RegInit(false.B)

  ctrl.latchedAddr := addrReg

  read.addr.ready := !pending
  read.data.valid := pending
  read.data.bits.id   := idReg
  read.data.bits.last := true.B
  read.data.bits.data := ctrl.dataIn
  read.data.bits.resp := ctrl.respIn

  when (read.addr.fire) {
    addrReg := read.addr.bits.addr
    idReg   := read.addr.bits.id
    pending := true.B
  }
  when (read.data.fire) {
    pending := false.B
  }
}

/** Internal AXI write slave handshake module. */
class AxiWriteSlave(idBits: Int) extends Module {
  val write = IO(new Bundle {
    val addr = Flipped(Decoupled(new AxiWriteAddrBits(32, idBits)))
    val data = Flipped(Decoupled(new AxiWriteDataBits(32)))
    val resp = Decoupled(new AxiWriteRespBits(idBits))
  })
  val ctrl = IO(new Bundle {
    val fired       = Output(Bool())
    val latchedAddr = Output(UInt(32.W))
    val latchedData = Output(UInt(32.W))
    val respIn      = Input(UInt(2.W))
  })

  val addrReg     = RegInit(0.U(32.W))
  val idReg       = RegInit(0.U(idBits.W))
  val dataReg     = RegInit(0.U(32.W))
  val addrPending = RegInit(false.B)
  val dataPending = RegInit(false.B)
  val respPending = RegInit(false.B)
  val respReg     = RegInit(0.U(2.W))

  ctrl.latchedAddr := addrReg
  ctrl.latchedData := dataReg

  write.addr.ready := !addrPending && !respPending
  write.data.ready := !dataPending && !respPending

  when (write.addr.fire) {
    addrReg     := write.addr.bits.addr
    idReg       := write.addr.bits.id
    addrPending := true.B
  }
  when (write.data.fire) {
    dataReg     := write.data.bits.data
    dataPending := true.B
  }

  val doWrite = addrPending && dataPending && !respPending
  ctrl.fired  := doWrite

  when (doWrite) {
    addrPending := false.B
    dataPending := false.B
    respPending := true.B
    respReg     := ctrl.respIn
  }

  write.resp.valid     := respPending
  write.resp.bits.id   := idReg
  write.resp.bits.resp := respReg

  when (write.resp.fire) {
    respPending := false.B
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// Public helper API
// ─────────────────────────────────────────────────────────────────────────────

/** Create an AXI read-slave sub-module and wire it with the supplied register
  * map.  Returns the slave's `read` bundle for use with `<>`.
  *
  * Example:
  * {{{
  *   io.axi.read <> ConnectAxiRead(6, Map("count" -> (0, countReg)))
  * }}}
  */
def ConnectAxiRead(
    idBits  : Int,
    readMap : Map[String, (Int, UInt)]
): Bundle = {
  val slave = Module(new AxiReadSlave(idBits))

  // Build a data multiplexer driven by the latched address.
  val addr = slave.ctrl.latchedAddr
  val dataEntries = readMap.values.map { case (off, reg) => addr === off.U -> reg }.toSeq
  val respEntries = readMap.values.map { case (off, _)   => addr === off.U -> 0.U(2.W) }.toSeq

  slave.ctrl.dataIn := MuxCase(0.U(32.W), dataEntries)
  slave.ctrl.respIn := MuxCase(2.U(2.W),  respEntries)   // 2 = SLVERR

  slave.read
}

/** Create an AXI write-slave sub-module and wire it.
  *
  * Returns `(writes, writeData)` where each `writes(name)` pulses for one
  * cycle when that register address fires.
  *
  * Example:
  * {{{
  *   val (writes, writeData) = ConnectAxiWrite(6,
  *     Map("count" -> 0, "limit" -> 4), io.axi.write)
  * }}}
  */
def ConnectAxiWrite(
    idBits   : Int,
    writeMap : Map[String, Int],
    writeIO  : Bundle
): (Map[String, Bool], UInt) = {
  val slave = Module(new AxiWriteSlave(idBits))

  // Bulk-connect the caller's write bundle to the slave's write bundle.
  slave.write <> writeIO

  // Determine whether the address is valid.
  val addr     = slave.ctrl.latchedAddr
  val isValid  = writeMap.values.map(off => addr === off.U).reduce(_ || _)
  slave.ctrl.respIn := Mux(isValid, 0.U, 2.U)

  // Per-register write strobes.
  val writeSignals = writeMap.map { case (name, off) =>
    name -> (slave.ctrl.fired && addr === off.U)
  }

  (writeSignals, slave.ctrl.latchedData)
}
