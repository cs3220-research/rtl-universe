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

package top_pkg;

  localparam int TL_AW  = 32;
  localparam int TL_DW  = 32;
  localparam int TL_AIW = 8;
  localparam int TL_DIW = 1;
  localparam int TL_AUW = 23;
  localparam int TL_DUW = 14;
  localparam int TL_DBW = (TL_DW >> 3);
  localparam int TL_SZW = $clog2($clog2(TL_DBW) + 1);

  localparam int unsigned AlertSkewCycles = 1;

endpackage
