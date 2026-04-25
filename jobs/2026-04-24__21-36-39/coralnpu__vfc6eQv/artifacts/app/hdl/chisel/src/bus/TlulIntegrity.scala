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

// =============================================================================
// SECDED Encoder
// =============================================================================
/**
  * SECDED (Single Error Correction, Double Error Detection) encoder.
  *
  * Computes ECC parity bits for a data word of arbitrary width.
  * The ECC uses a Hamming-based code with an extra overall parity bit.
  *
  * The number of parity bits required is ceil(log2(dataBits + numParityBits + 1)).
  * For 128-bit data, 8 parity bits are needed (7 Hamming + 1 overall).
  *
  * @param dataBits Width of the data word to protect.
  */
class SecdedEncoder(dataBits: Int) extends Module {
  // Number of Hamming parity bits needed
  val parBits = {
    var p = 1
    while ((1 << p) < dataBits + p + 1) p += 1
    p
  }
  val totalBits = dataBits + parBits + 1  // +1 for overall parity

  val io = IO(new Bundle {
    val data_i = Input(UInt(dataBits.W))
    val ecc_o  = Output(UInt(7.W))  // always 7 bits output (padded if fewer needed)
  })

  // Build the codeword: insert parity bits at power-of-2 positions
  // data bits occupy all non-power-of-2 positions (excluding position 0)
  val codeword = Wire(Vec(totalBits, Bool()))
  for (i <- 0 until totalBits) codeword(i) := false.B

  // Place data bits
  var dataIdx = 0
  for (pos <- 1 until totalBits) {
    // pos is 1-based; check if it's a power of 2
    val isPowerOf2 = (pos & (pos - 1)) == 0
    if (!isPowerOf2) {
      if (dataIdx < dataBits) {
        codeword(pos - 1) := io.data_i(dataIdx)
        dataIdx += 1
      }
    }
  }

  // Compute Hamming parity bits (each covers positions where bit j of pos is 1)
  val hammingPar = Wire(Vec(parBits, Bool()))
  for (j <- 0 until parBits) {
    val coveredBits = (1 until totalBits - 1).filter { pos =>
      (pos & (1 << j)) != 0 && (pos & (pos - 1)) != 0  // non-parity positions
    }.map(pos => if (pos - 1 < totalBits) codeword(pos - 1) else false.B)
    hammingPar(j) := coveredBits.foldLeft(false.B)(_ ^ _)
  }

  // Overall parity
  val allBits = Wire(Vec(parBits + dataBits, Bool()))
  for (j <- 0 until parBits) allBits(j) := hammingPar(j)
  for (i <- 0 until dataBits) allBits(parBits + i) := io.data_i(i)
  val overallPar = allBits.reduce(_ ^ _)

  // Pack ECC: [overall_par, hamming_par[parBits-1:0]]
  val eccBits = Wire(Vec(parBits + 1, Bool()))
  for (j <- 0 until parBits) eccBits(j) := hammingPar(j)
  eccBits(parBits) := overallPar

  // Output as 7-bit field (zero-padded if parBits+1 < 7)
  val eccOut = Wire(UInt(7.W))
  eccOut := 0.U
  for (j <- 0 to parBits) {
    if (j < 7) eccOut = eccOut | (eccBits(j).asUInt << j.U)
  }
  io.ecc_o := eccOut
}

// =============================================================================
// TL-UL Integrity: Request (Channel A) integrity generation and checking
// =============================================================================

/**
  * Generates integrity ECC for a TL-UL Channel A request.
  * Computes command integrity (over opcode/param/size/source/address/mask)
  * and data integrity (over data).
  */
class RequestIntegrityGen(p: TLULParameters) extends Module {
  val io = IO(new Bundle {
    val a_i = Input(new OpenTitanTileLink.A_Channel(p))
    val a_o = Output(new OpenTitanTileLink.A_Channel(p))
  })

  // Pass through all fields
  io.a_o := io.a_i

  // Compute command integrity: covers opcode(3) + param(3) + size(4) + source(srcBits) + addr(32) + mask(maskBits)
  val cmdWidth = 3 + 3 + p.sizeBits + p.sourceBits + p.addrBits + p.maskBits
  val cmdData  = Cat(
    io.a_i.mask,
    io.a_i.address,
    io.a_i.source,
    io.a_i.size,
    io.a_i.param,
    io.a_i.opcode
  )

  val cmdEnc = Module(new SecdedEncoder(cmdWidth.min(128)))
  cmdEnc.io.data_i := cmdData(cmdWidth.min(128) - 1, 0)
  io.a_o.user.cmd_intg := cmdEnc.io.ecc_o

  // Compute data integrity
  val dataEnc = Module(new SecdedEncoder(p.dataBits.min(128)))
  dataEnc.io.data_i := io.a_i.data(p.dataBits.min(128) - 1, 0)
  io.a_o.user.data_intg := dataEnc.io.ecc_o
}

/**
  * Checks integrity ECC for a TL-UL Channel A request.
  * Sets fault=true if any ECC mismatch is detected.
  */
class RequestIntegrityCheck(p: TLULParameters) extends Module {
  val io = IO(new Bundle {
    val a_i   = Input(new OpenTitanTileLink.A_Channel(p))
    val fault = Output(Bool())
  })

  // Recompute command integrity
  val cmdWidth = 3 + 3 + p.sizeBits + p.sourceBits + p.addrBits + p.maskBits
  val cmdData  = Cat(
    io.a_i.mask,
    io.a_i.address,
    io.a_i.source,
    io.a_i.size,
    io.a_i.param,
    io.a_i.opcode
  )

  val cmdEnc = Module(new SecdedEncoder(cmdWidth.min(128)))
  cmdEnc.io.data_i := cmdData(cmdWidth.min(128) - 1, 0)
  val cmdFault = (cmdEnc.io.ecc_o =/= io.a_i.user.cmd_intg) && (io.a_i.user.cmd_intg =/= 0.U)

  // Recompute data integrity
  val dataEnc = Module(new SecdedEncoder(p.dataBits.min(128)))
  dataEnc.io.data_i := io.a_i.data(p.dataBits.min(128) - 1, 0)
  val dataFault = (dataEnc.io.ecc_o =/= io.a_i.user.data_intg) && (io.a_i.user.data_intg =/= 0.U)

  io.fault := cmdFault || dataFault
}

// =============================================================================
// TL-UL Integrity: Response (Channel D) integrity generation and checking
// =============================================================================

/**
  * Generates integrity ECC for a TL-UL Channel D response.
  */
class ResponseIntegrityGen(p: TLULParameters) extends Module {
  val io = IO(new Bundle {
    val d_i = Input(new OpenTitanTileLink.D_Channel(p))
    val d_o = Output(new OpenTitanTileLink.D_Channel(p))
  })

  io.d_o := io.d_i

  // Response integrity (command part: opcode/param/size/source/sink)
  val rspCmdWidth = 3 + 3 + p.sizeBits + p.sourceBits + p.sinkBits
  val rspCmdData  = Cat(
    io.d_i.sink,
    io.d_i.source,
    io.d_i.size,
    io.d_i.param,
    io.d_i.opcode
  )

  val rspEnc = Module(new SecdedEncoder(rspCmdWidth.min(128)))
  rspEnc.io.data_i := rspCmdData(rspCmdWidth.min(128) - 1, 0)
  io.d_o.user.rsp_intg := rspEnc.io.ecc_o

  // Data integrity
  val dataEnc = Module(new SecdedEncoder(p.dataBits.min(128)))
  dataEnc.io.data_i := io.d_i.data(p.dataBits.min(128) - 1, 0)
  io.d_o.user.data_intg := dataEnc.io.ecc_o
}

/**
  * Checks integrity ECC for a TL-UL Channel D response.
  */
class ResponseIntegrityCheck(p: TLULParameters) extends Module {
  val io = IO(new Bundle {
    val d_i   = Input(new OpenTitanTileLink.D_Channel(p))
    val fault = Output(Bool())
  })

  val rspCmdWidth = 3 + 3 + p.sizeBits + p.sourceBits + p.sinkBits
  val rspCmdData  = Cat(
    io.d_i.sink,
    io.d_i.source,
    io.d_i.size,
    io.d_i.param,
    io.d_i.opcode
  )

  val rspEnc = Module(new SecdedEncoder(rspCmdWidth.min(128)))
  rspEnc.io.data_i := rspCmdData(rspCmdWidth.min(128) - 1, 0)
  val rspFault = (rspEnc.io.ecc_o =/= io.d_i.user.rsp_intg) && (io.d_i.user.rsp_intg =/= 0.U)

  val dataEnc = Module(new SecdedEncoder(p.dataBits.min(128)))
  dataEnc.io.data_i := io.d_i.data(p.dataBits.min(128) - 1, 0)
  val dataFault = (dataEnc.io.ecc_o =/= io.d_i.user.data_intg) && (io.d_i.user.data_intg =/= 0.U)

  io.fault := rspFault || dataFault
}
