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

import common.IDiv

object DvuOp extends ChiselEnum {
  val DIV, DIVU, REM, REMU = Value
}

// Divide unit – wraps the common IDiv iterative divider.
class Dvu(p: Parameters) extends Module {
  val io = IO(new Bundle {
    val req = Flipped(Valid(new Bundle {
      val addr = UInt(5.W)
      val op   = DvuOp()
    }))
    val rs1 = Input(new Bundle {
      val valid = Bool()
      val data  = UInt(32.W)
    })
    val rs2 = Input(new Bundle {
      val valid = Bool()
      val data  = UInt(32.W)
    })
    val rd = Decoupled(new Bundle {
      val addr = UInt(5.W)
      val data = UInt(32.W)
    })
  })

  val div = Module(new IDiv)

  // Track in-flight request metadata
  val inflight     = RegInit(false.B)
  val inflightAddr = RegInit(0.U(5.W))

  val isSigned = (io.req.bits.op === DvuOp.DIV) || (io.req.bits.op === DvuOp.REM)
  val isRem    = (io.req.bits.op === DvuOp.REM) || (io.req.bits.op === DvuOp.REMU)
  val canAccept = !inflight || div.io.resp.fire

  // Feed IDiv
  div.io.req.valid          := io.req.valid && canAccept
  div.io.req.bits.dividend  := io.rs1.data
  div.io.req.bits.divisor   := io.rs2.data
  div.io.req.bits.signed_   := isSigned
  div.io.req.bits.rem       := isRem

  when (div.io.req.fire) {
    inflight     := true.B
    inflightAddr := io.req.bits.addr
  } .elsewhen (div.io.resp.fire) {
    inflight := false.B
  }

  // Collect result
  div.io.resp.ready    := io.rd.ready
  io.rd.valid          := div.io.resp.valid
  io.rd.bits.data      := div.io.resp.bits.result
  io.rd.bits.addr      := inflightAddr
}
