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

// Instruction bus (CPU → instruction memory)
class IBusBundle(dataBits: Int, addrBits: Int = 32) extends Bundle {
  val valid = Output(Bool())
  val ready = Input(Bool())
  val addr  = Output(UInt(addrBits.W))
  val rdata = Input(UInt(dataBits.W))
}

// Data bus (CPU → data memory/fabric)
class DBusBundle(dataBits: Int, addrBits: Int = 32) extends Bundle {
  val valid = Output(Bool())
  val ready = Input(Bool())
  val addr  = Output(UInt(addrBits.W))
  val write = Output(Bool())
  val wdata = Output(UInt(dataBits.W))
  val wmask = Output(UInt((dataBits/8).W))
  val size  = Output(UInt(3.W))   // 1/2/4 bytes
  val rdata = Input(UInt(dataBits.W))
}

// Simple read/write port to SRAM
class SramPort(dataBits: Int, addrBits: Int) extends Bundle {
  val valid    = Output(Bool())
  val write    = Output(Bool())
  val addr     = Output(UInt(addrBits.W))
  val wdata    = Output(UInt(dataBits.W))
  val wmask    = Output(UInt((dataBits/8).W))
  val rdata    = Input(UInt(dataBits.W))
}

// Generic read-only port
class ReadPort(dataBits: Int, addrBits: Int) extends Bundle {
  val valid = Input(Bool())
  val addr  = Input(UInt(addrBits.W))
  val data  = Output(UInt(dataBits.W))
}

// Generic write port
class WritePort(dataBits: Int, addrBits: Int) extends Bundle {
  val valid = Input(Bool())
  val addr  = Input(UInt(addrBits.W))
  val data  = Input(UInt(dataBits.W))
}

// Register file read port
class RegfileReadPort extends Bundle {
  val valid = Input(Bool())
  val addr  = Input(UInt(5.W))
  val data  = Output(UInt(32.W))
}

// Register file write port
class RegfileWritePort extends Bundle {
  val valid = Input(Bool())
  val addr  = Input(UInt(5.W))
  val data  = Input(UInt(32.W))
}

// Dispatch debug info (4 slots)
class DispatchDebugInfo extends Bundle {
  val instFire = Bool()
  val instAddr = UInt(32.W)
  val instInst = UInt(32.W)
}

/** Retirement buffer configuration constants (shared between coralnpu_base and retirement_buffer). */
object RetirementBufferConfig {
  val size     = 8
  val idxWidth = 4  // log2(size) + 1
}

// Retirement buffer entry debug info
class RetirementBufferDebugEntry(rvvVlen: Int = 32) extends Bundle {
  val valid = Bool()
  val pc   = UInt(32.W)
  val inst = UInt(32.W)
  val idx  = UInt(RetirementBufferConfig.idxWidth.W)
  val data = UInt(rvvVlen.W)
  val trap = Bool()
}

// Comprehensive debug IO bundle for CoreDebug
class CoreDebugBundle(p: Parameters) extends Bundle {
  val en     = Input(UInt(4.W))
  val cycles = Output(UInt(32.W))
  val addr   = Vec(4, Output(UInt(32.W)))
  val inst   = Vec(4, Output(UInt(32.W)))
  val dbus   = Output(new Bundle {
    val valid = Bool()
    val bits  = new Bundle {
      val addr  = UInt(32.W)
      val wdata = UInt(p.lsuDataBits.W)
      val write = Bool()
    }
  })
  val dispatch          = Vec(4, Output(new DispatchDebugInfo))
  val regfile_writeAddr = Vec(4, Output(new Bundle {
    val valid = Bool()
    val bits  = UInt(5.W)
  }))
  val regfile_writeData = Vec(6, Output(new Bundle {
    val valid = Bool()
    val bits  = new Bundle {
      val addr = UInt(5.W)
      val data = UInt(32.W)
    }
  }))
  val float_writeAddr = Output(new Bundle {
    val valid = Bool()
    val bits  = UInt(5.W)
  })
  val float_writeData = Vec(2, Output(new Bundle {
    val valid = Bool()
    val bits  = new Bundle {
      val addr = UInt(32.W)
      val data = UInt(32.W)
    }
  }))
  val rb_inst = Vec(RetirementBufferConfig.size, Output(new Bundle {
    val valid = Bool()
    val bits  = new Bundle {
      val pc   = UInt(32.W)
      val inst = UInt(32.W)
      val idx  = UInt(RetirementBufferConfig.idxWidth.W)
      val data = UInt(32.W)
      val trap = Bool()
    }
  }))
}

// Memory region type
object MemoryRegionType extends ChiselEnum {
  val IMEM, DMEM, Peripheral = Value
}

// Memory region descriptor
class MemoryRegion(val base: Long, val size: Long, val regionType: MemoryRegionType.Type)

// Fabric port (simplified internal bus)
class FabricPort(dataBits: Int = 32, addrBits: Int = 32) extends Bundle {
  val readDataAddr  = Decoupled(UInt(addrBits.W))
  val readData      = Flipped(Valid(UInt(dataBits.W)))
  val writeDataAddr = Decoupled(UInt(addrBits.W))
  val writeDataBits = Output(UInt(dataBits.W))
  val writeDataStrb = Output(UInt((dataBits/8).W))
}

// CSR read interface (output from CSR unit to other components)
class CsrReadPort extends Bundle {
  val value = Vec(4, UInt(32.W))  // 0=boot_addr, 1=mtvec, 2=mie, 3=mstatus
}

// Debug Module request/response
class DebugModuleReq extends Bundle {
  val address = UInt(32.W)
  val data    = UInt(32.W)
  val op      = UInt(2.W)   // 0=nop, 1=read, 2=write
}
class DebugModuleRsp extends Bundle {
  val data = UInt(32.W)
  val op   = UInt(2.W)   // 0=success, 2=fail
}
