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

// ── Fetcher ────────────────────────────────────────────────────────────────
// Converts a fetch-address request into an IBus transaction and
// reassembles the individual 32-bit instructions from the wide rdata word.
class Fetcher(p: Parameters) extends Module {
  val nInst = p.fetchDataBits / 32

  val io = IO(new Bundle {
    val ctrl  = Flipped(Valid(UInt(32.W)))
    val ibus  = new IBusBundle(p.fetchDataBits)
    val fetch = Valid(new Bundle {
      val addr = UInt(32.W)
      val inst = Vec(nInst, UInt(32.W))
    })
  })

  // Latch the pending request address
  val pendingValid = RegInit(false.B)
  val pendingAddr  = RegInit(0.U(32.W))

  when (io.ctrl.valid) {
    pendingValid := true.B
    pendingAddr  := io.ctrl.bits
  } .elsewhen (io.ibus.valid && io.ibus.ready) {
    pendingValid := false.B
  }

  // Drive IBus
  io.ibus.valid := pendingValid
  io.ibus.addr  := pendingAddr

  // Emit fetch result one cycle after bus responds
  val resultValid = RegNext(io.ibus.valid && io.ibus.ready, false.B)
  val resultAddr  = RegNext(pendingAddr)
  val resultData  = RegNext(io.ibus.rdata)

  io.fetch.valid     := resultValid
  io.fetch.bits.addr := resultAddr
  for (i <- 0 until nInst) {
    io.fetch.bits.inst(i) := resultData(i*32+31, i*32)
  }
}

// ── FetchControl ───────────────────────────────────────────────────────────
// Manages the fetch address stream.
//
// Key behaviours (verified against UncachedFetchTest.scala):
//  - After reset: emit boot address (csr.value(0)) as first fetch request
//  - Backpressure: suppress fetchAddr.valid when bufferSpaces < nInst
//  - On fetchData arrival: output nValid = min(nReady, nFetch) instructions
//  - JAL (unconditional branch): truncate group; nFetch = jalIdx+1
//  - On branch.valid: suppress forwarding & fetchAddr; redirect after branch gone
class FetchControl(p: Parameters) extends Module {
  val nInst = p.fetchDataBits / 32

  val io = IO(new Bundle {
    val fetchAddr = Decoupled(UInt(32.W))
    val fetchData = Flipped(Valid(new Bundle {
      val addr = UInt(32.W)
      val inst = Vec(nInst, UInt(32.W))
    }))
    val bufferRequest = new Bundle {
      val nValid = Output(UInt(4.W))
      val nReady = Input(UInt(4.W))
    }
    val bufferSpaces = Input(UInt(4.W))
    val csr          = new CsrReadPort
    val branch       = Flipped(Valid(UInt(32.W)))
  })

  // ── State ──────────────────────────────────────────────────────────────────
  // nextPC: the address of the next fetch group to request
  val nextPC   = RegInit(0.U(32.W))
  val pcValid  = RegInit(false.B)  // true once we know where to fetch from

  // After reset, load boot address on first cycle
  when (!pcValid) {
    nextPC  := io.csr.value(0)
    pcValid := true.B
  }

  // ── Branch redirect ─────────────────────────────────────────────────────────
  // Track whether a branch is in progress (suppress fetch & data forwarding)
  val branchActive = RegInit(false.B)
  val branchTarget = RegInit(0.U(32.W))

  when (io.branch.valid) {
    branchActive := true.B
    branchTarget := io.branch.bits
  }

  // Branch resolves (branch.valid goes low) → redirect nextPC
  val branchResolve = branchActive && !io.branch.valid
  when (branchResolve) {
    branchActive := false.B
    nextPC       := branchTarget
  }

  // ── JAL detection ──────────────────────────────────────────────────────────
  def isJal(inst: UInt): Bool = inst(6,0) === "b1101111".U(7.W)

  // Find index of first JAL in the fetch group (priority: lower index wins)
  val jalIdx = Wire(UInt(4.W))
  jalIdx := nInst.U
  for (i <- (nInst-1) to 0 by -1) {
    when (isJal(io.fetchData.bits.inst(i))) {
      jalIdx := i.U
    }
  }
  val hasJal = jalIdx < nInst.U
  val nFetch  = Mux(hasJal, jalIdx +& 1.U, nInst.U)(3,0)

  // ── Backpressure ─────────────────────────────────────────────────────────
  // Can issue a new fetch only if the buffer has room for an entire fetch group
  val canFetch = io.bufferSpaces >= nInst.U

  // ── nValid output ──────────────────────────────────────────────────────────
  // Output instructions this cycle only when:
  //  - fetchData is valid
  //  - no active branch (we flush stale data)
  //  - branch is not valid this cycle either
  val dataGood = io.fetchData.valid && !branchActive && !io.branch.valid

  val nToSend = Wire(UInt(4.W))
  nToSend := 0.U
  when (dataGood) {
    nToSend := Mux(io.bufferRequest.nReady < nFetch, io.bufferRequest.nReady, nFetch)(3,0)
  }
  io.bufferRequest.nValid := nToSend

  // ── Advance PC after data consumed ─────────────────────────────────────────
  when (dataGood && !branchResolve) {
    // Advance nextPC past the fetched group
    nextPC := io.fetchData.bits.addr + (nFetch << 2)(31,0)
  }

  // ── fetchAddr handshake ─────────────────────────────────────────────────────
  // Emit a fetch request when:
  //  - we know where to fetch from (pcValid)
  //  - no active branch (branch redirect pending)
  //  - buffer has enough room
  //  - no incoming fetchData this cycle that would advance nextPC
  //    (to avoid dual-request in same cycle as data arrived)
  val sendFetch = pcValid && !branchActive && !io.branch.valid && canFetch &&
                  !io.fetchData.valid

  io.fetchAddr.valid := sendFetch
  io.fetchAddr.bits  := nextPC
}
