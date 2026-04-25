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

/** Load/store unit operation encoding. */
object LsuOp extends ChiselEnum {
  val LB  = Value  // load byte (signed)
  val LH  = Value  // load half-word (signed)
  val LW  = Value  // load word
  val LBU = Value  // load byte (unsigned)
  val LHU = Value  // load half-word (unsigned)
  val SB  = Value  // store byte
  val SH  = Value  // store half-word
  val SW  = Value  // store word
  // Floating-point loads/stores
  val FLW = Value
  val FSW = Value
  // Wide vector loads/stores (used for RVV)
  val VLD = Value
  val VST = Value
}

/** LSU command bundle. */
class LsuCmd extends Bundle {
  val op     = LsuOp()
  val addr   = UInt(32.W)
  val data   = UInt(32.W)   // write data (for stores)
  val rdAddr = UInt(5.W)    // destination register (for loads)
}

/** LSU response bundle. */
class LsuResp extends Bundle {
  val rdAddr = UInt(5.W)
  val data   = UInt(32.W)
  val fault  = Bool()
}

/** Data-bus interface from the LSU to the memory subsystem.
  *
  * This interface is used by both the L1 data cache and the direct-mapped
  * TCM path.  The bus is `lsuDataBits` wide to support vector loads/stores.
  */
class DBus(p: Parameters) extends Bundle {
  val valid = Output(Bool())
  val ready = Input(Bool())
  val write = Output(Bool())
  val addr  = Output(UInt(32.W))
  val size  = Output(UInt(3.W))           // log2(transfer bytes)
  val wdata = Output(UInt(p.lsuDataBits.W))
  val wmask = Output(UInt((p.lsuDataBits / 8).W))
  val rdata = Input(UInt(p.lsuDataBits.W))
  val fault = Input(Bool())
}

/** Load-store unit.
  *
  * Translates scalar and (in RVV-enabled configurations) vector memory
  * operations into DBus transactions.  Presents load results via a
  * Decoupled output port.
  */
class Lsu(p: Parameters) extends Module {
  val io = IO(new Bundle {
    val cmd  = Flipped(Decoupled(new LsuCmd))
    val resp = Decoupled(new LsuResp)
    val dbus = new DBus(p)
  })

  // -----------------------------------------------------------------------
  // Minimal stub: forward the command to the data bus and echo back.
  // -----------------------------------------------------------------------
  val pendingValid  = RegInit(false.B)
  val pendingCmd    = Reg(new LsuCmd)
  val waitingResp   = RegInit(false.B)

  io.cmd.ready := !pendingValid

  when(io.cmd.valid && !pendingValid) {
    pendingValid := true.B
    pendingCmd   := io.cmd.bits
  }

  io.dbus.valid := pendingValid
  io.dbus.write := pendingCmd.op === LsuOp.SB ||
                   pendingCmd.op === LsuOp.SH ||
                   pendingCmd.op === LsuOp.SW ||
                   pendingCmd.op === LsuOp.FSW ||
                   pendingCmd.op === LsuOp.VST
  io.dbus.addr  := pendingCmd.addr
  io.dbus.size  := MuxLookup(pendingCmd.op, 2.U)(Seq(
    LsuOp.LB  -> 0.U, LsuOp.LBU -> 0.U, LsuOp.SB -> 0.U,
    LsuOp.LH  -> 1.U, LsuOp.LHU -> 1.U, LsuOp.SH -> 1.U,
    LsuOp.LW  -> 2.U, LsuOp.SW  -> 2.U,
    LsuOp.FLW -> 2.U, LsuOp.FSW -> 2.U,
    LsuOp.VLD -> log2Ceil(p.lsuDataBits / 8).U,
    LsuOp.VST -> log2Ceil(p.lsuDataBits / 8).U,
  ))
  io.dbus.wdata := pendingCmd.data
  io.dbus.wmask := "hF".U

  // Response
  val respValid = RegInit(false.B)
  val respData  = Reg(UInt(32.W))
  val respRd    = Reg(UInt(5.W))
  val respFault = Reg(Bool())

  when(pendingValid && io.dbus.ready) {
    pendingValid := false.B
    respValid    := true.B
    respData     := io.dbus.rdata(31, 0)
    respRd       := pendingCmd.rdAddr
    respFault    := io.dbus.fault
  }

  when(io.resp.ready && respValid) {
    respValid := false.B
  }

  io.resp.valid      := respValid
  io.resp.bits.rdAddr := respRd
  io.resp.bits.data  := respData
  io.resp.bits.fault := respFault
}
