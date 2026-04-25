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

/** 128-bit wide SRAM wrapper.
  *
  * Provides a simple valid/write/addr/wdata/wmask/rdata interface over an
  * underlying `Sram` BlackBox.  The write mask uses one bit per byte
  * (standard AXI byte-strobe convention).
  *
  * @param sizeBytes Total SRAM capacity in bytes.
  * @param baseAddr  DPI back-door base address forwarded to the Sram BlackBox.
  */
class SramNx128(val sizeBytes: Int, val baseAddr: Long = 0L) extends Module {
  private val dataBits  = 128
  private val dataBytes = dataBits / 8
  private val numWords  = sizeBytes / dataBytes
  private val addrWidth = log2Ceil(numWords)

  val io = IO(new Bundle {
    val valid = Input(Bool())
    val write = Input(Bool())
    val addr  = Input(UInt(addrWidth.W))
    val wdata = Input(UInt(dataBits.W))
    val wmask = Input(UInt(dataBytes.W))  // byte-enable strobe
    val rdata = Output(UInt(dataBits.W))
  })

  val sram = Module(new Sram(numWords, dataBits, baseAddr))
  sram.io.clk   := clock
  sram.io.en    := io.valid
  sram.io.write := io.write
  sram.io.addr  := io.addr
  sram.io.wdata := io.wdata
  sram.io.wmask := io.wmask
  io.rdata      := sram.io.rdata
}
