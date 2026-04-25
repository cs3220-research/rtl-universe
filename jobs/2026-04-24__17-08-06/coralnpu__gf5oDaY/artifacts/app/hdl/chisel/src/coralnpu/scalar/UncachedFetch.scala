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

/**
 * FetchControl: Manages fetch address generation and instruction sequencing.
 *
 * The module:
 * 1. After reset, starts fetching from pcStart (csr.value(0)).
 * 2. Issues fetchAddr requests when there is enough buffer space.
 * 3. Processes fetchData responses and forwards instructions to the buffer.
 * 4. Handles branch redirections.
 *
 * nInst = fetchDataBits / ilen (number of instructions per fetch line)
 */
class FetchControl(p: Parameters) extends Module {
  val nInst = p.fetchDataBits / p.ilen  // e.g. 256/32 = 8
  val nBits = log2Ceil(nInst + 1)

  val io = IO(new Bundle {
    val fetchAddr   = Decoupled(UInt(p.addrBits.W))
    val fetchData   = Flipped(Valid(new Bundle {
      val addr = UInt(p.addrBits.W)
      val inst = Vec(nInst, UInt(p.ilen.W))
    }))
    val branch      = Flipped(Valid(UInt(p.addrBits.W)))
    val bufferRequest = new Bundle {
      val nValid = Output(UInt(nBits.W))
      val nReady = Input(UInt(nBits.W))
    }
    val bufferSpaces = Input(UInt(log2Ceil(256 + 1).W))
    val csr          = Input(new CsrBundle)  // csr.value(0) = pcStart
  })

  // PC register
  val pcReg        = RegInit(0.U(p.addrBits.W))
  val fetchingReg  = RegInit(false.B)
  val branchReg    = RegInit(false.B)
  val branchTarget = RegInit(0.U(p.addrBits.W))

  // After reset, start from csr.value(0)
  val postReset  = RegInit(false.B)
  when(!reset.asBool && !postReset) {
    postReset := true.B
    pcReg     := io.csr.value(0)
  }

  // Branch handling: when branch arrives, redirect fetch
  when(io.branch.valid) {
    branchReg    := true.B
    branchTarget := io.branch.bits
    fetchingReg  := false.B
  }

  // Defaults
  io.fetchAddr.valid := false.B
  io.fetchAddr.bits  := pcReg
  io.bufferRequest.nValid := 0.U

  // Number of valid instructions to report from fetchData.
  // Suppress when a branch is arriving in the same cycle or branchReg is set.
  val nValidInst = Wire(UInt(nBits.W))
  nValidInst := 0.U

  when(io.fetchData.valid && !branchReg && !io.branch.valid) {
    // Count instructions up to any unconditional jump (JAL = opcode 0x6F).
    // Compute without self-referencing wires by using a scan over instruction positions.
    // isJal(i) = instruction i has JAL opcode
    val instIsJal = VecInit((0 until nInst).map(i =>
      io.fetchData.bits.inst(i)(6, 0) === "b1101111".U
    ))
    // jalFoundBefore(i) = a JAL appeared in an earlier slot that was within budget
    // Computed as a chain: jalFoundBefore(0) = false, jalFoundBefore(i+1) = jalFoundBefore(i) | (isJal(i) & withinBudget(i))
    val jalFoundBefore: Seq[Bool] = (0 until nInst).scanLeft(false.B) { (prev, i) =>
      prev || (instIsJal(i) && (io.bufferRequest.nReady > i.U))
    }.init  // drop the last element (scanLeft produces nInst+1 values)
    // Slot i is included if within nReady AND no JAL found before
    val included = VecInit((0 until nInst).map(i =>
      (io.bufferRequest.nReady > i.U) && !jalFoundBefore(i)
    ))
    val cnt = PopCount(included)
    nValidInst := cnt
    io.bufferRequest.nValid := nValidInst
  }

  // Fetch address generation
  // Only fetch when:
  // - post reset (PC is initialized)
  // - not branching (registered or incoming)
  // - have enough buffer spaces (>= nInst)
  // - not currently waiting for fetch data
  // - not currently receiving fetch data (buffer spaces would be consumed)
  val canFetch = postReset && !branchReg && !io.branch.valid && !fetchingReg &&
                 (io.bufferSpaces >= nInst.U) && !io.fetchData.valid

  when(canFetch) {
    io.fetchAddr.valid := true.B
    io.fetchAddr.bits  := pcReg
    when(io.fetchAddr.ready) {
      fetchingReg := true.B
    }
  }

  // Handle branch resolution
  when(branchReg && !io.branch.valid) {
    branchReg := false.B
    pcReg     := branchTarget
  }

  // Advance PC when fetch data arrives (only when no branch in this or previous cycle)
  when(io.fetchData.valid && !branchReg && !io.branch.valid) {
    fetchingReg := false.B
    // Advance PC by fetch line size (nInst * 4 bytes)
    pcReg := io.fetchData.bits.addr + (nInst * 4).U

    // Check if last instruction is a jump and redirect
    val lastValidIdx = nValidInst - 1.U
    for (i <- 0 until nInst) {
      when(i.U === lastValidIdx) {
        val inst = io.fetchData.bits.inst(i)
        val isJal = inst(6, 0) === "b1101111".U
        when(isJal) {
          // Compute JAL target: sign-extended immediate + PC
          val imm20 = Cat(inst(31), inst(19, 12), inst(20), inst(30, 21), 0.U(1.W))
          val immSext = Cat(Fill(11, imm20(20)), imm20)
          pcReg := io.fetchData.bits.addr + (i.U * 4.U) + immSext
        }
      }
    }
  }
}

/**
 * Fetcher: Low-level fetch unit.
 *
 * Receives fetch addresses via ctrl (Decoupled) and issues IBus reads.
 * Returns fetch bundles via fetch (Valid), held for one cycle after completion.
 */
class Fetcher(p: Parameters) extends Module {
  val nInst = p.fetchDataBits / p.ilen

  val io = IO(new Bundle {
    val ctrl  = Flipped(Decoupled(UInt(p.addrBits.W)))
    val fetch = Valid(new Bundle {
      val addr = UInt(p.addrBits.W)
      val inst = Vec(nInst, UInt(p.ilen.W))
    })
    val ibus  = new IBusInterface(p)
  })

  val sIdle :: sFetch :: Nil = Enum(2)
  val state   = RegInit(sIdle)
  val addrReg = RegInit(0.U(p.addrBits.W))

  // Registered fetch output: valid is held for the cycle after completion
  val fetchValidReg = RegInit(false.B)
  val fetchAddrReg  = RegInit(0.U(p.addrBits.W))
  val fetchInstReg  = RegInit(VecInit(Seq.fill(nInst)(0.U(p.ilen.W))))

  io.ctrl.ready  := (state === sIdle)
  io.ibus.valid  := false.B
  io.ibus.addr   := addrReg

  io.fetch.valid     := fetchValidReg
  io.fetch.bits.addr := fetchAddrReg
  for (i <- 0 until nInst) {
    io.fetch.bits.inst(i) := fetchInstReg(i)
  }

  switch(state) {
    is(sIdle) {
      fetchValidReg := false.B  // Clear fetch valid when idle
      when(io.ctrl.valid) {
        addrReg := io.ctrl.bits
        state   := sFetch
      }
    }
    is(sFetch) {
      io.ibus.valid := true.B
      io.ibus.addr  := addrReg
      when(io.ibus.ready) {
        // Latch fetch result into registers
        fetchValidReg := true.B
        fetchAddrReg  := addrReg
        for (i <- 0 until nInst) {
          fetchInstReg(i) := io.ibus.rdata((i + 1) * p.ilen - 1, i * p.ilen)
        }
        state := sIdle
      }
    }
  }
}
