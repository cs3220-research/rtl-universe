package common
import chisel3._
import chisel3.util._

class FifoXe[T <: Data](gen: T, n: Int, depth: Int) extends Module {
  val io = IO(new Bundle {
    val enq   = Flipped(Decoupled(Vec(n, gen)))
    val deq   = Decoupled(gen)
    val count = Output(UInt(log2Ceil(depth * n + 1).W))
    val flush = Input(Bool())
  })
  // Flatten n-wide input to single-element output via an internal queue
  val buf = Module(new Queue(gen, depth * n))

  // Enqueue each word from the n-wide input one at a time using a counter
  val enqSel = RegInit(0.U(log2Ceil(n).W))

  buf.io.enq.valid := io.enq.valid
  buf.io.enq.bits  := io.enq.bits(enqSel)
  // Only propagate ready when we've consumed all n words
  io.enq.ready := buf.io.enq.ready

  io.deq   <> buf.io.deq
  io.count := buf.io.count
}
