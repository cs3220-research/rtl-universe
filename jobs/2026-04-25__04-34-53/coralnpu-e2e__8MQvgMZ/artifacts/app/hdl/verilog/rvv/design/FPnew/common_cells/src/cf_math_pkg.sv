// Copyright 2026 Google LLC
// SPDX-License-Identifier: Apache-2.0
//
// Constant-function math helpers used by FPnew/common_cells.
package cf_math_pkg;

  function automatic integer ceil_div(input integer a, input integer b);
    ceil_div = (a + b - 1) / b;
  endfunction

  function automatic integer idx_width(input integer n);
    idx_width = (n > 1) ? $clog2(n) : 1;
  endfunction

endpackage
