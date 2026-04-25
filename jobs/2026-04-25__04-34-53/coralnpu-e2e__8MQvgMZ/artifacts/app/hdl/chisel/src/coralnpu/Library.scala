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

object Library {
  def SignExtend(value: UInt, fromBit: Int): UInt = {
    val sign = value(fromBit)
    Cat(Fill(32 - fromBit - 1, sign), value(fromBit, 0))
  }

  def log2Ceil(x: Int): Int = {
    require(x >= 1)
    if (x == 1) 0 else scala.math.ceil(scala.math.log(x) / scala.math.log(2)).toInt
  }

  def BitsToBytes(bits: Int): Int = (bits + 7) / 8
  def BytesToBits(bytes: Int): Int = bytes * 8
}
