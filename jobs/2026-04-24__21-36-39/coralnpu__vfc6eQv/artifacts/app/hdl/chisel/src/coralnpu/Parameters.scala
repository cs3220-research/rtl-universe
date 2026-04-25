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

/** Parameters for the CoralNPU processor core.
  *
  * These are mutable so that tests and generator flags can override defaults.
  */
class Parameters {
  // Data bus widths
  var lsuDataBits: Int = 128    // LSU / vector data bus width in bits (128 or 256)
  var fetchDataBits: Int = 256  // Instruction fetch bus width in bits (256 = 8x32-bit instructions)

  // AXI2 master port (wide, for DRAM/TCM DMA)
  var axi2DataBits: Int = 256   // AXI2 master data bus width in bits
  var axi2IdBits: Int = 4       // AXI2 master ID field width
  var axi2AddrBits: Int = 32    // AXI2 address width

  // CSR / narrow AXI port
  var csrDataBits: Int = 64     // CSR AXI data bus width in bits

  // Memory sizes
  var itcmSizeKBytes: Int = 8   // Instruction TCM size in KiB
  var dtcmSizeKBytes: Int = 32  // Data TCM size in KiB

  // Optional features
  var enableFloat: Boolean = false     // Enable scalar FPU (F extension)
  var enableRvv: Boolean = false       // Enable RVV (Zve32x)
  var enableFetchL0: Boolean = true    // Enable L0 instruction cache
  var enableVerification: Boolean = false  // Enable RVVI trace port

  // Pipeline / microarchitecture
  var numSlots: Int = 4         // Number of issue/retirement slots
  var moduleName: String = "Core"  // Top-level module name for emission
  var useAxi: Boolean = false   // Expose AXI master port (vs TL-UL)
  var useTlul: Boolean = false  // Expose TL-UL master port

  def copy(): Parameters = {
    val p = new Parameters
    p.lsuDataBits        = lsuDataBits
    p.fetchDataBits      = fetchDataBits
    p.axi2DataBits       = axi2DataBits
    p.axi2IdBits         = axi2IdBits
    p.axi2AddrBits       = axi2AddrBits
    p.csrDataBits        = csrDataBits
    p.itcmSizeKBytes     = itcmSizeKBytes
    p.dtcmSizeKBytes     = dtcmSizeKBytes
    p.enableFloat        = enableFloat
    p.enableRvv          = enableRvv
    p.enableFetchL0      = enableFetchL0
    p.enableVerification = enableVerification
    p.numSlots           = numSlots
    p.moduleName         = moduleName
    p.useAxi             = useAxi
    p.useTlul            = useTlul
    p
  }
}
