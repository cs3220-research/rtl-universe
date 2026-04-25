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

package coralnpu.float

import chisel3._
import chisel3.util._
import coralnpu.Parameters
import common.Fp32

/** Interface between scalar core and floating-point unit. */
class FloatIssueIO extends Bundle {
  val inst  = UInt(32.W)
  val rs1   = new Fp32
  val rs2   = new Fp32
  val rs3   = new Fp32
  val rs1i  = UInt(32.W)  // integer source
}

class FloatCompleteIO extends Bundle {
  val rd    = UInt(5.W)
  val data  = new Fp32
  val idata = UInt(32.W)  // integer result
}

class FloatCoreInterface(p: Parameters) extends Bundle {
  val issue    = Flipped(Decoupled(new FloatIssueIO))
  val complete = Valid(new FloatCompleteIO)
}
