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

/** CSR interface for FetchControl (just pcStart). */
class FetchControlCSRIO extends Bundle {
  val value = Input(Vec(1, UInt(32.W)))
}

/** Buffer request interface. */
class BufferRequestIO(instsPerFetch: Int) extends Bundle {
  val nValid = Output(UInt(log2Ceil(instsPerFetch + 1).W))
  val nReady = Input(UInt(log2Ceil(instsPerFetch + 1).W))
}

/** FetchControl: manages the program counter and orchestrates instruction fetches.
  *
  * Behavior summary:
  * - Holds fetchAddr.valid low during reset and for one cycle after.
  * - After reset, starts fetching from csr.value(0) (pcStart).
  * - When branch.valid: flush bufferRequest.nValid to 0 and redirect PC.
  * - On fetchData: compute nValid (stop at first JAL), set nextPC = fetchAddr + jumpIdx*4
  *   (at JAL) or fetchAddr + 8*4 (no JAL).
  * - Back-pressure: don't issue fetchAddr when bufferSpaces < instsPerFetch.
  */
class FetchControl(p: Parameters) extends Module {
  private val InstsPerFetch = p.fetchDataBits / 32

  val io = IO(new Bundle {
    val fetchAddr     = Decoupled(UInt(p.axiAddrBits.W))
    val fetchData     = Flipped(Valid(new FetchData(p)))
    val branch        = Flipped(Valid(UInt(p.axiAddrBits.W)))
    val bufferRequest = new BufferRequestIO(InstsPerFetch)
    val bufferSpaces  = Input(UInt(log2Ceil(256 + 1).W))
    val csr           = new FetchControlCSRIO
  })

  // ---------------------------------------------------------------------------
  // PC register
  // ---------------------------------------------------------------------------
  val pc = RegInit(0.U(p.axiAddrBits.W))

  // One-cycle branch flush flag: while true, suppress fetches and nValid
  val branching = RegInit(false.B)

  // ---------------------------------------------------------------------------
  // Instruction type detection
  // ---------------------------------------------------------------------------
  def isJAL(inst: UInt): Bool = inst(6, 0) === "b1101111".U

  // ---------------------------------------------------------------------------
  // nValid and nextPC from fetchData
  // ---------------------------------------------------------------------------
  val insts    = io.fetchData.bits.inst
  val jumpMask = VecInit(insts.map(isJAL))
  val hasJump  = jumpMask.reduce(_ || _)
  val jumpIdx  = PriorityEncoder(jumpMask)  // index of first JAL

  // Number of valid instructions in this fetch block
  val fetchNValid = Mux(hasJump,
    jumpIdx +& 1.U,
    InstsPerFetch.U
  )

  // Next PC after this block
  // - If JAL found: restart from JAL address (jumpIdx * 4 from block base)
  // - Else: advance past full block
  val fetchNextPC = Wire(UInt(p.axiAddrBits.W))
  fetchNextPC := Mux(hasJump,
    io.fetchData.bits.addr + (jumpIdx << 2),
    io.fetchData.bits.addr + (InstsPerFetch * 4).U
  )

  // ---------------------------------------------------------------------------
  // Buffer output: limited to nReady and suppressed during branch flush
  // ---------------------------------------------------------------------------
  val rawNValid = Mux(io.fetchData.valid && !branching, fetchNValid, 0.U)
  io.bufferRequest.nValid := Mux(rawNValid > io.bufferRequest.nReady,
                                  io.bufferRequest.nReady, rawNValid)

  // ---------------------------------------------------------------------------
  // Fetch address handshake
  // ---------------------------------------------------------------------------
  val canFetch = io.bufferSpaces >= InstsPerFetch.U

  // Drive fetchAddr
  io.fetchAddr.valid := !reset.asBool && !branching && !io.branch.valid && canFetch
  io.fetchAddr.bits  := pc
  // ready is driven by downstream (fetcher)

  // ---------------------------------------------------------------------------
  // PC update (priority: reset > branch > fetchData > idle)
  // ---------------------------------------------------------------------------
  when(reset.asBool) {
    pc       := io.csr.value(0)
    branching := false.B
  }.elsewhen(io.branch.valid) {
    pc        := io.branch.bits
    branching  := true.B
  }.elsewhen(branching) {
    branching := false.B
  }.elsewhen(io.fetchData.valid) {
    pc := fetchNextPC
  }
}
