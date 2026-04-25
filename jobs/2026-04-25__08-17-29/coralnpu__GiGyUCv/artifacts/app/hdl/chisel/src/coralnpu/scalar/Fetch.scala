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

/** Fetch stage (stub): wraps the instruction fetch pipeline. */
class Fetch(p: Parameters) extends Module {
  private val InstsPerFetch = p.fetchDataBits / 32

  val io = IO(new Bundle {
    val ibus         = new IBusIO(p)
    val out          = Valid(new FetchData(p))
    val branch       = Flipped(Valid(UInt(p.axiAddrBits.W)))
    val bufferSpaces = Input(UInt(log2Ceil(256 + 1).W))
    val csr          = new FetchControlCSRIO
  })

  val fetcher = Module(new Fetcher(p))
  val ctrl    = Module(new FetchControl(p))

  ctrl.io.fetchAddr  <> fetcher.io.ctrl
  ctrl.io.fetchData  := fetcher.io.fetch
  ctrl.io.branch     <> io.branch
  ctrl.io.bufferSpaces := io.bufferSpaces
  ctrl.io.csr        <> io.csr
  ctrl.io.bufferRequest.nReady := InstsPerFetch.U

  // Wire ibus
  io.ibus.valid := fetcher.io.ibus.valid
  io.ibus.addr  := fetcher.io.ibus.addr
  fetcher.io.ibus.ready := io.ibus.ready
  fetcher.io.ibus.rdata := io.ibus.rdata

  io.out := fetcher.io.fetch
}
