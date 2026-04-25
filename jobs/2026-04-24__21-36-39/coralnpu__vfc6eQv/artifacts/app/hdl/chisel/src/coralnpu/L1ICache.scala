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
import scala.annotation.nowarn

class L1ICache(p: Parameters) extends Module {
  val AxiIdBits  = 4
  val AxiDataBits = 256

  val io = IO(new Bundle {
    // Flush interface
    val flush_valid  = Input(Bool())
    val flush_pcNext = Input(UInt(32.W))
    val flush_ready  = Output(Bool())

    // IBus (CPU side)
    val ibus_valid              = Input(Bool())
    val ibus_ready              = Output(Bool())
    val ibus_addr               = Input(UInt(32.W))
    val ibus_rdata              = Output(UInt(AxiDataBits.W))
    val ibus_fault_valid        = Output(Bool())
    val ibus_fault_bits_write   = Output(Bool())
    val ibus_fault_bits_addr    = Output(UInt(32.W))
    val ibus_fault_bits_epc     = Output(UInt(32.W))

    // AXI read address channel
    val axi_read_addr_valid       = Output(Bool())
    val axi_read_addr_ready       = Input(Bool())
    val axi_read_addr_bits_id     = Output(UInt(AxiIdBits.W))
    val axi_read_addr_bits_addr   = Output(UInt(32.W))
    val axi_read_addr_bits_region = Output(UInt(4.W))
    val axi_read_addr_bits_qos    = Output(UInt(4.W))
    val axi_read_addr_bits_prot   = Output(UInt(3.W))
    val axi_read_addr_bits_cache  = Output(UInt(4.W))
    val axi_read_addr_bits_lock   = Output(Bool())
    val axi_read_addr_bits_burst  = Output(UInt(2.W))
    val axi_read_addr_bits_size   = Output(UInt(3.W))
    val axi_read_addr_bits_len    = Output(UInt(8.W))

    // AXI read data channel
    val axi_read_data_valid       = Input(Bool())
    val axi_read_data_ready       = Output(Bool())
    val axi_read_data_bits_resp   = Input(UInt(2.W))
    val axi_read_data_bits_id     = Input(UInt(AxiIdBits.W))
    val axi_read_data_bits_data   = Input(UInt(AxiDataBits.W))
    val axi_read_data_bits_last   = Input(Bool())

    // Misc
    val volt_sel = Input(Bool())
  })

  // Stub: tie all outputs to 0
  io.flush_ready := false.B

  io.ibus_ready            := false.B
  io.ibus_rdata            := 0.U
  io.ibus_fault_valid      := false.B
  io.ibus_fault_bits_write := false.B
  io.ibus_fault_bits_addr  := 0.U
  io.ibus_fault_bits_epc   := 0.U

  io.axi_read_addr_valid       := false.B
  io.axi_read_addr_bits_id     := 0.U
  io.axi_read_addr_bits_addr   := 0.U
  io.axi_read_addr_bits_region := 0.U
  io.axi_read_addr_bits_qos    := 0.U
  io.axi_read_addr_bits_prot   := 0.U
  io.axi_read_addr_bits_cache  := 0.U
  io.axi_read_addr_bits_lock   := false.B
  io.axi_read_addr_bits_burst  := 0.U
  io.axi_read_addr_bits_size   := 0.U
  io.axi_read_addr_bits_len    := 0.U
  io.axi_read_data_ready       := false.B
}

@nowarn
object EmitL1ICache extends App {
  import _root_.circt.stage.ChiselStage
  import chisel3.stage.ChiselGeneratorAnnotation
  (new ChiselStage).execute(
    Array("--target", "systemverilog") ++ args,
    Seq(
      ChiselGeneratorAnnotation(() => new L1ICache(new Parameters)),
      _root_.circt.stage.FirtoolOption("--default-layer-specialization=disable"),
    )
  )
}
