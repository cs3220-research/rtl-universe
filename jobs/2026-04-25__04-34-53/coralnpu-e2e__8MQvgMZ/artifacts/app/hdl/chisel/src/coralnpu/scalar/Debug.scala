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

// Simple debug module interface.
// op=1 reads a register (address 0-31) or the PC (address 32).
// op=2 writes a register.
class Debug(p: Parameters) extends Module {
  val io = IO(new Bundle {
    val req    = Flipped(Decoupled(new DebugModuleReq))
    val rsp    = Decoupled(new DebugModuleRsp)

    // Register file visibility: 32 register values fed in from the CPU
    val regfile_read = Vec(32, Input(UInt(32.W)))

    // Register write output
    val regfile_write = Valid(new Bundle {
      val addr = UInt(5.W)
      val data = UInt(32.W)
    })

    val pc     = Input(UInt(32.W))
    val halted = Input(Bool())
  })

  // 1-entry pipeline
  val reqReg  = RegInit(0.U.asTypeOf(new DebugModuleReq))
  val pending = RegInit(false.B)
  val rspData = RegInit(0.U(32.W))
  val rspOp   = RegInit(0.U(2.W))

  io.req.ready := !pending

  when (io.req.fire) {
    reqReg  := io.req.bits
    pending := true.B

    when (io.req.bits.op === 1.U) {
      val addr = io.req.bits.address
      rspData := Mux(addr === 32.U, io.pc,
                 Mux(addr < 32.U,  io.regfile_read(addr(4,0)), 0.U))
      rspOp   := 0.U  // success
    } .otherwise {
      rspData := 0.U
      rspOp   := 0.U
    }
  }

  // Write output
  io.regfile_write.valid     := pending && (reqReg.op === 2.U)
  io.regfile_write.bits.addr := reqReg.address(4,0)
  io.regfile_write.bits.data := reqReg.data

  // Response
  io.rsp.valid     := pending
  io.rsp.bits.data := rspData
  io.rsp.bits.op   := rspOp

  when (io.rsp.fire) {
    pending := false.B
  }
}
