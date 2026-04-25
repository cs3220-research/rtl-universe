package common
import chisel3._
import chisel3.util._

abstract class IndexAllocator(capacity: Int) extends Module {
  val io = IO(new Bundle {
    val alloc = Decoupled(UInt(log2Ceil(capacity).W))
    val free  = Flipped(Valid(UInt(log2Ceil(capacity).W)))
  })
}

class IndexAllocatorShifting(capacity: Int) extends IndexAllocator(capacity) {
  val used = RegInit(0.U(capacity.W))

  // Find first free slot
  val freeSlot = PriorityEncoder(~used)
  val anyFree  = (~used).orR

  io.alloc.valid := anyFree
  io.alloc.bits  := freeSlot

  when(io.alloc.fire) {
    used := used | (1.U << freeSlot)
  }
  when(io.free.valid) {
    used := used & ~(1.U << io.free.bits)
  }
}
