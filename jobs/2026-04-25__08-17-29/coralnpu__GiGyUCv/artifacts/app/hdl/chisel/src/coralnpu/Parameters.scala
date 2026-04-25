// Copyright 2026 Google LLC
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

package coralnpu

class Parameters {
  // AXI parameters
  var axiAddrBits: Int = 32
  var axiDataBits: Int = 64
  var axiIdBits: Int   = 6

  // LSU / bus-width parameters
  var lsuDataBits: Int = 128

  // Secondary AXI port (wider data, more IDs)
  var axi2DataBits: Int = 256
  var axi2IdBits: Int   = 6

  // Instruction fetch width
  var fetchWidth: Int = 4

  // Number of hardware threads / cores
  var numHarts: Int = 1

  // Physical memory protection
  var pmpRegions: Int = 8

  // RISC-V ISA extensions
  var useM: Boolean = true
  var useA: Boolean = true
  var useF: Boolean = true
  var useD: Boolean = false
  var useV: Boolean = false
}
