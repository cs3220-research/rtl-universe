package common
import chisel3._
import chisel3.util._

class FIFOState[T <: Data](val capacity: Int, val gen: T) extends Bundle {
  val mem  = Vec(capacity, gen)
  val head = UInt(log2Ceil(capacity + 1).W)
  val tail = UInt(log2Ceil(capacity + 1).W)
  val cnt  = UInt(log2Ceil(capacity + 1).W)

  def count: UInt = cnt

  def peek(n: Int): Vec[T] = {
    val out = Wire(Vec(n, gen))
    for (i <- 0 until n) {
      out(i) := mem((head + i.U) % capacity.U)
    }
    out
  }

  def enqueue(data: Vec[T], nValid: UInt): FIFOState[T] = {
    val next = Wire(new FIFOState(capacity, gen))
    next := this
    val avail = Mux((capacity.U - cnt) < nValid, capacity.U - cnt, nValid)
    for (i <- 0 until capacity) {
      when(i.U < avail) {
        next.mem((tail + i.U) % capacity.U) := data(i)
      }
    }
    next.tail := (tail + avail) % capacity.U
    next.cnt  := cnt + avail
    next
  }

  def dequeue(nReady: UInt): FIFOState[T] = {
    val next = Wire(new FIFOState(capacity, gen))
    next := this
    val actual = Mux(cnt < nReady, cnt, nReady)
    next.head := (head + actual) % capacity.U
    next.cnt  := cnt - actual
    next
  }

  def flush(): FIFOState[T] = {
    val next = Wire(new FIFOState(capacity, gen))
    next      := this
    next.head := 0.U
    next.tail := 0.U
    next.cnt  := 0.U
    next
  }

  def invariant(): Bool = cnt <= capacity.U
}

object FIFOState {
  def init[T <: Data](capacity: Int, gen: T): FIFOState[T] = {
    val s = Wire(new FIFOState(capacity, gen))
    s.mem  := 0.U.asTypeOf(Vec(capacity, gen))
    s.head := 0.U
    s.tail := 0.U
    s.cnt  := 0.U
    s
  }
}
