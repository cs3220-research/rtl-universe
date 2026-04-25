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
// AXI channel bit-field bundles
// ---------------------------------------------------------------------------

class AxiReadAddrBits(addrBits: Int, idBits: Int) extends Bundle {
  val id     = UInt(idBits.W)
  val addr   = UInt(addrBits.W)
  val len    = UInt(8.W)
  val size   = UInt(3.W)
  val burst  = UInt(2.W)
  val prot   = UInt(3.W)
  val lock   = UInt(1.W)
  val cache  = UInt(4.W)
  val qos    = UInt(4.W)
  val region = UInt(4.W)
}

class AxiReadDataBits(dataBits: Int, idBits: Int) extends Bundle {
  val id   = UInt(idBits.W)
  val data = UInt(dataBits.W)
  val resp = UInt(2.W)
  val last = Bool()
}

class AxiWriteAddrBits(addrBits: Int, idBits: Int) extends Bundle {
  val id     = UInt(idBits.W)
  val addr   = UInt(addrBits.W)
  val len    = UInt(8.W)
  val size   = UInt(3.W)
  val burst  = UInt(2.W)
  val prot   = UInt(3.W)
  val lock   = UInt(1.W)
  val cache  = UInt(4.W)
  val qos    = UInt(4.W)
  val region = UInt(4.W)
}

class AxiWriteDataBits(dataBits: Int) extends Bundle {
  val data = UInt(dataBits.W)
  val strb = UInt((dataBits / 8).W)
  val last = Bool()
}

class AxiWriteRespBits(idBits: Int) extends Bundle {
  val id   = UInt(idBits.W)
  val resp = UInt(2.W)
}

// ---------------------------------------------------------------------------
// AxiBundle — from the DUT perspective as AXI MASTER
//
// Signal naming convention (matches testbench expectations):
//   io_axi_slave_read_addr_valid/ready/bits_id/bits_addr/…
//   io_axi_master_read_addr_valid/ready/…
// ---------------------------------------------------------------------------

/** AXI master bundle (DUT is the master).
  *
  * @param dataBits data-bus width in bits
  * @param addrBits address width in bits
  * @param idBits   AXI ID width in bits
  */
class AxiBundle(dataBits: Int, addrBits: Int, idBits: Int) extends Bundle {
  val read = new Bundle {
    /** AR channel – master sends read addresses. */
    val addr = Decoupled(new AxiReadAddrBits(addrBits, idBits))
    /** R channel  – master receives read data. */
    val data = Flipped(Decoupled(new AxiReadDataBits(dataBits, idBits)))
  }
  val write = new Bundle {
    /** AW channel – master sends write addresses. */
    val addr = Decoupled(new AxiWriteAddrBits(addrBits, idBits))
    /** W channel  – master sends write data. */
    val data = Decoupled(new AxiWriteDataBits(dataBits))
    /** B channel  – master receives write responses. */
    val resp = Flipped(Decoupled(new AxiWriteRespBits(idBits)))
  }
}

// ---------------------------------------------------------------------------
// AxiMasterIO — same structure as AxiBundle; a separate type used by
// peripheral wrappers that prefer the "MasterIO" naming convention.
// ---------------------------------------------------------------------------

class AxiMasterIO(addrBits: Int, dataBits: Int, idBits: Int) extends Bundle {
  val read = new Bundle {
    val addr = Decoupled(new AxiReadAddrBits(addrBits, idBits))
    val data = Flipped(Decoupled(new AxiReadDataBits(dataBits, idBits)))
  }
  val write = new Bundle {
    val addr = Decoupled(new AxiWriteAddrBits(addrBits, idBits))
    val data = Decoupled(new AxiWriteDataBits(dataBits))
    val resp = Flipped(Decoupled(new AxiWriteRespBits(idBits)))
  }
}

/** AXI slave bundle (DUT is the slave – all directions are flipped relative
  * to AxiBundle). */
class AxiSlaveIO(addrBits: Int, dataBits: Int, idBits: Int) extends Bundle {
  val read = new Bundle {
    val addr = Flipped(Decoupled(new AxiReadAddrBits(addrBits, idBits)))
    val data = Decoupled(new AxiReadDataBits(dataBits, idBits))
  }
  val write = new Bundle {
    val addr = Flipped(Decoupled(new AxiWriteAddrBits(addrBits, idBits)))
    val data = Flipped(Decoupled(new AxiWriteDataBits(dataBits)))
    val resp = Decoupled(new AxiWriteRespBits(idBits))
  }
}

// ---------------------------------------------------------------------------
// Helper functions
// ---------------------------------------------------------------------------

object AxiHelpers {
  /** Drive all read-address channel bits to safe defaults. */
  def axiReadAddrDefaults[T <: AxiReadAddrBits](bits: T): Unit = {
    bits.id     := 0.U
    bits.addr   := 0.U
    bits.len    := 0.U
    bits.size   := 2.U  // 4 bytes
    bits.burst  := 1.U  // INCR
    bits.prot   := 0.U
    bits.lock   := 0.U
    bits.cache  := 0.U
    bits.qos    := 0.U
    bits.region := 0.U
  }

  /** Drive all write-address channel bits to safe defaults. */
  def axiWriteAddrDefaults[T <: AxiWriteAddrBits](bits: T): Unit = {
    bits.id     := 0.U
    bits.addr   := 0.U
    bits.len    := 0.U
    bits.size   := 2.U
    bits.burst  := 1.U
    bits.prot   := 0.U
    bits.lock   := 0.U
    bits.cache  := 0.U
    bits.qos    := 0.U
    bits.region := 0.U
  }
}
