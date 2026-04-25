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

/** Interface between the scalar core and the RVV (vector) coprocessor. */
class RvvIssueIO(p: Parameters) extends Bundle {
  val inst   = UInt(32.W)
  val rs1    = UInt(32.W)
  val rs2    = UInt(32.W)
  val vstart = UInt(32.W)
  val vl     = UInt(32.W)
}

class RvvCompleteIO(p: Parameters) extends Bundle {
  val rd    = UInt(32.W)
  val rdVal = UInt(32.W)
}

class RvvInterface(p: Parameters) extends Bundle {
  val issue    = Flipped(Decoupled(new RvvIssueIO(p)))
  val complete = Valid(new RvvCompleteIO(p))
}
