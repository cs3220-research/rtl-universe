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
import chisel3.ActualDirection

/** Generates a SystemVerilog port list string from a Chisel Record (Bundle).
  *
  * Each leaf port is emitted on its own line as:
  *   "  input  logic [N-1:0] name"  (for N-bit ports)
  *   "  input  logic name"           (for 1-bit / Bool ports)
  *
  * The last port has no trailing comma.
  *
  * @param io     The top-level IO Record (e.g. dut.io).
  * @param prefix The prefix to prepend to all port names (e.g. "io").
  * @return A formatted SystemVerilog port list string.
  */
object GenerateInterface {

  /** Recursively collect leaf (Data, name) pairs in declaration order.
    *
    * - Bundle: fields in reverse-alphabetical order (as returned by Chisel's elements,
    *   which are stored in reverse declaration order), then reversed to get declaration order.
    * - Vec: elements in index order (0, 1, ..., n-1).
    * - Leaf Data: emit as-is.
    */
  private def collectLeaves(data: Data, name: String): Seq[(Data, String)] = {
    data match {
      case r: Record =>
        // elements returns fields in reverse declaration order; reverse to get declaration order
        val fields = r.elements.toSeq.reverse
        fields.flatMap { case (fieldName, fieldData) =>
          collectLeaves(fieldData, s"${name}_${fieldName}")
        }
      case v: Vec[_] =>
        v.getElements.zipWithIndex.flatMap { case (elem, idx) =>
          collectLeaves(elem, s"${name}_${idx}")
        }
      case leaf =>
        Seq((leaf, name))
    }
  }

  def apply(io: Record, prefix: String): String = {
    val leaves = collectLeaves(io, prefix)

    val ports = leaves.map { case (leaf, name) =>
      // Determine direction from the module's perspective
      val dir = DataMirror.directionOf(leaf) match {
        case ActualDirection.Input  => "input "
        case ActualDirection.Output => "output"
        case _                      => "input " // fallback
      }

      // Determine width
      val width = DataMirror.widthOf(leaf)
      val widthStr = if (width.known && width.get > 1) {
        s" [${width.get - 1}:0]"
      } else {
        ""
      }

      s"  $dir logic$widthStr $name"
    }

    ports.mkString(",\n")
  }
}
