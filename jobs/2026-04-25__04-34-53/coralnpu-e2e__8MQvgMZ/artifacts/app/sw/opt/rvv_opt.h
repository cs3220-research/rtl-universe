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

#pragma once

#include <cstdint>
#include <riscv_vector.h>

// RVV optimization utilities for CoralNPU.
// All functions use striped element processing to handle arbitrary lengths.
namespace rvv_opt {

// Vector add: c[i] = a[i] + b[i] for int8 elements.
inline void vadd_int8(const int8_t* a, const int8_t* b, int8_t* c, size_t n) {
  size_t vl;
  while (n > 0) {
    vl = __riscv_vsetvl_e8m1(n);
    vint8m1_t va = __riscv_vle8_v_i8m1(a, vl);
    vint8m1_t vb = __riscv_vle8_v_i8m1(b, vl);
    vint8m1_t vc = __riscv_vadd_vv_i8m1(va, vb, vl);
    __riscv_vse8_v_i8m1(c, vc, vl);
    a += vl; b += vl; c += vl; n -= vl;
  }
}

// Vector add: c[i] = a[i] + b[i] for int32 elements.
inline void vadd_int32(const int32_t* a, const int32_t* b, int32_t* c,
                       size_t n) {
  size_t vl;
  while (n > 0) {
    vl = __riscv_vsetvl_e32m1(n);
    vint32m1_t va = __riscv_vle32_v_i32m1(a, vl);
    vint32m1_t vb = __riscv_vle32_v_i32m1(b, vl);
    vint32m1_t vc = __riscv_vadd_vv_i32m1(va, vb, vl);
    __riscv_vse32_v_i32m1(c, vc, vl);
    a += vl; b += vl; c += vl; n -= vl;
  }
}

// Vector multiply-add: c[i] += a[i] * b[i] for int32 elements.
inline void vmacc_int32(const int32_t* a, const int32_t* b, int32_t* c,
                        size_t n) {
  size_t vl;
  while (n > 0) {
    vl = __riscv_vsetvl_e32m1(n);
    vint32m1_t va  = __riscv_vle32_v_i32m1(a, vl);
    vint32m1_t vb  = __riscv_vle32_v_i32m1(b, vl);
    vint32m1_t vc  = __riscv_vle32_v_i32m1(c, vl);
    vint32m1_t vr  = __riscv_vmacc_vv_i32m1(vc, va, vb, vl);
    __riscv_vse32_v_i32m1(c, vr, vl);
    a += vl; b += vl; c += vl; n -= vl;
  }
}

// Vector subtract: c[i] = a[i] - b[i] for int32 elements.
inline void vsub_int32(const int32_t* a, const int32_t* b, int32_t* c,
                       size_t n) {
  size_t vl;
  while (n > 0) {
    vl = __riscv_vsetvl_e32m1(n);
    vint32m1_t va = __riscv_vle32_v_i32m1(a, vl);
    vint32m1_t vb = __riscv_vle32_v_i32m1(b, vl);
    vint32m1_t vc = __riscv_vsub_vv_i32m1(va, vb, vl);
    __riscv_vse32_v_i32m1(c, vc, vl);
    a += vl; b += vl; c += vl; n -= vl;
  }
}

}  // namespace rvv_opt
