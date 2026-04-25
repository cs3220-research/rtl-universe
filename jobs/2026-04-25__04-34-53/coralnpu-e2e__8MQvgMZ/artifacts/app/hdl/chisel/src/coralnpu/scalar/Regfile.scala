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

// Integer register file with scoreboard support.
// nReadPorts  – number of concurrent read ports
// nWritePorts – number of concurrent write ports (higher index wins on conflict)
class Regfile(p: Parameters, nReadPorts: Int = 4, nWritePorts: Int = 6) extends Module {
  val io = IO(new Bundle {
    val scoreboard     = Output(UInt(32.W))
    val scoreboard_set = Input(UInt(32.W))
    val read_ports     = Vec(nReadPorts,  new RegfileReadPort)
    val write_ports    = Vec(nWritePorts, new RegfileWritePort)
  })

  // 32 × 32-bit register file; x0 is hardwired to zero
  val regs = RegInit(VecInit(Seq.fill(32)(0.U(32.W))))

  // Scoreboard: bitmask of registers with outstanding (in-flight) writes
  val scoreboard = RegInit(0.U(32.W))
  io.scoreboard := scoreboard

  // ── Write ports (higher index wins on same-address conflict) ─────────────
  for (wp <- 0 until nWritePorts) {
    when (io.write_ports(wp).valid && io.write_ports(wp).addr =/= 0.U) {
      regs(io.write_ports(wp).addr) := io.write_ports(wp).data
    }
  }

  // Update scoreboard: set bits from scoreboard_set, then clear bits for
  // all registers written this cycle.
  val clearMask = io.write_ports.foldLeft(0.U(32.W)) { (acc, wp) =>
    Mux(wp.valid && wp.addr =/= 0.U,
        acc | (1.U(32.W) << wp.addr),
        acc)
  }
  scoreboard := (scoreboard | io.scoreboard_set) & ~clearMask

  // ── Read ports ────────────────────────────────────────────────────────────
  for (rp <- 0 until nReadPorts) {
    // Forward from the highest-priority writer that matches this address
    // (bypassing writes happening in the same cycle)
    val bypass = Wire(Valid(UInt(32.W)))
    bypass.valid := false.B
    bypass.bits  := 0.U

    for (wp <- 0 until nWritePorts) {
      when (io.write_ports(wp).valid &&
            io.write_ports(wp).addr === io.read_ports(rp).addr &&
            io.read_ports(rp).addr =/= 0.U) {
        bypass.valid := true.B
        bypass.bits  := io.write_ports(wp).data
      }
    }

    io.read_ports(rp).data :=
      Mux(io.read_ports(rp).addr === 0.U, 0.U,
        Mux(bypass.valid, bypass.bits,
          regs(io.read_ports(rp).addr)))
  }
}
