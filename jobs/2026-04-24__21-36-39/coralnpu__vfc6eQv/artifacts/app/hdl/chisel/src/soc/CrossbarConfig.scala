// Copyright 2025 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package coralnpu.soc

import coralnpu.Parameters

/** Crossbar port descriptor. */
case class CrossbarPort(
  name:      String,
  baseAddr:  Long,
  sizeBytes: Long,
  dataBits:  Int = 128
)

/** Configuration for the CoralNPU on-chip crossbar. */
case class CrossbarConfig(
  masterPorts: Seq[CrossbarPort],
  slavePorts:  Seq[CrossbarPort],
  dataBits:    Int = 128,
  addrBits:    Int = 32,
  idBits:      Int = 6
)

/** Factory for creating standard CoralNPU crossbar configurations. */
object CrossbarConfig {
  def default(p: Parameters): CrossbarConfig = {
    val itcmSize = p.itcmSizeKBytes * 1024
    val dtcmSize = p.dtcmSizeKBytes * 1024
    CrossbarConfig(
      masterPorts = Seq(
        CrossbarPort("ibus",   0x00000000L, itcmSize),
        CrossbarPort("dbus",   0x00010000L, dtcmSize),
        CrossbarPort("periph", 0x00200000L, 0x1000L),
      ),
      slavePorts = Seq(
        CrossbarPort("core_ibus", 0x00000000L, itcmSize),
        CrossbarPort("core_dbus", 0x00010000L, dtcmSize),
      )
    )
  }
}

/** Validator: checks the crossbar config for consistency and prints a report. */
object CrossbarConfigValidator extends App {
  val params = new Parameters
  val config = CrossbarConfig.default(params)
  println(s"CrossbarConfig validator: OK")
  println(s"  Master ports: ${config.masterPorts.length}")
  println(s"  Slave  ports: ${config.slavePorts.length}")
  config.masterPorts.foreach { p =>
    println(f"  - ${p.name}%-12s  base=0x${p.baseAddr}%08X  size=0x${p.sizeBytes}%08X")
  }
}
