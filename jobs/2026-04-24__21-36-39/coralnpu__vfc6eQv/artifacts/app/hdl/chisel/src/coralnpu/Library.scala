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

import chisel3._
import chisel3.util._

/** Integer log2 ceiling (Scala compile-time). */
object CLog2 {
  def apply(x: Int): Int = math.ceil(math.log(x) / math.log(2)).toInt
}

/** Next power-of-two >= x. */
object NextPow2 {
  def apply(x: Int): Int = {
    var n = 1
    while (n < x) n *= 2
    n
  }
}

/** Align an address down to a power-of-two boundary. */
object AlignDown {
  /** Hardware: align addr down to `alignBytes` boundary (must be power of two). */
  def apply(addr: UInt, alignBytes: Int): UInt = {
    require(alignBytes > 0 && (alignBytes & (alignBytes - 1)) == 0)
    (addr >> log2Ceil(alignBytes).U) << log2Ceil(alignBytes).U
  }
}
