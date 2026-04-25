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
import coralnpu.Parameters
import common.Fp32

/** FloatCore interface bundles. */

class FloatCoreCmd(p: Parameters) extends Bundle {
  val op    = UInt(5.W)
  val waddr = UInt(5.W)
  val ina   = new Fp32
  val inb   = new Fp32
  val inc   = new Fp32
}

class FloatCoreResult(p: Parameters) extends Bundle {
  val addr = UInt(5.W)
  val data = new Fp32
}
