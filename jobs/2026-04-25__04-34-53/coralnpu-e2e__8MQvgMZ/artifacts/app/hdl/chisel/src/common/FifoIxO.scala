package common
import chisel3._
import chisel3.util._

class FifoIxO[T <: Data](gen: T, n: Int, depth: Int) extends Module {
  val io = IO(new Bundle {
    val enq   = Vec(n, Flipped(Decoupled(gen)))
    val deq   = Decoupled(gen)
    val count = Output(UInt(log2Ceil(depth + 1).W))
  })
  val buf   = RegInit(VecInit(Seq.fill(depth)(0.U.asTypeOf(gen))))
  val head  = RegInit(0.U(log2Ceil(depth).W))
  val tail  = RegInit(0.U(log2Ceil(depth).W))
  val count = RegInit(0.U(log2Ceil(depth + 1).W))
  io.count := count
  for (i <- 0 until n) io.enq(i).ready := count < depth.U
  val enqIdx = PriorityEncoder(io.enq.map(_.valid))
  val anyEnq = io.enq.map(_.valid).reduce(_ || _)
  when(anyEnq && count < depth.U) {
    buf(tail) := io.enq(enqIdx).bits
    tail      := (tail + 1.U) % depth.U
    count     := count + 1.U
  }
  io.deq.valid := count > 0.U
  io.deq.bits  := buf(head)
  when(io.deq.fire) {
    head  := (head + 1.U) % depth.U
    count := count - 1.U
  }
}
