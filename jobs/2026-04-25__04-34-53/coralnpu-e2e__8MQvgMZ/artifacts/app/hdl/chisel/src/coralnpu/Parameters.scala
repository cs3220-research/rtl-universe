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

package coralnpu

/** Top-level hardware parameters for the CoralNPU design.
  *
  * All fields are mutable vars so that test harnesses can override
  * individual parameters after construction without requiring a new
  * case-class instance for every combination.
  */
class Parameters {
  // ---------------------------------------------------------------------------
  // LSU / data-path width
  // ---------------------------------------------------------------------------
  /** Width (in bits) of the Load/Store Unit data bus.  Typical values: 32, 64, 128. */
  var lsuDataBits: Int = 32

  // ---------------------------------------------------------------------------
  // Fetch / instruction-path width
  // ---------------------------------------------------------------------------
  /** Width (in bits) of the instruction-fetch data bus. */
  var fetchDataBits: Int = 256

  // ---------------------------------------------------------------------------
  // Address space
  // ---------------------------------------------------------------------------
  /** Width (in bits) of physical addresses. */
  var addrBits: Int = 32

  // ---------------------------------------------------------------------------
  // AXI / TileLink ID widths
  // ---------------------------------------------------------------------------
  /** Number of AXI ID bits on the primary (host) AXI interface. */
  var axiIdBits: Int = 6

  /** Number of AXI ID bits on the secondary AXI-2 interface (e.g. DMA engine). */
  var axi2IdBits: Int = 4

  /** Number of TL-UL source-ID bits. */
  var tlulSourceWidth: Int = 4

  // ---------------------------------------------------------------------------
  // Memory sizes
  // ---------------------------------------------------------------------------
  /** Instruction-TCM size in bytes. */
  var itcmSizeBytes: Int = MemorySize.kbytes(64)

  /** Data-TCM size in bytes. */
  var dtcmSizeBytes: Int = MemorySize.kbytes(64)

  // ---------------------------------------------------------------------------
  // Optional feature enables
  // ---------------------------------------------------------------------------
  var enableFloat:       Boolean = false
  var enableRvv:         Boolean = false
  var enableVerification: Boolean = false
  var enableFetchL0:     Boolean = true
  var useAxi:            Boolean = false
  var useTlul:           Boolean = false

  // ---------------------------------------------------------------------------
  // Derived helpers
  // ---------------------------------------------------------------------------

  /** Data-bus width (bits) for the secondary AXI-2 interface; mirrors lsuDataBits. */
  def axi2DataBits: Int = lsuDataBits

  /** Address width (bits) for the secondary AXI-2 interface; mirrors addrBits. */
  def axi2AddrBits: Int = addrBits

  /** Byte width of the LSU data bus. */
  def lsuDataBytes: Int = lsuDataBits / 8

  /** log2 of the LSU data byte width (used for TLUL size field). */
  def lsuSizeWidth: Int = log2Ceil(lsuDataBytes + 1)

  /** Mask width for the LSU bus (one bit per byte lane). */
  def lsuMaskWidth: Int = lsuDataBytes

  private def log2Ceil(x: Int): Int = {
    require(x > 0)
    var r = 0; var v = x - 1
    while (v > 0) { r += 1; v >>= 1 }
    r
  }
}
