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

/** Integer register file read port. */
class IRegfileReadPort extends Bundle {
  val valid = Input(Bool())
  val addr  = Input(UInt(5.W))
  val data  = Output(UInt(32.W))
}

/** Integer register file write port. */
class IRegfileWritePort extends Bundle {
  val valid = Input(Bool())
  val addr  = Input(UInt(5.W))
  val data  = Input(UInt(32.W))
}

/** Integer register file (RV32I: 32 x 32-bit registers, x0 hardwired to 0). */
class Regfile(p: Parameters, nRead: Int = 2, nWrite: Int = 1) extends Module {
  val io = IO(new Bundle {
    val read_ports  = Vec(nRead,  new IRegfileReadPort)
    val write_ports = Vec(nWrite, new IRegfileWritePort)
  })

  val regs = RegInit(VecInit(Seq.fill(32)(0.U(32.W))))

  for (wp <- io.write_ports) {
    when(wp.valid && wp.addr =/= 0.U) {
      regs(wp.addr) := wp.data
    }
  }

  for (rp <- io.read_ports) {
    rp.data := Mux(rp.addr === 0.U, 0.U, regs(rp.addr))
  }
}
