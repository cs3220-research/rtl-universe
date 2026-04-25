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

/** ConnectAxiRead: connects a register-file read port to an AXI Read channel.
  *
  * @param idBits   Width of the AXI ID field.
  * @param readMap  Map from register name to (byteOffset, readData).
  * @return         A Flipped(AxiRead) bundle wired up to serve reads.
  *
  * Usage: io.axi.read <> ConnectAxiRead(idBits, readMap)
  *
  * On each valid read address, looks up the address in readMap and returns
  * the associated data in the next cycle.  Unknown addresses return resp=2
  * (SLVERR) with data=0.  The read is fully combinational (addr→data in
  * the same cycle, i.e., zero-cycle latency read with registered output).
  */
object ConnectAxiRead {
  def apply(
      idBits: Int,
      readMap: Map[String, (Int, UInt)]
  ): AxiRead = {
    val addrBits = 32
    val dataBits = 32

    // We return a Flipped(AxiRead) = from the peripheral's perspective
    // (receives addr, drives data)
    val io = Wire(Flipped(new AxiRead(addrBits, dataBits, idBits)))

    // Address channel: always ready
    io.addr.ready := true.B

    // Registers for pipeline stage
    val validReg = RegInit(false.B)
    val dataReg  = RegInit(0.U(dataBits.W))
    val respReg  = RegInit(0.U(2.W))
    val idReg    = RegInit(0.U(idBits.W))

    // When address arrives, compute response immediately
    when(io.addr.valid) {
      validReg := true.B
      idReg    := io.addr.bits.id

      val addrIn = io.addr.bits.addr

      // Build decode table
      val cases = readMap.toSeq.map { case (_, (offset, reg)) =>
        (addrIn === offset.U) -> reg.asUInt.pad(dataBits)
      }
      val matchAny = readMap.values.map { case (offset, _) =>
        addrIn === offset.U
      }.fold(false.B)(_ || _)

      dataReg := MuxCase(0.U, cases)
      respReg := Mux(matchAny, 0.U, 2.U)
    }

    // Clear valid when consumed
    when(validReg && io.data.ready) {
      validReg := false.B
    }

    io.data.valid      := validReg
    io.data.bits.data  := dataReg
    io.data.bits.resp  := respReg
    io.data.bits.id    := idReg
    io.data.bits.last  := true.B

    io
  }
}

/** ConnectAxiWrite: connects a register-file write port to an AXI Write channel.
  *
  * @param idBits    Width of the AXI ID field.
  * @param writeMap  Map from register name to byte offset.
  * @param write     The AXI write channel (AxiWrite bundle, manager-side).
  * @return          (writeStrobes: Map[String, Bool], writeData: UInt)
  *
  * `writeStrobes(name)` is true for one cycle when a write to that register
  * has been accepted.  `writeData` holds the write data.
  *
  * Usage:
  *   val (writes, writeData) = ConnectAxiWrite(idBits, writeMap, io.axi.write)
  */
object ConnectAxiWrite {
  def apply(
      idBits: Int,
      writeMap: Map[String, Int],
      write: AxiWrite
  ): (Map[String, Bool], UInt) = {
    val dataBits = 32

    // Accept addr and data as soon as both are valid
    write.addr.ready := true.B
    write.data.ready := true.B

    val fire = write.addr.valid && write.data.valid

    // Latch address and data when a write fires
    val addrReg     = RegInit(0.U(32.W))
    val dataReg     = RegInit(0.U(dataBits.W))
    val writeActive = RegInit(false.B)
    val respValid   = RegInit(false.B)
    val respCode    = RegInit(0.U(2.W))
    val respId      = RegInit(0.U(idBits.W))

    when(fire) {
      addrReg     := write.addr.bits.addr
      dataReg     := write.data.bits.data
      writeActive := true.B
      respValid   := true.B
      respId      := write.addr.bits.id

      val matched = writeMap.values
        .map(off => write.addr.bits.addr === off.U)
        .fold(false.B)(_ || _)
      respCode := Mux(matched, 0.U, 2.U)
    }.otherwise {
      writeActive := false.B
    }

    when(respValid && write.resp.ready) {
      respValid := false.B
    }

    write.resp.valid      := respValid
    write.resp.bits.resp  := respCode
    write.resp.bits.id    := respId

    // Generate per-register write strobes (active for one cycle after fire)
    val writeStrobes = writeMap.map { case (name, offset) =>
      name -> (writeActive && addrReg === offset.U)
    }

    (writeStrobes, dataReg)
  }
}
