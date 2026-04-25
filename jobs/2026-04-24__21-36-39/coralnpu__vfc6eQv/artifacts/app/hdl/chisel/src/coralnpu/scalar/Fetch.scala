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

/** L0 instruction-cache-backed fetch controller.
  *
  * This wraps either a cached or uncached fetch path depending on the
  * `p.enableFetchL0` parameter.  In both cases the external interface
  * is identical to FetchControl.
  */
class Fetch(p: Parameters) extends Module {
  val NumInstsPerFetch = 8

  val io = IO(new Bundle {
    val fetchAddr     = Decoupled(UInt(32.W))
    val fetchData     = Flipped(Valid(new FetchData(NumInstsPerFetch)))
    val bufferRequest = new BulkRequestIO
    val bufferSpaces  = Input(UInt(5.W))
    val branch        = Flipped(Valid(UInt(32.W)))
    val csr           = new CsrReadPort(1)
  })

  // Instantiate the uncached fetch controller as the underlying implementation.
  // A cached version (L1ICache backed) would be swapped in for the full design.
  val ctrl = Module(new FetchControl(p))

  ctrl.io.fetchAddr     <> io.fetchAddr
  ctrl.io.fetchData     <> io.fetchData
  ctrl.io.bufferRequest <> io.bufferRequest
  ctrl.io.bufferSpaces  := io.bufferSpaces
  ctrl.io.branch        <> io.branch
  ctrl.io.csr           <> io.csr
}
