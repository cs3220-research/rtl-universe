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
import chisel3.util._
import chisel3.reflect.DataMirror

/** Utilities for generating SystemVerilog interface declarations from Chisel
  * modules.
  *
  * The primary entry-point is [[GenerateInterface]], which recursively walks
  * a Chisel Bundle and emits a comma-separated (but without trailing comma)
  * list of `input`/`output logic` port declarations suitable for inclusion
  * in a SystemVerilog `module` header.
  */
object SvGenerationUtils {

  /** Compute the direction string ("input " / "output") for a Data element.
    * The direction is determined relative to the *module* (i.e. a Flipped
    * port is an input from the module's perspective).
    */
  private def directionString(data: Data): String = {
    DataMirror.directionOf(data) match {
      case ActualDirection.Input  => "input "
      case ActualDirection.Output => "output"
      case _                      => "input "  // fallback
    }
  }

  /** Recursively flatten a Data into a sequence of (name, Data) leaf pairs.
    * Names are built by concatenating field names with underscores.
    */
  private def flattenData(data: Data, prefix: String): Seq[(String, Data)] = {
    data match {
      case b: Bundle =>
        b.elements.toSeq.flatMap { case (name, elem) =>
          val newPrefix = if (prefix.isEmpty) name else s"${prefix}_${name}"
          flattenData(elem, newPrefix)
        }
      case v: Vec[_] =>
        v.zipWithIndex.flatMap { case (elem, idx) =>
          val newPrefix = s"${prefix}_${idx}"
          flattenData(elem, newPrefix)
        }
      case _ =>
        Seq((prefix, data))
    }
  }

  /** Generate a port declaration line for a single leaf signal.
    *
    * @param name  The flattened port name.
    * @param data  The leaf Data element.
    * @return      A string like `"  input  logic [31:0] io_in"`.
    */
  private def generatePortDecl(name: String, data: Data): String = {
    val dir   = directionString(data)
    val width = data.getWidth
    val range = if (width == 1) "" else s" [${width - 1}:0]"
    s"  $dir logic$range $name"
  }

  /** Generate SystemVerilog port declarations for all leaves of `data`.
    *
    * @param data    The top-level IO bundle.
    * @param prefix  The name prefix (typically `"io"`).
    * @return        Multi-line string of port declarations, one per leaf,
    *                separated by newlines and *not* trailed by a comma.
    */
  def generateInterface(data: Data, prefix: String): String = {
    val leaves = flattenData(data, prefix)
    leaves.map { case (name, d) => generatePortDecl(name, d) }.mkString(",\n")
  }
}

/** Top-level convenience object that delegates to [[SvGenerationUtils]]. */
object GenerateInterface {
  def apply(data: Data, prefix: String): String =
    SvGenerationUtils.generateInterface(data, prefix)
}
