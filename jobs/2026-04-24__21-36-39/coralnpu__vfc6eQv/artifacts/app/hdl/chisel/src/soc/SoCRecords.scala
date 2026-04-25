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

package coralnpu.soc

import coralnpu.Parameters

/** Describes a single memory region in the SoC address map. */
case class SoCMemoryRegion(
  name:      String,
  baseAddr:  Long,
  sizeBytes: Long
)

/** Top-level SoC configuration record. */
case class SoCConfig(
  itcmSizeKBytes: Int = 8,
  dtcmSizeKBytes: Int = 32,
  csrBaseAddr:    Long = 0x00200000L,
  enableTestHarness: Boolean = false,
  enableRvv:      Boolean = false,
  enableFloat:    Boolean = false,
  enableVerification: Boolean = false,
  moduleName:     String = "CoralNPUChiselSubsystem"
) {
  def itcmSizeBytes: Long = itcmSizeKBytes.toLong * 1024L
  def dtcmSizeBytes: Long = dtcmSizeKBytes.toLong * 1024L

  def memoryMap: Seq[SoCMemoryRegion] = Seq(
    SoCMemoryRegion("itcm", 0x00000000L, itcmSizeBytes),
    SoCMemoryRegion("dtcm", 0x00010000L, dtcmSizeBytes),
    SoCMemoryRegion("csr",  csrBaseAddr, 0x1000L),
  )
}

/** SoC parameters derived from command-line flags. */
case class SoCParams(
  itcmSizeKBytes:    Int     = 8,
  dtcmSizeKBytes:    Int     = 32,
  enableTestHarness: Boolean = false,
  moduleName:        String  = "CoralNPUChiselSubsystem"
)
