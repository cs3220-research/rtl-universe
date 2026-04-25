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

/** Read port for the integer register file. */
class RegReadPort extends Bundle {
  val valid = Input(Bool())
  val addr  = Input(UInt(5.W))
  val data  = Output(UInt(32.W))
}

/** Write port for the integer register file. */
class RegWritePort extends Bundle {
  val valid = Input(Bool())
  val addr  = Input(UInt(5.W))
  val data  = Input(UInt(32.W))
}

/** Integer register file (RV32 — 32 × 32-bit registers).
  *
  * Register x0 is hardwired to 0 and ignores writes.
  * Supports parameterised numbers of read and write ports.
  *
  * Scoreboard: tracks in-flight (pending writeback) registers.
  *   - `scoreboard_set` sets bits for newly-issued instructions.
  *   - Write port completion clears the corresponding scoreboard bit.
  */
class Regfile(p: Parameters, numReadPorts: Int, numWritePorts: Int) extends Module {
  val io = IO(new Bundle {
    val read_ports     = Vec(numReadPorts,  new RegReadPort)
    val write_ports    = Vec(numWritePorts, new RegWritePort)
    val scoreboard      = Output(UInt(32.W))
    val scoreboard_set  = Input(UInt(32.W))
    val exception       = Output(Bool())
  })

  // -----------------------------------------------------------------------
  // Register storage (reg(0) is always 0)
  // -----------------------------------------------------------------------
  val regs = RegInit(VecInit(Seq.fill(32)(0.U(32.W))))

  // -----------------------------------------------------------------------
  // Scoreboard
  // -----------------------------------------------------------------------
  val scoreboard = RegInit(0.U(32.W))

  val clearMask = Wire(UInt(32.W))
  clearMask := 0.U
  for (i <- 0 until numWritePorts) {
    when(io.write_ports(i).valid) {
      clearMask := clearMask | (1.U << io.write_ports(i).addr)
    }
  }
  scoreboard := (scoreboard | io.scoreboard_set) & ~clearMask
  io.scoreboard := scoreboard

  // -----------------------------------------------------------------------
  // Write ports (port 0 has highest priority on conflict)
  // -----------------------------------------------------------------------
  for (i <- 0 until numWritePorts) {
    when(io.write_ports(i).valid && io.write_ports(i).addr =/= 0.U) {
      regs(io.write_ports(i).addr) := io.write_ports(i).data
    }
  }

  // -----------------------------------------------------------------------
  // Read ports (combinational; x0 always returns 0)
  // -----------------------------------------------------------------------
  for (i <- 0 until numReadPorts) {
    io.read_ports(i).data := Mux(
      io.read_ports(i).addr === 0.U,
      0.U,
      regs(io.read_ports(i).addr)
    )
  }

  // -----------------------------------------------------------------------
  // Exception: two write ports targeting the same non-zero register
  // -----------------------------------------------------------------------
  val exc = Wire(Bool())
  exc := false.B
  if (numWritePorts > 1) {
    for (i <- 0 until numWritePorts) {
      for (j <- i + 1 until numWritePorts) {
        when(io.write_ports(i).valid && io.write_ports(j).valid &&
             io.write_ports(i).addr === io.write_ports(j).addr &&
             io.write_ports(i).addr =/= 0.U) {
          exc := true.B
        }
      }
    }
  }
  io.exception := exc
}
