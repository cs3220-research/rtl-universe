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

/** Retirement buffer stub for out-of-order commit. */
class RetirementBuffer(p: Parameters, depth: Int = 16) extends Module {
  val addrWidth = log2Ceil(p.nRegs)

  val io = IO(new Bundle {
    val enq    = Flipped(Decoupled(new Bundle {
      val addr = UInt(addrWidth.W)
      val data = UInt(p.xlen.W)
      val pc   = UInt(p.addrBits.W)
    }))
    val deq    = Decoupled(new Bundle {
      val addr = UInt(addrWidth.W)
      val data = UInt(p.xlen.W)
      val pc   = UInt(p.addrBits.W)
    })
    val flush  = Input(Bool())
    val full   = Output(Bool())
    val empty  = Output(Bool())
  })

  val fifo = Module(new Queue(new Bundle {
    val addr = UInt(addrWidth.W)
    val data = UInt(p.xlen.W)
    val pc   = UInt(p.addrBits.W)
  }, depth))

  fifo.io.enq <> io.enq
  io.deq      <> fifo.io.deq
  io.full     := !fifo.io.enq.ready
  io.empty    := !fifo.io.deq.valid

  when(io.flush) {
    fifo.io.enq.valid := false.B
    fifo.io.deq.ready := false.B
  }
}
