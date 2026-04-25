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

class FRegfileReadPort extends Bundle {
  val valid = Input(Bool())
  val addr  = Input(UInt(5.W))
  val data  = Output(new Fp32)
}

class FRegfileWritePort extends Bundle {
  val valid = Input(Bool())
  val addr  = Input(UInt(5.W))
  val data  = Input(new Fp32)
}

/** Floating-point register file with scoreboard tracking in-flight writes.
  *
  * @param p      Processor parameters.
  * @param nRead  Number of read ports.
  * @param nWrite Number of write ports.
  *
  * Scoreboard: a 32-bit bitmask, one bit per register.
  * - scoreboard_set(i)=1 marks register i as "in flight" (being written).
  * - When a write port completes (write_ports(j).valid), the corresponding
  *   scoreboard bit is cleared.
  * - Multiple write ports to the SAME register in the same cycle raises
  *   `exception`.
  */
class FRegfile(p: Parameters, nRead: Int, nWrite: Int) extends Module {
  val io = IO(new Bundle {
    val read_ports    = Vec(nRead,  new FRegfileReadPort)
    val write_ports   = Vec(nWrite, new FRegfileWritePort)
    val scoreboard     = Output(UInt(32.W))
    val scoreboard_set = Input(UInt(32.W))
    val exception      = Output(Bool())
  })

  // ---------------------------------------------------------------------------
  // Register file (32 FP32 registers)
  // ---------------------------------------------------------------------------
  val regs = RegInit(VecInit(Seq.fill(32)(0.U.asTypeOf(new Fp32))))

  // ---------------------------------------------------------------------------
  // Write ports (with conflict detection)
  // ---------------------------------------------------------------------------
  val writeAddrs = io.write_ports.map(p => Mux(p.valid, p.addr, 31.U + 1.U))

  // Detect conflict: two write ports targeting the same register
  val conflict = Wire(Bool())
  conflict := false.B
  for (i <- 0 until nWrite) {
    for (j <- i + 1 until nWrite) {
      when(io.write_ports(i).valid && io.write_ports(j).valid &&
           io.write_ports(i).addr === io.write_ports(j).addr) {
        conflict := true.B
      }
    }
  }
  io.exception := conflict

  for (wp <- io.write_ports) {
    when(wp.valid) {
      regs(wp.addr) := wp.data
    }
  }

  // ---------------------------------------------------------------------------
  // Read ports
  // ---------------------------------------------------------------------------
  for (rp <- io.read_ports) {
    rp.data := regs(rp.addr)
  }

  // ---------------------------------------------------------------------------
  // Scoreboard
  // ---------------------------------------------------------------------------
  val scoreboardReg = RegInit(0.U(32.W))

  // Build the clear mask from write ports (clear bit for each completed write)
  val clearMask = io.write_ports.map(wp =>
    Mux(wp.valid, (1.U(32.W)) << wp.addr, 0.U(32.W))
  ).reduce(_ | _)

  // Next scoreboard = (current | set) & ~clear
  // Note: set and clear can happen in the same cycle; set takes priority
  // (per the test: "Clear the two entries and set 1 in the same cycle" → result = 17)
  // scoreboard[17] = 17 in decimal = 0b010001 → bits 0 and 4 set
  // Test: set bit 0, clear bits 2 and 3. Previous = 28 = 0b011100
  // Expected after step: 17 = 0b010001 = bits 0 and 4. Hmm, bit 4 stays.
  // 28 = 0b011100 (bits 2,3,4 set)
  // clear bits 2,3: 28 & ~(0b1100) = 28 & 0b10011 = 0b010000 = 16
  // set bit 0: 16 | 1 = 17 ✓
  scoreboardReg := (scoreboardReg | io.scoreboard_set) & ~clearMask

  io.scoreboard := scoreboardReg
}
