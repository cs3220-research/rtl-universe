package common
import chisel3._
import chisel3.util._

class Fp32 extends Bundle {
  val sign     = Bool()
  val exponent = UInt(8.W)
  val mantissa = UInt(23.W)

  def isZero: Bool = exponent === 0.U && mantissa === 0.U
  def isInf:  Bool = exponent === 255.U && mantissa === 0.U
  def isNan:  Bool = exponent === 255.U && mantissa =/= 0.U

  def asWord: UInt = Cat(sign, exponent, mantissa)
}

object Fp32 {
  def fromWord(w: UInt): Fp32 = {
    val f = Wire(new Fp32)
    f.sign     := w(31)
    f.exponent := w(30, 23)
    f.mantissa := w(22, 0)
    f
  }

  def fromInteger(value: UInt, signed: Bool): Fp32 = {
    // Simple integer to float conversion
    val f = Wire(new Fp32)
    val absVal = Mux(signed && value(31), (~value + 1.U), value)
    val lz = PriorityEncoder(Reverse(absVal))
    f.sign     := signed && value(31)
    f.exponent := Mux(absVal === 0.U, 0.U, (127.U + 31.U - lz)(7, 0))
    f.mantissa := Mux(absVal === 0.U, 0.U, (absVal << (lz + 1.U))(30, 8))
    f
  }
}
