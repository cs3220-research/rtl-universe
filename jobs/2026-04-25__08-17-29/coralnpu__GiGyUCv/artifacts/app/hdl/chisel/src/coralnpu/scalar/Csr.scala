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

object CsrOp extends ChiselEnum {
  val CSRRW, CSRRS, CSRRC, CSRRWI, CSRRSI, CSRRCI = Value
}

class CsrRequest(p: Parameters) extends Bundle {
  val addr  = UInt(5.W)
  val csrAddr = UInt(12.W)
  val op    = CsrOp()
  val rs1   = UInt(32.W)
  val imm   = UInt(5.W)
}

/** CSR (Control and Status Register) stub. */
class Csr(p: Parameters) extends Module {
  val io = IO(new Bundle {
    val req  = Flipped(Valid(new CsrRequest(p)))
    val rd   = Valid(new RegData(p))
  })

  io.rd.valid := false.B
  io.rd.bits  := 0.U.asTypeOf(new RegData(p))
}
