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

import coralnpu.{Parameters, MemorySize}

/** Chisel-level SoC configuration: parsed from command-line args and
  * used to instantiate the right hardware variant.
  */
class SoCChiselConfig(args: Array[String]) {
  var itcmSizeKBytes:    Int     = 8
  var dtcmSizeKBytes:    Int     = 32
  var enableTestHarness: Boolean = false
  var moduleName:        String  = "CoralNPUChiselSubsystem"

  // Parse arguments
  var i = 0
  while (i < args.length) {
    args(i) match {
      case "--itcmSizeKBytes" if i + 1 < args.length =>
        itcmSizeKBytes = args(i + 1).toInt; i += 2
      case "--dtcmSizeKBytes" if i + 1 < args.length =>
        dtcmSizeKBytes = args(i + 1).toInt; i += 2
      case "--enableTestHarness" =>
        enableTestHarness = true; i += 1
      case "--moduleName" if i + 1 < args.length =>
        moduleName = args(i + 1); i += 2
      case other =>
        // Ignore unknown flags
        i += 1
    }
  }

  def toParameters: Parameters = {
    val p = new Parameters
    p.itcmSizeKBytes = itcmSizeKBytes
    p.dtcmSizeKBytes = dtcmSizeKBytes
    p
  }
}
