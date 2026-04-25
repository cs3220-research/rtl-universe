package bus
import chisel3._
import coralnpu.Parameters

case class GPIOParameters(width: Int = 32)

class GPIO(p: Parameters, gp: GPIOParameters) extends Module {
  val tlp = new TLULParameters(p)
  val io = IO(new Bundle {
    val tl       = Flipped(new OpenTitanTileLink.Host2Device(tlp))
    val gpio_i   = Input(UInt(gp.width.W))
    val gpio_o   = Output(UInt(gp.width.W))
    val gpio_en_o = Output(UInt(gp.width.W))
  })
  // Stub
  io.tl.a.ready := false.B
  io.tl.d.valid := false.B
  io.tl.d.bits  := 0.U.asTypeOf(new TLULChannelD(tlp))
  io.gpio_o     := 0.U
  io.gpio_en_o  := 0.U
}
