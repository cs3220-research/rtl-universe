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

// RVV ALU operation codes - all operations tested in RvvDecodeTest
object RvvAluOp extends ChiselEnum {
  val VADD, VSUB, VRSUB, VMINU, VMIN, VMAXU, VMAX,
      VAND, VOR, VXOR,
      VRGATHER, VRGATHEREI16, VSLIDEUP, VSLIDEDOWN,
      VADC, VMADC, VSBC, VMSBC, VMERGE, VMV,
      VMSEQ, VMSNE, VMSLTU, VMSLT, VMSLEU, VMSLE, VMSGTU, VMSGT,
      VSADDU, VSADD, VSSUBU, VSSUB, VSMUL,
      VMV1R, VMV2R, VMV4R, VMV8R,
      VSLL, VSRL, VSRA, VSSRL, VSSRA,
      VNSRL, VNSRA, VNCLIPU, VNCLIP = Value
}

// A decoded RVV instruction (output of Stage 1 decode)
class RvvS1DecodedInstruction extends Bundle {
  val op      = RvvAluOp()
  val vd      = UInt(5.W)   // dest register
  val vs1     = UInt(5.W)   // source register 1
  val vs2     = UInt(5.W)   // source register 2
  val vm      = Bool()      // mask bit (1=unmasked, 0=masked)
  val funct3  = UInt(3.W)
  val funct6  = UInt(6.W)
}

// A "compressed" RVV instruction representation
class RvvCompressedInstruction extends Bundle {
  val inst    = UInt(32.W)  // full instruction encoding
  val offset  = UInt(4.W)   // offset within fetch bundle
}

object RvvCompressedInstruction {
  def from_uncompressed(inst: UInt, offset: UInt): RvvCompressedInstruction = {
    val out = Wire(new RvvCompressedInstruction)
    out.inst   := inst
    out.offset := offset
    out
  }
}
