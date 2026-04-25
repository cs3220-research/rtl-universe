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

#include <stdint.h>
#include <stddef.h>

#include "tensorflow/lite/kernels/internal/compatibility.h"

namespace coralnpu_v2 {
namespace opt {
namespace litert_micro {

// Copies n bytes from src to dst using RVV vector instructions when available.
inline void MemCopy(void* dst, const void* src, size_t n) {
  const uint8_t* s = reinterpret_cast<const uint8_t*>(src);
  uint8_t* d = reinterpret_cast<uint8_t*>(dst);
  for (size_t i = 0; i < n; ++i) d[i] = s[i];
}

}  // namespace litert_micro
}  // namespace opt
}  // namespace coralnpu_v2
