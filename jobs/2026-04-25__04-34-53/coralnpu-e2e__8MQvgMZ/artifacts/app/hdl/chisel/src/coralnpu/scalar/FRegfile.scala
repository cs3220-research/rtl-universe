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
import common.Fp32

// Read port interface
class FRegReadPort extends Bundle {
  val valid = Input(Bool())
  val addr  = Input(UInt(5.W))
  val data  = Output(new Fp32)
}

// Write port interface
class FRegWritePort extends Bundle {
  val valid = Input(Bool())
  val addr  = Input(UInt(5.W))
  val data  = Input(new Fp32)
}

// Floating-point register file with a scoreboard for in-flight tracking.
//
// Parameters:
//   p           - top-level design parameters
//   nReadPorts  - number of combinational read ports
//   nWritePorts - number of synchronous write ports
//
// Scoreboard semantics:
//   scoreboard_set ORed in on next clock edge; write (clear) beats set for
//   the same bit in the same cycle.
//
// Exception:
//   Asserted combinationally when two valid write ports target the same
//   register address simultaneously.
class FRegfile(p: Parameters, nReadPorts: Int, nWritePorts: Int) extends Module {
  val io = IO(new Bundle {
    val scoreboard     = Output(UInt(32.W))
    val scoreboard_set = Input(UInt(32.W))
    val read_ports     = Vec(nReadPorts,  new FRegReadPort)
    val write_ports    = Vec(nWritePorts, new FRegWritePort)
    val exception      = Output(Bool())
  })

  // ------------------------------------------------------------------
  // Register file: 32 x 32-bit words, initialised to 0 on reset.
  // We store the raw IEEE-754 bit pattern as a UInt for easy RegInit.
  // ------------------------------------------------------------------
  val regFile = RegInit(VecInit(Seq.fill(32)(0.U(32.W))))

  // ------------------------------------------------------------------
  // Scoreboard register
  // ------------------------------------------------------------------
  val scoreboard = RegInit(0.U(32.W))
  io.scoreboard := scoreboard

  // Build a mask of all registers being written this cycle.
  // Each write port contributes a one-hot bit; we OR all contributions.
  val writeMask: UInt = if (nWritePorts == 0) {
    0.U(32.W)
  } else {
    io.write_ports.map { wp =>
      Mux(wp.valid, UIntToOH(wp.addr, 32), 0.U(32.W))
    }.reduce(_ | _)
  }

  // Next scoreboard: set bits first, then clear written entries
  scoreboard := (scoreboard | io.scoreboard_set) & ~writeMask

  // ------------------------------------------------------------------
  // Write ports (synchronous)
  // ------------------------------------------------------------------
  for (wp <- io.write_ports) {
    when (wp.valid) {
      regFile(wp.addr) := Cat(wp.data.sign, wp.data.exponent, wp.data.mantissa)
    }
  }

  // ------------------------------------------------------------------
  // Read ports (combinational, async)
  // ------------------------------------------------------------------
  for (rp <- io.read_ports) {
    val word = regFile(rp.addr)
    rp.data := Fp32.fromWord(word)
  }

  // ------------------------------------------------------------------
  // Exception: two write ports targeting the same address
  // ------------------------------------------------------------------
  val exc = Wire(Bool())
  exc := false.B
  if (nWritePorts >= 2) {
    for (i <- 0 until nWritePorts) {
      for (j <- i + 1 until nWritePorts) {
        when (io.write_ports(i).valid && io.write_ports(j).valid &&
              (io.write_ports(i).addr === io.write_ports(j).addr)) {
          exc := true.B
        }
      }
    }
  }
  io.exception := exc
}
