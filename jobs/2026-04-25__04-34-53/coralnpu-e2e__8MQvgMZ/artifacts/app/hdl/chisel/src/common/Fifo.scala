package common
import chisel3._
import chisel3.util._

class Fifo[T <: Data](gen: T, depth: Int) extends Module {
  val io = IO(new Bundle {
    val enq = Flipped(Decoupled(gen))
    val deq = Decoupled(gen)
  })
  val q = Module(new Queue(gen, depth))
  q.io.enq <> io.enq
  io.deq   <> q.io.deq
}
