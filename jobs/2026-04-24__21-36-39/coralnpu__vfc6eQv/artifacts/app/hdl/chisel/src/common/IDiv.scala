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

package common

import chisel3._
import chisel3.util._

/** IDiv: integer division module.
  *
  * Computes quotient and remainder for unsigned 32-bit integers.
  * Uses an iterative (non-restoring) divider.
  *
  * @param width Bit width of the operands.
  */
class IDiv(width: Int = 32) extends Module {
  val io = IO(new Bundle {
    val in  = Flipped(Decoupled(new Bundle {
      val dividend = UInt(width.W)
      val divisor  = UInt(width.W)
    }))
    val out = Decoupled(new Bundle {
      val quotient  = UInt(width.W)
      val remainder = UInt(width.W)
    })
  })

  // Simple state machine: idle -> computing -> done
  val sIdle :: sCompute :: sDone :: Nil = Enum(3)
  val state = RegInit(sIdle)

  val dividend  = Reg(UInt(width.W))
  val divisor   = Reg(UInt(width.W))
  val quotient  = Reg(UInt(width.W))
  val remainder = Reg(UInt(width.W))
  val counter   = Reg(UInt(log2Ceil(width + 1).W))

  val partial   = Reg(UInt((width + 1).W))

  io.in.ready  := (state === sIdle)
  io.out.valid := (state === sDone)
  io.out.bits.quotient  := quotient
  io.out.bits.remainder := remainder

  switch(state) {
    is(sIdle) {
      when(io.in.valid) {
        dividend  := io.in.bits.dividend
        divisor   := io.in.bits.divisor
        quotient  := 0.U
        partial   := 0.U
        counter   := 0.U
        when(io.in.bits.divisor === 0.U) {
          // Division by zero: return all-ones quotient, dividend as remainder
          quotient  := ((1.U << width) - 1.U)
          remainder := io.in.bits.dividend
          state     := sDone
        }.otherwise {
          state := sCompute
        }
      }
    }
    is(sCompute) {
      // Non-restoring division step
      val bit      = width.U - 1.U - counter
      val partialShifted = Cat(partial(width - 1, 0), dividend(bit))
      val newPartial = WireDefault(partialShifted)
      val quotBit   = WireDefault(0.U(1.W))

      when(partialShifted >= divisor) {
        newPartial := partialShifted - divisor
        quotBit    := 1.U
      }
      partial  := newPartial
      quotient := Cat(quotient(width - 2, 0), quotBit)
      counter  := counter + 1.U

      when(counter === (width - 1).U) {
        remainder := newPartial(width - 1, 0)
        state     := sDone
      }
    }
    is(sDone) {
      when(io.out.ready) {
        state := sIdle
      }
    }
  }
}
