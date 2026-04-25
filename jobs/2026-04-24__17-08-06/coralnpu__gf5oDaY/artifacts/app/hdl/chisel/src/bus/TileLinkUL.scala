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

package bus

import chisel3._
import chisel3.util._
import coralnpu.Parameters

/** TileLink-UL parameters. */
class TLULParameters(p: Parameters) {
  val addrBits: Int = p.addrBits
  val dataBits: Int = p.lsuDataBits
  val sourceBits: Int = 4
  val sinkBits: Int = 1
  val sizeBits: Int = 4
}

/** TileLink-UL opcodes for channel A. */
object TLULOpcodesA extends ChiselEnum {
  val PutFullData, PutPartialData, Get, ArithmeticData, LogicalData = Value
}

/** TileLink-UL opcodes for channel D. */
object TLULOpcodesD extends ChiselEnum {
  val AccessAck, AccessAckData = Value
}

/** Channel A message bundle. */
class TLULChannelA(p: TLULParameters) extends Bundle {
  val opcode  = UInt(3.W)
  val param   = UInt(3.W)
  val size    = UInt(p.sizeBits.W)
  val source  = UInt(p.sourceBits.W)
  val address = UInt(p.addrBits.W)
  val mask    = UInt((p.dataBits / 8).W)
  val data    = UInt(p.dataBits.W)
  val corrupt = Bool()
}

/** Channel D message bundle. */
class TLULChannelD(p: TLULParameters) extends Bundle {
  val opcode  = UInt(3.W)
  val param   = UInt(2.W)
  val size    = UInt(p.sizeBits.W)
  val source  = UInt(p.sourceBits.W)
  val sink    = UInt(p.sinkBits.W)
  val denied  = Bool()
  val corrupt = Bool()
  val data    = UInt(p.dataBits.W)
  val error   = Bool()  // OpenTitan-style error bit
}

/** Host-to-Device TileLink bundle. */
object OpenTitanTileLink {
  class Host2Device(p: TLULParameters) extends Bundle {
    val a = Decoupled(new TLULChannelA(p))
    val d = Flipped(Decoupled(new TLULChannelD(p)))
  }

  /** Type alias for Channel A message bundle. */
  class A_Channel(p: TLULParameters) extends TLULChannelA(p)

  /** Type alias for Channel D message bundle. */
  class D_Channel(p: TLULParameters) extends TLULChannelD(p)
}
