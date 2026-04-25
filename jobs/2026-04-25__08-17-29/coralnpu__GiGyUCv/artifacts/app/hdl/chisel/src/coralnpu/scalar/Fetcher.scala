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

/** Fetcher: issues a fetch request on the IBus and returns packed FetchData.
  *
  * One-cycle latency: fetch.valid and ctrl.ready are raised the cycle AFTER
  * ibus.ready is asserted.
  */
class Fetcher(p: Parameters) extends Module {
  private val InstsPerFetch = p.fetchDataBits / 32

  val io = IO(new Bundle {
    val ctrl  = Flipped(Decoupled(UInt(p.axiAddrBits.W)))
    val ibus  = new IBusIO(p)
    val fetch = Valid(new FetchData(p))
  })

  object State extends ChiselEnum { val sIdle, sFetch = Value }
  val state       = RegInit(State.sIdle)
  val pendingAddr = Reg(UInt(p.axiAddrBits.W))

  // Registered outputs
  val fetchValid = RegInit(false.B)
  val fetchAddr  = Reg(UInt(p.axiAddrBits.W))
  val fetchInsts = Reg(Vec(InstsPerFetch, UInt(32.W)))
  val ctrlReady  = RegInit(false.B)

  io.ctrl.ready      := ctrlReady
  io.ibus.valid      := false.B
  io.ibus.addr       := 0.U
  io.fetch.valid     := fetchValid
  io.fetch.bits.addr := fetchAddr
  io.fetch.bits.inst := fetchInsts

  // Default: deassert outputs
  fetchValid := false.B
  ctrlReady  := false.B

  switch(state) {
    is(State.sIdle) {
      when(io.ctrl.valid) {
        pendingAddr := io.ctrl.bits
        state       := State.sFetch
      }
    }

    is(State.sFetch) {
      io.ibus.valid := true.B
      io.ibus.addr  := pendingAddr
      when(io.ibus.ready) {
        // Latch result; outputs will be valid next cycle
        fetchValid := true.B
        fetchAddr  := pendingAddr
        for (i <- 0 until InstsPerFetch) {
          fetchInsts(i) := io.ibus.rdata(32 * i + 31, 32 * i)
        }
        ctrlReady := true.B
        state     := State.sIdle
      }
    }
  }
}
