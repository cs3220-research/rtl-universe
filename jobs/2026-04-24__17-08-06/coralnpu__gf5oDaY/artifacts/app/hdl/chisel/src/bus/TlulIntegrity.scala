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

package bus

import chisel3._

/**
 * SECDED encoder for ECC protection.
 * Computes parity bits for the given data width.
 */
class SecdedEncoder(w: Int) extends Module {
  val io = IO(new Bundle {
    val data_i = Input(UInt(w.W))
    val ecc_o  = Output(UInt(7.W))
  })
  // Stub: output zeros (real implementation would compute parity)
  io.ecc_o := 0.U
}

/**
 * RequestIntegrityGen: adds integrity ECC bits to a TL-UL Channel A message.
 */
class RequestIntegrityGen(p: TLULParameters) extends Module {
  val io = IO(new Bundle {
    val a_i = Input(new OpenTitanTileLink.A_Channel(p))
    val a_o = Output(new OpenTitanTileLink.A_Channel(p))
  })
  // Pass through (stub)
  io.a_o := io.a_i
}

/**
 * RequestIntegrityCheck: checks integrity ECC bits on a TL-UL Channel A message.
 */
class RequestIntegrityCheck(p: TLULParameters) extends Module {
  val io = IO(new Bundle {
    val a_i   = Input(new OpenTitanTileLink.A_Channel(p))
    val fault = Output(Bool())
  })
  // Stub: never fault
  io.fault := false.B
}

/**
 * ResponseIntegrityGen: adds integrity ECC bits to a TL-UL Channel D message.
 */
class ResponseIntegrityGen(p: TLULParameters) extends Module {
  val io = IO(new Bundle {
    val d_i = Input(new OpenTitanTileLink.D_Channel(p))
    val d_o = Output(new OpenTitanTileLink.D_Channel(p))
  })
  // Pass through (stub)
  io.d_o := io.d_i
}

/**
 * ResponseIntegrityCheck: checks integrity ECC bits on a TL-UL Channel D message.
 */
class ResponseIntegrityCheck(p: TLULParameters) extends Module {
  val io = IO(new Bundle {
    val d_i   = Input(new OpenTitanTileLink.D_Channel(p))
    val fault = Output(Bool())
  })
  // Stub: never fault
  io.fault := false.B
}
