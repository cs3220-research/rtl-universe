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

object MluOp extends ChiselEnum {
  val MUL, MULH, MULHSU, MULHU, MAC = Value
}

// 4-slot multiply unit.  Accepts one of 4 dispatch slots, produces one result.
// Latency: 1 cycle (result registered, consumed via Decoupled handshake).
class Mlu(p: Parameters) extends Module {
  val io = IO(new Bundle {
    val req = Vec(4, Flipped(Valid(new Bundle {
      val addr = UInt(5.W)
      val op   = MluOp()
    })))
    val rs1 = Vec(4, Input(new Bundle {
      val valid = Bool()
      val data  = UInt(32.W)
    }))
    val rs2 = Vec(4, Input(new Bundle {
      val valid = Bool()
      val data  = UInt(32.W)
    }))
    val rd = Decoupled(new Bundle {
      val addr = UInt(5.W)
      val data = UInt(32.W)
    })
  })

  // Pick the first valid slot (priority encoder)
  val selValid = io.req.map(_.valid).reduce(_ || _)
  val selIdx   = PriorityEncoder(VecInit(io.req.map(_.valid)))

  val selReq = io.req(selIdx)
  val selRs1 = io.rs1(selIdx).data
  val selRs2 = io.rs2(selIdx).data
  val selOp  = selReq.bits.op
  val selAddr = selReq.bits.addr

  // ── Combinational multiply ────────────────────────────────────────────────
  val product64s  = (selRs1.asSInt * selRs2.asSInt).asUInt  // signed × signed
  val product64u  = selRs1 * selRs2                          // unsigned × unsigned
  val product64su = (selRs1.asSInt * selRs2).asUInt          // signed × unsigned

  val mulResult = Wire(UInt(32.W))
  mulResult := 0.U
  switch (selOp) {
    is (MluOp.MUL)    { mulResult := product64s(31,0)  }
    is (MluOp.MULH)   { mulResult := product64s(63,32) }
    is (MluOp.MULHU)  { mulResult := product64u(63,32) }
    is (MluOp.MULHSU) { mulResult := product64su(63,32) }
    is (MluOp.MAC)    { mulResult := product64s(31,0)  }
  }

  // ── Registered output (1-cycle latency) with Decoupled back-pressure ─────
  val resultValid = RegInit(false.B)
  val resultAddr  = RegInit(0.U(5.W))
  val resultData  = RegInit(0.U(32.W))

  // Capture when a valid request comes in and output register is empty/consumed
  val capture = selValid && (!resultValid || io.rd.fire)

  when (io.rd.fire && !selValid) {
    resultValid := false.B
  } .elsewhen (capture) {
    resultValid := true.B
    resultAddr  := selAddr
    resultData  := mulResult
  }

  io.rd.valid      := resultValid
  io.rd.bits.addr  := resultAddr
  io.rd.bits.data  := resultData
}
