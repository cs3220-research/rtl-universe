package common
import chisel3._
import chisel3.util._

class IDivReq extends Bundle {
  val dividend = UInt(32.W)
  val divisor  = UInt(32.W)
  val signed_  = Bool()
  val rem      = Bool()
}

class IDivResp extends Bundle {
  val result = UInt(32.W)
}

class IDiv extends Module {
  val io = IO(new Bundle {
    val req  = Flipped(Decoupled(new IDivReq))
    val resp = Decoupled(new IDivResp)
  })

  val busy   = RegInit(false.B)
  val result = RegInit(0.U(32.W))

  val dividend = io.req.bits.dividend
  val divisor  = io.req.bits.divisor

  val absDividend = Mux(io.req.bits.signed_ && dividend(31), ~dividend + 1.U, dividend)
  val absDivisor  = Mux(io.req.bits.signed_ && divisor(31),  ~divisor  + 1.U, divisor)
  val negResult   = io.req.bits.signed_ && (dividend(31) ^ divisor(31))

  val quot      = Mux(absDivisor === 0.U, ~0.U(32.W), absDividend / absDivisor)
  val rem       = Mux(absDivisor === 0.U, dividend,   absDividend % absDivisor)

  val quotSigned = Mux(negResult,                            ~quot + 1.U, quot)
  val remSigned  = Mux(io.req.bits.signed_ && dividend(31), ~rem  + 1.U, rem)

  io.req.ready        := !busy
  io.resp.valid       := busy
  io.resp.bits.result := RegNext(Mux(io.req.bits.rem, remSigned, quotSigned))

  when(io.req.fire)  { busy := true.B  }
  when(io.resp.fire) { busy := false.B }
}
