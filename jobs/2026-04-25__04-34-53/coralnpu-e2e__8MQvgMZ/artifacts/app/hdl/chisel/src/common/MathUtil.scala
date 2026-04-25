package common

object MathUtil {
  def ceilDiv(a: Int, b: Int): Int = (a + b - 1) / b
  def roundUp(a: Int, b: Int): Int = ceilDiv(a, b) * b
  def isPow2(n: Int): Boolean = n > 0 && (n & (n - 1)) == 0
}
