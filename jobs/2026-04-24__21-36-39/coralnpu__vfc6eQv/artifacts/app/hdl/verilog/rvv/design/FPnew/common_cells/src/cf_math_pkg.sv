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

// Common math utility package for FPnew
package cf_math_pkg;

  // Ceiling log2
  function automatic int unsigned idx_width(input int unsigned num_idx);
    return (num_idx > 1) ? $clog2(num_idx) : 1;
  endfunction

  // Greatest common divisor
  function automatic int unsigned gcd(input int unsigned a, b);
    while (b != 0) begin
      int unsigned t = b;
      b = a % b;
      a = t;
    end
    return a;
  endfunction

  // Least common multiple
  function automatic int unsigned lcm(input int unsigned a, b);
    return (a / gcd(a, b)) * b;
  endfunction

endpackage
