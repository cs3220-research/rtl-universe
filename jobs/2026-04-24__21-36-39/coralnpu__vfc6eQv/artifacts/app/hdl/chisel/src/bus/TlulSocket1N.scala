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
import coralnpu.Parameters

/**
  * TileLink-UL 1-to-N socket (address-based demultiplexer).
  *
  * Routes requests from a single host to one of N devices based on address ranges.
  * This 128-bit variant has a fixed configuration with 4 downstream ports and
  * standard 32-bit address ranges.
  */
class TlulSocket1N(p: Parameters, nDevices: Int, addrBase: Seq[Long], addrMask: Seq[Long]) extends Module {
  require(nDevices == addrBase.length && nDevices == addrMask.length)
  val tlp = new TLULParameters(p)

  val io = IO(new Bundle {
    val host   = Flipped(new OpenTitanTileLink.Host2Device(tlp))
    val device = Vec(nDevices, new OpenTitanTileLink.Host2Device(tlp))
  })

  // State
  val sIdle :: sWaitD :: Nil = Enum(2)
  val state  = RegInit(sIdle)
  val selReg = RegInit(0.U(log2Ceil(nDevices).W))

  // Default
  for (i <- 0 until nDevices) {
    io.device(i).a.valid := false.B
    io.device(i).a.bits  := 0.U.asTypeOf(new TLULChannelA(tlp))
    io.device(i).d.ready := false.B
  }
  io.host.a.ready := false.B
  io.host.d.valid := false.B
  io.host.d.bits  := 0.U.asTypeOf(new TLULChannelD(tlp))

  // Address decode
  val sel = Wire(UInt(log2Ceil(nDevices).W))
  sel := 0.U
  for (i <- 0 until nDevices) {
    when((io.host.a.bits.address & addrMask(i).U) === addrBase(i).U) {
      sel := i.U
    }
  }

  switch(state) {
    is(sIdle) {
      io.host.a.ready := io.device(sel).a.ready
      for (i <- 0 until nDevices) {
        io.device(i).a.valid := io.host.a.valid && (sel === i.U)
        io.device(i).a.bits  := io.host.a.bits
      }
      when(io.host.a.valid && io.device(sel).a.ready) {
        selReg := sel
        state  := sWaitD
      }
    }
    is(sWaitD) {
      io.device(selReg).d.ready := io.host.d.ready
      io.host.d.valid           := io.device(selReg).d.valid
      io.host.d.bits            := io.device(selReg).d.bits
      when(io.host.d.valid && io.host.d.ready) {
        state := sIdle
      }
    }
  }
}

/**
  * Concrete 1-to-8 socket with 128-bit bus for the coral NPU subsystem.
  */
class TlulSocket1N_128 extends Module {
  val p = new Parameters
  p.lsuDataBits = 128
  val tlp      = new TLULParameters(p)
  val N_DEVICES = 8

  val io = IO(new Bundle {
    val host   = Flipped(new OpenTitanTileLink.Host2Device(tlp))
    val device = Vec(N_DEVICES, new OpenTitanTileLink.Host2Device(tlp))
    // Address configuration ports
    val addr_base = Input(Vec(N_DEVICES, UInt(32.W)))
    val addr_mask = Input(Vec(N_DEVICES, UInt(32.W)))
  })

  val state  = RegInit(false.B)
  val selReg = RegInit(0.U(log2Ceil(N_DEVICES).W))

  for (i <- 0 until N_DEVICES) {
    io.device(i).a.valid := false.B
    io.device(i).a.bits  := 0.U.asTypeOf(new TLULChannelA(tlp))
    io.device(i).d.ready := false.B
  }
  io.host.a.ready := false.B
  io.host.d.valid := false.B
  io.host.d.bits  := 0.U.asTypeOf(new TLULChannelD(tlp))

  val sel = Wire(UInt(log2Ceil(N_DEVICES).W))
  sel := 0.U
  for (i <- 0 until N_DEVICES) {
    when((io.host.a.bits.address & io.addr_mask(i)) === io.addr_base(i)) {
      sel := i.U
    }
  }

  when(!state) {
    io.host.a.ready := io.device(sel).a.ready
    for (i <- 0 until N_DEVICES) {
      io.device(i).a.valid := io.host.a.valid && (sel === i.U)
      io.device(i).a.bits  := io.host.a.bits
    }
    when(io.host.a.valid && io.device(sel).a.ready) {
      selReg := sel
      state  := true.B
    }
  }.otherwise {
    io.device(selReg).d.ready := io.host.d.ready
    io.host.d.valid           := io.device(selReg).d.valid
    io.host.d.bits            := io.device(selReg).d.bits
    when(io.host.d.valid && io.host.d.ready) {
      state := false.B
    }
  }
}

import _root_.circt.stage.{ChiselStage, FirtoolOption}
import chisel3.stage.ChiselGeneratorAnnotation
import scala.annotation.nowarn

@nowarn
object TlulSocket1N_128Emitter extends App {
  (new ChiselStage).execute(
    Array("--target", "systemverilog") ++ args,
    Seq(ChiselGeneratorAnnotation(() => new TlulSocket1N_128))
  )
}
