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

package coralnpu.rvv

import chisel3._
import chisel3.util._

// ---------------------------------------------------------------------------
// RVV ALU operations
// ---------------------------------------------------------------------------
object RvvAluOp extends ChiselEnum {
  val VADD, VSUB, VRSUB = Value
  val VMINU, VMIN, VMAXU, VMAX = Value
  val VAND, VOR, VXOR = Value
  val VRGATHER, VRGATHEREI16 = Value
  val VSLIDEUP, VSLIDEDOWN = Value
  val VADC, VMADC, VSBC, VMSBC = Value
  val VMERGE, VMV = Value
  val VMSEQ, VMSNE, VMSLTU, VMSLT, VMSLEU, VMSLE, VMSGTU, VMSGT = Value
  val VSADDU, VSADD, VSSUBU, VSSUB = Value
  val VSMUL = Value
  val VMV1R, VMV2R, VMV4R, VMV8R = Value
  val VSLL = Value
  val VSRL, VSRA, VSSRL, VSSRA = Value
  val VNSRL, VNSRA = Value
  val VNCLIPU, VNCLIP = Value
}

// ---------------------------------------------------------------------------
// Decoded instruction bundle
// ---------------------------------------------------------------------------
class RvvS1DecodedInstruction extends Bundle {
  val op  = RvvAluOp()
  val vd  = UInt(5.W)
  val vs1 = UInt(5.W)
  val vs2 = UInt(5.W)
  val vm  = Bool()
}

// ---------------------------------------------------------------------------
// Compressed instruction representation
// ---------------------------------------------------------------------------
class RvvCompressedInstruction extends Bundle {
  val bits  = UInt(32.W)
  val pcRel = UInt(32.W)  // PC-relative offset (unused in decode)
}

object RvvCompressedInstruction {
  def from_uncompressed(inst: UInt, pcRel: UInt): RvvCompressedInstruction = {
    val c = Wire(new RvvCompressedInstruction)
    c.bits  := inst
    c.pcRel := pcRel
    c
  }
}

// ---------------------------------------------------------------------------
// Decoder
// ---------------------------------------------------------------------------

/** Decode a 32-bit RVV instruction.
  *
  * Returns Valid(decoded) where valid is true iff the instruction is a
  * supported V-extension ALU instruction (opcode = 0x57).
  *
  * Float load (opcode=0x07) and store (opcode=0x27) are explicitly rejected.
  */
object RvvS1DecodeInstruction {
  def apply(inst: UInt): Valid[RvvS1DecodedInstruction] = {
    val out = Wire(Valid(new RvvS1DecodedInstruction))

    val opcode = inst(6, 0)
    val funct3 = inst(14, 12)
    val funct6 = inst(31, 26)
    val vm     = inst(25)
    val vs2    = inst(24, 20)
    val vs1    = inst(19, 15)
    val vd     = inst(11, 7)

    // RVV opcode = 1010111 (0x57)
    val isVec = opcode === "b1010111".U

    // funct3 encoding (source types):
    // 000 = OPIVV (vv), 100 = OPIVX (vx), 011 = OPIVI (vi)
    // 010 = OPMVV (vv), 110 = OPMVX (vx)
    // 001 = OPFVV, 101 = OPFVX
    val isIVV = funct3 === "b000".U
    val isIVX = funct3 === "b100".U
    val isIVI = funct3 === "b011".U
    val isMVV = funct3 === "b010".U
    val isMVX = funct3 === "b110".U

    val isAnyIV = isIVV || isIVX || isIVI
    val isAnyMV = isMVV || isMVX

    import RvvAluOp._

    // Decode table: (funct6, allowed_funct3) → op
    val op = Wire(RvvAluOp())
    val valid = WireDefault(false.B)
    op := VADD  // default

    when(isVec) {
      switch(funct6) {
        // VADD: funct6=000000, vm=x, OPIVV/OPIVX/OPIVI
        is("b000000".U) {
          when(isAnyIV) { op := VADD; valid := true.B }
        }
        // VSUB: funct6=000010
        is("b000010".U) {
          when(isIVV || isIVX) { op := VSUB; valid := true.B }
        }
        // VRSUB: funct6=000011
        is("b000011".U) {
          when(isIVX || isIVI) { op := VRSUB; valid := true.B }
        }
        // VMINU: funct6=000100
        is("b000100".U) {
          when(isIVV || isIVX) { op := VMINU; valid := true.B }
        }
        // VMIN: funct6=000101
        is("b000101".U) {
          when(isIVV || isIVX) { op := VMIN; valid := true.B }
        }
        // VMAXU: funct6=000110
        is("b000110".U) {
          when(isIVV || isIVX) { op := VMAXU; valid := true.B }
        }
        // VMAX: funct6=000111
        is("b000111".U) {
          when(isIVV || isIVX) { op := VMAX; valid := true.B }
        }
        // VAND: funct6=001001
        is("b001001".U) {
          when(isAnyIV) { op := VAND; valid := true.B }
        }
        // VOR: funct6=001010
        is("b001010".U) {
          when(isAnyIV) { op := VOR; valid := true.B }
        }
        // VXOR: funct6=001011
        is("b001011".U) {
          when(isAnyIV) { op := VXOR; valid := true.B }
        }
        // VRGATHER: funct6=001100
        is("b001100".U) {
          when(isAnyIV) { op := VRGATHER; valid := true.B }
        }
        // VSLIDEUP / VRGATHEREI16: funct6=001110
        is("b001110".U) {
          when(isIVV) { op := VRGATHEREI16; valid := true.B }
          when(isIVX || isIVI) { op := VSLIDEUP; valid := true.B }
        }
        // VSLIDEDOWN: funct6=001111
        is("b001111".U) {
          when(isIVX || isIVI) { op := VSLIDEDOWN; valid := true.B }
        }
        // VADC: funct6=010000, requires vm=0 (mask present)
        is("b010000".U) {
          when(isAnyIV && !vm) { op := VADC; valid := true.B }
        }
        // VMADC: funct6=010001
        is("b010001".U) {
          when(isAnyIV) { op := VMADC; valid := true.B }
        }
        // VSBC: funct6=010010, requires vm=0
        is("b010010".U) {
          when((isIVV || isIVX) && !vm) { op := VSBC; valid := true.B }
        }
        // VMSBC: funct6=010011
        is("b010011".U) {
          when(isIVV || isIVX) { op := VMSBC; valid := true.B }
        }
        // VMERGE (vm=0) / VMV (vm=1): funct6=010111
        is("b010111".U) {
          when(isAnyIV) {
            when(!vm) { op := VMERGE; valid := true.B }
            when(vm)  { op := VMV;    valid := true.B }
          }
        }
        // VMSEQ: funct6=011000
        is("b011000".U) {
          when(isAnyIV) { op := VMSEQ; valid := true.B }
        }
        // VMSNE: funct6=011001
        is("b011001".U) {
          when(isAnyIV) { op := VMSNE; valid := true.B }
        }
        // VMSLTU: funct6=011010
        is("b011010".U) {
          when(isIVV || isIVX) { op := VMSLTU; valid := true.B }
        }
        // VMSLT: funct6=011011
        is("b011011".U) {
          when(isIVV || isIVX) { op := VMSLT; valid := true.B }
        }
        // VMSLEU: funct6=011100
        is("b011100".U) {
          when(isAnyIV) { op := VMSLEU; valid := true.B }
        }
        // VMSLE: funct6=011101
        is("b011101".U) {
          when(isAnyIV) { op := VMSLE; valid := true.B }
        }
        // VMSGTU: funct6=011110
        is("b011110".U) {
          when(isIVX || isIVI) { op := VMSGTU; valid := true.B }
        }
        // VMSGT: funct6=011111
        is("b011111".U) {
          when(isIVX || isIVI) { op := VMSGT; valid := true.B }
        }
        // VSADDU: funct6=100000
        is("b100000".U) {
          when(isAnyIV) { op := VSADDU; valid := true.B }
        }
        // VSADD: funct6=100001
        is("b100001".U) {
          when(isAnyIV) { op := VSADD; valid := true.B }
        }
        // VSSUBU: funct6=100010
        is("b100010".U) {
          when(isIVV || isIVX) { op := VSSUBU; valid := true.B }
        }
        // VSSUB: funct6=100011
        is("b100011".U) {
          when(isIVV || isIVX) { op := VSSUB; valid := true.B }
        }
        // VSLL: funct6=100101
        is("b100101".U) {
          when(isAnyIV) { op := VSLL; valid := true.B }
        }
        // VSMUL / VMV{1,2,4,8}R: funct6=100111
        is("b100111".U) {
          when(isIVV || isIVX) { op := VSMUL; valid := true.B }
          when(isIVI) {
            // VMVnR: encoded in vs1 (imm) field
            // 0 → VMV1R, 1 → VMV2R, 3 → VMV4R, 7 → VMV8R
            // vm must be 1 (no mask)
            when(vm) {
              switch(vs1) {
                is(0.U)  { op := VMV1R; valid := true.B }
                is(1.U)  { op := VMV2R; valid := true.B }
                is(3.U)  { op := VMV4R; valid := true.B }
                is(7.U)  { op := VMV8R; valid := true.B }
              }
            }
          }
        }
        // VSRL: funct6=101000
        is("b101000".U) {
          when(isAnyIV) { op := VSRL; valid := true.B }
        }
        // VSRA: funct6=101001
        is("b101001".U) {
          when(isAnyIV) { op := VSRA; valid := true.B }
        }
        // VSSRL: funct6=101010
        is("b101010".U) {
          when(isAnyIV) { op := VSSRL; valid := true.B }
        }
        // VSSRA: funct6=101011
        is("b101011".U) {
          when(isAnyIV) { op := VSSRA; valid := true.B }
        }
        // VNSRL: funct6=101100
        is("b101100".U) {
          when(isAnyIV) { op := VNSRL; valid := true.B }
        }
        // VNSRA: funct6=101101
        is("b101101".U) {
          when(isAnyIV) { op := VNSRA; valid := true.B }
        }
        // VNCLIPU: funct6=101110
        is("b101110".U) {
          when(isAnyIV) { op := VNCLIPU; valid := true.B }
        }
        // VNCLIP: funct6=101111
        is("b101111".U) {
          when(isAnyIV) { op := VNCLIP; valid := true.B }
        }
      }
    }

    out.valid      := valid
    out.bits.op    := op
    out.bits.vd    := vd
    out.bits.vs1   := vs1
    out.bits.vs2   := vs2
    out.bits.vm    := vm

    out
  }
}

/** Decode a compressed RVV instruction (same as uncompressed for now). */
object RvvS1DecodeCompressedInstruction {
  def apply(ci: RvvCompressedInstruction): Valid[RvvS1DecodedInstruction] = {
    RvvS1DecodeInstruction(ci.bits)
  }
}
