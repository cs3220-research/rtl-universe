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

/** Tightly-Coupled Memory (TCM).
  *
  * A single-port SRAM with 128-bit data width, accessible from two sources:
  *
  *   1. `cpu`      – the CPU instruction or data bus (highest priority).
  *   2. `backdoor` – the AXI slave back-door for firmware loading (lower
  *                   priority, only used when the CPU port is idle).
  *
  * Both ports use *byte* addresses.  Internally the byte address is shifted
  * right to produce a 128-bit word address fed to `SramNx128`.
  *
  * @param sizeBytes  Total SRAM capacity in bytes.
  * @param baseAddr   DPI back-door base address.
  */
class TCM(val sizeBytes: Int, val baseAddr: Long = 0L) extends Module {

  private val dataBits  = 128
  private val dataBytes = dataBits / 8
  private val numWords  = sizeBytes / dataBytes
  private val wordAddrWidth = log2Ceil(numWords)
  // CPU-visible byte-address width that covers this TCM
  private val byteAddrWidth = log2Ceil(sizeBytes)

  val io = IO(new Bundle {
    // ---- CPU access (highest priority) ------------------------------------
    val cpu = new Bundle {
      val en    = Input(Bool())
      val we    = Input(Bool())
      val addr  = Input(UInt(byteAddrWidth.W))
      val wdata = Input(UInt(dataBits.W))
      val wmask = Input(UInt(dataBytes.W))
      val rdata = Output(UInt(dataBits.W))
    }
    // ---- AXI back-door (firmware loading, lower priority) ----------------
    val backdoor = new Bundle {
      val en    = Input(Bool())
      val we    = Input(Bool())
      val addr  = Input(UInt(byteAddrWidth.W))
      val wdata = Input(UInt(dataBits.W))
      val wmask = Input(UInt(dataBytes.W))
      val rdata = Output(UInt(dataBits.W))
    }
  })

  val sram = Module(new SramNx128(sizeBytes, baseAddr))

  // CPU wins when active; otherwise fall through to back-door.
  val cpuActive = io.cpu.en
  sram.io.valid := Mux(cpuActive, io.cpu.en,    io.backdoor.en)
  sram.io.write := Mux(cpuActive, io.cpu.we,    io.backdoor.we)
  sram.io.addr  := Mux(cpuActive, io.cpu.addr,  io.backdoor.addr)(byteAddrWidth - 1, log2Ceil(dataBytes))
  sram.io.wdata := Mux(cpuActive, io.cpu.wdata, io.backdoor.wdata)
  sram.io.wmask := Mux(cpuActive, io.cpu.wmask, io.backdoor.wmask)

  // Read data is broadcast to both ports; the caller knows which one is active.
  io.cpu.rdata      := sram.io.rdata
  io.backdoor.rdata := sram.io.rdata
}
