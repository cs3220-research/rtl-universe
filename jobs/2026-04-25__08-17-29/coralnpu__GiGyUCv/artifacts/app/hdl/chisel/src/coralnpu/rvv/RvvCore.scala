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

package coralnpu.rvv

import chisel3._
import chisel3.util._
import coralnpu.Parameters

/** RVV vector coprocessor stub. */
class RvvCore(p: Parameters) extends Module {
  val io = IO(new Bundle {
    val rvv = new RvvInterface(p)
  })

  io.rvv.issue.ready    := false.B
  io.rvv.complete.valid := false.B
  io.rvv.complete.bits  := 0.U.asTypeOf(io.rvv.complete.bits)
}
