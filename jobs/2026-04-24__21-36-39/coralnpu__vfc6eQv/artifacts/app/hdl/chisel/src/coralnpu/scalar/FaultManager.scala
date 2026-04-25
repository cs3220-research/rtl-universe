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

/** Cause codes for RISC-V mcause register (exception = bit 31 clear). */
object ExceptionCause {
  val InstructionAddressMisaligned = 0.U
  val InstructionAccessFault       = 1.U
  val IllegalInstruction           = 2.U
  val Breakpoint                   = 3.U
  val LoadAddressMisaligned        = 4.U
  val LoadAccessFault              = 5.U
  val StoreAddressMisaligned       = 6.U
  val StoreAccessFault             = 7.U
  val EnvironmentCallFromMMode     = 11.U
}

/** Fault / exception input from a pipeline stage. */
class FaultInput extends Bundle {
  val valid  = Bool()
  val cause  = UInt(32.W)   // MCAUSE value
  val tval   = UInt(32.W)   // MTVAL value (fault address or instruction)
  val pc     = UInt(32.W)   // PC of faulting instruction
}

/** Trap resolution output to the core pipeline. */
class TrapOutput extends Bundle {
  val valid  = Bool()
  val target = UInt(32.W)  // redirect PC (mtvec)
  val mepc   = UInt(32.W)  // save PC (for mret)
  val cause  = UInt(32.W)
  val tval   = UInt(32.W)
}

/** Fault / exception manager.
  *
  * Receives fault indications from multiple pipeline sources (LSU, decode,
  * fetch), arbitrates them, and drives the trap-handling redirect.
  */
class FaultManager(p: Parameters) extends Module {
  val NumSources = 4  // fetch, decode, execute, LSU

  val io = IO(new Bundle {
    val faults  = Input(Vec(NumSources, new FaultInput))
    val mtvec   = Input(UInt(32.W))   // from CSR unit
    val trap    = Output(new TrapOutput)
    val fault   = Output(Bool())      // fault asserted (for external status)
  })

  // Priority: source 0 (fetch) has highest priority
  val anyFault = io.faults.map(_.valid).reduce(_ || _)
  val firstFault = io.faults.indexWhere(_.valid)

  // Latch the first detected fault
  val faultLatched = RegInit(false.B)
  val faultCause   = Reg(UInt(32.W))
  val faultTval    = Reg(UInt(32.W))
  val faultPC      = Reg(UInt(32.W))

  when(!faultLatched && anyFault) {
    faultLatched := true.B
    faultCause   := io.faults(firstFault).cause
    faultTval    := io.faults(firstFault).tval
    faultPC      := io.faults(firstFault).pc
  }

  io.fault       := faultLatched
  io.trap.valid  := anyFault && !faultLatched
  io.trap.target := io.mtvec
  io.trap.mepc   := Mux(anyFault, io.faults(firstFault).pc, faultPC)
  io.trap.cause  := Mux(anyFault, io.faults(firstFault).cause, faultCause)
  io.trap.tval   := Mux(anyFault, io.faults(firstFault).tval, faultTval)
  io.trap.mepc   := Mux(anyFault, io.faults(firstFault).pc, faultPC)
}
