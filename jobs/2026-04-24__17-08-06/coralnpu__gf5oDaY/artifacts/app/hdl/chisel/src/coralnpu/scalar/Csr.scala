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

/** CSR (Control and Status Register) unit stub. */
class Csr(p: Parameters) extends Module {
  val io = IO(new Bundle {
    val read  = new Bundle {
      val addr  = Input(UInt(12.W))
      val data  = Output(UInt(p.xlen.W))
    }
    val write = new Bundle {
      val valid = Input(Bool())
      val addr  = Input(UInt(12.W))
      val data  = Input(UInt(p.xlen.W))
    }
    val mret  = Input(Bool())
    val mepc  = Output(UInt(p.addrBits.W))
    val csr   = Output(new CsrBundle)
  })

  io.read.data := 0.U
  io.mepc      := 0.U
  io.csr       := 0.U.asTypeOf(new CsrBundle)
}
