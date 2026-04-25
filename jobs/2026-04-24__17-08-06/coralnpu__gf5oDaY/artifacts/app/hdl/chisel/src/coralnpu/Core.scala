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

/**
 * Top-level NPU Core.
 * Stub implementation.
 */
class Core(p: Parameters) extends Module {
  val io = IO(new Bundle {
    val axi_csr  = new AxiCSRInterface
    val axi_mem  = Flipped(new AxiDataInterface(p))
    val cg       = Output(Bool())
    val halted   = Output(Bool())
    val fault    = Output(Bool())
    val coralnpu_csr = Input(new CsrValues)
    val internal = Input(Bool())
  })

  val csr = Module(new CoreAxiCSR(p))
  csr.io.axi          <> io.axi_csr
  csr.io.coralnpu_csr := io.coralnpu_csr
  csr.io.internal     := io.internal
  csr.io.halted       := false.B
  csr.io.fault        := false.B

  io.cg     := csr.io.cg
  io.halted := false.B
  io.fault  := false.B

  io.axi_mem.read.addr.ready  := false.B
  io.axi_mem.read.data.valid  := false.B
  io.axi_mem.read.data.bits   := 0.U.asTypeOf(io.axi_mem.read.data.bits)
  io.axi_mem.write.addr.ready := false.B
  io.axi_mem.write.data.ready := false.B
  io.axi_mem.write.resp.valid := false.B
  io.axi_mem.write.resp.bits  := 0.U.asTypeOf(io.axi_mem.write.resp.bits)
}

/** Emitter for Core. */
object EmitCore extends App {
  val p = new Parameters
  _root_.circt.stage.ChiselStage.emitSystemVerilog(new Core(p))
}

/** Emitter for L1DCache. */
object EmitL1DCache extends App {
  val p = new Parameters
  _root_.circt.stage.ChiselStage.emitSystemVerilog(new L1DCache(p))
}

/** Emitter for L1DCacheBank. */
object EmitL1DCacheBank extends App {
  val p = new Parameters
  _root_.circt.stage.ChiselStage.emitSystemVerilog(new L1DCacheBank(p))
}

/** Emitter for L1ICache. */
object EmitL1ICache extends App {
  val p = new Parameters
  _root_.circt.stage.ChiselStage.emitSystemVerilog(new L1ICache(p))
}
