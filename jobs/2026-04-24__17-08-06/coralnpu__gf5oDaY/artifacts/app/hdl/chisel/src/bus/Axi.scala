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

/** AXI4 interface bundles. */

class Axi4ReadAddrChannel(idBits: Int, addrBits: Int) extends Bundle {
  val id    = UInt(idBits.W)
  val addr  = UInt(addrBits.W)
  val len   = UInt(8.W)
  val size  = UInt(3.W)
  val burst = UInt(2.W)
}

class Axi4ReadDataChannel(idBits: Int, dataBits: Int) extends Bundle {
  val id   = UInt(idBits.W)
  val data = UInt(dataBits.W)
  val resp = UInt(2.W)
  val last = Bool()
}

class Axi4WriteAddrChannel(idBits: Int, addrBits: Int) extends Bundle {
  val id    = UInt(idBits.W)
  val addr  = UInt(addrBits.W)
  val len   = UInt(8.W)
  val size  = UInt(3.W)
  val burst = UInt(2.W)
}

class Axi4WriteDataChannel(dataBits: Int) extends Bundle {
  val data = UInt(dataBits.W)
  val strb = UInt((dataBits / 8).W)
  val last = Bool()
}

class Axi4WriteRespChannel(idBits: Int) extends Bundle {
  val id   = UInt(idBits.W)
  val resp = UInt(2.W)
}

class Axi4Master(p: Parameters) extends Bundle {
  val ar = Decoupled(new Axi4ReadAddrChannel(p.axiIdBits, p.addrBits))
  val r  = Flipped(Decoupled(new Axi4ReadDataChannel(p.axiIdBits, p.lsuDataBits)))
  val aw = Decoupled(new Axi4WriteAddrChannel(p.axiIdBits, p.addrBits))
  val w  = Decoupled(new Axi4WriteDataChannel(p.lsuDataBits))
  val b  = Flipped(Decoupled(new Axi4WriteRespChannel(p.axiIdBits)))
}

class Axi4Slave(p: Parameters) extends Bundle {
  val ar = Flipped(Decoupled(new Axi4ReadAddrChannel(p.axiIdBits, p.addrBits)))
  val r  = Decoupled(new Axi4ReadDataChannel(p.axiIdBits, p.lsuDataBits))
  val aw = Flipped(Decoupled(new Axi4WriteAddrChannel(p.axiIdBits, p.addrBits)))
  val w  = Flipped(Decoupled(new Axi4WriteDataChannel(p.lsuDataBits)))
  val b  = Decoupled(new Axi4WriteRespChannel(p.axiIdBits))
}
