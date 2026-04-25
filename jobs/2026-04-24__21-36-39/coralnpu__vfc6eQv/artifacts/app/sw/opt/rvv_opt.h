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

#include <riscv_vector.h>
#include <stddef.h>
#include <stdint.h>

namespace coralnpu_v2 {
namespace opt {

inline void Memcpy(void* dst, const void* src, size_t n) {
  uint8_t* d = reinterpret_cast<uint8_t*>(dst);
  const uint8_t* s = reinterpret_cast<const uint8_t*>(src);
  for (size_t vl; n > 0; n -= vl, d += vl, s += vl) {
    vl = __riscv_vsetvl_e8m8(n);
    vuint8m8_t v = __riscv_vle8_v_u8m8(s, vl);
    __riscv_vse8_v_u8m8(d, v, vl);
  }
}

inline void Memset(void* dst, int val, size_t n) {
  uint8_t* d = reinterpret_cast<uint8_t*>(dst);
  const uint8_t fill = static_cast<uint8_t>(val & 0xFF);
  for (size_t vl; n > 0; n -= vl, d += vl) {
    vl = __riscv_vsetvl_e8m8(n);
    vuint8m8_t v = __riscv_vmv_v_x_u8m8(fill, vl);
    __riscv_vse8_v_u8m8(d, v, vl);
  }
}

}  // namespace opt
}  // namespace coralnpu_v2
