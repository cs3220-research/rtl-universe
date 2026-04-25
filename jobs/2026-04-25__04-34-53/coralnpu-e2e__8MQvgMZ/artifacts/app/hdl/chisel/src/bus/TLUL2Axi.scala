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

/** TL-UL-to-AXI bridge.
  *
  * Accepts TL-UL requests on the device-facing port and converts them to
  * AXI4 transactions on the master-facing AXI port.
  *
  * @param tlP      TL-UL parameters
  * @param addrBits AXI address width in bits
  * @param dataBits AXI data width in bits
  * @param idBits   AXI ID width in bits
  */
class TLUL2Axi(
    tlP:      TLULParameters = TLULParameters(),
    addrBits: Int = 32,
    dataBits: Int = 32,
    idBits:   Int = 4
) extends Module {

  val io = IO(new Bundle {
    val tl  = Flipped(new TLBundleUL(tlP))
    val axi = new AxiBundle(dataBits, addrBits, idBits)
  })

  val isRead = io.tl.a.bits.opcode === TLULOpcodesA.Get

  // -------------------------------------------------------------------------
  // A channel → AXI AR / AW + W
  // -------------------------------------------------------------------------
  io.axi.read.addr.valid         := io.tl.a.valid && isRead
  io.axi.read.addr.bits.id       := io.tl.a.bits.source
  io.axi.read.addr.bits.addr     := io.tl.a.bits.address
  io.axi.read.addr.bits.len      := 0.U
  io.axi.read.addr.bits.size     := io.tl.a.bits.size
  io.axi.read.addr.bits.burst    := 1.U  // INCR
  io.axi.read.addr.bits.prot     := 0.U
  io.axi.read.addr.bits.lock     := 0.U
  io.axi.read.addr.bits.cache    := 0.U
  io.axi.read.addr.bits.qos      := 0.U
  io.axi.read.addr.bits.region   := 0.U

  io.axi.write.addr.valid        := io.tl.a.valid && !isRead
  io.axi.write.addr.bits.id      := io.tl.a.bits.source
  io.axi.write.addr.bits.addr    := io.tl.a.bits.address
  io.axi.write.addr.bits.len     := 0.U
  io.axi.write.addr.bits.size    := io.tl.a.bits.size
  io.axi.write.addr.bits.burst   := 1.U
  io.axi.write.addr.bits.prot    := 0.U
  io.axi.write.addr.bits.lock    := 0.U
  io.axi.write.addr.bits.cache   := 0.U
  io.axi.write.addr.bits.qos     := 0.U
  io.axi.write.addr.bits.region  := 0.U

  io.axi.write.data.valid       := io.tl.a.valid && !isRead
  io.axi.write.data.bits.data   := io.tl.a.bits.data
  io.axi.write.data.bits.strb   := io.tl.a.bits.mask
  io.axi.write.data.bits.last   := true.B

  io.tl.a.ready := Mux(isRead, io.axi.read.addr.ready, io.axi.write.addr.ready)

  // -------------------------------------------------------------------------
  // AXI R / B → TL-UL D channel
  // -------------------------------------------------------------------------
  io.tl.d.valid           := io.axi.read.data.valid || io.axi.write.resp.valid
  io.tl.d.bits.opcode     := Mux(io.axi.read.data.valid,
    TLULOpcodesD.AccessAckData, TLULOpcodesD.AccessAck)
  io.tl.d.bits.param      := 0.U
  io.tl.d.bits.size       := 2.U
  io.tl.d.bits.source     := Mux(io.axi.read.data.valid,
    io.axi.read.data.bits.id,
    io.axi.write.resp.bits.id)
  io.tl.d.bits.sink       := 0.U
  io.tl.d.bits.denied     := false.B
  io.tl.d.bits.data       := io.axi.read.data.bits.data
  io.tl.d.bits.corrupt    := false.B
  io.tl.d.bits.error      := Mux(io.axi.read.data.valid,
    io.axi.read.data.bits.resp(0),
    io.axi.write.resp.bits.resp(0))

  io.axi.read.data.ready  := io.tl.d.ready
  io.axi.write.resp.ready := io.tl.d.ready
}

import _root_.circt.stage.{ChiselStage, FirtoolOption}
import chisel3.stage.ChiselGeneratorAnnotation
import scala.annotation.nowarn

@nowarn
object EmitTLUL2Axi extends App {
  (new ChiselStage).execute(
    Array("--target", "systemverilog") ++ args,
    Seq(ChiselGeneratorAnnotation(() => new TLUL2Axi())) ++
      Seq(FirtoolOption("-enable-layers=Verification"))
  )
}
