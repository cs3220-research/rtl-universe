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

/** TileLink-UL parameters derived from the project-wide coralnpu Parameters. */
class TLULParameters(p: coralnpu.Parameters) {
  val addrBits   = 32
  val dataBits   = p.lsuDataBits
  val sourceBits = 8
  val sinkBits   = 1
  val sizeBits   = 4
  val maskBits   = dataBits / 8
}

object TLULParameters {
  /** Construct TLULParameters directly from a data-width integer. */
  def apply(dataBits: Int): TLULParameters = {
    val p = new coralnpu.Parameters
    p.lsuDataBits = dataBits
    new TLULParameters(p)
  }
}

// ---------------------------------------------------------------------------
// TileLink-UL opcode enumerations
// ---------------------------------------------------------------------------

/** TileLink-UL A-channel opcodes (host → device). */
object TLULOpcodesA extends ChiselEnum {
  val PutFullData    = Value(0.U)
  val PutPartialData = Value(1.U)
  val Get            = Value(4.U)
}

/** TileLink-UL D-channel opcodes (device → host). */
object TLULOpcodesD extends ChiselEnum {
  val AccessAck     = Value(0.U)
  val AccessAckData = Value(1.U)
}

// ---------------------------------------------------------------------------
// User-field bundles
// ---------------------------------------------------------------------------

/** D-channel user-extension field (integrity / ECC bits). */
class OpenTitanTileLink_D_User extends Bundle {
  val rsvd = UInt(14.W)
}

/** A-channel user-extension field (integrity / ECC bits). */
class OpenTitanTileLink_A_User extends Bundle {
  val rsvd = UInt(23.W)
}

// ---------------------------------------------------------------------------
// OpenTitanTileLink channel bundles and host/device IO wrappers
// ---------------------------------------------------------------------------

/** OpenTitan-flavoured TileLink-UL channel and IO definitions. */
object OpenTitanTileLink {

  /** TileLink-UL A-channel message (host → device). */
  class A_Channel(p: TLULParameters) extends Bundle {
    val opcode  = UInt(3.W)
    val param   = UInt(3.W)
    val size    = UInt(p.sizeBits.W)
    val source  = UInt(p.sourceBits.W)
    val address = UInt(p.addrBits.W)
    val mask    = UInt(p.maskBits.W)
    val data    = UInt(p.dataBits.W)
    val user    = new OpenTitanTileLink_A_User
    val corrupt = Bool()
  }

  /** TileLink-UL D-channel message (device → host). */
  class D_Channel(p: TLULParameters) extends Bundle {
    val opcode  = UInt(3.W)
    val param   = UInt(3.W)
    val size    = UInt(p.sizeBits.W)
    val source  = UInt(p.sourceBits.W)
    val sink    = UInt(p.sinkBits.W)
    val data    = UInt(p.dataBits.W)
    val user    = new OpenTitanTileLink_D_User
    val error   = Bool()
    val corrupt = Bool()
  }

  /** Host-to-device IO: host drives A, receives D. */
  class Host2Device(p: TLULParameters) extends Bundle {
    val a = Decoupled(new A_Channel(p))
    val d = Flipped(Decoupled(new D_Channel(p)))
  }

  /** Device-to-host IO (flipped Host2Device). */
  class Device2Host(p: TLULParameters) extends Bundle {
    val a = Flipped(Decoupled(new A_Channel(p)))
    val d = Decoupled(new D_Channel(p))
  }
}

// ---------------------------------------------------------------------------
// Convenience type aliases used by peripheral implementations
// ---------------------------------------------------------------------------

/** Alias: TileLink host-to-device IO (host drives A, receives D).
  *
  * Parameterised by (addrBits, dataBits, sourceBits).  The implementation
  * internally wraps [[OpenTitanTileLink.Host2Device]].
  */
class TlHost2DeviceIO(addrBits: Int, dataBits: Int, sourceBits: Int) extends Bundle {
  private val p = TLULParameters(dataBits)
  val a = Decoupled(new OpenTitanTileLink.A_Channel(p))
  val d = Flipped(Decoupled(new OpenTitanTileLink.D_Channel(p)))
}

/** Alias: TileLink device-to-host IO (device receives A, drives D). */
class TlDevice2HostIO(addrBits: Int, dataBits: Int, sourceBits: Int) extends Bundle {
  private val p = TLULParameters(dataBits)
  val a = Flipped(Decoupled(new OpenTitanTileLink.A_Channel(p)))
  val d = Decoupled(new OpenTitanTileLink.D_Channel(p))
}

// ---------------------------------------------------------------------------
// TileLink opcode constants (legacy / simple integer form)
// ---------------------------------------------------------------------------
object TlOp {
  val Get            = 4
  val PutFullData    = 0
  val PutPartialData = 1
  val AccessAck      = 0
  val AccessAckData  = 1
}
