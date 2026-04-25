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

/** Division/remainder unit operation encoding. */
object DvuOp extends ChiselEnum {
  val DIV  = Value  // signed division
  val DIVU = Value  // unsigned division
  val REM  = Value  // signed remainder
  val REMU = Value  // unsigned remainder
}

/** Division unit request. */
class DvuRequest extends Bundle {
  val addr = UInt(5.W)
  val op   = DvuOp()
}

/** Division unit result. */
class DvuResult extends Bundle {
  val addr = UInt(5.W)
  val data = UInt(32.W)
}

/** Division operand. */
class DvuOperand extends Bundle {
  val valid = Bool()
  val data  = UInt(32.W)
}

/** Integer division / remainder unit.
  *
  * Uses Chisel's built-in `/` and `%` operators (synthesised to a multi-cycle
  * divider by the back-end).  The result is presented via a Decoupled output
  * port; the latency is currently a single-cycle registered result (stub).
  */
class Dvu(p: Parameters) extends Module {
  val io = IO(new Bundle {
    val req = Input(Valid(new DvuRequest))
    val rs1 = Input(new DvuOperand)
    val rs2 = Input(new DvuOperand)
    val rd  = Decoupled(new DvuResult)
  })

  // RISC-V division semantics: divide-by-zero and overflow results
  val dividend = io.rs1.data
  val divisor  = io.rs2.data
  val divByZero = (divisor === 0.U)
  val overflow  = (dividend === "h80000000".U(32.W)) && (divisor === "hFFFFFFFF".U(32.W))

  val divResult  = Wire(UInt(32.W))
  val remResult  = Wire(UInt(32.W))

  // Signed division/remainder
  val dividendS = dividend.asSInt
  val divisorS  = divisor.asSInt
  divResult := MuxCase(0.U, Seq(
    (divByZero)                                  -> "hFFFFFFFF".U,
    (!divByZero && overflow && io.req.bits.op === DvuOp.DIV) -> "h80000000".U,
    (!divByZero && !overflow && io.req.bits.op === DvuOp.DIV) -> (dividendS / divisorS).asUInt,
    (!divByZero && io.req.bits.op === DvuOp.DIVU) -> (dividend / divisor),
  ))
  remResult := MuxCase(0.U, Seq(
    (divByZero && io.req.bits.op === DvuOp.REM)  -> dividend,
    (divByZero && io.req.bits.op === DvuOp.REMU) -> dividend,
    (!divByZero && overflow && io.req.bits.op === DvuOp.REM) -> 0.U,
    (!divByZero && !overflow && io.req.bits.op === DvuOp.REM) -> (dividendS % divisorS).asUInt,
    (!divByZero && io.req.bits.op === DvuOp.REMU) -> (dividend % divisor),
  ))

  val result = Mux(io.req.bits.op === DvuOp.REM || io.req.bits.op === DvuOp.REMU,
    remResult, divResult)

  // Single-entry output register
  val pendingValid = RegInit(false.B)
  val pendingAddr  = Reg(UInt(5.W))
  val pendingData  = Reg(UInt(32.W))

  val outFree = !pendingValid || io.rd.ready

  when(outFree) {
    pendingValid := io.req.valid
    when(io.req.valid) {
      pendingAddr := io.req.bits.addr
      pendingData := result
    }
  }

  io.rd.valid      := pendingValid
  io.rd.bits.addr  := pendingAddr
  io.rd.bits.data  := pendingData
}

/** Emit helper used by the build system. */
object EmitDvu extends App {
  import circt.stage.ChiselStage
  ChiselStage.emitSystemVerilog(new Dvu(new Parameters), args)
}
