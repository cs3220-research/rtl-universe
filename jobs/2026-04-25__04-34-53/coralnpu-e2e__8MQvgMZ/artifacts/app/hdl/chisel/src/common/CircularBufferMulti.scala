package common
import chisel3._
import chisel3.util._

class CircularBufferMulti[T <: Data](gen: T, n: Int, capacity: Int) extends Module {
  val io = IO(new Bundle {
    val enqValid  = Input(UInt(log2Ceil(n + 1).W))
    val enqData   = Input(Vec(n, gen))
    val deqReady  = Input(UInt(log2Ceil(n + 1).W))
    val dataOut   = Output(Vec(n, gen))
    val nEnqueued = Output(UInt(log2Ceil(capacity + 1).W))
    val flush     = Input(Bool())
  })

  val buf   = RegInit(VecInit(Seq.fill(capacity)(0.U.asTypeOf(gen))))
  val head  = RegInit(0.U(log2Ceil(capacity).W))
  val tail  = RegInit(0.U(log2Ceil(capacity).W))
  val count = RegInit(0.U(log2Ceil(capacity + 1).W))

  io.nEnqueued := count

  for (i <- 0 until n) {
    io.dataOut(i) := buf((head + i.U) % capacity.U)
  }

  when(io.flush) {
    head  := 0.U
    tail  := 0.U
    count := 0.U
  } .otherwise {
    val enqAmt = Mux(io.enqValid > (capacity.U - count), capacity.U - count, io.enqValid)
    val deqAmt = Mux(io.deqReady > count, count, io.deqReady)
    for (i <- 0 until n) {
      when(i.U < enqAmt) {
        buf((tail + i.U) % capacity.U) := io.enqData(i)
      }
    }
    tail  := (tail + enqAmt) % capacity.U
    head  := (head + deqAmt) % capacity.U
    count := count + enqAmt - deqAmt
  }
}
