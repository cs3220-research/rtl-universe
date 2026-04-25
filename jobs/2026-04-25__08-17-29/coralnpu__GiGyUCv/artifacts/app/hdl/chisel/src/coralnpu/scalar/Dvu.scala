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

object DvuOp extends ChiselEnum {
  val DIV, DIVU, REM, REMU = Value
}

class DvuRequest(p: Parameters) extends Bundle {
  val addr = UInt(5.W)
  val op   = DvuOp()
}

/** Divide unit (DVU) stub. */
class Dvu(p: Parameters) extends Module {
  val io = IO(new Bundle {
    val req = Flipped(Valid(new DvuRequest(p)))
    val rs1 = Input(new RegSource(p))
    val rs2 = Input(new RegSource(p))
    val rd  = Valid(new RegData(p))
  })

  io.rd.valid := false.B
  io.rd.bits  := 0.U.asTypeOf(new RegData(p))
}

object EmitDvu extends App {
  import circt.stage.ChiselStage
  val p = new Parameters
  ChiselStage.emitSystemVerilog(new Dvu(p))
}
