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

// Zbb ALU operation codes
object AluOp extends ChiselEnum {
  val SEXTB, SEXTH, ZEXTH,
      CLZ, CTZ, CPOP,
      ORCB, REV8,
      XNOR, ORN, ANDN,
      MAX, MAXU, MIN, MINU,
      ROL, ROR = Value
}

// Zbb bit-manipulation ALU.  All outputs are registered (1-cycle latency).
class Alu(p: Parameters) extends Module {
  val io = IO(new Bundle {
    val req = Flipped(Valid(new Bundle {
      val addr = UInt(5.W)
      val op   = AluOp()
    }))
    val rs1 = Input(new Bundle {
      val valid = Bool()
      val data  = UInt(32.W)
    })
    val rs2 = Input(new Bundle {
      val valid = Bool()
      val data  = UInt(32.W)
    })
    val rd = Valid(new Bundle {
      val addr = UInt(5.W)
      val data = UInt(32.W)
    })
  })

  // ── Combinational result ──────────────────────────────────────────────────
  val rs1 = io.rs1.data
  val rs2 = io.rs2.data

  // CLZ: count leading zeros in a 32-bit word
  def clz32(x: UInt): UInt = {
    val w = Wire(UInt(6.W))
    w := MuxCase(32.U, Seq(
      x(31)          -> 0.U,
      x(30)          -> 1.U,
      x(29)          -> 2.U,
      x(28)          -> 3.U,
      x(27)          -> 4.U,
      x(26)          -> 5.U,
      x(25)          -> 6.U,
      x(24)          -> 7.U,
      x(23)          -> 8.U,
      x(22)          -> 9.U,
      x(21)          -> 10.U,
      x(20)          -> 11.U,
      x(19)          -> 12.U,
      x(18)          -> 13.U,
      x(17)          -> 14.U,
      x(16)          -> 15.U,
      x(15)          -> 16.U,
      x(14)          -> 17.U,
      x(13)          -> 18.U,
      x(12)          -> 19.U,
      x(11)          -> 20.U,
      x(10)          -> 21.U,
      x(9)           -> 22.U,
      x(8)           -> 23.U,
      x(7)           -> 24.U,
      x(6)           -> 25.U,
      x(5)           -> 26.U,
      x(4)           -> 27.U,
      x(3)           -> 28.U,
      x(2)           -> 29.U,
      x(1)           -> 30.U,
      x(0)           -> 31.U
    ))
    w
  }

  // CTZ: count trailing zeros – reverse then CLZ
  // Reversal: put x(31) at bit 0, x(0) at bit 31
  def ctz32(x: UInt): UInt = {
    val rev = VecInit((31 to 0 by -1).map(i => x(i))).asUInt
    clz32(rev)
  }

  // CPOP: population count
  def cpop32(x: UInt): UInt = PopCount(x)

  // ORCB: OR-reduce bytes (each byte becomes 0x00 or 0xFF)
  def orcb32(x: UInt): UInt =
    Cat((3 to 0 by -1).map(i => Fill(8, x(i*8+7, i*8).orR)))

  // REV8: byte-reverse
  def rev8_32(x: UInt): UInt =
    Cat(x(7,0), x(15,8), x(23,16), x(31,24))

  // ROL / ROR (32-bit)
  def rol32(x: UInt, shamt: UInt): UInt = {
    val s = shamt(4,0)
    val left  = (x << s)(31,0)
    val right = (x >> (32.U - s))(31,0)
    left | right
  }
  def ror32(x: UInt, shamt: UInt): UInt = {
    val s = shamt(4,0)
    val right = (x >> s)(31,0)
    val left  = (x << (32.U - s))(31,0)
    right | left
  }

  val result = Wire(UInt(32.W))
  result := 0.U
  switch (io.req.bits.op) {
    is (AluOp.SEXTB) { result := Cat(Fill(24, rs1(7)),  rs1(7,0))  }
    is (AluOp.SEXTH) { result := Cat(Fill(16, rs1(15)), rs1(15,0)) }
    is (AluOp.ZEXTH) { result := Cat(0.U(16.W), rs1(15,0))        }
    is (AluOp.CLZ)   { result := clz32(rs1)                        }
    is (AluOp.CTZ)   { result := ctz32(rs1)                        }
    is (AluOp.CPOP)  { result := cpop32(rs1)                       }
    is (AluOp.ORCB)  { result := orcb32(rs1)                       }
    is (AluOp.REV8)  { result := rev8_32(rs1)                      }
    is (AluOp.XNOR)  { result := ~(rs1 ^ rs2)                      }
    is (AluOp.ORN)   { result := rs1 | ~rs2                        }
    is (AluOp.ANDN)  { result := rs1 & ~rs2                        }
    is (AluOp.MAX)   { result := Mux(rs1.asSInt > rs2.asSInt, rs1, rs2) }
    is (AluOp.MAXU)  { result := Mux(rs1 > rs2, rs1, rs2)         }
    is (AluOp.MIN)   { result := Mux(rs1.asSInt < rs2.asSInt, rs1, rs2) }
    is (AluOp.MINU)  { result := Mux(rs1 < rs2, rs1, rs2)         }
    is (AluOp.ROL)   { result := rol32(rs1, rs2) }
    is (AluOp.ROR)   { result := ror32(rs1, rs2) }
  }

  // ── Registered output (1-cycle latency) ──────────────────────────────────
  val rdValid = RegNext(io.req.valid, false.B)
  val rdAddr  = RegNext(io.req.bits.addr)
  val rdData  = RegNext(result)

  io.rd.valid      := rdValid
  io.rd.bits.addr  := rdAddr
  io.rd.bits.data  := rdData
}
