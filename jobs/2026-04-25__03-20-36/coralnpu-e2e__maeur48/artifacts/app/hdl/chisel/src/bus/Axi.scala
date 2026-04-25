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

/** AXI4 address channel (AR/AW). */
class AxiAddrBundle(addrBits: Int, idBits: Int) extends Bundle {
  val addr   = UInt(addrBits.W)
  val prot   = UInt(3.W)
  val id     = UInt(idBits.W)
  val len    = UInt(8.W)
  val size   = UInt(3.W)
  val burst  = UInt(2.W)
  val lock   = UInt(1.W)
  val cache  = UInt(4.W)
  val qos    = UInt(4.W)
  val region = UInt(4.W)
}

/** AXI4 read data channel (R). */
class AxiReadDataBundle(dataBits: Int, idBits: Int) extends Bundle {
  val data = UInt(dataBits.W)
  val id   = UInt(idBits.W)
  val resp = UInt(2.W)
  val last = Bool()
}

/** AXI4 write data channel (W). */
class AxiWriteDataBundle(dataBits: Int) extends Bundle {
  val data = UInt(dataBits.W)
  val strb = UInt((dataBits / 8).W)
  val last = Bool()
}

/** AXI4 write response channel (B). */
class AxiWriteRespBundle(idBits: Int) extends Bundle {
  val id   = UInt(idBits.W)
  val resp = UInt(2.W)
}

/** AXI4 master-side IO bundle.
  *
  * From the master's perspective: AR/AW/W are outputs (Decoupled), R/B are inputs
  * (Flipped(Decoupled)).
  */
class AxiMasterIO(addrBits: Int, dataBits: Int, idBits: Int) extends Bundle {
  val read_addr  = Decoupled(new AxiAddrBundle(addrBits, idBits))
  val read_data  = Flipped(Decoupled(new AxiReadDataBundle(dataBits, idBits)))
  val write_addr = Decoupled(new AxiAddrBundle(addrBits, idBits))
  val write_data = Decoupled(new AxiWriteDataBundle(dataBits))
  val write_resp = Flipped(Decoupled(new AxiWriteRespBundle(idBits)))
}

/** AXI4 slave-side IO bundle — the Flip of AxiMasterIO. */
class AxiSlaveIO(addrBits: Int, dataBits: Int, idBits: Int) extends Bundle {
  val read_addr  = Flipped(Decoupled(new AxiAddrBundle(addrBits, idBits)))
  val read_data  = Decoupled(new AxiReadDataBundle(dataBits, idBits))
  val write_addr = Flipped(Decoupled(new AxiAddrBundle(addrBits, idBits)))
  val write_data = Flipped(Decoupled(new AxiWriteDataBundle(dataBits)))
  val write_resp = Decoupled(new AxiWriteRespBundle(idBits))
}

/** AXI4 response codes. */
object AxiResp {
  val OKAY   = 0.U(2.W)
  val EXOKAY = 1.U(2.W)
  val SLVERR = 2.U(2.W)
  val DECERR = 3.U(2.W)
}

/** AXI4 burst type codes. */
object AxiBurst {
  val FIXED = 0.U(2.W)
  val INCR  = 1.U(2.W)
  val WRAP  = 2.U(2.W)
}
