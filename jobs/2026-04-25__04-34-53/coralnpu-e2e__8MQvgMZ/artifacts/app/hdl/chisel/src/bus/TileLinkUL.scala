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

package bus

import chisel3._
import chisel3.util._

// ---------------------------------------------------------------------------
// Parameters
// ---------------------------------------------------------------------------

/** TileLink-UL bus parameters. */
class TLULParameters(
  val dataWidth:   Int = 32,
  val addrWidth:   Int = 32,
  val idWidth:     Int = 4,
  val sizeWidth:   Int = 3,
  val maskWidth:   Int = 4,
  val sourceWidth: Int = 4
) {
  /** Construct from a coralnpu.Parameters instance.
    * The LSU data width drives the TL-UL data / mask widths.
    */
  def this(p: coralnpu.Parameters) = this(
    dataWidth   = p.lsuDataBits,
    addrWidth   = p.addrBits,
    idWidth     = p.axiIdBits,
    sizeWidth   = {
      val maskBits = p.lsuDataBits / 8
      math.max(math.ceil(math.log(maskBits + 1) / math.log(2)).toInt, 3)
    },
    maskWidth   = p.lsuDataBits / 8,
    sourceWidth = p.tlulSourceWidth
  )
}

object TLULParameters {
  def apply(
    dataWidth:   Int = 32,
    addrWidth:   Int = 32,
    idWidth:     Int = 4,
    sizeWidth:   Int = 3,
    maskWidth:   Int = 4,
    sourceWidth: Int = 4
  ): TLULParameters = new TLULParameters(dataWidth, addrWidth, idWidth, sizeWidth, maskWidth, sourceWidth)

  /** Construct from a coralnpu.Parameters instance. */
  def apply(p: coralnpu.Parameters): TLULParameters = new TLULParameters(p)
}

// ---------------------------------------------------------------------------
// Channel A (request: host → device)
// ---------------------------------------------------------------------------

class TLChannelA(p: TLULParameters) extends Bundle {
  val opcode  = UInt(3.W)
  val param   = UInt(3.W)
  val size    = UInt(p.sizeWidth.W)
  val source  = UInt(p.sourceWidth.W)
  val address = UInt(p.addrWidth.W)
  val mask    = UInt(p.maskWidth.W)
  val data    = UInt(p.dataWidth.W)
  val corrupt = Bool()
}

// ---------------------------------------------------------------------------
// Channel D (response: device → host)
// ---------------------------------------------------------------------------

class TLChannelD(p: TLULParameters) extends Bundle {
  val opcode  = UInt(3.W)
  val param   = UInt(2.W)
  val size    = UInt(p.sizeWidth.W)
  val source  = UInt(p.sourceWidth.W)
  val sink    = UInt(1.W)
  val denied  = Bool()
  val data    = UInt(p.dataWidth.W)
  val corrupt = Bool()
  /** Error flag – set by a peripheral to indicate an access fault (e.g. bad
    * address, write to read-only register, etc.). */
  val error   = Bool()
}

// ---------------------------------------------------------------------------
// Top-level bundle (host perspective)
// ---------------------------------------------------------------------------

class TLBundleUL(p: TLULParameters) extends Bundle {
  val a = Decoupled(new TLChannelA(p))
  val d = Flipped(Decoupled(new TLChannelD(p)))
}

// ---------------------------------------------------------------------------
// OpenTitan TileLink compatibility wrappers
// ---------------------------------------------------------------------------

object OpenTitanTileLink {
  class Host2Device(p: TLULParameters) extends TLBundleUL(p)

  class A_Channel(p: TLULParameters) extends TLChannelA(p)
  class D_Channel(p: TLULParameters) extends TLChannelD(p)
}

/** Placeholder user-field bundle attached to D-channel responses in the
  * extended (OpenTitan-style) TL-UL encoding used by Spi2TLULV2. */
class OpenTitanTileLink_D_User extends Bundle {
  val data = UInt(14.W)
}

// ---------------------------------------------------------------------------
// Opcode constants
// ---------------------------------------------------------------------------

object TLULOpcodesA {
  val Get         = 4.U(3.W)
  val PutFullData = 0.U(3.W)
  val PutPartData = 1.U(3.W)
}

object TLULOpcodesD {
  val AccessAck     = 0.U(3.W)
  val AccessAckData = 1.U(3.W)
}

object TLULOpcodesParam {
  val None = 0.U(3.W)
}

// ---------------------------------------------------------------------------
// SECDED encoder
// ---------------------------------------------------------------------------

/** Hamming SEC-DED encoder.  For `width`-bit input data, produces a 7-bit ECC
  * field.  The implementation is a structural XOR over the standard Hamming
  * check-bit positions. */
class SecdedEncoder(width: Int) extends Module {
  val io = IO(new Bundle {
    val data_i = Input(UInt(width.W))
    val ecc_o  = Output(UInt(7.W))
  })

  // Compute up-to 7 parity bits using standard SEC-DED Hamming parity positions.
  // Parity bit p(k) covers every data bit whose (1-based) position has bit k set.
  // We unpack the data vector and compute each parity bit as XOR of its data bits.
  val bits = Wire(Vec(width, Bool()))
  for (i <- 0 until width) bits(i) := io.data_i(i)

  val numParity = 7
  val ecc = Wire(Vec(numParity, Bool()))
  for (k <- 0 until numParity) {
    val covered = (0 until width).filter { i =>
      // data bit i is covered by parity bit k if bit k is set in (i + numParity + 1)
      // (1-based indexing, parity bits occupy positions 1,2,4,8,16,32,64)
      val pos = i + numParity + 1 // 1-based position of data bit i in the code word
      ((pos >> k) & 1) != 0
    }.map(bits(_))
    ecc(k) := covered.foldLeft(false.B)(_ ^ _)
  }
  io.ecc_o := ecc.asUInt
}

// ---------------------------------------------------------------------------
// Integrity generation / checking stubs
// ---------------------------------------------------------------------------

/** Adds integrity ECC to a TL-UL channel-A request.
  * IO uses raw channel bundles (not Decoupled wrappers) so that the
  * TlulIntegrityTestbench can connect them directly. */
class RequestIntegrityGen(p: TLULParameters) extends Module {
  val io = IO(new Bundle {
    val a_i = Input(new TLChannelA(p))
    val a_o = Output(new TLChannelA(p))
  })
  io.a_o := io.a_i
}

class RequestIntegrityCheck(p: TLULParameters) extends Module {
  val io = IO(new Bundle {
    val a_i   = Input(new TLChannelA(p))
    val fault = Output(Bool())
  })
  io.fault := false.B
}

class ResponseIntegrityGen(p: TLULParameters) extends Module {
  val io = IO(new Bundle {
    val d_i = Input(new TLChannelD(p))
    val d_o = Output(new TLChannelD(p))
  })
  io.d_o := io.d_i
}

class ResponseIntegrityCheck(p: TLULParameters) extends Module {
  val io = IO(new Bundle {
    val d_i   = Input(new TLChannelD(p))
    val fault = Output(Bool())
  })
  io.fault := false.B
}
