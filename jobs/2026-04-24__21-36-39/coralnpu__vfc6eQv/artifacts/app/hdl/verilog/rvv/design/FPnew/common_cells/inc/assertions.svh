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

`ifndef ASSERTIONS_SVH
`define ASSERTIONS_SVH

// Common cell assertions (stub / no-op versions)
`ifdef SYNTHESIS
  `define ASSERT_I(name, cond)
  `define ASSERT_INIT(name, cond)
  `define ASSERT_INIT_NET(name, cond)
  `define ASSERT_NEVER(name, cond)
  `define ASSERT_KNOWN(name, sig)
  `define COVER(name, cond)
  `define ASSUME(name, cond)
`else
  `define ASSERT_I(name, cond)
  `define ASSERT_INIT(name, cond)
  `define ASSERT_INIT_NET(name, cond)
  `define ASSERT_NEVER(name, cond)
  `define ASSERT_KNOWN(name, sig)
  `define COVER(name, cond)
  `define ASSUME(name, cond)
`endif

`endif // ASSERTIONS_SVH
