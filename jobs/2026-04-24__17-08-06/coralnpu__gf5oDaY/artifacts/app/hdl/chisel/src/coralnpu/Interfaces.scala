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

// Extension to Parameters: AXI data bus width
// We patch this here since Parameters.scala may not have it yet
object ParametersExt {
  implicit class ParamOps(p: Parameters) {
    def axi2DataBits: Int = p.lsuDataBits  // 128 bits
    def axiDataBits: Int  = 64
    def fetchWords: Int   = p.fetchDataBits / p.ilen  // e.g. 128/32 = 4
  }
}

// ---- AXI CSR interface (64-bit data, 32-bit addr) ----

class AxiCSRReadAddrBits extends Bundle {
  val addr = UInt(32.W)
  val len  = UInt(8.W)
  val size = UInt(3.W)
}

class AxiCSRReadDataBits extends Bundle {
  val data = UInt(64.W)
  val resp = UInt(2.W)
  val last = Bool()
}

class AxiCSRWriteAddrBits extends Bundle {
  val addr = UInt(32.W)
  val len  = UInt(8.W)
  val size = UInt(3.W)
}

class AxiCSRWriteDataBits extends Bundle {
  val data = UInt(64.W)
  val strb = UInt(16.W)  // 16-bit strobe for 128-bit transfers
  val last = Bool()
}

class AxiCSRWriteRespBits extends Bundle {
  val resp = UInt(2.W)
}

class AxiCSRRead extends Bundle {
  val addr = Flipped(Decoupled(new AxiCSRReadAddrBits))
  val data = Decoupled(new AxiCSRReadDataBits)
}

class AxiCSRWrite extends Bundle {
  val addr = Flipped(Decoupled(new AxiCSRWriteAddrBits))
  val data = Flipped(Decoupled(new AxiCSRWriteDataBits))
  val resp = Decoupled(new AxiCSRWriteRespBits)
}

class AxiCSRInterface extends Bundle {
  val read  = new AxiCSRRead
  val write = new AxiCSRWrite
}

// ---- AXI data interface (for DBus2Axi) ----

class AxiReadAddrBits(idBits: Int) extends Bundle {
  val id   = UInt(idBits.W)
  val addr = UInt(32.W)
  val len  = UInt(8.W)
  val size = UInt(3.W)
}

class AxiReadDataBits(idBits: Int, dataBits: Int) extends Bundle {
  val id   = UInt(idBits.W)
  val data = UInt(dataBits.W)
  val resp = UInt(2.W)
  val last = Bool()
}

class AxiWriteAddrBits(idBits: Int) extends Bundle {
  val id   = UInt(idBits.W)
  val addr = UInt(32.W)
  val len  = UInt(8.W)
  val size = UInt(3.W)
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

class AxiDataRead(p: Parameters) extends Bundle {
  val addr = Decoupled(new AxiReadAddrBits(p.axiIdBits))
  val data = Flipped(Decoupled(new AxiReadDataBits(p.axiIdBits, p.lsuDataBits)))
}

class AxiDataWrite(p: Parameters) extends Bundle {
  val addr = Decoupled(new AxiWriteAddrBits(p.axiIdBits))
  val data = Decoupled(new AxiWriteDataBits(p.lsuDataBits))
  val resp = Flipped(Decoupled(new AxiWriteRespBits(p.axiIdBits)))
}

class AxiDataInterface(p: Parameters) extends Bundle {
  val read  = new AxiDataRead(p)
  val write = new AxiDataWrite(p)
}

// ---- DBus interface ----

class DBusInterface(p: Parameters) extends Bundle {
  val valid = Input(Bool())
  val addr  = Input(UInt(p.addrBits.W))
  val write = Input(Bool())
  val wdata = Input(UInt(p.lsuDataBits.W))
  val wmask = Input(UInt((p.lsuDataBits / 8).W))
  val size  = Input(UInt(4.W))
  val ready = Output(Bool())
}

// ---- IBus interface ----

class IBusInterface(p: Parameters) extends Bundle {
  val valid  = Output(Bool())
  val addr   = Output(UInt(p.addrBits.W))
  val ready  = Input(Bool())
  val rdata  = Input(UInt(p.fetchDataBits.W))
}

// ---- CSR bundle (used by FetchControl) ----

class CsrBundle extends Bundle {
  val value = Vec(4, UInt(32.W))
}

// ---- CsrValues (used by CoreAxiCSR) ----

class CsrValues extends Bundle {
  val value = Vec(16, UInt(32.W))
}

// ---- DataBus port (used in Fabric) ----

class DataBusPort extends Bundle {
  val readDataAddr  = Decoupled(UInt(32.W))
  val readData      = Flipped(Valid(UInt(32.W)))
  val writeDataAddr = Decoupled(UInt(32.W))
  val writeDataBits = Output(UInt(128.W))
  val writeDataStrb = Output(UInt(16.W))
}

// ---- Memory region (used in Fabric) ----

object MemoryRegionType extends Enumeration {
  type MemoryRegionType = Value
  val IMEM, DMEM, Peripheral = Value
}

class MemoryRegion(val base: Long, val size: Long, val regionType: MemoryRegionType.MemoryRegionType)
