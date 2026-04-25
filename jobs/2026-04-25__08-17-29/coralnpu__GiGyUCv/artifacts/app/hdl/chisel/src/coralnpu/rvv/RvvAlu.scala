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

/** RVV ALU stub: performs vector ALU operations. */
class RvvAlu(p: Parameters) extends Module {
  val io = IO(new Bundle {
    val op    = Input(RvvAluOp())
    val vs1   = Input(UInt((p.numLanes * 32).W))
    val vs2   = Input(UInt((p.numLanes * 32).W))
    val vd    = Output(UInt((p.numLanes * 32).W))
    val valid = Input(Bool())
    val ready = Output(Bool())
  })

  io.vd    := 0.U
  io.ready := true.B
}
