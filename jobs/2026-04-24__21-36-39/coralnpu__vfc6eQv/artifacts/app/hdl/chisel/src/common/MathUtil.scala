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

package common

import chisel3._

/** Clamp: clamps value to [min, max]. */
object Clamp {
  def apply(value: UInt, min: UInt, max: UInt): UInt = {
    Mux(value < min, min, Mux(value > max, max, value))
  }
}

/** Saturating add for UInt. */
object SatAdd {
  def apply(a: UInt, b: UInt, maxVal: UInt): UInt = {
    val result = a +& b
    Mux(result > maxVal, maxVal, result(a.getWidth - 1, 0))
  }
}

/** Saturating sub for UInt. */
object SatSub {
  def apply(a: UInt, b: UInt): UInt = {
    Mux(a > b, a - b, 0.U)
  }
}

/** Log2 ceiling for power-of-2 detection. */
object Log2Ceil {
  def apply(n: Int): Int = {
    require(n > 0)
    if (n == 1) 0
    else math.ceil(math.log(n) / math.log(2)).toInt
  }
}
