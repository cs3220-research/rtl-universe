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

/** TileLink-UL width adapter.
  *
  * Adapts between a host-side TL-UL bus of width [[hostWidth]] bits and a
  * device-side bus of width [[deviceWidth]] bits.
  *
  * === Narrowing (hostWidth > deviceWidth) ===
  * A single host-side beat is split into `ratio` device-side beats.  The
  * outgoing A-channel beats carry consecutive sub-words with appropriate
  * byte-enable masks.  D-channel responses are reassembled before being
  * returned to the host.
  *
  * === Widening (hostWidth < deviceWidth) ===
  * The host beat is zero-extended and forwarded as a single device beat.
  * The D-channel response is truncated/extracted.
  *
  * This is a simplified implementation that handles the common case of
  * power-of-two width ratios.
  *
  * @param hostWidth    Width of the host-side data bus in bits.
  * @param deviceWidth  Width of the device-side data bus in bits.
  */
class TlulWidthBridge(hostWidth: Int, deviceWidth: Int) extends Module {
  require(hostWidth > 0 && deviceWidth > 0)
  require(
    hostWidth % 8 == 0 && deviceWidth % 8 == 0,
    "Bus widths must be byte-aligned"
  )

  val hostP = new TLULParameters(new coralnpu.Parameters {
    lsuDataBits = hostWidth
  })
  val devP = new TLULParameters(new coralnpu.Parameters {
    lsuDataBits = deviceWidth
  })

  val io = IO(new Bundle {
    val host   = new OpenTitanTileLink.Device2Host(hostP)
    val device = new OpenTitanTileLink.Host2Device(devP)
  })

  if (hostWidth == deviceWidth) {
    // -----------------------------------------------------------------------
    // No-op bridge: directly connect
    // -----------------------------------------------------------------------
    io.host.a.ready  := io.device.a.ready
    io.device.a.valid := io.host.a.valid
    io.device.a.bits.opcode  := io.host.a.bits.opcode
    io.device.a.bits.param   := io.host.a.bits.param
    io.device.a.bits.size    := io.host.a.bits.size
    io.device.a.bits.source  := io.host.a.bits.source
    io.device.a.bits.address := io.host.a.bits.address
    io.device.a.bits.mask    := io.host.a.bits.mask
    io.device.a.bits.data    := io.host.a.bits.data
    io.device.a.bits.user    := 0.U.asTypeOf(new OpenTitanTileLink_A_User)
    io.device.a.bits.corrupt := io.host.a.bits.corrupt

    io.device.d.ready := io.host.d.ready
    io.host.d.valid   := io.device.d.valid
    io.host.d.bits.opcode  := io.device.d.bits.opcode
    io.host.d.bits.param   := io.device.d.bits.param
    io.host.d.bits.size    := io.device.d.bits.size
    io.host.d.bits.source  := io.device.d.bits.source
    io.host.d.bits.sink    := io.device.d.bits.sink
    io.host.d.bits.data    := io.device.d.bits.data
    io.host.d.bits.user    := 0.U.asTypeOf(new OpenTitanTileLink_D_User)
    io.host.d.bits.error   := io.device.d.bits.error
    io.host.d.bits.corrupt := io.device.d.bits.corrupt

  } else if (deviceWidth > hostWidth) {
    // -----------------------------------------------------------------------
    // Widening bridge: host is narrower, device is wider
    // -----------------------------------------------------------------------
    val ratio      = deviceWidth / hostWidth
    val ratioBits  = log2Ceil(ratio)

    io.host.a.ready  := io.device.a.ready
    io.device.a.valid := io.host.a.valid
    io.device.a.bits.opcode  := io.host.a.bits.opcode
    io.device.a.bits.param   := io.host.a.bits.param
    io.device.a.bits.size    := io.host.a.bits.size
    io.device.a.bits.source  := io.host.a.bits.source
    io.device.a.bits.address := io.host.a.bits.address
    io.device.a.bits.mask    := io.host.a.bits.mask.pad(devP.maskBits)
    io.device.a.bits.data    := io.host.a.bits.data.pad(deviceWidth)
    io.device.a.bits.user    := 0.U.asTypeOf(new OpenTitanTileLink_A_User)
    io.device.a.bits.corrupt := io.host.a.bits.corrupt

    io.device.d.ready := io.host.d.ready
    io.host.d.valid   := io.device.d.valid
    io.host.d.bits.opcode  := io.device.d.bits.opcode
    io.host.d.bits.param   := io.device.d.bits.param
    io.host.d.bits.size    := io.device.d.bits.size
    io.host.d.bits.source  := io.device.d.bits.source
    io.host.d.bits.sink    := io.device.d.bits.sink
    io.host.d.bits.data    := io.device.d.bits.data(hostWidth - 1, 0)
    io.host.d.bits.user    := 0.U.asTypeOf(new OpenTitanTileLink_D_User)
    io.host.d.bits.error   := io.device.d.bits.error
    io.host.d.bits.corrupt := io.device.d.bits.corrupt

  } else {
    // -----------------------------------------------------------------------
    // Narrowing bridge: host is wider, device is narrower
    // -----------------------------------------------------------------------
    val ratio     = hostWidth / deviceWidth
    val ratioBits = log2Ceil(ratio)

    val sIdle :: sBurst :: sCollect :: Nil = Enum(3)
    val state    = RegInit(sIdle)
    val beatCnt  = RegInit(0.U(ratioBits.W))

    // Latched host request
    val hostA    = Reg(new OpenTitanTileLink.A_Channel(hostP))
    // Response accumulation
    val rspData  = Reg(UInt(hostWidth.W))
    val rspErr   = RegInit(false.B)

    // Device-side A channel
    io.device.a.valid            := false.B
    io.device.a.bits             := 0.U.asTypeOf(new OpenTitanTileLink.A_Channel(devP))
    io.device.d.ready            := false.B
    io.host.a.ready              := false.B
    io.host.d.valid              := false.B
    io.host.d.bits               := 0.U.asTypeOf(new OpenTitanTileLink.D_Channel(hostP))

    switch(state) {
      is(sIdle) {
        io.host.a.ready := true.B
        when(io.host.a.valid) {
          hostA   := io.host.a.bits
          beatCnt := 0.U
          rspData := 0.U
          rspErr  := false.B
          state   := sBurst
        }
      }

      is(sBurst) {
        val byteOff   = beatCnt ## 0.U(log2Ceil(deviceWidth / 8).W)
        val subAddr   = hostA.address + byteOff.pad(hostP.addrBits)
        val subMask   = hostA.mask(deviceWidth / 8 - 1 + (beatCnt * (deviceWidth / 8).U)(log2Ceil(hostWidth/8)-1, 0),
                                   (beatCnt * (deviceWidth / 8).U)(log2Ceil(hostWidth/8)-1, 0))
        val subData   = hostA.data >> (beatCnt * deviceWidth.U)

        io.device.a.valid           := true.B
        io.device.a.bits.opcode     := hostA.opcode
        io.device.a.bits.param      := hostA.param
        io.device.a.bits.size       := (hostA.size - ratioBits.U)
        io.device.a.bits.source     := hostA.source
        io.device.a.bits.address    := subAddr(hostP.addrBits - 1, 0)
        io.device.a.bits.mask       := hostA.mask >> (beatCnt * (deviceWidth / 8).U)
        io.device.a.bits.data       := (hostA.data >> (beatCnt * deviceWidth.U))(deviceWidth - 1, 0)
        io.device.a.bits.user       := 0.U.asTypeOf(new OpenTitanTileLink_A_User)
        io.device.a.bits.corrupt    := hostA.corrupt

        when(io.device.a.ready) {
          state := sCollect
        }
      }

      is(sCollect) {
        io.device.d.ready := true.B
        when(io.device.d.valid) {
          val shift   = beatCnt * deviceWidth.U
          rspData     := rspData | (io.device.d.bits.data << shift)
          rspErr      := rspErr || io.device.d.bits.error
          when(beatCnt === (ratio - 1).U) {
            // All sub-beats received — return to host
            io.host.d.valid            := true.B
            io.host.d.bits.opcode      := io.device.d.bits.opcode
            io.host.d.bits.param       := io.device.d.bits.param
            io.host.d.bits.size        := hostA.size
            io.host.d.bits.source      := hostA.source
            io.host.d.bits.sink        := io.device.d.bits.sink
            io.host.d.bits.data        := rspData | (io.device.d.bits.data << shift)
            io.host.d.bits.user        := 0.U.asTypeOf(new OpenTitanTileLink_D_User)
            io.host.d.bits.error       := rspErr || io.device.d.bits.error
            io.host.d.bits.corrupt     := io.device.d.bits.corrupt
            when(io.host.d.ready) {
              state   := sIdle
              beatCnt := 0.U
            }
          }.otherwise {
            beatCnt := beatCnt + 1.U
            state   := sBurst
          }
        }
      }
    }
  }
}
