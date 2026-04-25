package common
import chisel3._
import chisel3.util._

class FifoX[T <: Data](gen: T, n: Int, depth: Int) extends Module {
  val io = IO(new Bundle {
    val enq   = Flipped(Decoupled(Vec(n, gen)))
    val deq   = Decoupled(Vec(n, gen))
    val count = Output(UInt(log2Ceil(depth + 1).W))
  })
  val buf   = RegInit(VecInit(Seq.fill(depth)(0.U.asTypeOf(Vec(n, gen)))))
  val head  = RegInit(0.U(log2Ceil(depth).W))
  val tail  = RegInit(0.U(log2Ceil(depth).W))
  val count = RegInit(0.U(log2Ceil(depth + 1).W))
  io.count     := count
  io.enq.ready := count < depth.U
  io.deq.valid := count > 0.U
  io.deq.bits  := buf(head)
  when(io.enq.fire) {
    buf(tail) := io.enq.bits
    tail      := (tail + 1.U) % depth.U
    count     := count + 1.U
  }
  when(io.deq.fire) {
    head  := (head + 1.U) % depth.U
    count := count - 1.U
  }
}
