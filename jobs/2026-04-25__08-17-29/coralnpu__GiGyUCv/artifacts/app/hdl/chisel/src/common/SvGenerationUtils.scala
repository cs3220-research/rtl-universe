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

/** Generates a SystemVerilog port list string for a given Chisel IO bundle.
  *
  * Produces lines of the form:
  *   "  input  logic [W-1:0] name,"
  *   "  output logic [W-1:0] name,"
  *   "  input  logic name,"       (for single-bit signals)
  *
  * Each field is recursively expanded. The direction is determined by
  * DataMirror.directionOf on each leaf element.
  *
  * Usage:
  *   val interface = GenerateInterface(dut.io, "io")
  */
object GenerateInterface {

  /** Recursively traverse a Data node, collecting (direction, width, name) tuples. */
  private def collectPorts(data: Data, prefix: String): Seq[(String, Int, String)] = {
    data match {
      case b: Bundle =>
        b.elements.toSeq.reverse.flatMap { case (fieldName, fieldData) =>
          val newPrefix = if (prefix.isEmpty) fieldName else s"${prefix}_${fieldName}"
          collectPorts(fieldData, newPrefix)
        }
      case v: Vec[_] =>
        v.zipWithIndex.flatMap { case (elem, idx) =>
          collectPorts(elem, s"${prefix}_${idx}")
        }
      case leaf =>
        val dir = DataMirror.directionOf(leaf) match {
          case ActualDirection.Input  => "input"
          case ActualDirection.Output => "output"
          case _                      => "input"
        }
        val w = leaf.getWidth
        Seq((dir, w, prefix))
    }
  }

  def apply(io: Data, prefix: String): String = {
    val ports = collectPorts(io, prefix)
    ports.map { case (dir, width, name) =>
      val dirStr  = if (dir == "input") "input " else "output"
      val typeStr = if (width == 1) "logic" else s"logic [${width - 1}:0]"
      s"  $dirStr $typeStr $name"
    }.mkString(",\n")
  }
}
