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

#include "tensorflow/lite/kernels/internal/compatibility.h"

namespace coralnpu_v2 {
namespace opt {
namespace litert_micro {

// Applies per-channel multiplier/shift and output offset, clamps to int8.
inline int8_t MultiplyByQuantizedMultiplierAndClamp(
    int32_t acc, int32_t multiplier, int shift, int32_t output_offset,
    int32_t activation_min, int32_t activation_max) {
  // Reference scalar implementation.
  int64_t scaled = static_cast<int64_t>(acc) * multiplier;
  int result;
  if (shift > 0) {
    result = static_cast<int>(scaled << shift);
  } else {
    // Rounding right shift
    int64_t rounding = 1LL << (-shift - 1);
    result = static_cast<int>((scaled + rounding) >> (-shift));
  }
  result += output_offset;
  if (result < activation_min) result = activation_min;
  if (result > activation_max) result = activation_max;
  return static_cast<int8_t>(result);
}

}  // namespace litert_micro
}  // namespace opt
}  // namespace coralnpu_v2
