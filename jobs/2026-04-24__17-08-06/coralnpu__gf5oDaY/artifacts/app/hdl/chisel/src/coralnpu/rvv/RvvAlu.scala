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
import coralnpu.Parameters

/** RVV ALU operation codes. */
object RvvAluOp extends ChiselEnum {
  // Arithmetic
  val VADD, VSUB, VRSUB, VMINU, VMIN, VMAXU, VMAX = Value
  // Logical
  val VAND, VOR, VXOR = Value
  // Gather/scatter/slide
  val VRGATHER, VRGATHEREI16, VSLIDEUP, VSLIDEDOWN = Value
  // Carry operations
  val VADC, VMADC, VSBC, VMSBC = Value
  // Merge/move
  val VMERGE, VMV = Value
  // Compare (mask-producing)
  val VMSEQ, VMSNE, VMSLTU, VMSLT, VMSLEU, VMSLE, VMSGTU, VMSGT = Value
  // Saturating arithmetic
  val VSADDU, VSADD, VSSUBU, VSSUB = Value
  // Saturating multiply
  val VSMUL = Value
  // Shift
  val VSLL, VSRL, VSRA, VSSRL, VSSRA = Value
  // Narrowing
  val VNSRL, VNSRA, VNCLIPU, VNCLIP = Value
  // Move multiple registers
  val VMV1R, VMV2R, VMV4R, VMV8R = Value
  // Multiply (backend)
  val VMUL, VMULH, VMULHU, VMULHSU = Value
  // Integer divide
  val VDIV, VDIVU, VREM, VREMU = Value
  // Widening
  val VWMUL, VWMULU, VWMULSU = Value
  // MAC
  val VMACC, VNMSAC, VMADD, VNMSUB = Value
  // Widening MAC
  val VWMACC, VWMACCU, VWMACCSU, VWMACCUS = Value
  // Reduction
  val VREDSUM, VREDAND, VREDOR, VREDXOR = Value
  val VREDMINU, VREDMIN, VREDMAXU, VREDMAX = Value
  val VWREDSUMU, VWREDSUM = Value
  // Mask logic
  val VMAND, VMNAND, VMANDNOT, VMXOR, VMOR, VMNOR, VMORNOT, VMXNOR = Value
  // Mask pop
  val VCPOP, VFIRST = Value
  // Mask set-before-first, etc.
  val VMSBF, VMSIF, VMSOF = Value
  // Iota, ID
  val VIOTA, VID = Value
  // Compress, permutation
  val VCOMPRESS, VMVR = Value
  // Fixed
  val FIXED = Value
}

/** RVV ALU stub module (actual computation in Verilog backend). */
class RvvAlu(p: Parameters) extends Module {
  val io = IO(new Bundle {
    val cmd   = Flipped(Decoupled(new RvvS1DecodedInstruction))
    val done  = Output(Bool())
    val busy  = Output(Bool())
  })

  io.cmd.ready := true.B
  io.done      := false.B
  io.busy      := false.B
}
