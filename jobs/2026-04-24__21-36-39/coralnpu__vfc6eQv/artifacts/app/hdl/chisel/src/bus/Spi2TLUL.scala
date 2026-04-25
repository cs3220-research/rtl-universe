// Copyright 2026 Google LLC
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
import coralnpu.Parameters

/**
  * SPI-to-TileLink-UL bridge (version 1).
  *
  * Frame format (MSB-first per byte, header then payload):
  *   Byte 0  : opcode (0x01=read, 0x02=write)
  *   Bytes 1..4 : address (big-endian 32-bit)
  *   Bytes 5..6 : beat_count minus 1 (big-endian 16-bit)
  *   Bytes 7..N : write payload (N = (beat_count) * 16 bytes), or nothing for read
  *
  * On read: MISO outputs 0xFE sync then 16 bytes of TL response per beat.
  */
class Spi2TLUL(p: Parameters) extends RawModule {
  val tlp = new TLULParameters(p)

  val io = IO(new Bundle {
    val spi_clk   = Input(Clock())
    val spi_rst_n = Input(Bool())

    // SPI pin queues (in SPI clock domain)
    val q_mosi_pin = Flipped(Decoupled(Bool()))
    val q_miso_pin = Decoupled(Bool())

    // TileLink-UL master port (in system clock domain, but here we use single clock)
    val q_tl_a = Decoupled(new OpenTitanTileLink.A_Channel(tlp))
    val q_tl_d = Flipped(Decoupled(new OpenTitanTileLink.D_Channel(tlp)))
  })

  withClockAndReset(io.spi_clk, (!io.spi_rst_n).asAsyncReset) {
    // Stub: tie off outputs
    io.q_mosi_pin.ready := false.B
    io.q_miso_pin.valid := false.B
    io.q_miso_pin.bits  := false.B
    io.q_tl_a.valid     := false.B
    io.q_tl_a.bits      := 0.U.asTypeOf(new OpenTitanTileLink.A_Channel(tlp))
    io.q_tl_d.ready     := false.B
  }
}
