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

// Core with TileLink-UL interface.
class CoreTlul(p: Parameters) extends Module {
  val tlp = new TLULParameters(p)

  val io = IO(new Bundle {
    val tl     = Flipped(new OpenTitanTileLink.Host2Device(tlp))
    val halted = Output(Bool())
    val fault  = Output(Bool())
  })

  io.halted := false.B
  io.fault  := false.B

  io.tl.a.ready := false.B
  io.tl.d.valid := false.B
  io.tl.d.bits  := 0.U.asTypeOf(new TLULChannelD(tlp))
}
