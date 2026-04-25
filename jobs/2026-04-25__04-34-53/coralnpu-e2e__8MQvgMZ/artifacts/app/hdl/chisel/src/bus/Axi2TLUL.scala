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

package bus

import chisel3._
import chisel3.util._

/** AXI-to-TL-UL bridge.
  *
  * Accepts AXI read/write transactions from an AXI master (slave-facing port)
  * and converts them to TL-UL transactions on the outbound TL-UL host port.
  *
  * Limitations of this implementation:
  *   – Only single-beat transfers (AXI len = 0) are supported.
  *   – Read and write cannot be outstanding simultaneously.
  *
  * @param dataBits TL-UL data width (bits)
  * @param addrBits address width (bits)
  * @param idBits   AXI ID width (bits)
  * @param tlP      TL-UL parameters
  */
class Axi2TLUL(dataBits: Int = 32, addrBits: Int = 32, idBits: Int = 4,
               tlP: TLULParameters = TLULParameters()) extends Module {

  val io = IO(new Bundle {
    // AXI slave port (this module is the slave; master drives it).
    val axi = Flipped(new AxiBundle(dataBits, addrBits, idBits))
    // TL-UL host port (this module drives requests).
    val tl  = new TLBundleUL(tlP)
  })

  // Prefer reads over writes when both are presented simultaneously.
  val isRead = io.axi.read.addr.valid

  io.tl.a.valid          := io.axi.read.addr.valid || io.axi.write.addr.valid
  io.tl.a.bits.opcode    := Mux(isRead, TLULOpcodesA.Get, TLULOpcodesA.PutFullData)
  io.tl.a.bits.param     := 0.U
  io.tl.a.bits.size      := 2.U  // 4 bytes default
  io.tl.a.bits.source    := 0.U
  io.tl.a.bits.address   := Mux(isRead,
    io.axi.read.addr.bits.addr,
    io.axi.write.addr.bits.addr)
  io.tl.a.bits.mask      := Mux(isRead,
    ~0.U(tlP.maskWidth.W),
    io.axi.write.data.bits.strb(tlP.maskWidth - 1, 0))
  io.tl.a.bits.data      := io.axi.write.data.bits.data(tlP.dataWidth - 1, 0)
  io.tl.a.bits.corrupt   := false.B

  // Back-pressure.
  io.axi.read.addr.ready  := io.tl.a.ready && isRead
  io.axi.write.addr.ready := io.tl.a.ready && !isRead
  io.axi.write.data.ready := true.B

  // TL-UL D → AXI response.
  io.axi.read.data.valid        := io.tl.d.valid && (io.tl.d.bits.opcode === TLULOpcodesD.AccessAckData)
  io.axi.read.data.bits.id      := 0.U
  io.axi.read.data.bits.data    := io.tl.d.bits.data
  io.axi.read.data.bits.resp    := Cat(0.U(1.W), io.tl.d.bits.error)
  io.axi.read.data.bits.last    := true.B

  io.axi.write.resp.valid       := io.tl.d.valid && (io.tl.d.bits.opcode === TLULOpcodesD.AccessAck)
  io.axi.write.resp.bits.id     := 0.U
  io.axi.write.resp.bits.resp   := Cat(0.U(1.W), io.tl.d.bits.error)

  io.tl.d.ready := io.axi.read.data.ready || io.axi.write.resp.ready
}

import _root_.circt.stage.{ChiselStage, FirtoolOption}
import chisel3.stage.ChiselGeneratorAnnotation
import scala.annotation.nowarn

@nowarn
object EmitAxi2TLUL extends App {
  (new ChiselStage).execute(
    Array("--target", "systemverilog") ++ args,
    Seq(ChiselGeneratorAnnotation(() => new Axi2TLUL())) ++
      Seq(FirtoolOption("-enable-layers=Verification"))
  )
}
