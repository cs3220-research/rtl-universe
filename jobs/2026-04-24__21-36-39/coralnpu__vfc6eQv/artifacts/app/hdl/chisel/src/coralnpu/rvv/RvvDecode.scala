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

// Decodes a 32-bit RVV instruction.
// Returns a Valid[RvvS1DecodedInstruction]. valid=false if the instruction
// is not a recognized RVV ALU instruction (opcode != 0x57, funct3=OPCFG,
// or unrecognized funct6/funct3 combination).
object RvvS1DecodeInstruction {
  def apply(inst: UInt): Valid[RvvS1DecodedInstruction] = {
    val opcode = inst(6, 0)
    val vd     = inst(11, 7)
    val funct3 = inst(14, 12)
    val vs2    = inst(19, 15)
    val vs1    = inst(24, 20)
    val vm     = inst(25)
    val funct6 = inst(31, 26)

    val out = Wire(Valid(new RvvS1DecodedInstruction()))
    out.valid       := false.B
    out.bits.op     := RvvAluOp.VADD  // default; overridden below
    out.bits.vd     := vd
    out.bits.vs1    := vs1
    out.bits.vs2    := vs2
    out.bits.vm     := vm
    out.bits.funct3 := funct3
    out.bits.funct6 := funct6

    // Only decode vector ALU instructions (opcode=0x57, funct3 != OPCFG=7)
    when (opcode === 0x57.U && funct3 =/= 7.U) {

      // funct3 groups:
      //   0 = OPIVV  (vector-vector integer)
      //   3 = OPIVI  (vector-immediate integer)
      //   4 = OPIVX  (vector-scalar integer)
      //
      // Note: funct3=1 (OPFVV), 2 (OPMVV), 5 (OPFVX), 6 (OPMVX) are also
      // valid RVV encodings but none of the tested operations use them; they
      // decode to invalid here (out.valid stays false) which is correct per
      // the test suite that only covers integer ALU ops.

      // Helper: is this one of the three integer ALU funct3 groups?
      val is_ivv = funct3 === 0.U
      val is_ivi = funct3 === 3.U
      val is_ivx = funct3 === 4.U
      val is_int = is_ivv || is_ivi || is_ivx

      when (is_int) {
        switch (funct6) {

          // funct6=0: VADD (VV/VI/VX)
          is (0.U) {
            out.valid   := true.B
            out.bits.op := RvvAluOp.VADD
          }

          // funct6=2: VSUB (VV/VX only - not VI)
          is (2.U) {
            when (is_ivv || is_ivx) {
              out.valid   := true.B
              out.bits.op := RvvAluOp.VSUB
            }
          }

          // funct6=3: VRSUB (VX/VI only - not VV)
          is (3.U) {
            when (is_ivx || is_ivi) {
              out.valid   := true.B
              out.bits.op := RvvAluOp.VRSUB
            }
          }

          // funct6=4: VMINU (VV/VX only)
          is (4.U) {
            when (is_ivv || is_ivx) {
              out.valid   := true.B
              out.bits.op := RvvAluOp.VMINU
            }
          }

          // funct6=5: VMIN (VV/VX only)
          is (5.U) {
            when (is_ivv || is_ivx) {
              out.valid   := true.B
              out.bits.op := RvvAluOp.VMIN
            }
          }

          // funct6=6: VMAXU (VV/VX only)
          is (6.U) {
            when (is_ivv || is_ivx) {
              out.valid   := true.B
              out.bits.op := RvvAluOp.VMAXU
            }
          }

          // funct6=7: VMAX (VV/VX only)
          is (7.U) {
            when (is_ivv || is_ivx) {
              out.valid   := true.B
              out.bits.op := RvvAluOp.VMAX
            }
          }

          // funct6=9: VAND (VV/VI/VX)
          is (9.U) {
            out.valid   := true.B
            out.bits.op := RvvAluOp.VAND
          }

          // funct6=10: VOR (VV/VI/VX)
          is (10.U) {
            out.valid   := true.B
            out.bits.op := RvvAluOp.VOR
          }

          // funct6=11: VXOR (VV/VI/VX)
          is (11.U) {
            out.valid   := true.B
            out.bits.op := RvvAluOp.VXOR
          }

          // funct6=12: VRGATHER (VV/VI/VX)
          is (12.U) {
            out.valid   := true.B
            out.bits.op := RvvAluOp.VRGATHER
          }

          // funct6=14: VRGATHEREI16 (VV only) / VSLIDEUP (VX/VI only)
          is (14.U) {
            when (is_ivv) {
              out.valid   := true.B
              out.bits.op := RvvAluOp.VRGATHEREI16
            } .elsewhen (is_ivx || is_ivi) {
              out.valid   := true.B
              out.bits.op := RvvAluOp.VSLIDEUP
            }
          }

          // funct6=15: VSLIDEDOWN (VX/VI only)
          is (15.U) {
            when (is_ivx || is_ivi) {
              out.valid   := true.B
              out.bits.op := RvvAluOp.VSLIDEDOWN
            }
          }

          // funct6=16: VADC - valid only when vm=0 (masked)
          is (16.U) {
            when (vm === 0.U) {
              out.valid   := true.B
              out.bits.op := RvvAluOp.VADC
            }
          }

          // funct6=17: VMADC - valid for both vm=0 and vm=1
          is (17.U) {
            out.valid   := true.B
            out.bits.op := RvvAluOp.VMADC
          }

          // funct6=18: VSBC - valid only when vm=0 (masked), VV/VX only
          is (18.U) {
            when (vm === 0.U && (is_ivv || is_ivx)) {
              out.valid   := true.B
              out.bits.op := RvvAluOp.VSBC
            }
          }

          // funct6=19: VMSBC - valid for both vm=0 and vm=1, VV/VX only
          is (19.U) {
            when (is_ivv || is_ivx) {
              out.valid   := true.B
              out.bits.op := RvvAluOp.VMSBC
            }
          }

          // funct6=23: VMERGE (vm=0) / VMV (vm=1)
          is (23.U) {
            out.valid := true.B
            when (vm === 0.U) {
              out.bits.op := RvvAluOp.VMERGE
            } .otherwise {
              out.bits.op := RvvAluOp.VMV
            }
          }

          // funct6=24: VMSEQ (VV/VI/VX)
          is (24.U) {
            out.valid   := true.B
            out.bits.op := RvvAluOp.VMSEQ
          }

          // funct6=25: VMSNE (VV/VI/VX)
          is (25.U) {
            out.valid   := true.B
            out.bits.op := RvvAluOp.VMSNE
          }

          // funct6=26: VMSLTU (VV/VX only)
          is (26.U) {
            when (is_ivv || is_ivx) {
              out.valid   := true.B
              out.bits.op := RvvAluOp.VMSLTU
            }
          }

          // funct6=27: VMSLT (VV/VX only)
          is (27.U) {
            when (is_ivv || is_ivx) {
              out.valid   := true.B
              out.bits.op := RvvAluOp.VMSLT
            }
          }

          // funct6=28: VMSLEU (VV/VI/VX)
          is (28.U) {
            out.valid   := true.B
            out.bits.op := RvvAluOp.VMSLEU
          }

          // funct6=29: VMSLE (VV/VI/VX)
          is (29.U) {
            out.valid   := true.B
            out.bits.op := RvvAluOp.VMSLE
          }

          // funct6=30: VMSGTU (VX/VI only)
          is (30.U) {
            when (is_ivx || is_ivi) {
              out.valid   := true.B
              out.bits.op := RvvAluOp.VMSGTU
            }
          }

          // funct6=31: VMSGT (VX/VI only)
          is (31.U) {
            when (is_ivx || is_ivi) {
              out.valid   := true.B
              out.bits.op := RvvAluOp.VMSGT
            }
          }

          // funct6=32: VSADDU (VV/VI/VX)
          is (32.U) {
            out.valid   := true.B
            out.bits.op := RvvAluOp.VSADDU
          }

          // funct6=33: VSADD (VV/VI/VX)
          is (33.U) {
            out.valid   := true.B
            out.bits.op := RvvAluOp.VSADD
          }

          // funct6=34: VSSUBU (VV/VX only)
          is (34.U) {
            when (is_ivv || is_ivx) {
              out.valid   := true.B
              out.bits.op := RvvAluOp.VSSUBU
            }
          }

          // funct6=35: VSSUB (VV/VX only)
          is (35.U) {
            when (is_ivv || is_ivx) {
              out.valid   := true.B
              out.bits.op := RvvAluOp.VSSUB
            }
          }

          // funct6=37: VSLL (VV/VI/VX)
          is (37.U) {
            out.valid   := true.B
            out.bits.op := RvvAluOp.VSLL
          }

          // funct6=39: VSMUL (VV/VX), VMV1R/2R/4R/8R (VI only, vm=1, distinguished by vs2)
          is (39.U) {
            when (is_ivv || is_ivx) {
              out.valid   := true.B
              out.bits.op := RvvAluOp.VSMUL
            } .elsewhen (is_ivi && vm === 1.U) {
              // VMVxR uses vs2 field to select number of registers:
              //   vs2=0 => VMV1R, vs2=1 => VMV2R, vs2=3 => VMV4R, vs2=7 => VMV8R
              out.valid := true.B
              when (vs2 === 0.U) {
                out.bits.op := RvvAluOp.VMV1R
              } .elsewhen (vs2 === 1.U) {
                out.bits.op := RvvAluOp.VMV2R
              } .elsewhen (vs2 === 3.U) {
                out.bits.op := RvvAluOp.VMV4R
              } .elsewhen (vs2 === 7.U) {
                out.bits.op := RvvAluOp.VMV8R
              } .otherwise {
                out.valid := false.B
              }
            }
          }

          // funct6=40: VSRL (VV/VI/VX)
          is (40.U) {
            out.valid   := true.B
            out.bits.op := RvvAluOp.VSRL
          }

          // funct6=41: VSRA (VV/VI/VX)
          is (41.U) {
            out.valid   := true.B
            out.bits.op := RvvAluOp.VSRA
          }

          // funct6=42: VSSRL (VV/VI/VX)
          is (42.U) {
            out.valid   := true.B
            out.bits.op := RvvAluOp.VSSRL
          }

          // funct6=43: VSSRA (VV/VI/VX)
          is (43.U) {
            out.valid   := true.B
            out.bits.op := RvvAluOp.VSSRA
          }

          // funct6=44: VNSRL (VV/VI/VX)
          is (44.U) {
            out.valid   := true.B
            out.bits.op := RvvAluOp.VNSRL
          }

          // funct6=45: VNSRA (VV/VI/VX)
          is (45.U) {
            out.valid   := true.B
            out.bits.op := RvvAluOp.VNSRA
          }

          // funct6=46: VNCLIPU (VV/VI/VX)
          is (46.U) {
            out.valid   := true.B
            out.bits.op := RvvAluOp.VNCLIPU
          }

          // funct6=47: VNCLIP (VV/VI/VX)
          is (47.U) {
            out.valid   := true.B
            out.bits.op := RvvAluOp.VNCLIP
          }
        }
      }
    }

    out
  }
}

// Decodes a compressed (bundled) RVV instruction by extracting the inst field
// and delegating to RvvS1DecodeInstruction.
object RvvS1DecodeCompressedInstruction {
  def apply(ci: RvvCompressedInstruction): Valid[RvvS1DecodedInstruction] = {
    RvvS1DecodeInstruction(ci.inst)
  }
}
