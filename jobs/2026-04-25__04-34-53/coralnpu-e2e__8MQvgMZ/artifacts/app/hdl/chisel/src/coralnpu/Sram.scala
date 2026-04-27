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
import chisel3.experimental.IntParam

/** BlackBox wrapping the parameterizable single-port SRAM (hdl/verilog/Sram.v).
  *
  * In simulation the Verilog implementation uses DPI-C calls to a C++ back-door
  * (when `USE_DPI` is defined), which allows test harnesses to pre-load memory
  * contents.  In synthesis it falls back to a plain reg-array.
  *
  * @param depth     Number of words in the SRAM.
  * @param dataWidth Data bus width in bits (must be a multiple of 8).
  * @param baseAddr  Global base address passed to the DPI `sram_init` call.
  */
class Sram(val depth: Int, val dataWidth: Int, val baseAddr: Long = 0L)
    extends BlackBox(
      Map(
        "DEPTH"     -> IntParam(depth),
        "WIDTH"     -> IntParam(dataWidth),
        "BASE_ADDR" -> IntParam(baseAddr.toInt)
      )
    ) {

  private val addrWidth = chisel3.util.log2Ceil(depth)

  val io = IO(new Bundle {
    val clk      = Input(Clock())
    val en       = Input(Bool())
    val write    = Input(Bool())
    val addr     = Input(UInt(addrWidth.W))
    val wdata    = Input(UInt(dataWidth.W))
    val wmask    = Input(UInt((dataWidth / 8).W))
    val rdata    = Output(UInt(dataWidth.W))
    val bd_en    = Input(Bool())
    val bd_addr  = Input(UInt(addrWidth.W))
    val bd_rdata = Output(UInt(dataWidth.W))
    val bd_wen   = Input(Bool())
    val bd_waddr = Input(UInt(addrWidth.W))
    val bd_wdata = Input(UInt(dataWidth.W))
    val bd_wmask = Input(UInt((dataWidth / 8).W))
  })
}
