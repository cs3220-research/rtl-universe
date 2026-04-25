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

import common.Fp32

/** Read port for the floating-point register file. */
class FRegReadPort extends Bundle {
  val valid = Input(Bool())
  val addr  = Input(UInt(5.W))
  val data  = Output(new Fp32)
}

/** Write port for the floating-point register file. */
class FRegWritePort extends Bundle {
  val valid = Input(Bool())
  val addr  = Input(UInt(5.W))
  val data  = Input(new Fp32)
}

/** Floating-point register file with scoreboard.
  *
  * Features:
  *  - 32 IEEE-754 single-precision (Fp32) registers
  *  - Parameterised number of read and write ports
  *  - Scoreboard: a bitmask tracking which registers have pending (in-flight)
  *    writeback operations.  Bits are SET by `scoreboard_set` and CLEARED when
  *    the corresponding write-port fires.
  *  - `exception` is raised (combinationally) when two write ports target the
  *    same register in the same cycle.
  *
  * @param p              Core parameters
  * @param numReadPorts   Number of simultaneous read ports
  * @param numWritePorts  Number of simultaneous write ports
  */
class FRegfile(p: Parameters, numReadPorts: Int, numWritePorts: Int) extends Module {
  val io = IO(new Bundle {
    val read_ports    = Vec(numReadPorts,  new FRegReadPort)
    val write_ports   = Vec(numWritePorts, new FRegWritePort)
    val scoreboard     = Output(UInt(32.W))
    val scoreboard_set = Input(UInt(32.W))
    val exception      = Output(Bool())
  })

  // -----------------------------------------------------------------------
  // Register file storage (32 Fp32 registers, initialised to 0)
  // -----------------------------------------------------------------------
  val regs = RegInit(VecInit(Seq.fill(32)(0.U.asTypeOf(new Fp32))))

  // -----------------------------------------------------------------------
  // Scoreboard register
  //
  // Each cycle:
  //   new_scoreboard = (old | set_mask) & ~clear_mask
  // where clear_mask has a bit set for every register written this cycle.
  // -----------------------------------------------------------------------
  val scoreboard = RegInit(0.U(32.W))

  // Build the clear mask from active write ports
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
  // Write ports (priority: port 0 wins if multiple ports write same register)
  // -----------------------------------------------------------------------
  for (i <- 0 until numWritePorts) {
    when(io.write_ports(i).valid) {
      regs(io.write_ports(i).addr) := io.write_ports(i).data
    }
  }

  // -----------------------------------------------------------------------
  // Read ports (combinational, registered data returned synchronously via regs)
  // -----------------------------------------------------------------------
  for (i <- 0 until numReadPorts) {
    io.read_ports(i).data := regs(io.read_ports(i).addr)
  }

  // -----------------------------------------------------------------------
  // Exception: two write ports targeting the same register in the same cycle
  // -----------------------------------------------------------------------
  val exc = Wire(Bool())
  exc := false.B
  if (numWritePorts > 1) {
    for (i <- 0 until numWritePorts) {
      for (j <- i + 1 until numWritePorts) {
        when(io.write_ports(i).valid && io.write_ports(j).valid &&
             io.write_ports(i).addr === io.write_ports(j).addr) {
          exc := true.B
        }
      }
    }
  }
  io.exception := exc
}
