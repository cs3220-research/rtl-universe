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

/** SPI-to-TL-UL bridge (v1, bit-banging style).
  *
  * Converts an SPI transaction into a TL-UL request.  The SPI frame format is:
  *   Byte 0       : opcode (0x01 = read, 0x02 = write)
  *   Bytes 1-4    : address (big-endian)
  *   Bytes 5-6    : beat count minus 1 (big-endian)
  *   Bytes 7-…   : write data (for write frames)
  *
  * This module is a stub implementation; the full protocol is implemented in
  * the V2 variant (Spi2TLULV2).
  *
  * @param p coralnpu system parameters
  */
class Spi2TLUL(p: coralnpu.Parameters) extends Module {
  private val tlP = TLULParameters(p)

  val io = IO(new Bundle {
    val spi_clk   = Input(Clock())
    val spi_rst_n = Input(Bool())
    val spi_mosi  = Input(Bool())
    val spi_miso  = Output(Bool())
    val spi_csn   = Input(Bool())
    val tl        = new TLBundleUL(tlP)
  })

  io.spi_miso := false.B

  io.tl.a.valid := false.B
  io.tl.a.bits  := 0.U.asTypeOf(new TLChannelA(tlP))
  io.tl.d.ready := true.B
}
