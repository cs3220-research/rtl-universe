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

// RVV ALU operation codes
object RvvAluOp extends ChiselEnum {
  val VADD, VSUB, VRSUB, VMINU, VMIN, VMAXU, VMAX,
      VAND, VOR, VXOR,
      VRGATHER, VRGATHEREI16, VSLIDEUP, VSLIDEDOWN,
      VADC, VMADC, VSBC, VMSBC, VMERGE, VMV,
      VMSEQ, VMSNE, VMSLTU, VMSLT, VMSLEU, VMSLE, VMSGTU, VMSGT,
      VSADDU, VSADD, VSSUBU, VSSUB, VSMUL,
      VMV1R, VMV2R, VMV4R, VMV8R,
      VSLL, VSRL, VSRA, VSSRL, VSSRA, VNSRL, VNSRA, VNCLIPU, VNCLIP,
      VLE8, VLE16, VLE32, VSE8, VSE16, VSE32,
      VSETVL, VSETVLI, VSETIVLI = Value
}

// Decoded RVV instruction from stage 1
class RvvS1DecodedInstruction extends Bundle {
  val op       = RvvAluOp()
  val vd       = UInt(5.W)
  val vs1      = UInt(5.W)
  val vs2      = UInt(5.W)
  val vm       = Bool()        // mask enabled (vm=0 means mask active in encoding)
  val imm      = SInt(6.W)     // immediate (sign-extended from 5 bits)
  val rs1      = UInt(5.W)     // scalar register index
  val funct3   = UInt(3.W)     // vv/vx/vi encoding
  val isMemory = Bool()
  val isLoad   = Bool()
  val isSetVl  = Bool()
}

// Compressed RVV instruction (stores full 32-bit encoding for simplicity)
class RvvCompressedInstruction extends Bundle {
  val inst = UInt(32.W)
  val idx  = UInt(4.W)
}

object RvvCompressedInstruction {
  // Create a compressed instruction from an uncompressed 32-bit instruction
  def from_uncompressed(inst: UInt, idx: UInt): RvvCompressedInstruction = {
    val c = Wire(new RvvCompressedInstruction)
    c.inst := inst
    c.idx  := idx
    c
  }
}
