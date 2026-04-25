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

package common

import chisel3._
import chisel3.reflect.DataMirror

/** GenerateInterface: generates a SystemVerilog port list string from a Chisel bundle.
  *
  * Recursively walks the bundle, producing one line per leaf signal in
  * declaration order. Each line has the form:
  *   "  <direction> logic [W-1:0] <prefix_fieldname>,"  (or without [W-1:0] for 1-bit)
  * The last entry does not have a trailing comma.
  *
  * @param bundle The IO bundle to inspect.
  * @param prefix The port name prefix (usually "io").
  * @return A multi-line string of SystemVerilog port declarations.
  */
object GenerateInterface {
  def apply(bundle: Data, prefix: String): String = {
    val lines = collectPorts(bundle, prefix)
    lines.zipWithIndex.map { case (line, idx) =>
      if (idx < lines.length - 1) line + "," else line
    }.mkString("\n")
  }

  private def collectPorts(data: Data, name: String): Seq[String] = {
    data match {
      case b: Bundle =>
        b.elements.toSeq.reverse.flatMap { case (fieldName, fieldData) =>
          collectPorts(fieldData, s"${name}_${fieldName}")
        }
      case v: Vec[_] =>
        v.zipWithIndex.flatMap { case (elem, idx) =>
          collectPorts(elem, s"${name}_${idx}")
        }
      case leaf: Element =>
        val dir = DataMirror.directionOf(leaf) match {
          case ActualDirection.Input  => "input "
          case ActualDirection.Output => "output"
          case _ => "input "
        }
        val width = leaf.getWidth
        val widthStr = if (width <= 1) "" else s" [${width - 1}:0]"
        Seq(s"  $dir logic$widthStr $name")
    }
  }
}
