package common
import chisel3._
import chisel3.util._

class CoralNPURRArbiter[T <: Data](gen: T, n: Int) extends Module {
  val io = IO(new Bundle {
    val in     = Vec(n, Flipped(Decoupled(gen)))
    val out    = Decoupled(gen)
    val chosen = Output(UInt(log2Ceil(n).W))
  })

  val priority = RegInit(0.U(log2Ceil(n).W))

  // Round-robin: start from priority, find first valid
  val anyValid = io.in.map(_.valid).reduce(_ || _)

  // Priority encoder starting from priority
  val rotated       = VecInit((0 until n).map(i => io.in((i.U + priority) % n.U).valid))
  val rotatedChosen = PriorityEncoder(rotated)
  val chosen        = (rotatedChosen + priority) % n.U

  io.chosen    := chosen
  io.out.valid := anyValid
  io.out.bits  := io.in(chosen).bits

  for (i <- 0 until n) {
    io.in(i).ready := io.out.ready && chosen === i.U && anyValid
  }

  when(io.out.fire) {
    priority := (chosen + 1.U) % n.U
  }
}
