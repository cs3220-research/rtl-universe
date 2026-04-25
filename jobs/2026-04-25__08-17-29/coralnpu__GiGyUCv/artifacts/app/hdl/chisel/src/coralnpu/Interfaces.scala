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

// ---------------------------------------------------------------------------
// AXI4 channel bundles
// ---------------------------------------------------------------------------

/** AXI4 write-address channel (manager side). */
class AxiWriteAddrChannel(addrBits: Int, idBits: Int) extends Bundle {
  val id    = UInt(idBits.W)
  val addr  = UInt(addrBits.W)
  val len   = UInt(8.W)   // burst length - 1
  val size  = UInt(3.W)   // log2(bytes per beat)
  val burst = UInt(2.W)
}

/** AXI4 write-data channel. */
class AxiWriteDataChannel(dataBits: Int) extends Bundle {
  val data = UInt(dataBits.W)
  val strb = UInt((dataBits / 8).W)
  val last = Bool()
}

/** AXI4 write-response channel (subordinate side). */
class AxiWriteRespChannel(idBits: Int) extends Bundle {
  val id   = UInt(idBits.W)
  val resp = UInt(2.W)
}

/** AXI4 read-address channel (manager side). */
class AxiReadAddrChannel(addrBits: Int, idBits: Int) extends Bundle {
  val id    = UInt(idBits.W)
  val addr  = UInt(addrBits.W)
  val len   = UInt(8.W)
  val size  = UInt(3.W)
  val burst = UInt(2.W)
}

/** AXI4 read-data channel (subordinate side). */
class AxiReadDataChannel(dataBits: Int, idBits: Int) extends Bundle {
  val id   = UInt(idBits.W)
  val data = UInt(dataBits.W)
  val resp = UInt(2.W)
  val last = Bool()
}

// ---------------------------------------------------------------------------
// Grouped AXI read/write interfaces
// ---------------------------------------------------------------------------

class AxiReadInterface(addrBits: Int, dataBits: Int, idBits: Int) extends Bundle {
  val addr = Decoupled(new AxiReadAddrChannel(addrBits, idBits))
  val data = Flipped(Decoupled(new AxiReadDataChannel(dataBits, idBits)))
}

class AxiWriteInterface(addrBits: Int, dataBits: Int, idBits: Int) extends Bundle {
  val addr = Decoupled(new AxiWriteAddrChannel(addrBits, idBits))
  val data = Decoupled(new AxiWriteDataChannel(dataBits))
  val resp = Flipped(Decoupled(new AxiWriteRespChannel(idBits)))
}

/** Full AXI4 manager (master) interface (driven by manager, seen as Flipped by subordinate). */
class AxiMasterBundle(addrBits: Int, dataBits: Int, idBits: Int) extends Bundle {
  val read  = new AxiReadInterface(addrBits, dataBits, idBits)
  val write = new AxiWriteInterface(addrBits, dataBits, idBits)
}

/** Convenience constructor using Parameters. */
object AxiMasterBundle {
  def apply(p: Parameters): AxiMasterBundle =
    new AxiMasterBundle(p.axiAddrBits, p.axiDataBits, p.axiIdBits)
}

// ---------------------------------------------------------------------------
// DBus interface (internal load/store unit bus)
// ---------------------------------------------------------------------------

class DBusRequest(p: Parameters) extends Bundle {
  val addr  = UInt(p.axiAddrBits.W)
  val write = Bool()
  val wdata = UInt(p.axi2DataBits.W)
  val wmask = UInt((p.axi2DataBits / 8).W)
  val size  = UInt(log2Ceil(p.axi2DataBits / 8 + 1).W)
}

/** DBus: valid/ready handshake request channel + rdata response on the same port. */
class DBusIO(p: Parameters) extends Bundle {
  val valid = Input(Bool())
  val ready = Output(Bool())
  val addr  = Input(UInt(p.axiAddrBits.W))
  val write = Input(Bool())
  val wdata = Input(UInt(p.axi2DataBits.W))
  val wmask = Input(UInt((p.axi2DataBits / 8).W))
  val size  = Input(UInt(log2Ceil(p.axi2DataBits / 8 + 1).W))
  val rdata = Output(UInt(p.axi2DataBits.W))
}

// ---------------------------------------------------------------------------
// IBus interface (instruction bus)
// ---------------------------------------------------------------------------

class IBusIO(p: Parameters) extends Bundle {
  val valid = Output(Bool())
  val ready = Input(Bool())
  val addr  = Output(UInt(p.axiAddrBits.W))
  val rdata = Input(UInt(p.fetchDataBits.W))
}

// ---------------------------------------------------------------------------
// Fetch data bundle (instructions fetched from memory)
// ---------------------------------------------------------------------------

class FetchData(p: Parameters) extends Bundle {
  val addr = UInt(p.axiAddrBits.W)
  val inst = Vec(p.fetchDataBits / 32, UInt(32.W))
}

// ---------------------------------------------------------------------------
// Fabric port interface (used by FabricArbiter and FabricMux)
// ---------------------------------------------------------------------------

class FabricPort(p: Parameters) extends Bundle {
  val readDataAddr  = Decoupled(UInt(p.axiAddrBits.W))
  val readData      = Flipped(Valid(UInt(p.lsuDataBits.W)))
  val writeDataAddr = Decoupled(UInt(p.axiAddrBits.W))
  val writeDataBits = Output(UInt(p.lsuDataBits.W))
  val writeDataStrb = Output(UInt((p.lsuDataBits / 8).W))
}

// ---------------------------------------------------------------------------
// Memory region for FabricMux
// ---------------------------------------------------------------------------

object MemoryRegionType extends Enumeration {
  val IMEM, DMEM, Peripheral = Value
}

class MemoryRegion(val base: Long, val size: Long, val regionType: MemoryRegionType.Value) {
  def contains(addr: Long): Boolean = addr >= base && addr < base + size
  def offset(addr: Long): Long = addr - base
}
