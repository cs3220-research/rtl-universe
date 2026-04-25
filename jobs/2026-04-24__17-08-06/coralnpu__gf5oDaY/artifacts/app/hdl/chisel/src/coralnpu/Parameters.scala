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

package coralnpu

// Core parameters for CoralNPU RTL generation.
class Parameters {
  val xlen: Int  = 32   // data path width
  val ilen: Int  = 32   // instruction width
  val nRegs: Int = 32   // number of integer registers
  val nFRegs: Int = 32  // number of float registers
  val addrBits: Int = 32

  // Memory sizes (in kilobytes)
  var itcmSizeKBytes: Int = 8   // MemorySize.ITCM_DEFAULT_KB
  var dtcmSizeKBytes: Int = 32  // MemorySize.DTCM_DEFAULT_KB

  // Pipeline / feature configuration
  var fetchDataBits: Int       = 256    // fetch bus width in bits
  var lsuDataBits: Int         = 256    // LSU data bus width in bits
  var enableFetchL0: Boolean   = false
  var enableFloat: Boolean     = true
  var enableRvv: Boolean       = false
  var enableVerification: Boolean = false

  // AXI
  val axiIdBits: Int = 4
  def axi2DataBits: Int = lsuDataBits  // AXI2 data bus width (same as LSU)
  var axi2IdBits: Int = 4              // AXI2 ID width (configurable)

  // Memory map base addresses
  val itcmBase: Long = 0x00000000L
  val dtcmBase: Long = 0x00010000L
  val csrBase:  Long = 0x00030000L
}

object Parameters {
  def apply(): Parameters = new Parameters
}
