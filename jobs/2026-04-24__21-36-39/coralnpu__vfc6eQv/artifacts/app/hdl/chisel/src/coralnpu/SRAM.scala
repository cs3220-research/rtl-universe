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

// SRAM controller module.
class SRAM(p: Parameters, depth: Int = 256, width: Int = 128) extends Module {
  val io = IO(new Bundle {
    val enable = Input(Bool())
    val write  = Input(Bool())
    val addr   = Input(UInt(log2Ceil(depth).W))
    val wdata  = Input(UInt(width.W))
    val wmask  = Input(UInt((width / 8).W))
    val rdata  = Output(UInt(width.W))
    val rvalid = Output(Bool())
  })

  val mem      = SyncReadMem(depth, UInt(width.W))
  val rdata_r  = Reg(UInt(width.W))
  val rvalid_r = RegInit(false.B)

  when(io.enable && io.write) {
    mem.write(io.addr, io.wdata)
  }
  rvalid_r := io.enable && !io.write
  rdata_r  := mem.read(io.addr)

  io.rdata  := rdata_r
  io.rvalid := rvalid_r
}
