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

/** Integer register file for the scalar core. */
class Regfile(p: Parameters, nReadPorts: Int = 2, nWritePorts: Int = 1) extends Module {
  val addrWidth = log2Ceil(p.nRegs)

  val io = IO(new Bundle {
    val read  = Vec(nReadPorts, new Bundle {
      val addr = Input(UInt(addrWidth.W))
      val data = Output(UInt(p.xlen.W))
    })
    val write = Vec(nWritePorts, new Bundle {
      val valid = Input(Bool())
      val addr  = Input(UInt(addrWidth.W))
      val data  = Input(UInt(p.xlen.W))
    })
  })

  val regs = RegInit(VecInit(Seq.fill(p.nRegs)(0.U(p.xlen.W))))

  for (w <- 0 until nWritePorts) {
    when(io.write(w).valid && io.write(w).addr =/= 0.U) {
      regs(io.write(w).addr) := io.write(w).data
    }
  }

  for (r <- 0 until nReadPorts) {
    io.read(r).data := Mux(io.read(r).addr === 0.U, 0.U, regs(io.read(r).addr))
  }
}
