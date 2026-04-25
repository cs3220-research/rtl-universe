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
import chisel3.util._

/** Scala-land math utilities (elaboration time only, not hardware). */
object MathUtil {
  /** Round n up to the nearest multiple of m. */
  def roundUpTo(n: Int, m: Int): Int = {
    require(m > 0, "m must be positive")
    ((n + m - 1) / m) * m
  }

  /** Integer division rounded up: ceil(a / b). */
  def divRoundUp(a: Int, b: Int): Int = {
    require(b > 0, "b must be positive")
    (a + b - 1) / b
  }

  /** Minimum of two integers. */
  def min(a: Int, b: Int): Int = if (a < b) a else b

  /** Maximum of two integers. */
  def max(a: Int, b: Int): Int = if (a > b) a else b

  /** Clamp v to the range [lo, hi]. */
  def clamp(v: Int, lo: Int, hi: Int): Int = {
    require(lo <= hi, "lo must be <= hi")
    if (v < lo) lo
    else if (v > hi) hi
    else v
  }
}
