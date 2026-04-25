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

/** Byte-addressable SRAM module used by TCMs and caches.
  *
  * Wraps the `Sram` BlackBox with a standard chip-enable / write-enable /
  * byte-mask interface.  The address is a *word* address (not byte address).
  *
  * @param sizeBytes  Total capacity in bytes.
  * @param dataBits   Data bus width (must be a multiple of 8).
  * @param baseAddr   DPI back-door base address.
  */
class SRAM(val sizeBytes: Int, val dataBits: Int = 32, val baseAddr: Long = 0L)
    extends Module {

  private val dataBytes = dataBits / 8
  private val numWords  = sizeBytes / dataBytes
  private val addrWidth = log2Ceil(numWords)

  val io = IO(new Bundle {
    val en    = Input(Bool())
    val we    = Input(Bool())
    val addr  = Input(UInt(addrWidth.W))
    val wdata = Input(UInt(dataBits.W))
    val wmask = Input(UInt(dataBytes.W))  // byte-enable strobe
    val rdata = Output(UInt(dataBits.W))
  })

  val sram = Module(new Sram(numWords, dataBits, baseAddr))
  sram.io.clk   := clock
  sram.io.en    := io.en
  sram.io.write := io.we
  sram.io.addr  := io.addr
  sram.io.wdata := io.wdata
  sram.io.wmask := io.wmask
  io.rdata      := sram.io.rdata
}
