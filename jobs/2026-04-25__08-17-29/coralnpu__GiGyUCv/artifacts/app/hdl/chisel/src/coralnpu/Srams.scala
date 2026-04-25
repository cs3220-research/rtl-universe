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

// Note: The BUILD file lists "Sram.scala" and "SramNx128.scala" in the srams library.
// This file provides common SRAM utility classes.

/** 1R1W SRAM with 128-bit data width, N rows. */
class SramNx128(depth: Int) extends Module {
  val io = IO(new Bundle {
    val we    = Input(Bool())
    val waddr = Input(UInt(log2Ceil(depth).W))
    val wdata = Input(UInt(128.W))
    val wmask = Input(UInt(16.W))
    val re    = Input(Bool())
    val raddr = Input(UInt(log2Ceil(depth).W))
    val rdata = Output(UInt(128.W))
  })

  val mem = SyncReadMem(depth, UInt(128.W))

  when(io.we) {
    mem.write(io.waddr, io.wdata)
  }
  io.rdata := mem.read(io.raddr, io.re)
}
