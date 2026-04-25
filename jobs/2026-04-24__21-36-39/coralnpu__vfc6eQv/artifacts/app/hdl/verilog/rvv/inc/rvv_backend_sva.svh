// Copyright 2024 Google LLC
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

`ifndef RVV_BACKEND_SVA_SVH
`define RVV_BACKEND_SVA_SVH

// ============================================================
// RVV Backend SVA (SystemVerilog Assertions)
// These are intentionally empty/commented for simulation builds.
// Formal verification flows may add assertions here.
// ============================================================

// Assertion macros (no-ops unless formal verification is enabled)
`ifdef FORMAL_VERIFICATION
  `define RVV_ASSERT(name, prop) assert property (prop) else $fatal(1, "Assertion %s failed", name);
  `define RVV_ASSUME(name, prop) assume property (prop);
  `define RVV_COVER(name, prop)  cover  property (prop);
`else
  `define RVV_ASSERT(name, prop)
  `define RVV_ASSUME(name, prop)
  `define RVV_COVER(name, prop)
`endif

`endif // RVV_BACKEND_SVA_SVH
