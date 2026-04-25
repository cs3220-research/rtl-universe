package common
import chisel3._
import chisel3.util._

class Aligner[T <: Data](gen: T, n: Int) extends Module {
  val io = IO(new Bundle {
    val in  = Input(Vec(n, Valid(gen)))
    val out = Output(Vec(n, Valid(gen)))
  })

  // For each output position j, find the j-th valid input
  for (j <- 0 until n) {
    // For each input i, count how many valid inputs appear before it
    val validsBefore = (0 until n).map { i =>
      val countsBeforeI = (0 until i).map(k => io.in(k).valid.asUInt)
      if (countsBeforeI.isEmpty) 0.U else countsBeforeI.reduce(_ + _)
    }
    // Output j gets the input where validsBefore(i) == j and in(i).valid
    val candidates = (0 until n).map { i =>
      (io.in(i).valid && validsBefore(i) === j.U, io.in(i).bits)
    }
    val anyMatch = candidates.map(_._1).reduce(_ || _)
    val matchData = candidates.foldLeft(0.U.asTypeOf(gen)) { case (acc, (sel, data)) =>
      Mux(sel, data, acc)
    }
    io.out(j).valid := anyMatch
    io.out(j).bits  := matchData
  }
}
