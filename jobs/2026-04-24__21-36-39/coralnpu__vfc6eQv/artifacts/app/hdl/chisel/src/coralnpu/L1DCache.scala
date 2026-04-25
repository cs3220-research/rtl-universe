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

class L1DCacheIO(addrBits: Int, dataBits: Int, axiIdBits: Int) extends Bundle {
  // Flush interface
  val flush_valid = Input(Bool())
  val flush_ready = Output(Bool())
  val flush_all   = Input(Bool())
  val flush_clean = Input(Bool())

  // DBus (CPU side)
  val dbus_valid = Input(Bool())
  val dbus_ready = Output(Bool())
  val dbus_write = Input(Bool())
  val dbus_size  = Input(UInt(6.W))
  val dbus_addr  = Input(UInt(addrBits.W))
  val dbus_adrx  = Input(UInt(addrBits.W))
  val dbus_rdata = Output(UInt(dataBits.W))
  val dbus_pc    = Output(UInt(32.W))
  val dbus_wdata = Input(UInt(dataBits.W))
  val dbus_wmask = Input(UInt((dataBits / 8).W))

  // AXI read address channel
  val axi_read_addr_valid         = Output(Bool())
  val axi_read_addr_ready         = Input(Bool())
  val axi_read_addr_bits_id       = Output(UInt(axiIdBits.W))
  val axi_read_addr_bits_addr     = Output(UInt(addrBits.W))
  val axi_read_addr_bits_region   = Output(UInt(4.W))
  val axi_read_addr_bits_qos      = Output(UInt(4.W))
  val axi_read_addr_bits_prot     = Output(UInt(3.W))
  val axi_read_addr_bits_cache    = Output(UInt(4.W))
  val axi_read_addr_bits_lock     = Output(Bool())
  val axi_read_addr_bits_burst    = Output(UInt(2.W))
  val axi_read_addr_bits_size     = Output(UInt(3.W))
  val axi_read_addr_bits_len      = Output(UInt(8.W))

  // AXI read data channel
  val axi_read_data_valid         = Input(Bool())
  val axi_read_data_ready         = Output(Bool())
  val axi_read_data_bits_resp     = Input(UInt(2.W))
  val axi_read_data_bits_id       = Input(UInt(axiIdBits.W))
  val axi_read_data_bits_data     = Input(UInt(dataBits.W))
  val axi_read_data_bits_last     = Input(Bool())

  // AXI write address channel
  val axi_write_addr_valid        = Output(Bool())
  val axi_write_addr_ready        = Input(Bool())
  val axi_write_addr_bits_id      = Output(UInt(axiIdBits.W))
  val axi_write_addr_bits_addr    = Output(UInt(addrBits.W))
  val axi_write_addr_bits_region  = Output(UInt(4.W))
  val axi_write_addr_bits_qos     = Output(UInt(4.W))
  val axi_write_addr_bits_prot    = Output(UInt(3.W))
  val axi_write_addr_bits_cache   = Output(UInt(4.W))
  val axi_write_addr_bits_lock    = Output(Bool())
  val axi_write_addr_bits_burst   = Output(UInt(2.W))
  val axi_write_addr_bits_size    = Output(UInt(3.W))
  val axi_write_addr_bits_len     = Output(UInt(8.W))

  // AXI write data channel
  val axi_write_data_valid        = Output(Bool())
  val axi_write_data_ready        = Input(Bool())
  val axi_write_data_bits_strb    = Output(UInt((dataBits / 8).W))
  val axi_write_data_bits_data    = Output(UInt(dataBits.W))
  val axi_write_data_bits_last    = Output(Bool())

  // AXI write response channel
  val axi_write_resp_valid        = Input(Bool())
  val axi_write_resp_ready        = Output(Bool())
  val axi_write_resp_bits_resp    = Input(UInt(2.W))
  val axi_write_resp_bits_id      = Input(UInt(axiIdBits.W))

  // Misc
  val volt_sel = Input(Bool())
}

class L1DCache(p: Parameters) extends Module {
  val AxiIdBits = 4
  val io = IO(new L1DCacheIO(32, p.lsuDataBits, AxiIdBits))

  // Stub: tie all outputs to 0
  io.flush_ready := false.B
  io.dbus_ready  := false.B
  io.dbus_rdata  := 0.U
  io.dbus_pc     := 0.U

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

  io.axi_write_addr_valid       := false.B
  io.axi_write_addr_bits_id     := 0.U
  io.axi_write_addr_bits_addr   := 0.U
  io.axi_write_addr_bits_region := 0.U
  io.axi_write_addr_bits_qos    := 0.U
  io.axi_write_addr_bits_prot   := 0.U
  io.axi_write_addr_bits_cache  := 0.U
  io.axi_write_addr_bits_lock   := false.B
  io.axi_write_addr_bits_burst  := 0.U
  io.axi_write_addr_bits_size   := 0.U
  io.axi_write_addr_bits_len    := 0.U

  io.axi_write_data_valid     := false.B
  io.axi_write_data_bits_strb := 0.U
  io.axi_write_data_bits_data := 0.U
  io.axi_write_data_bits_last := false.B
  io.axi_write_resp_ready     := false.B
}

class L1DCacheBankIO(p: Parameters) extends L1DCacheIO(31, p.lsuDataBits, 3)

class L1DCacheBank(p: Parameters) extends Module {
  val io = IO(new L1DCacheBankIO(p))

  // Stub: tie all outputs to 0
  io.flush_ready := false.B
  io.dbus_ready  := false.B
  io.dbus_rdata  := 0.U
  io.dbus_pc     := 0.U

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

  io.axi_write_addr_valid       := false.B
  io.axi_write_addr_bits_id     := 0.U
  io.axi_write_addr_bits_addr   := 0.U
  io.axi_write_addr_bits_region := 0.U
  io.axi_write_addr_bits_qos    := 0.U
  io.axi_write_addr_bits_prot   := 0.U
  io.axi_write_addr_bits_cache  := 0.U
  io.axi_write_addr_bits_lock   := false.B
  io.axi_write_addr_bits_burst  := 0.U
  io.axi_write_addr_bits_size   := 0.U
  io.axi_write_addr_bits_len    := 0.U

  io.axi_write_data_valid     := false.B
  io.axi_write_data_bits_strb := 0.U
  io.axi_write_data_bits_data := 0.U
  io.axi_write_data_bits_last := false.B
  io.axi_write_resp_ready     := false.B
}

@nowarn
object EmitL1DCache extends App {
  import _root_.circt.stage.ChiselStage
  import chisel3.stage.ChiselGeneratorAnnotation
  val p = new Parameters
  p.lsuDataBits = 256
  (new ChiselStage).execute(
    Array("--target", "systemverilog") ++ args,
    Seq(
      ChiselGeneratorAnnotation(() => new L1DCache(p)),
      _root_.circt.stage.FirtoolOption("--default-layer-specialization=disable"),
    )
  )
}

@nowarn
object EmitL1DCacheBank extends App {
  import _root_.circt.stage.ChiselStage
  import chisel3.stage.ChiselGeneratorAnnotation
  val p = new Parameters
  p.lsuDataBits = 256
  (new ChiselStage).execute(
    Array("--target", "systemverilog") ++ args,
    Seq(
      ChiselGeneratorAnnotation(() => new L1DCacheBank(p)),
      _root_.circt.stage.FirtoolOption("--default-layer-specialization=disable"),
    )
  )
}
