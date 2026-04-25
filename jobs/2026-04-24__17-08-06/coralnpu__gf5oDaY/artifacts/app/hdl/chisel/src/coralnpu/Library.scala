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

import chisel3._
import chisel3.util._

/** Utility to count leading zeros for a UInt of given width. */
object CountLeadingZeros {
  def apply(x: UInt, width: Int): UInt = {
    val result = Wire(UInt((log2Ceil(width) + 1).W))
    result := width.U
    for (i <- 0 until width) {
      when(x(width - 1 - i)) {
        result := i.U
      }
    }
    result
  }
}

/** Utility to count trailing zeros. */
object CountTrailingZeros {
  def apply(x: UInt, width: Int): UInt = {
    val result = Wire(UInt((log2Ceil(width) + 1).W))
    result := width.U
    for (i <- (width - 1) to 0 by -1) {
      when(x(i)) {
        result := i.U
      }
    }
    result
  }
}

/** Count population (number of set bits). */
object CountOnes {
  def apply(x: UInt, width: Int): UInt = {
    val bits = (0 until width).map(i => x(i).asUInt)
    bits.reduce(_ +& _)
  }
}
