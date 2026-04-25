package common
import chisel3._
import chisel3.util._

class FmaCmd extends Bundle {
  val ina = new Fp32
  val inb = new Fp32
  val inc = new Fp32
}

class FmaState1 extends Bundle {
  val cmd      = new FmaCmd
  val expA     = UInt(9.W)
  val expB     = UInt(9.W)
  val expSum   = UInt(9.W)
  val signProd = Bool()
}

class FmaState2 extends Bundle {
  val state1   = new FmaState1
  val mantProd = UInt(48.W)
  val alignedC = UInt(48.W)
  val signC    = Bool()
}

object Fma {
  class FmaStage1 extends Module {
    val io = IO(new Bundle {
      val in  = Input(Valid(new FmaCmd))
      val out = Output(Valid(new FmaState1))
    })
    io.out.valid    := io.in.valid
    val s            = io.out.bits
    s.cmd           := io.in.bits
    s.expA          := io.in.bits.ina.exponent
    s.expB          := io.in.bits.inb.exponent
    s.expSum        := (io.in.bits.ina.exponent +& io.in.bits.inb.exponent) - 127.U
    s.signProd      := io.in.bits.ina.sign ^ io.in.bits.inb.sign
  }

  class FmaStage2 extends Module {
    val io = IO(new Bundle {
      val in  = Input(Valid(new FmaState1))
      val out = Output(Valid(new FmaState2))
    })
    io.out.valid := io.in.valid
    val s         = io.out.bits
    s.state1     := io.in.bits
    val mA        = Cat(1.U(1.W), io.in.bits.cmd.ina.mantissa)  // 24 bits
    val mB        = Cat(1.U(1.W), io.in.bits.cmd.inb.mantissa)  // 24 bits
    s.mantProd   := mA * mB
    s.alignedC   := (Cat(1.U(1.W), io.in.bits.cmd.inc.mantissa) << 23.U)(47, 0)
    s.signC      := io.in.bits.cmd.inc.sign
  }

  class FmaStage3 extends Module {
    val io = IO(new Bundle {
      val in  = Input(Valid(new FmaState2))
      val out = Output(Valid(new Fp32))
    })
    io.out.valid := io.in.valid
    val result    = Wire(new Fp32)
    // Simplified: normalise the product mantissa
    val prod      = io.in.bits.mantProd
    val lz        = PriorityEncoder(Reverse(prod(47, 0)))
    result.sign     := io.in.bits.state1.signProd
    result.exponent := (io.in.bits.state1.expSum + 1.U - lz)(7, 0)
    result.mantissa := prod(46, 24)
    io.out.bits := result
  }
}
