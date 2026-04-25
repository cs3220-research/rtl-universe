package common
import chisel3._
import chisel3.util._

class Slice[T <: Data](gen: T) extends Module {
  val io = IO(new Bundle {
    val in  = Flipped(Decoupled(gen))
    val out = Decoupled(gen)
  })
  val reg = RegInit(0.U.asTypeOf(Valid(gen)))
  when(io.out.fire) { reg.valid := false.B }
  when(io.in.fire)  { reg.valid := true.B; reg.bits := io.in.bits }
  io.out.valid := reg.valid
  io.out.bits  := reg.bits
  io.in.ready  := !reg.valid || io.out.fire
}
