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
import common.Fp32

/**
 * Floating-point register file.
 *
 * @param nReadPorts  Number of read ports.
 * @param nWritePorts Number of write ports.
 *
 * Scoreboard tracks "in-flight" writes.
 * scoreboard_set: each bit that is 1 sets the corresponding scoreboard bit.
 * When a write port commits a value, the corresponding scoreboard bit is cleared.
 */
class FRegfile(p: Parameters, nReadPorts: Int, nWritePorts: Int) extends Module {
  val io = IO(new Bundle {
    val read_ports  = Vec(nReadPorts, new Bundle {
      val valid = Input(Bool())
      val addr  = Input(UInt(5.W))
      val data  = Output(new Fp32)
    })
    val write_ports = Vec(nWritePorts, new Bundle {
      val valid = Input(Bool())
      val addr  = Input(UInt(5.W))
      val data  = Input(new Fp32)
    })
    val scoreboard      = Output(UInt(32.W))
    val scoreboard_set  = Input(UInt(32.W))
    val exception       = Output(Bool())
  })

  // Register file: 32 FP32 registers
  val regs = RegInit(VecInit(Seq.fill(32)(0.U.asTypeOf(new Fp32))))

  // Scoreboard register: each bit = 1 means register is in-flight
  val sbReg = RegInit(0.U(32.W))

  // Update scoreboard: set bits from scoreboard_set, clear bits from completed writes
  val writeMask = (0 until nWritePorts).map { w =>
    Mux(io.write_ports(w).valid, 1.U(32.W) << io.write_ports(w).addr, 0.U(32.W))
  }.reduce(_ | _)

  sbReg := (sbReg | io.scoreboard_set) & ~writeMask

  io.scoreboard := sbReg

  // Write ports: commit values to registers
  for (w <- 0 until nWritePorts) {
    when(io.write_ports(w).valid) {
      regs(io.write_ports(w).addr) := io.write_ports(w).data
    }
  }

  // Read ports: return register values
  for (r <- 0 until nReadPorts) {
    io.read_ports(r).data := regs(io.read_ports(r).addr)
  }

  // Exception: multiple write ports writing to same address in same cycle
  val exception = Wire(Bool())
  exception := false.B
  for (w1 <- 0 until nWritePorts) {
    for (w2 <- (w1 + 1) until nWritePorts) {
      when(io.write_ports(w1).valid && io.write_ports(w2).valid &&
           io.write_ports(w1).addr === io.write_ports(w2).addr) {
        exception := true.B
      }
    }
  }
  io.exception := exception
}
