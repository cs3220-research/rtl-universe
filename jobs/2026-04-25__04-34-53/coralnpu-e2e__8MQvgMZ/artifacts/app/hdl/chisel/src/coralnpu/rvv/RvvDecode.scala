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

package coralnpu.rvv

import chisel3._
import chisel3.util._

// funct3 values for RVV
object RvvFunct3 {
  val OPIVV = 0.U(3.W)  // vector-vector integer
  val OPFVV = 1.U(3.W)  // vector-vector float
  val OPMVV = 2.U(3.W)  // vector-vector mask/multiply
  val OPIVI = 3.U(3.W)  // vector-immediate
  val OPIVX = 4.U(3.W)  // vector-scalar integer
  val OPFVX = 5.U(3.W)  // vector-scalar float
  val OPMVX = 6.U(3.W)  // vector-scalar mask/multiply
  val OPCFG = 7.U(3.W)  // vector configuration (setvl*)
}

// Decode a 32-bit RISC-V instruction as an RVV instruction.
// Returns Valid(decoded) where valid=true iff it is an RVV instruction.
object RvvS1DecodeInstruction {
  def apply(inst: UInt): ValidIO[RvvS1DecodedInstruction] = {
    val out = Wire(Valid(new RvvS1DecodedInstruction()))

    // RVV opcode = 0x57 (VECTOR)
    val opcode  = inst(6, 0)
    val isRvv   = opcode === 0x57.U

    val vd      = inst(11, 7)
    val funct3  = inst(14, 12)
    val vs1_rs1 = inst(19, 15)
    val vs2     = inst(24, 20)
    val vm      = inst(25)           // 0=masked, 1=unmasked
    val funct6  = inst(31, 26)

    // SETVL* detection: funct3 = OPCFG(7)
    val isSetVl = isRvv && (funct3 === RvvFunct3.OPCFG)

    // Default all fields
    out.valid            := false.B
    out.bits.op          := RvvAluOp.VADD
    out.bits.vd          := vd
    out.bits.vs1         := vs1_rs1
    out.bits.vs2         := vs2
    out.bits.vm          := vm
    out.bits.imm         := Cat(vs1_rs1(4), vs1_rs1(4, 0)).asSInt
    out.bits.rs1         := vs1_rs1
    out.bits.funct3      := funct3
    out.bits.isMemory    := false.B
    out.bits.isLoad      := false.B
    out.bits.isSetVl     := false.B

    when (isRvv) {
      out.valid       := true.B
      out.bits.isSetVl := isSetVl

      when (isSetVl) {
        // VSETVL:   inst[31:30] = 10
        // VSETIVLI: inst[31:30] = 11
        // VSETVLI:  inst[31]    = 0
        when (inst(31, 30) === "b10".U) {
          out.bits.op := RvvAluOp.VSETVL
        } .elsewhen (inst(31, 30) === "b11".U) {
          out.bits.op := RvvAluOp.VSETIVLI
        } .otherwise {
          out.bits.op := RvvAluOp.VSETVLI
        }
      } .otherwise {
        // Main decode by funct6
        switch (funct6) {
          is (0x00.U) { out.bits.op := RvvAluOp.VADD }
          is (0x02.U) { out.bits.op := RvvAluOp.VSUB }
          is (0x03.U) { out.bits.op := RvvAluOp.VRSUB }
          is (0x04.U) { out.bits.op := RvvAluOp.VMINU }
          is (0x05.U) { out.bits.op := RvvAluOp.VMIN }
          is (0x06.U) { out.bits.op := RvvAluOp.VMAXU }
          is (0x07.U) { out.bits.op := RvvAluOp.VMAX }
          is (0x09.U) { out.bits.op := RvvAluOp.VAND }
          is (0x0a.U) { out.bits.op := RvvAluOp.VOR }
          is (0x0b.U) { out.bits.op := RvvAluOp.VXOR }
          is (0x0c.U) { out.bits.op := RvvAluOp.VRGATHER }
          is (0x0d.U) { out.bits.op := RvvAluOp.VSLIDEUP }
          is (0x0e.U) {
            // funct6=0x0e: VRGATHEREI16 when funct3=OPIVV(0), else VSLIDEUP
            when (funct3 === RvvFunct3.OPIVV) {
              out.bits.op := RvvAluOp.VRGATHEREI16
            } .otherwise {
              out.bits.op := RvvAluOp.VSLIDEUP
            }
          }
          is (0x0f.U) { out.bits.op := RvvAluOp.VSLIDEDOWN }
          is (0x10.U) { out.bits.op := RvvAluOp.VADC }
          is (0x11.U) { out.bits.op := RvvAluOp.VMADC }
          is (0x12.U) { out.bits.op := RvvAluOp.VSBC }
          is (0x13.U) { out.bits.op := RvvAluOp.VMSBC }
          is (0x17.U) {
            // funct6=0x17: VMERGE when masked (vm=0), VMV when unmasked (vm=1)
            when (vm) {
              out.bits.op := RvvAluOp.VMV
            } .otherwise {
              out.bits.op := RvvAluOp.VMERGE
            }
          }
          is (0x18.U) { out.bits.op := RvvAluOp.VMSEQ }
          is (0x19.U) { out.bits.op := RvvAluOp.VMSNE }
          is (0x1a.U) { out.bits.op := RvvAluOp.VMSLTU }
          is (0x1b.U) { out.bits.op := RvvAluOp.VMSLT }
          is (0x1c.U) { out.bits.op := RvvAluOp.VMSLEU }
          is (0x1d.U) { out.bits.op := RvvAluOp.VMSLE }
          is (0x1e.U) { out.bits.op := RvvAluOp.VMSGTU }
          is (0x1f.U) { out.bits.op := RvvAluOp.VMSGT }
          is (0x20.U) { out.bits.op := RvvAluOp.VSADDU }
          is (0x21.U) { out.bits.op := RvvAluOp.VSADD }
          is (0x22.U) { out.bits.op := RvvAluOp.VSSUBU }
          is (0x23.U) { out.bits.op := RvvAluOp.VSSUB }
          is (0x25.U) { out.bits.op := RvvAluOp.VSLL }
          is (0x27.U) {
            // funct6=0x27: VSMUL (vv/vx) or VMVxR (vi, based on imm/vs1)
            when (funct3 === RvvFunct3.OPIVI) {
              // VMV1R=imm0, VMV2R=imm1, VMV4R=imm3, VMV8R=imm7
              switch (vs1_rs1) {
                is (0.U) { out.bits.op := RvvAluOp.VMV1R }
                is (1.U) { out.bits.op := RvvAluOp.VMV2R }
                is (3.U) { out.bits.op := RvvAluOp.VMV4R }
                is (7.U) { out.bits.op := RvvAluOp.VMV8R }
              }
            } .otherwise {
              out.bits.op := RvvAluOp.VSMUL
            }
          }
          is (0x28.U) { out.bits.op := RvvAluOp.VSRL }
          is (0x29.U) { out.bits.op := RvvAluOp.VSRA }
          is (0x2a.U) { out.bits.op := RvvAluOp.VSSRL }
          is (0x2b.U) { out.bits.op := RvvAluOp.VSSRA }
          is (0x2c.U) { out.bits.op := RvvAluOp.VNSRL }
          is (0x2d.U) { out.bits.op := RvvAluOp.VNSRA }
          is (0x2e.U) { out.bits.op := RvvAluOp.VNCLIPU }
          is (0x2f.U) { out.bits.op := RvvAluOp.VNCLIP }
        }
      }
    }

    out
  }
}

// Decode a compressed RVV instruction (stored as full 32-bit encoding).
object RvvS1DecodeCompressedInstruction {
  def apply(c: RvvCompressedInstruction): ValidIO[RvvS1DecodedInstruction] = {
    RvvS1DecodeInstruction(c.inst)
  }
}
