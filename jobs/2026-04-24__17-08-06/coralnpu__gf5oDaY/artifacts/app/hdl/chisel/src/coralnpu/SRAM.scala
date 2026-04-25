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

/** Generic SRAM stub module. */
class SRAM(depth: Int, width: Int) extends Module {
  val addrBits = log2Ceil(depth)

  val io = IO(new Bundle {
    val addr   = Input(UInt(addrBits.W))
    val wen    = Input(Bool())
    val wdata  = Input(UInt(width.W))
    val wmask  = Input(UInt((width / 8).W))
    val rdata  = Output(UInt(width.W))
  })

  val mem = SyncReadMem(depth, UInt(width.W))
  io.rdata := mem.read(io.addr)

  when(io.wen) {
    mem.write(io.addr, io.wdata)
  }
}
