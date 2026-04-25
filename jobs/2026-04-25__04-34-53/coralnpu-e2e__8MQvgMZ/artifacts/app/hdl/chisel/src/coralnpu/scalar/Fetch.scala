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

// FetchL0: L0 instruction cache / fetch wrapper.
// When enableFetchL0 is false this is a thin pass-through around the
// uncached fetcher.  When true a small direct-mapped L0 buffer is
// interposed to absorb repeated fetches of the same cache line.
class FetchL0(p: Parameters) extends Module {
  val nInst = p.fetchDataBits / 32

  val io = IO(new Bundle {
    // From FetchControl
    val ctrl  = Flipped(Valid(UInt(32.W)))
    // Instruction bus toward memory
    val ibus  = new IBusBundle(p.fetchDataBits)
    // Fetched instruction group back to FetchControl
    val fetch = Valid(new Bundle {
      val addr = UInt(32.W)
      val inst = Vec(nInst, UInt(32.W))
    })
  })

  if (p.enableFetchL0) {
    // Simple 1-entry L0 buffer: hit if address matches the cached line
    val l0Valid = RegInit(false.B)
    val l0Addr  = RegInit(0.U(32.W))
    val l0Data  = RegInit(0.U(p.fetchDataBits.W))

    val fetcher = Module(new Fetcher(p))
    fetcher.io.ctrl  := io.ctrl
    io.ibus          := fetcher.io.ibus

    val hit = l0Valid && io.ctrl.valid && (io.ctrl.bits === l0Addr)
    when (fetcher.io.fetch.valid) {
      l0Valid := true.B
      l0Addr  := fetcher.io.fetch.bits.addr
      l0Data  := fetcher.io.fetch.bits.inst.asUInt
    }

    io.fetch.valid     := fetcher.io.fetch.valid || (io.ctrl.valid && hit)
    io.fetch.bits.addr := Mux(hit && !fetcher.io.fetch.valid, l0Addr, fetcher.io.fetch.bits.addr)
    for (i <- 0 until nInst) {
      val cached = l0Data(i*32+31, i*32)
      io.fetch.bits.inst(i) := Mux(hit && !fetcher.io.fetch.valid, cached,
                                    fetcher.io.fetch.bits.inst(i))
    }
  } else {
    // Pass-through: just wire the Fetcher directly
    val fetcher = Module(new Fetcher(p))
    fetcher.io.ctrl := io.ctrl
    io.ibus         := fetcher.io.ibus
    io.fetch        := fetcher.io.fetch
  }
}
