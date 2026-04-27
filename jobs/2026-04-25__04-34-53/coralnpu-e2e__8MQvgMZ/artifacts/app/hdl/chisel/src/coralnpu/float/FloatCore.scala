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

package coralnpu

import chisel3._

// FloatCore wraps an FPU pipeline.  It accepts commands via a Valid
// interface and produces results on a Decoupled output.
//
// The implementation delegates actual arithmetic to the scalar Fpu module
// which provides a 1-cycle pipelined implementation.
class FloatCore(p: Parameters) extends Module {
  val io = IO(new FpuInterface)

  val fpu = Module(new Fpu)

  fpu.io.cmd        <> io.cmd
  io.output         <> fpu.io.output
}
