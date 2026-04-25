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

#ifndef SW_OPT_LITERT_MICRO_ACCUMULATOR_UTIL_H_
#define SW_OPT_LITERT_MICRO_ACCUMULATOR_UTIL_H_

#include <cstdint>

#include "tensorflow/lite/kernels/internal/compatibility.h"

namespace coralnpu_v2 {
namespace opt {
namespace litert_micro {

inline int32_t MultiplyByQuantizedMultiplier(int32_t x, int32_t multiplier,
                                             int shift) {
  int left_shift = shift > 0 ? shift : 0;
  int right_shift = shift > 0 ? 0 : -shift;
  int64_t result = static_cast<int64_t>(x) * multiplier;
  result <<= left_shift;
  // Rounding right shift
  result += (1LL << (30 + right_shift));
  return static_cast<int32_t>(result >> (31 + right_shift));
}

}  // namespace litert_micro
}  // namespace opt
}  // namespace coralnpu_v2

#endif  // SW_OPT_LITERT_MICRO_ACCUMULATOR_UTIL_H_
