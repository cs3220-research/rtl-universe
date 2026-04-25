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

/** Math utility functions. */
object MathUtil {
  /** Ceiling log2. Returns ceil(log2(x)). */
  def clog2(x: Int): Int = log2Ceil(x)

  /** Returns true if x is a power of two. */
  def isPow2(x: Int): Boolean = x > 0 && (x & (x - 1)) == 0

  /** Sign-extend a UInt of `fromWidth` to `toWidth` bits. */
  def signExtend(x: UInt, fromWidth: Int, toWidth: Int): SInt = {
    require(toWidth >= fromWidth)
    x(fromWidth - 1, 0).asSInt.pad(toWidth)
  }
}
