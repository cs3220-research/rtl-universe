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
import bus._

// N x 128-bit SRAM (N banks, each 128-bit wide)
class SramNx128(p: Parameters, n: Int, depth: Int) extends Module {
  val io = IO(new Bundle {
    val enable = Input(Bool())
    val write  = Input(Bool())
    val addr   = Input(UInt(log2Ceil(depth).W))
    val wdata  = Input(UInt((n * 128).W))
    val wmask  = Input(UInt(n.W))
    val rdata  = Output(UInt((n * 128).W))
    val rvalid = Output(Bool())
  })
  io.rdata  := RegNext(io.wdata)  // placeholder
  io.rvalid := RegNext(io.enable && !io.write)
}
