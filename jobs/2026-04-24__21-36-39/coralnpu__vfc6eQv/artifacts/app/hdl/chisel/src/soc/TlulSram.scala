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

package coralnpu.soc

import chisel3._
import chisel3.util._
import bus._
import coralnpu.Parameters

/** TileLink-UL to SRAM bridge for the SoC TCM memories.
  *
  * Wraps a synchronous SRAM with a TileLink-UL slave interface.
  * Supports single-beat reads and writes with byte enables.
  */
class TlulSram(p: Parameters, depthWords: Int) extends Module {
  val tlul_p = new TLULParameters(p)
  val addrW  = log2Ceil(depthWords)

  val io = IO(new Bundle {
    val tl   = Flipped(new OpenTitanTileLink.Host2Device(tlul_p))
    // SRAM backdoor interface (for simulation only)
    val sram_addr  = Output(UInt(addrW.W))
    val sram_wdata = Output(UInt(p.lsuDataBits.W))
    val sram_wmask = Output(UInt((p.lsuDataBits / 8).W))
    val sram_we    = Output(Bool())
    val sram_en    = Output(Bool())
    val sram_rdata = Input(UInt(p.lsuDataBits.W))
    val sram_rvalid = Input(Bool())
  })

  // Use TlulToSram internally
  val adapter = Module(new TlulToSram(p, addrW))
  io.tl <> adapter.io.tl

  io.sram_addr  := adapter.io.sram.addr
  io.sram_wdata := adapter.io.sram.wdata
  io.sram_wmask := adapter.io.sram.wmask
  io.sram_we    := adapter.io.sram.write
  io.sram_en    := adapter.io.sram.enable
  adapter.io.sram.rdata  := io.sram_rdata
  adapter.io.sram.rvalid := io.sram_rvalid
}
