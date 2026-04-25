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

/**
 * RVV instruction decoder.
 *
 * RISC-V Vector extension instruction format:
 *   [31:26] funct6
 *   [25]    vm     (mask bit: 0=masked, 1=unmasked/nomask)
 *   [24:20] vs2
 *   [19:15] vs1/rs1/imm5
 *   [14:12] funct3 (0=OPIVV, 1=OPFVV, 2=OPMVV, 3=OPIVI, 4=OPIVX, 5=OPFVF, 6=OPMVX, 7=OPCFG)
 *   [11:7]  vd
 *   [6:0]   opcode (0x57 = OP-V)
 *
 * funct3 values:
 *   000 (0) = OPIVV (vector-vector integer)
 *   001 (1) = OPFVV (vector-vector float)
 *   010 (2) = OPMVV (vector-vector mask/MUL)
 *   011 (3) = OPIVI (vector-immediate integer)
 *   100 (4) = OPIVX (vector-scalar integer)
 *   101 (5) = OPFVF (vector-scalar float)
 *   110 (6) = OPMVX (vector-scalar mask/MUL)
 *   111 (7) = OPCFG (config: vsetvl, etc.)
 */
object RvvS1DecodeInstruction {
  val OPC_VEC = "b1010111".U(7.W)  // 0x57

  def apply(inst: UInt): Valid[RvvS1DecodedInstruction] = {
    val out = Wire(Valid(new RvvS1DecodedInstruction()))
    out.valid       := false.B
    out.bits        := 0.U.asTypeOf(new RvvS1DecodedInstruction())

    // Check opcode
    val opcode = inst(6, 0)
    val funct3 = inst(14, 12)
    val funct6 = inst(31, 26)
    val vm     = inst(25)
    val vs2    = inst(24, 20)
    val vs1    = inst(19, 15)
    val vd     = inst(11, 7)

    // Reject non-vector opcodes
    when(opcode =/= OPC_VEC) {
      out.valid := false.B
    }.otherwise {
      // Reject float load/store (flw=0x07, fsw=0x27 with funct3=010)
      // These won't reach here since they have different opcodes.
      // Also reject config (OPCFG = funct3=7)
      when(funct3 === 7.U) {
        out.valid := false.B
      }.otherwise {
        // Decode funct6 to RvvAluOp
        val op = decodeOp(funct6, funct3, vm, vs1, vd, vs2)
        out.valid        := op._1
        out.bits.op      := op._2
        out.bits.vd      := vd
        out.bits.vs1     := vs1
        out.bits.vs2     := vs2
        out.bits.vm      := vm
        out.bits.rs1     := vs1
        out.bits.imm     := vs1.asSInt
        out.bits.funct3  := funct3
      }
    }

    out
  }

  /** Decode funct6 to RvvAluOp. Returns (valid, op). */
  private def decodeOp(funct6: UInt, funct3: UInt, vm: UInt, vs1: UInt, vd: UInt, vs2: UInt): (Bool, RvvAluOp.Type) = {
    val valid = Wire(Bool())
    val op    = Wire(RvvAluOp())
    valid := false.B
    op    := RvvAluOp.VADD

    // funct6 table (from RVV spec):
    // 0x00 VADD
    // 0x02 VSUB (VV, VX only; no VI)
    // 0x03 VRSUB (VX, VI only)
    // 0x04 VMINU
    // 0x05 VMIN
    // 0x06 VMAXU
    // 0x07 VMAX
    // 0x09 VAND
    // 0x0A VOR
    // 0x0B VXOR
    // 0x0C VRGATHER (funct3 != OPMVV)
    // 0x0E VRGATHEREI16 (funct3=OPIVV)
    // 0x0D VSLIDEUP (VX, VI) -- funct6=0x0D? Let me check from encodings
    // Actually VSLIDEUP = funct6 = 0x0E for VX/VI, VRGATHEREI16 = 0x0E for VV
    // VSLIDEDOWN = 0x0F
    //
    // Let me decode from the hex values in the test:
    // 0x02000057 = 0000_0010_0000_0000_0000_0000_0101_0111
    //   funct6=0b000000=0, vm=1, vs2=0, vs1=0, funct3=0(VV), vd=0 -> VADD
    // 0x02004057: funct3=4(VX) -> VADD
    // 0x02003057: funct3=3(VI) -> VADD
    // 0x0a000057: funct6=0b000010=2 -> VSUB (VV)
    // 0x0a004057: funct6=2, funct3=4 -> VSUB (VX)
    // 0x0e004057: funct6=0b000011=3, funct3=4 -> VRSUB
    // 0x0e003057: funct6=3, funct3=3 -> VRSUB
    // 0x12000057: funct6=4 -> VMINU
    // 0x16000057: funct6=5 -> VMIN
    // 0x1a000057: funct6=6 -> VMAXU
    // 0x1e000057: funct6=7 -> VMAX
    // 0x26000057: funct6=9 -> VAND
    // 0x2a000057: funct6=0xa -> VOR
    // 0x2e000057: funct6=0xb -> VXOR
    // 0x32108057: funct6=0xc -> VRGATHER (VV: vd=0, vs2=2, vs1=1) wait
    //   0x32108057 = 0011_0010_0001_0000_1000_0000_0101_0111
    //   funct6[31:26] = 001100 = 0x0C, vm=1, vs2=1, vs1=1, funct3=0(VV), vd=1?
    //   Actually vd[11:7] = 00001 = 1... let me recalculate
    //   0x32108057 in binary:
    //   0011 0010 0001 0000 1000 0000 0101 0111
    //   31-26: 001100 = 12 = 0xC
    //   25: 1 (vm=1, unmasked)
    //   24-20: 00001 = 1 (vs2)
    //   19-15: 00001 = 1 (vs1)
    //   14-12: 000 (funct3=VV)
    //   11-7: 00001 = 1 (vd? wait 0x32108057...)
    //
    // Let me be more careful: 0x32108057
    //   0x32108057 = 0011_0010_0001_0000_1000_0000_0101_0111
    //   bits[31:26] = 001100 = 0xC -> VRGATHER
    //   bit[25]     = 1           -> vm=1 (unmasked)
    //   bits[24:20] = 00001 = 1   -> vs2=1
    //   bits[19:15] = 00001 = 1   -> vs1=1
    //   bits[14:12] = 000          -> funct3=0 (VV)
    //   bits[11:7]  = 00001 = 1   -> vd? Wait: 0x32108057:
    //   Let me hex decode properly:
    //   0x32 = 0011 0010
    //   0x10 = 0001 0000
    //   0x80 = 1000 0000
    //   0x57 = 0101 0111
    //   So: 0011_0010_0001_0000_1000_0000_0101_0111
    //   [31:24] = 0011_0010 = 0x32
    //   [23:16] = 0001_0000 = 0x10
    //   [15:8]  = 1000_0000 = 0x80
    //   [7:0]   = 0101_0111 = 0x57
    //   funct6  = [31:26] = 001100 = 12
    //   vm      = [25]    = 1
    //   vs2     = [24:20] = 00010 = 2? Wait: 0x32 = 0011_0010, so bits 24=0,23-20=0010
    //   Actually: [31:0] in MSB order:
    //   31 30 29 28 27 26 25 24 23 22 21 20 19 18 17 16 15 14 13 12 11 10  9  8  7  6  5  4  3  2  1  0
    //    0  0  1  1  0  0  1  0  0  0  0  1  0  0  0  0  1  0  0  0  0  0  0  0  0  1  0  1  0  1  1  1
    //   funct6 = [31:26] = 001100 = 12 = 0xC
    //   vm     = [25]    = 1
    //   vs2    = [24:20] = 00001 = 1  (bit 24=0, 23=0, 22=0, 21=0, 20=1)
    //   vs1    = [19:15] = 00001 = 1  (bit 19=0, 18=0, 17=0, 16=0, 15=1)
    //   funct3 = [14:12] = 000 = 0 (VV)
    //   vd     = [11:7]  = 00001 = 1
    // -> VRGATHER.vv v1, v1, v1 (vd=1, vs2=1, vs1=1)
    // -> funct6=0xC, funct3=VV -> VRGATHER ✓
    //
    // 0x32104057: funct3=4(VX) -> VRGATHER.vx ✓
    // 0x32103057: funct3=3(VI) -> VRGATHER.vi ✓
    // 0x3a108057: 0x3a=0011_1010
    //   funct6=[31:26]=001110=14=0xE
    //   vm=1, vs2=1, vs1=1, funct3=000(VV), vd=1
    //   -> VRGATHEREI16.vv ✓
    //
    // 0x3a0040d7: funct6=0xE, vm=1, vs2=0, rs1=0, funct3=4(VX), vd=2? wait
    //   0x3a=0011_1010, 0x00=0000_0000, 0x40=0100_0000, 0xd7=1101_0111
    //   [31:26]=001110=14=0xE, [25]=1=vm, [24:20]=00000=0(vs2),
    //   [19:15]=00000=0(vs1/rs1), [14:12]=100=4(VX), [11:7]=00010=2(vd?)
    //   Wait 0xd7: d=1101, 7=0111 -> opcode=[6:0]=1010111=0x57 ✓, vd=[11:7]=00010=2? Wait:
    //   0x40=0100_0000 -> bits[15:8]=0100_0000, so bit[14:12]=100=4 and bits[11:8]=0000
    //   0xd7=1101_0111 -> bits[7:0]=1101_0111, so bit[11:7]=00010 ... hmm
    //   Actually 0x3a0040d7 in hex -> 0x3a=58, 0x00=0, 0x40=64, 0xd7=215
    //   As 32-bit: 0011_1010_0000_0000_0100_0000_1101_0111
    //   [31:26]=001110=14=0xE (VSLIDEUP/VRGATHEREI16)
    //   [25]=1=vm, [24:20]=00000=0(vs2), [19:15]=00000=0(rs1)
    //   [14:12]=100=4 -> VX format
    //   [11:7]=00010=2 -> vd? but test says vslideup.vx v1, v0, x0
    //   vd=v1=1, vs2=v0=0, rs1=x0=0 -> [11:7]=1=vd? Wait 0x0040d7:
    //   0x0040=0000_0000_0100_0000: bits[15:8]=0000_0000, [14:12]=100, [11:8]=0000
    //   0xd7=1101_0111: bits[7:0]=1101_0111, so [6:0]=1010111 ✓ opcode, [7]=1
    //   So vd=[11:7]=00010=2? That's v2, not v1.
    //   Hmm, the test says "vslideup.vx v1, v0, x0" -> vd=1.
    //   Let me re-read 0x3a0040d7:
    //   0x3a = 58 = 0011_1010
    //   0x00 = 0  = 0000_0000
    //   0x40 = 64 = 0100_0000
    //   0xd7 = 215 = 1101_0111
    //   32-bit LE: 0xd7_40_00_3a = 1101_0111_0100_0000_0000_0000_0011_1010
    //   Wait no: 0x3a0040d7 means the 4 bytes are 3a 00 40 d7 in memory (big-endian).
    //   As a 32-bit integer: MSB=0x3a, LSB=0xd7.
    //   So the 32-bit value = 0x3a0040d7.
    //   bits[31:24] = 0x3a = 0011_1010
    //   bits[23:16] = 0x00 = 0000_0000
    //   bits[15:8]  = 0x40 = 0100_0000
    //   bits[7:0]   = 0xd7 = 1101_0111
    //
    //   funct6 = bits[31:26] = 0011_10 = 14 = 0xE
    //   vm     = bit[25]     = 1 (unmasked... but VSLIDEUP requires mask!)
    //            Actually 0x3a = 0011_1010, bit 26=1 (funct6 bit 0), bit 25=0? Let me recount:
    //            0x3a = 0011 1010
    //            bit31=0, 30=0, 29=1, 28=1, 27=1, 26=0, 25=1, 24=0
    //   Actually: 0x3a = 58 = 0b00111010
    //   bits[31:24]: 0, 0, 1, 1, 1, 0, 1, 0
    //   So bit31=0, bit30=0, bit29=1, bit28=1, bit27=1, bit26=0, bit25=1, bit24=0
    //   funct6[5:0] = bits[31:26] = 001110 = 14 = 0xE
    //   vm = bit25 = 1 (unmasked)
    //   vs2 = bits[24:20] = 0, bits[23:16]=0x00, so bits[24:20]=0_0000=0
    //   vs1/rs1 = bits[19:15] = from 0x00: 00000 = 0
    //   funct3 = bits[14:12] = from 0x40 = 0100_0000: bit14=1, bit13=0, bit12=0 = 100 = 4 (VX)
    //   vd = bits[11:7] = from 0x40 upper and 0xd7:
    //         bits[11:8] from 0x40 lower = 0000, bit[7] from 0xd7 bit7 = 1
    //         vd[11:7]: bit11=0, 10=0, 9=0, 8=0, 7=1 -> but wait bit7 is part of opcode!
    //         vd = bits[11:7], opcode = bits[6:0]
    //         0xd7 = 1101_0111, bits[7:0] = d7
    //         bit7=1, opcode=[6:0]=101_0111=0x57 ✓
    //         So bit11=from 0x40[3]=0, bit10=0x40[2]=0, bit9=0x40[1]=0, bit8=0x40[0]=0
    //         bit7=0xd7[7]=1
    //         vd = bits[11:7] = 0000_1 = 1 -> vd=1 ✓ (v1)
    //
    //   So 0x3a0040d7 = funct6=0xE, vm=1, vs2=0, rs1=0, funct3=4(VX), vd=1
    //   From the test (unmasked): vslideup.vx v1, v0, x0 ✓ (funct6=0xE, VX, vm=1)
    //   But 0x3a is listed as VSLIDEUP... and 0x3a0040d7 where funct6=14=0xE
    //   vs 0x3a108057 which is VRGATHEREI16 (funct3=0=VV, funct6=0xE)
    //   So funct6=0xE + funct3=VV -> VRGATHEREI16
    //   funct6=0xE + funct3=VX/VI -> VSLIDEUP ✓
    //
    // 0x3e0040d7: funct6=[31:26] of 0x3e=0011_1110 -> 001111=15=0xF -> VSLIDEDOWN
    //   funct3=4(VX) -> VSLIDEDOWN.vx ✓
    //
    // 0x46000057: funct6=0x46>>2? Let me decode:
    //   0x46=0100_0110, funct6=[31:26]: bit31=0,30=1,29=0,28=0,30-26=10001? No:
    //   0x46=0100_0110 (MSB=0x46):
    //   bits[31:24]=0x46=0100_0110: bit31=0,30=1,29=0,28=0,27=0,26=1,25=1,24=0
    //   funct6 = bits[31:26] = 010001 = 17 = 0x11 -> VMADC (no carry)
    //   But wait: 0x46000057 has funct6=0b010001=17? Let me compute:
    //   0x46000057 = 0100_0110_0000_0000_0000_0000_0101_0111
    //   bits[31:26] = 010001 = 17 = 0x11? Hmm.
    //   Actually 0x46 = 70 = 0b01000110:
    //   bit7=0,bit6=1,bit5=0,bit4=0,bit3=0,bit2=1,bit1=1,bit0=0
    //   For a 32-bit value 0x46xxxxxx:
    //   bits31-24 = 0100_0110
    //   bit31=0, bit30=1, bit29=0, bit28=0, bit27=0, bit26=1, bit25=1, bit24=0
    //   funct6 = bits[31:26] = 010001 = 17
    //   vm     = bit25 = 1
    //   So for 0x46000057 (VMADC, no mask): funct6=17=0x11, vm=1 ✓
    //   For 0x44000057 (VMADC, with mask): funct6=[31:26] of 0x44=0100_0100
    //   bits31-24=0100_0100: bit31=0,30=1,29=0,28=0,27=0,26=1,25=0,24=0
    //   funct6=010001=17? No: 010001 vs 010001...
    //   0x44: 0100_0100: bit31=0,30=1,29=0,28=0,27=0,26=1 -> funct6=010001? No!
    //   bits[31:26]: bit31=0,30=1,29=0,28=0,27=0,26=1 = 0b010001 = 17
    //   But 0x44 vs 0x46: 0x44=0100_0100 (bit25=0), 0x46=0100_0110 (bit25=1, bit24=1? no)
    //   0x46=70=0b0100_0110: bit7=0,6=1,5=0,4=0,3=0,2=1,1=1,0=0
    //   For the MSB byte of a 32-bit value:
    //   Byte value 0x46 at bits [31:24]:
    //   bit31=0, bit30=1, bit29=0, bit28=0, bit27=0, bit26=1, bit25=1, bit24=0
    //   So 0x46000057: funct6=010001=17, vm=1(unmasked), rest=0
    //   0x44000057: 0x44=0100_0100 at bits[31:24]:
    //   bit31=0,30=1,29=0,28=0,27=0,26=1,25=0,24=0
    //   funct6=010001=17, vm=0(masked)
    //
    // So VMADC has funct6=17=0x11, and vm distinguishes between:
    //   vm=1: no carry in (VMADC without mask)
    //   vm=0: carry in from v0 (VMADC with mask)
    //
    // Let me now build the decode table from funct6 -> op mapping:
    // funct6: op
    //  0x00: VADD
    //  0x02: VSUB (VV,VX)
    //  0x03: VRSUB (VX,VI)
    //  0x04: VMINU
    //  0x05: VMIN
    //  0x06: VMAXU
    //  0x07: VMAX
    //  0x09: VAND
    //  0x0A: VOR
    //  0x0B: VXOR
    //  0x0C: VRGATHER (VV,VX,VI)
    //  0x0E: VRGATHEREI16 (VV) / VSLIDEUP (VX,VI)
    //  0x0F: VSLIDEDOWN (VX,VI)
    //  0x10: VADC (VV,VX,VI - needs mask vm=0) / VMADC (no mask: vm=1)
    //    Wait: 0x40000057 = vadc.vvm v0, v0, v0, v0 -> funct6?
    //    0x40=0100_0000: funct6=[31:26]=010000=16=0x10, vm=0 (bit25=0)
    //    0x46000057 -> VMADC: funct6=010001=17=0x11?? Or...
    //    Hmm let me recompute 0x40: 0100_0000
    //    bits31-24=0x40=64=0100_0000:
    //    bit31=0,30=1,29=0,28=0,27=0,26=0,25=0,24=0
    //    funct6 = 010000 = 16 = 0x10
    //    vm = 0 (masked)
    //    -> 0x40000057 = VADC (vm=0, needs mask v0)
    //    -> 0x46000057: 0x46=70=0100_0110, bit25=1 -> funct6=010001=17=0x11... wait
    //    0x46=0100_0110: bit7=0,6=1,5=0,4=0,3=0,2=1,1=1,0=0
    //    As byte at bits[31:24]:
    //    bit31=0,30=1,29=0,28=0,27=0,26=1,25=1,24=0
    //    funct6=bits[31:26]=010001=17=0x11
    //    vm=bit25=1
    //    So 0x46000057 -> funct6=17, vm=1 -> VMADC (no carry)
    //    And 0x44000057 -> 0x44=68=0100_0100:
    //    bit31=0,30=1,29=0,28=0,27=0,26=1,25=0,24=0 -> funct6=010001=17, vm=0 -> VMADC (with carry)
    //
    // So: funct6=16=0x10, vm=0 -> VADC (uses carry from v0)
    //     funct6=16=0x10, vm=1 -> VMADC (no carry in, produces mask)? Check test...
    //     Test: 0x46000057 -> VMADC (no mask, "unmasked" variant)
    //           0x44000057 with vm=0 -> VMADC (with mask carry)
    //     But the comment says "VADC requires mask" meaning VADC only works with vm=0.
    //     And VMADC can be with or without mask.
    //
    // Let me re-examine:
    //   0x46000057 = inst from test "Decode VAlu ops (no mask)" -> VMADC (no carry)
    //   -> funct6=17=0x11, vm=1 (unmasked)
    //   0x44000057 = inst from "Errata 1" -> VMADC (with carry from v0)
    //   -> funct6=17=0x11, vm=0 (masked=has carry)
    //   0x401080d7 -> VADC (masked): 0x40=funct6=16=0x10, vm=0
    //
    // Wait, but 0x46 and 0x44 both have funct6=17 according to my calculation?
    //   0x46: bits31-26 = 010001 = 17
    //   0x44: bits31-26 = 010001 = 17 also (bit26=1 in both)
    //   No wait: 0x46=0100_0110, 0x44=0100_0100
    //   Both have bits [31:26] (as positions of the byte at [31:24]):
    //   The byte at [31:24] = 0x46 = 0100_0110:
    //   bit31=0,bit30=1,bit29=0,bit28=0,bit27=0,bit26=1,bit25=1,bit24=0
    //   funct6 = {bit31,bit30,bit29,bit28,bit27,bit26} = {0,1,0,0,0,1} = 17
    //   0x44 = 0100_0100:
    //   bit31=0,bit30=1,bit29=0,bit28=0,bit27=0,bit26=1,bit25=0,bit24=0
    //   funct6 = 17 same!
    //   They differ only in bit25 (vm).
    //
    //   So funct6=0x11(17): VMADC (vm=1 means no carry, vm=0 means with carry)
    //   funct6=0x10(16): VADC
    //
    // 0x4e000057: 0x4e=0100_1110
    //   bit31=0,30=1,29=0,28=0,27=1,26=1,25=1,24=0 -> funct6=010011=19, vm=1 -> VMSBC?
    //   Hmm, test: VMSBC (no mask)
    //   0x4c000057: 0x4c=0100_1100:
    //   bit31=0,30=1,29=0,28=0,27=1,26=1,25=0,24=0 -> funct6=010011=19, vm=0 -> VMSBC (with mask)
    //   0x4e000057: funct6=19, vm=1 -> VMSBC (no carry) = same funct6 ✓
    //
    //   Then VSBC would need a different funct6:
    //   0x480000d7: 0x48=0100_1000:
    //   bit31=0,30=1,29=0,28=0,27=1,26=0,25=0,24=0 -> funct6=010010=18, vm=0
    //   -> VSBC (uses carry from v0) funct6=18=0x12
    //
    // Summary so far:
    //   funct6=0x10(16): VADC (vm=0 only valid)
    //   funct6=0x11(17): VMADC (vm=0=with mask carry, vm=1=without carry)
    //   funct6=0x12(18): VSBC (vm=0 only valid)
    //   funct6=0x13(19): VMSBC
    //
    // 0x5e000057: 0x5e=0101_1110:
    //   bit31=0,30=1,29=0,28=1,27=1,26=1,25=1,24=0 -> funct6=010111=23=0x17, vm=1 -> VMV
    // 0x5c000057: 0x5c=0101_1100:
    //   funct6=010111=23, vm=0 -> VMERGE
    //
    // Comparison ops (mask-producing, funct3=OPIVV/OPIVX/OPIVI):
    // 0x60000057: 0x60=0110_0000:
    //   bit31=0,30=1,29=1,28=0,27=0,26=0,25=0,24=0 -> funct6=011000=24=0x18, vm=0
    //   -> VMSEQ (with mask - "no mask" in test means the instruction doesn't USE mask, but vm=0 is fine for comparison)
    //   Actually looking at test "Decode VAlu ops (no mask)": VMSEQ not listed.
    //   And in "with mask": VMSEQ at 0x60000057 with vm=0.
    //   So VMSEQ always has vm=0 (mask encoding), and the "comparison" section is only in "with mask".
    //   funct6=24=0x18 -> VMSEQ
    //
    // 0x64000057: 0x64=0110_0100: funct6=011001=25 -> VMSNE
    // 0x68000057: 0x68=0110_1000: funct6=011010=26 -> VMSLTU
    // 0x6c000057: 0x6c=0110_1100: funct6=011011=27 -> VMSLT
    // 0x70000057: 0x70=0111_0000: funct6=011100=28 -> VMSLEU
    // 0x74000057: 0x74=0111_0100: funct6=011101=29 -> VMSLE
    // 0x78004057: 0x78=0111_1000: funct6=011110=30 -> VMSGTU (VX,VI only)
    // 0x7c004057: 0x7c=0111_1100: funct6=011111=31 -> VMSGT (VX,VI only)
    //
    // 0x82000057: 0x82=1000_0010:
    //   bit31=1,30=0,29=0,28=0,27=0,26=0,25=1 -> funct6=100000=32=0x20, vm=1 -> VSADDU
    // 0x86000057: funct6=100001=33 -> VSADD
    // 0x8a000057: funct6=100010=34 -> VSSUBU
    // 0x8e000057: funct6=100011=35 -> VSSUB
    // 0x9e000057: 0x9e=1001_1110: funct6=100111=39 -> VSMUL (vm=1) or VMV1R (vi, imm=0)
    //   Actually VSMUL and VMVnR share funct6=0x27(39)?
    //   0x9e003057: funct3=3(VI), imm=0 -> VMV1R
    //   0x9e00b057: funct3=3(VI), imm[4:0]=00001=1 -> VMV2R
    //   0x9e01b057: funct3=3(VI), imm=3? let me decode:
    //   0x9e01b057: 0x9e=1001_1110, 0x01=0000_0001, 0xb0=1011_0000, 0x57=0101_0111
    //   bits[31:26]=100111=39, vm=1, vs2=[24:20]=00000=0, vs1/imm=[19:15]=00011=3
    //   funct3=[14:12]=011=3(VI), vd=[11:7]=00000=0? 0xb0=10110000, bit[11:7]:
    //   0xb0 at bits[15:8]: bits[15:8]=1011_0000, so bit[11:8]=1011, and from 0x57 bit7=0
    //   vd=[11:7]: 0b10110=22? That seems wrong for "vmv4r.v v0, v0"
    //   Actually let me reparse: "vmv4r.v v0, v0" should have vd=0, vs2=0.
    //   0x9e01b057 in binary:
    //   1001_1110_0000_0001_1011_0000_0101_0111
    //   bits[31:24]=1001_1110=0x9E
    //   bits[23:16]=0000_0001=0x01
    //   bits[15:8] =1011_0000=0xB0
    //   bits[7:0]  =0101_0111=0x57
    //   funct6=[31:26]=100111=39
    //   vm=[25]=1
    //   vs2=[24:20]=00000=0
    //   vs1/imm=[19:15]=00011=3 (VMVnR uses imm for n: 0=vmv1r, 1=vmv2r, 3=vmv4r, 7=vmv8r)
    //   funct3=[14:12]=011=3 (VI)
    //   vd=[11:7]: bits[11:7] = bit[11]=1,10=0,9=1,8=1,7=0 = 0b10110=22?
    //   But the test says "vmv4r.v v0, v0". Let me re-check encoding from RISC-V spec.
    //   From RISC-V spec: vmv1r.v vd, vs2 = funct6=100111, vm=1, vs2=vs2, vs1=00000, funct3=011, vd=vd
    //   So imm=[19:15]=0 for vmv1r, =1 for vmv2r, =3 for vmv4r, =7 for vmv8r.
    //   0x9e003057: imm=[19:15]=00000=0 -> VMV1R
    //   0x9e00b057: 0x9e=...funct6=39,vm=1,vs2=0, 0x00b057: [19:15]=00001=1 -> VMV2R
    //   0x9e01b057: [19:15]=00011=3 -> VMV4R ✓
    //   0x9e03b057: [19:15]=00111=7 -> VMV8R
    //   For vs1=0: VSMUL (VV: funct3=0, VX: funct3=4)
    //
    // 0x96000057: 0x96=1001_0110: funct6=100101=37 -> VSLL
    // 0xa2000057: 0xa2=1010_0010: funct6=101000=40 -> VSRL
    // 0xa6000057: funct6=101001=41 -> VSRA
    // 0xaa000057: funct6=101010=42 -> VSSRL
    // 0xae000057: funct6=101011=43 -> VSSRA
    // 0xb2000057: funct6=101100=44 -> VNSRL
    // 0xb6000057: funct6=101101=45 -> VNSRA
    // 0xba000057: funct6=101110=46 -> VNCLIPU
    // 0xbe000057: funct6=101111=47 -> VNCLIP

    // Build the decode: create a big switch on funct6
    // Special handling for funct6=0x0E (VRGATHEREI16 vs VSLIDEUP based on funct3)
    // Special handling for funct6=0x27 (VSMUL vs VMVnR based on funct3)
    // Special handling for funct6=0x10/0x11/0x12/0x13 (ADC/SBC with/without mask)

    switch(funct6) {
      is(0x00.U) { op := RvvAluOp.VADD;   valid := true.B }
      is(0x02.U) { op := RvvAluOp.VSUB;   valid := funct3 =/= 3.U }  // not VI
      is(0x03.U) { op := RvvAluOp.VRSUB;  valid := funct3 === 3.U || funct3 === 4.U }  // VI or VX
      is(0x04.U) { op := RvvAluOp.VMINU;  valid := true.B }
      is(0x05.U) { op := RvvAluOp.VMIN;   valid := true.B }
      is(0x06.U) { op := RvvAluOp.VMAXU;  valid := true.B }
      is(0x07.U) { op := RvvAluOp.VMAX;   valid := true.B }
      is(0x09.U) { op := RvvAluOp.VAND;   valid := true.B }
      is(0x0A.U) { op := RvvAluOp.VOR;    valid := true.B }
      is(0x0B.U) { op := RvvAluOp.VXOR;   valid := true.B }
      is(0x0C.U) { op := RvvAluOp.VRGATHER; valid := true.B }
      is(0x0E.U) {
        // VRGATHEREI16 for VV (funct3=0), VSLIDEUP for VX(4)/VI(3)
        when(funct3 === 0.U) {
          op    := RvvAluOp.VRGATHEREI16
          valid := true.B
        }.otherwise {
          op    := RvvAluOp.VSLIDEUP
          valid := funct3 === 3.U || funct3 === 4.U
        }
      }
      is(0x0F.U) { op := RvvAluOp.VSLIDEDOWN; valid := funct3 === 3.U || funct3 === 4.U }
      // ADC/SBC group
      is(0x10.U) { op := RvvAluOp.VADC;   valid := vm === 0.U }  // requires mask
      is(0x11.U) { op := RvvAluOp.VMADC;  valid := true.B }
      is(0x12.U) { op := RvvAluOp.VSBC;   valid := vm === 0.U }  // requires mask
      is(0x13.U) { op := RvvAluOp.VMSBC;  valid := true.B }
      // VMERGE/VMV
      is(0x17.U) {
        when(vm === 0.U) {
          op    := RvvAluOp.VMERGE
          valid := true.B
        }.otherwise {
          op    := RvvAluOp.VMV
          valid := true.B
        }
      }
      // Compare ops
      is(0x18.U) { op := RvvAluOp.VMSEQ;  valid := true.B }
      is(0x19.U) { op := RvvAluOp.VMSNE;  valid := true.B }
      is(0x1A.U) { op := RvvAluOp.VMSLTU; valid := funct3 =/= 3.U }  // VV,VX only
      is(0x1B.U) { op := RvvAluOp.VMSLT;  valid := funct3 =/= 3.U }  // VV,VX only
      is(0x1C.U) { op := RvvAluOp.VMSLEU; valid := true.B }
      is(0x1D.U) { op := RvvAluOp.VMSLE;  valid := true.B }
      is(0x1E.U) { op := RvvAluOp.VMSGTU; valid := funct3 === 3.U || funct3 === 4.U }  // VX,VI only
      is(0x1F.U) { op := RvvAluOp.VMSGT;  valid := funct3 === 3.U || funct3 === 4.U }  // VX,VI only
      // Saturating arithmetic
      is(0x20.U) { op := RvvAluOp.VSADDU; valid := true.B }
      is(0x21.U) { op := RvvAluOp.VSADD;  valid := true.B }
      is(0x22.U) { op := RvvAluOp.VSSUBU; valid := funct3 =/= 3.U }
      is(0x23.U) { op := RvvAluOp.VSSUB;  valid := funct3 =/= 3.U }
      // VSLL, VSMUL/VMVnR
      is(0x25.U) { op := RvvAluOp.VSLL;   valid := true.B }
      is(0x27.U) {
        // VSMUL for VV/VX, VMVnR for VI
        when(funct3 === 3.U) {
          // VMVnR: distinguish by imm value
          val imm5 = vs1  // imm5 = [19:15]
          when(imm5 === 0.U)       { op := RvvAluOp.VMV1R; valid := true.B }
          .elsewhen(imm5 === 1.U)  { op := RvvAluOp.VMV2R; valid := true.B }
          .elsewhen(imm5 === 3.U)  { op := RvvAluOp.VMV4R; valid := true.B }
          .elsewhen(imm5 === 7.U)  { op := RvvAluOp.VMV8R; valid := true.B }
          .otherwise               { valid := false.B }
        }.otherwise {
          op    := RvvAluOp.VSMUL
          valid := funct3 === 0.U || funct3 === 4.U
        }
      }
      // Shift
      is(0x28.U) { op := RvvAluOp.VSRL;   valid := true.B }
      is(0x29.U) { op := RvvAluOp.VSRA;   valid := true.B }
      is(0x2A.U) { op := RvvAluOp.VSSRL;  valid := true.B }
      is(0x2B.U) { op := RvvAluOp.VSSRA;  valid := true.B }
      is(0x2C.U) { op := RvvAluOp.VNSRL;  valid := true.B }
      is(0x2D.U) { op := RvvAluOp.VNSRA;  valid := true.B }
      is(0x2E.U) { op := RvvAluOp.VNCLIPU; valid := true.B }
      is(0x2F.U) { op := RvvAluOp.VNCLIP; valid := true.B }
    }

    (valid, op)
  }
}

/** Decode a compressed RVV instruction (same as uncompressed for now). */
object RvvS1DecodeCompressedInstruction {
  def apply(c: RvvCompressedInstruction): Valid[RvvS1DecodedInstruction] = {
    RvvS1DecodeInstruction(c.inst)
  }
}
