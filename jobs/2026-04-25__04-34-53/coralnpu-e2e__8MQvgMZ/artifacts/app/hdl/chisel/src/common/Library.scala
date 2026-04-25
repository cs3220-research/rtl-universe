package common
import chisel3._
import chisel3.util._

class ForceZero[T <: Data](gen: T) extends Module {
  val io = IO(new Bundle {
    val in = Input(Valid(gen))
    val out = Output(gen)
  })
  io.out := Mux(io.in.valid, io.in.bits, 0.U.asTypeOf(gen))
}

// Zip32: interleave two sz-word values
// sz=4 (word), sz=2 (half), sz=1 (byte)
object Zip32 {
  def apply(sz: Int, a: UInt, b: UInt): UInt = {
    val result = Wire(UInt(64.W))
    val chunks = 32 / (sz * 8)
    val chunkBits = sz * 8
    val parts = (0 until chunks).map { i =>
      Cat(b(i*chunkBits + chunkBits-1, i*chunkBits), a(i*chunkBits + chunkBits-1, i*chunkBits))
    }
    result := parts.foldRight(0.U(64.W))((a, b) => Cat(a, b))
    result
  }
}

class RotateVectorLeft[T <: Data](gen: T, n: Int) extends Module {
  val io = IO(new Bundle {
    val in = Input(Vec(n, gen))
    val shift = Input(UInt(log2Ceil(n).W))
    val out = Output(Vec(n, gen))
  })
  for (i <- 0 until n) {
    io.out(i) := io.in((i.U + io.shift) % n.U)
  }
}

class RotateVectorRight[T <: Data](gen: T, n: Int) extends Module {
  val io = IO(new Bundle {
    val in = Input(Vec(n, gen))
    val shift = Input(UInt(log2Ceil(n).W))
    val out = Output(Vec(n, gen))
  })
  for (i <- 0 until n) {
    io.out(i) := io.in((i.U + n.U - io.shift) % n.U)
  }
}

class ShiftVectorLeft[T <: Data](gen: T, n: Int) extends Module {
  val io = IO(new Bundle {
    val in = Input(Vec(n, gen))
    val shift = Input(UInt(log2Ceil(n+1).W))
    val out = Output(Vec(n, gen))
  })
  for (i <- 0 until n) {
    val idx = i.U + io.shift
    io.out(i) := Mux(idx < n.U, io.in(idx), 0.U.asTypeOf(gen))
  }
}

class ShiftVectorRight[T <: Data](gen: T, n: Int) extends Module {
  val io = IO(new Bundle {
    val in = Input(Vec(n, gen))
    val shift = Input(UInt(log2Ceil(n+1).W))
    val out = Output(Vec(n, gen))
  })
  for (i <- 0 until n) {
    io.out(i) := Mux(io.shift <= i.U, io.in(i.U - io.shift), 0.U.asTypeOf(gen))
  }
}
