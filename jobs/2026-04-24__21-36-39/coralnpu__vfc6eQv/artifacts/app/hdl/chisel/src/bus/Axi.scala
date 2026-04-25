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

// ---------------------------------------------------------------------------
// AXI4 Channel Bundles
// ---------------------------------------------------------------------------

class AxiReadAddrChannel(addrBits: Int, idBits: Int) extends Bundle {
  val addr  = UInt(addrBits.W)
  val size  = UInt(3.W)
  val len   = UInt(8.W)
  val id    = UInt(idBits.W)
  val burst = UInt(2.W)
  val lock  = UInt(1.W)
  val cache = UInt(4.W)
  val prot  = UInt(3.W)
  val qos   = UInt(4.W)
}

class AxiReadDataChannel(dataBits: Int, idBits: Int) extends Bundle {
  val data = UInt(dataBits.W)
  val last = Bool()
  val resp = UInt(2.W)
  val id   = UInt(idBits.W)
}

class AxiWriteAddrChannel(addrBits: Int, idBits: Int) extends Bundle {
  val addr  = UInt(addrBits.W)
  val size  = UInt(3.W)
  val len   = UInt(8.W)
  val id    = UInt(idBits.W)
  val burst = UInt(2.W)
  val lock  = UInt(1.W)
  val cache = UInt(4.W)
  val prot  = UInt(3.W)
  val qos   = UInt(4.W)
}

class AxiWriteDataChannel(dataBits: Int) extends Bundle {
  val data = UInt(dataBits.W)
  val strb = UInt((dataBits / 8).W)
  val last = Bool()
}

class AxiWriteRespChannel(idBits: Int) extends Bundle {
  val resp = UInt(2.W)
  val id   = UInt(idBits.W)
}

// ---------------------------------------------------------------------------
// AXI4 Sub-interfaces (read and write separately)
// ---------------------------------------------------------------------------

/** AXI4 Read channel sub-interface (master perspective). */
class AxiReadIO(addrBits: Int, dataBits: Int, idBits: Int) extends Bundle {
  val addr = Decoupled(new AxiReadAddrChannel(addrBits, idBits))
  val data = Flipped(Decoupled(new AxiReadDataChannel(dataBits, idBits)))
}

/** AXI4 Write channel sub-interface (master perspective). */
class AxiWriteIO(addrBits: Int, dataBits: Int, idBits: Int) extends Bundle {
  val addr = Decoupled(new AxiWriteAddrChannel(addrBits, idBits))
  val data = Decoupled(new AxiWriteDataChannel(dataBits))
  val resp = Flipped(Decoupled(new AxiWriteRespChannel(idBits)))
}

/** Full AXI4 master interface (read + write). */
class AxiMasterIO(addrBits: Int, dataBits: Int, idBits: Int) extends Bundle {
  val read  = new AxiReadIO(addrBits, dataBits, idBits)
  val write = new AxiWriteIO(addrBits, dataBits, idBits)
}
