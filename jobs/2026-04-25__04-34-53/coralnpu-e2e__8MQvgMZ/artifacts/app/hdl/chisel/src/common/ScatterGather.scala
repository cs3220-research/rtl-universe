package common
import chisel3._

object Gather {
  def apply[T <: Data](indices: Seq[UInt], data: Vec[T]): Vec[T] = {
    VecInit(indices.map(i => data(i)))
  }
}

object Scatter {
  // Returns (result, writeMask, indicesSelected)
  def apply[T <: Data](
    indicesValid: Vec[Bool],
    indices: Vec[UInt],
    data: Vec[T]
  ): (Vec[T], Vec[Bool], Vec[Bool]) = {
    val n = data.length
    val result = Wire(Vec(n, data.head.cloneType))
    val writeMask = Wire(Vec(n, Bool()))
    val indicesSelected = Wire(Vec(indices.length, Bool()))
    result := 0.U.asTypeOf(Vec(n, data.head.cloneType))
    writeMask := VecInit(Seq.fill(n)(false.B))
    indicesSelected := VecInit(Seq.fill(indices.length)(false.B))
    for (i <- 0 until indices.length) {
      when(indicesValid(i)) {
        result(indices(i)) := data(i)
        writeMask(indices(i)) := true.B
        indicesSelected(i) := true.B
      }
    }
    (result, writeMask, indicesSelected)
  }
}
