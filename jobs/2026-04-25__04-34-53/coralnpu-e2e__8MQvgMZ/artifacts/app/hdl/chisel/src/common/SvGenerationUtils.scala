package common
import chisel3._
import chisel3.util._

object SvGenerationUtils {
  def GenerateInterface(io: Record, name: String): String = {
    s"interface ${name};\n  // auto-generated\nendinterface\n"
  }
}
