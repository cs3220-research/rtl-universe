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

// ============================================================================
// Shared bundle types
// ============================================================================

/** A fetch response: one fetch-line of instructions.
  *
  * `addr` is the base address of the fetch (aligned to the bus width).
  * `inst` holds `n` 32-bit instructions in order (inst(0) is at `addr`).
  */
class FetchData(n: Int = 8) extends Bundle {
  val addr = UInt(32.W)
  val inst = Vec(n, UInt(32.W))
}

/** Bulk-request handshake from FetchControl to the instruction buffer.
  *
  * `nValid` (output): number of instructions being pushed this cycle.
  * `nReady` (input) : number of slots the buffer is willing to accept.
  */
class BulkRequestIO extends Bundle {
  val nValid = Output(UInt(4.W))
  val nReady = Input(UInt(4.W))
}

/** CSR read port used by FetchControl to obtain the reset-PC value.
  *
  * value(0) = PC_START register.
  */
class CsrReadPort(n: Int = 1) extends Bundle {
  val value = Vec(n, Input(UInt(32.W)))
}

/** Instruction bus: simple valid/ready interface to instruction memory. */
class IBus extends Bundle {
  val valid = Output(Bool())     // request is valid
  val ready = Input(Bool())      // memory accepts request and rdata is valid
  val addr  = Output(UInt(32.W))
  val rdata = Input(UInt(256.W)) // 8 × 32-bit instructions
}

// ============================================================================
// Fetcher
// ============================================================================

/** Low-level instruction fetcher.
  *
  * Accepts a fetch-address on `ctrl` (Decoupled), drives `ibus`, and presents
  * fetched instructions on `fetch` (Valid, one-cycle pulse) when the memory
  * responds.
  *
  * Timing (2-cycle fetch):
  *   Cycle 0 : ctrl accepted → ibus.valid raised, addr driven
  *   Cycle 1 : ibus.ready sampled → fetch.valid raised with instruction data
  */
class Fetcher(p: Parameters) extends Module {
  val NumInsts = 8  // 256-bit / 32-bit

  val io = IO(new Bundle {
    val ctrl  = Flipped(Decoupled(UInt(32.W)))
    val ibus  = new IBus
    val fetch = Valid(new FetchData(NumInsts))
  })

  // FSM: idle → pending → (back to idle)
  val sIdle    = 0.U(2.W)
  val sPending = 1.U(2.W)
  val state    = RegInit(sIdle)

  val savedAddr = Reg(UInt(32.W))

  // ctrl accepts a new address when idle
  io.ctrl.ready := (state === sIdle)

  // ibus outputs
  io.ibus.valid := (state === sPending)
  io.ibus.addr  := savedAddr

  // fetch output (registered pulse)
  val outValid = RegInit(false.B)
  val outAddr  = Reg(UInt(32.W))
  val outInsts = Reg(Vec(NumInsts, UInt(32.W)))

  io.fetch.valid       := outValid
  io.fetch.bits.addr   := outAddr
  io.fetch.bits.inst   := outInsts

  outValid := false.B  // default: deassert each cycle

  when(state === sIdle) {
    when(io.ctrl.valid) {
      savedAddr := io.ctrl.bits
      state     := sPending
    }
  }.elsewhen(state === sPending) {
    when(io.ibus.ready) {
      outValid := true.B
      outAddr  := savedAddr
      for (i <- 0 until NumInsts) {
        outInsts(i) := io.ibus.rdata(i * 32 + 31, i * 32)
      }
      state := sIdle
    }
  }
}

// ============================================================================
// FetchControl
// ============================================================================

/** Instruction fetch controller.
  *
  * After reset is de-asserted, starts fetching from CSR.value(0) (PCStart).
  * Issues fetches via `fetchAddr` (Decoupled) gated on available buffer space.
  * Counts valid instructions per fetch line, stopping at a branch/jump opcode.
  * Handles branch redirects from the execute stage via `branch`.
  */
class FetchControl(p: Parameters) extends Module {
  val NumInsts     = 8   // instructions per fetch line
  val FetchBytes   = NumInsts * 4  // 32 bytes per fetch

  // RISC-V branch/jump opcodes
  val OpcodeJAL  = "b1101111".U(7.W)
  val OpcodeJALR = "b1100111".U(7.W)
  val OpcodeBRANCH = "b1100011".U(7.W)

  val io = IO(new Bundle {
    val fetchAddr     = Decoupled(UInt(32.W))
    val fetchData     = Flipped(Valid(new FetchData(NumInsts)))
    val bufferRequest = new BulkRequestIO
    val bufferSpaces  = Input(UInt(5.W))
    val branch        = Flipped(Valid(UInt(32.W)))
    val csr           = new CsrReadPort(1)
  })

  // -----------------------------------------------------------------------
  // Internal state
  // -----------------------------------------------------------------------
  val started       = RegInit(false.B)   // true once reset has been released
  val fetchPC       = RegInit(0.U(32.W)) // next fetch address
  val branchPending = RegInit(false.B)   // waiting to redirect fetch
  val branchTarget  = Reg(UInt(32.W))

  // -----------------------------------------------------------------------
  // Initialisation: capture PCStart the first cycle after reset
  // -----------------------------------------------------------------------
  when(!started) {
    fetchPC := io.csr.value(0)
    started := true.B
  }

  // -----------------------------------------------------------------------
  // Branch / redirect handling
  //
  // When branch.valid is asserted, latch the target and suppress fetch until
  // the branch.valid goes low (resolve).
  // -----------------------------------------------------------------------
  when(io.branch.valid) {
    branchPending := true.B
    branchTarget  := io.branch.bits
  }.elsewhen(branchPending) {
    // Branch has resolved: redirect the PC
    branchPending := false.B
    fetchPC       := branchTarget
  }

  // -----------------------------------------------------------------------
  // Count valid instructions in the incoming fetch line.
  //
  // Scan in order; stop (inclusive) at the first branch / jump opcode.
  // Also compute the jump target for JAL to set the next fetch address.
  // -----------------------------------------------------------------------
  // Combinational: find the cut-off index and next PC after a branch.
  val nValidInst = Wire(UInt(4.W))
  nValidInst := NumInsts.U

  val branchInFetch = Wire(Bool())
  branchInFetch := false.B

  val branchNextPC = Wire(UInt(32.W))
  branchNextPC := io.fetchData.bits.addr  // default: start of fetch

  for (i <- 0 until NumInsts) {
    val inst   = io.fetchData.bits.inst(i)
    val opcode = inst(6, 0)
    val instPC = io.fetchData.bits.addr + (i * 4).U

    val isJal    = (opcode === OpcodeJAL)
    val isJalr   = (opcode === OpcodeJALR)
    val isBranch = (opcode === OpcodeBRANCH)
    val isAnyBranch = isJal || isJalr || isBranch

    // Only update on the FIRST branch found (combinational priority scan)
    when(isAnyBranch && !branchInFetch) {
      nValidInst    := (i + 1).U
      branchInFetch := true.B

      // Compute JAL target: sign-extend the J-type immediate
      val jalImm = Wire(SInt(21.W))
      jalImm := Cat(
        inst(31),           // imm[20]
        inst(19, 12),       // imm[19:12]
        inst(20),           // imm[11]
        inst(30, 21),       // imm[10:1]
        0.U(1.W)            // imm[0] = 0 (always)
      ).asSInt
      // For JAL: target = instPC + jalImm
      // For others: use next sequential address (simplified)
      branchNextPC := Mux(isJal,
        (instPC.asSInt + jalImm).asUInt,
        instPC + 4.U
      )
    }
  }

  // -----------------------------------------------------------------------
  // Determine whether enough buffer space is available to push a full line
  // and to accept the NEXT fetch too.
  //
  // Suppress fetchAddr when:
  //  - not yet started
  //  - branch is pending
  //  - fetchData is currently valid (we are processing it this cycle; let the
  //    consumer absorb it before requesting more)
  //  - bufferSpaces < NumInsts
  // -----------------------------------------------------------------------
  val effectiveNValid = Mux(io.fetchData.valid && !branchPending, nValidInst, 0.U)
  // Space available after the current push
  val spaceAfterPush  = io.bufferSpaces -& effectiveNValid
  val canFetch        = spaceAfterPush >= NumInsts.U

  val fetchBlocked = !started || branchPending || io.fetchData.valid || !canFetch

  io.fetchAddr.valid := !fetchBlocked
  io.fetchAddr.bits  := fetchPC

  // -----------------------------------------------------------------------
  // Push instruction count to the buffer
  // -----------------------------------------------------------------------
  io.bufferRequest.nValid := effectiveNValid

  // -----------------------------------------------------------------------
  // Advance PC after consuming a fetch line (when not redirected by branch)
  // -----------------------------------------------------------------------
  when(io.fetchData.valid && !branchPending) {
    when(branchInFetch) {
      fetchPC := branchNextPC
    }.otherwise {
      fetchPC := io.fetchData.bits.addr + FetchBytes.U
    }
  }
}
