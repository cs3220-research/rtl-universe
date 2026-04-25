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

/** Divide unit stub. */
class Dvu(p: Parameters) extends Module {
  val addrWidth = log2Ceil(p.nRegs)

  val io = IO(new Bundle {
    val req = Flipped(Decoupled(new Bundle {
      val addr = UInt(addrWidth.W)
      val op   = MluOp()
      val rs1  = UInt(32.W)
      val rs2  = UInt(32.W)
    }))
    val rd = Decoupled(new Bundle {
      val addr = UInt(addrWidth.W)
      val data = UInt(32.W)
    })
  })

  val result = Wire(UInt(32.W))
  result := 0.U

  switch(io.req.bits.op) {
    is(MluOp.DIV) {
      result := Mux(io.req.bits.rs2 === 0.U, "hFFFFFFFF".U,
                  (io.req.bits.rs1.asSInt / io.req.bits.rs2.asSInt).asUInt)
    }
    is(MluOp.DIVU) {
      result := Mux(io.req.bits.rs2 === 0.U, "hFFFFFFFF".U,
                  io.req.bits.rs1 / io.req.bits.rs2)
    }
    is(MluOp.REM) {
      result := Mux(io.req.bits.rs2 === 0.U, io.req.bits.rs1,
                  (io.req.bits.rs1.asSInt % io.req.bits.rs2.asSInt).asUInt)
    }
    is(MluOp.REMU) {
      result := Mux(io.req.bits.rs2 === 0.U, io.req.bits.rs1,
                  io.req.bits.rs1 % io.req.bits.rs2)
    }
  }

  val fifo = Module(new Queue(new Bundle {
    val addr = UInt(addrWidth.W)
    val data = UInt(32.W)
  }, 2))

  fifo.io.enq.valid      := io.req.valid
  fifo.io.enq.bits.addr  := io.req.bits.addr
  fifo.io.enq.bits.data  := result
  io.req.ready           := fifo.io.enq.ready
  io.rd                  <> fifo.io.deq
}

/** Emitter for Dvu. */
object EmitDvu extends App {
  val p = new Parameters
  _root_.circt.stage.ChiselStage.emitSystemVerilog(new Dvu(p))
}
