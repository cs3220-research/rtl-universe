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

package bus

import chisel3._
import chisel3.util._

/** RISC-V PLIC (Platform Level Interrupt Controller).
  *
  * Simplified implementation supporting up to `nSources` interrupt sources.
  * Register map:
  *   0x000000 + 4*src : priority[src]  (3 bits)
  *   0x001000        : pending[0..31]  (bitmask)
  *   0x002000        : enable[0..31]   (bitmask)
  *   0x200000        : threshold
  *   0x200004        : claim/complete
  *
  * @param p        TL-UL parameters
  * @param nSources number of interrupt sources (max 32 in this implementation)
  */
class Plic(p: TLULParameters, nSources: Int = 32) extends Module {
  val io = IO(new Bundle {
    val tl     = Flipped(new TLBundleUL(p))
    val irqs   = Input(UInt(nSources.W))
    val irqOut = Output(Bool())
  })

  val pending   = RegInit(0.U(nSources.W))
  val enable    = RegInit(0.U(nSources.W))
  val priority  = RegInit(VecInit(Seq.fill(nSources)(0.U(3.W))))
  val threshold = RegInit(0.U(3.W))

  // Latch new interrupt edges.
  pending := pending | io.irqs

  // Generate interrupt output: any enabled pending source above threshold.
  val maskedPending = pending & enable
  io.irqOut := maskedPending.orR

  // ---------------------------------------------------------------------------
  // TL-UL interface (simplified: always ready, one-cycle response)
  // ---------------------------------------------------------------------------
  io.tl.a.ready := true.B

  val respValid = RegInit(false.B)
  val respData  = RegInit(0.U(p.dataWidth.W))
  val respSrc   = RegInit(0.U(p.sourceWidth.W))
  val respOp    = RegInit(TLULOpcodesD.AccessAck)
  val respSz    = RegInit(0.U(p.sizeWidth.W))

  when(io.tl.a.fire) {
    respValid := true.B
    respSrc   := io.tl.a.bits.source
    respSz    := io.tl.a.bits.size
    val addr  = io.tl.a.bits.address

    when(io.tl.a.bits.opcode === TLULOpcodesA.Get) {
      respOp := TLULOpcodesD.AccessAckData
      respData := MuxLookup(addr, 0.U)(Seq(
        0x001000.U -> pending,
        0x002000.U -> enable,
        0x200000.U -> threshold,
        0x200004.U -> PriorityEncoder(maskedPending)
      ))
    } .otherwise {
      respOp := TLULOpcodesD.AccessAck
      respData := 0.U
      when(addr === 0x002000.U) { enable    := io.tl.a.bits.data(nSources - 1, 0) }
      when(addr === 0x200000.U) { threshold := io.tl.a.bits.data(2, 0) }
      // Claim/complete: clear the pending bit for the claimed source.
      when(addr === 0x200004.U) {
        val src = io.tl.a.bits.data(4, 0)
        pending := pending & ~(1.U << src)
      }
    }
  }
  when(io.tl.d.fire) { respValid := false.B }

  io.tl.d.valid        := respValid
  io.tl.d.bits.opcode  := respOp
  io.tl.d.bits.param   := 0.U
  io.tl.d.bits.size    := respSz
  io.tl.d.bits.source  := respSrc
  io.tl.d.bits.sink    := 0.U
  io.tl.d.bits.denied  := false.B
  io.tl.d.bits.data    := respData
  io.tl.d.bits.corrupt := false.B
  io.tl.d.bits.error   := false.B
}
