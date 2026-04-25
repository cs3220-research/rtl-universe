package common
import chisel3._
import chisel3.util._

class InstructionBuffer[T <: Data](gen: T, n: Int, window: Int) extends Module {
  val io = IO(new Bundle {
    val feedIn = new Bundle {
      val nValid  = Input(UInt(log2Ceil(n + 1).W))
      val nReady  = Output(UInt(log2Ceil(n + 1).W))
      val bits    = Input(Vec(n, gen))
    }
    val out       = Vec(n, Decoupled(gen))
    val nEnqueued = Output(UInt(log2Ceil(window + 1).W))
    val nSpace    = Output(UInt(log2Ceil(window + 1).W))
    val flush     = Input(Bool())
  })

  val buf   = RegInit(VecInit(Seq.fill(window)(0.U.asTypeOf(gen))))
  val head  = RegInit(0.U(log2Ceil(window).W))
  val count = RegInit(0.U(log2Ceil(window + 1).W))

  io.nEnqueued := count
  io.nSpace    := window.U - count

  val canEnq = Mux(io.feedIn.nValid < (window.U - count), io.feedIn.nValid, window.U - count)
  io.feedIn.nReady := canEnq

  when(io.flush) {
    count := 0.U
    head  := 0.U
  } .otherwise {
    for (i <- 0 until n) {
      when(i.U < canEnq) {
        buf((head + count + i.U) % window.U) := io.feedIn.bits(i)
      }
    }
    val deqCount = PopCount(io.out.map(_.fire))
    count := count + canEnq - deqCount
    head  := (head + deqCount) % window.U
  }

  for (i <- 0 until n) {
    io.out(i).valid := i.U < count
    io.out(i).bits  := buf((head + i.U) % window.U)
  }
}
