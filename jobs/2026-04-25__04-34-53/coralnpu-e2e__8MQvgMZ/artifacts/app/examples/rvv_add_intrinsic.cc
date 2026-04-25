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

#include <cstdint>
#include <riscv_vector.h>

// RVV vector add example using RISC-V intrinsics.
// Adds two arrays element-wise and verifies the result.
// Returns 0 on success, 1 on failure.
int main() {
  const int n = 4;
  int32_t a[n] = {1, 2, 3, 4};
  int32_t b[n] = {10, 20, 30, 40};
  int32_t c[n] = {0};

  size_t vl = __riscv_vsetvl_e32m1(n);
  vint32m1_t va = __riscv_vle32_v_i32m1(a, vl);
  vint32m1_t vb = __riscv_vle32_v_i32m1(b, vl);
  vint32m1_t vc = __riscv_vadd_vv_i32m1(va, vb, vl);
  __riscv_vse32_v_i32m1(c, vc, vl);

  // Verify each element
  for (int i = 0; i < n; i++) {
    if (c[i] != a[i] + b[i]) return 1;
  }
  return 0;
}
