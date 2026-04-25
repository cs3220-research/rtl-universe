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
import chisel3.util._

/** Generic single-port SRAM stub. */
class Sram(dataBits: Int, depth: Int) extends Module {
  private val addrBits = log2Ceil(depth)
  val io = IO(new Bundle {
    val ce    = Input(Bool())
    val we    = Input(Bool())
    val addr  = Input(UInt(addrBits.W))
    val wdata = Input(UInt(dataBits.W))
    val wmask = Input(UInt((dataBits / 8).W))
    val rdata = Output(UInt(dataBits.W))
  })

  val mem = SyncReadMem(depth, UInt(dataBits.W))

  when(io.ce && io.we) {
    mem.write(io.addr, io.wdata)
  }
  io.rdata := mem.read(io.addr, io.ce && !io.we)
}
