// Copyright 2026 Google LLC
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

/** TileLink-UL (Uncached Lightweight) protocol parameters. */
class TLULParameters(p: Parameters) {
  val dataBits   : Int = p.lsuDataBits   // data bus width (default 128)
  val addrBits   : Int = 32              // address width
  val sourceBits : Int = p.axi2IdBits    // source/transaction ID width
  val sinkBits   : Int = 1               // sink ID width
  val sizeBits   : Int = 4               // size field width (log2 of bytes)
  val maskBits   : Int = dataBits / 8    // byte mask width
}

/** User sideband on channel A (for integrity). */
class OpenTitanTileLink_A_User extends Bundle {
  val instr_type = UInt(1.W)
  val cmd_intg   = UInt(7.W)
  val data_intg  = UInt(7.W)
}

/** User sideband on channel D (for integrity). */
class OpenTitanTileLink_D_User extends Bundle {
  val rsp_intg  = UInt(7.W)
  val data_intg = UInt(7.W)
}

/** TileLink-UL Channel A (request from host to device). */
class TLULChannelA(p: TLULParameters) extends Bundle {
  val opcode  = UInt(3.W)
  val param   = UInt(3.W)
  val size    = UInt(p.sizeBits.W)
  val source  = UInt(p.sourceBits.W)
  val address = UInt(p.addrBits.W)
  val mask    = UInt(p.maskBits.W)
  val data    = UInt(p.dataBits.W)
  val corrupt = Bool()
}

/** TileLink-UL Channel D (response from device to host). */
class TLULChannelD(p: TLULParameters) extends Bundle {
  val opcode  = UInt(3.W)
  val param   = UInt(2.W)
  val size    = UInt(p.sizeBits.W)
  val source  = UInt(p.sourceBits.W)
  val sink    = UInt(p.sinkBits.W)
  val denied  = Bool()
  val data    = UInt(p.dataBits.W)
  val corrupt = Bool()
  val error   = Bool()
}

/** Standard OpenTitan TileLink-UL interface. */
object OpenTitanTileLink {

  /** A_Channel with optional integrity user sideband (used by Spi2TLULV2). */
  class A_Channel(p: TLULParameters) extends Bundle {
    val opcode  = UInt(3.W)
    val param   = UInt(3.W)
    val size    = UInt(p.sizeBits.W)
    val source  = UInt(p.sourceBits.W)
    val address = UInt(p.addrBits.W)
    val mask    = UInt(p.maskBits.W)
    val data    = UInt(p.dataBits.W)
    val corrupt = Bool()
    val user    = new OpenTitanTileLink_A_User
  }

  /** D_Channel with optional integrity user sideband. */
  class D_Channel(p: TLULParameters) extends Bundle {
    val opcode  = UInt(3.W)
    val param   = UInt(3.W)
    val size    = UInt(p.sizeBits.W)
    val source  = UInt(p.sourceBits.W)
    val sink    = UInt(p.sinkBits.W)
    val denied  = Bool()
    val data    = UInt(p.dataBits.W)
    val corrupt = Bool()
    val user    = new OpenTitanTileLink_D_User
    val error   = UInt(1.W)
  }

  /** Host (master) facing interface. The host drives Channel A and receives Channel D. */
  class Host2Device(p: TLULParameters) extends Bundle {
    val a = Decoupled(new TLULChannelA(p))
    val d = Flipped(Decoupled(new TLULChannelD(p)))
  }
}

/** TL-UL Channel A opcodes. */
object TLULOpcodesA {
  val PutFullData    = 0.U(3.W)
  val PutPartialData = 1.U(3.W)
  val Get            = 4.U(3.W)
}

/** TL-UL Channel D opcodes. */
object TLULOpcodesD {
  val AccessAck     = 0.U(3.W)
  val AccessAckData = 1.U(3.W)
}
