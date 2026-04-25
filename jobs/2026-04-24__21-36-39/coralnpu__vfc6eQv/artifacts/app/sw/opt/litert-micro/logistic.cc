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

#include "sw/opt/litert-micro/logistic.h"

#include "tensorflow/lite/kernels/internal/reference/integer_ops/logistic.h"

namespace coralnpu_v2 {
namespace opt {
namespace litert_micro {

void LogisticInit(int32_t /*input_zero_point*/, int32_t /*input_range_radius*/,
                  int32_t /*input_multiplier*/, int32_t /*input_left_shift*/) {
  // No precomputation needed for reference implementation.
}

void Logistic(int32_t input_zero_point, int32_t input_range_radius,
              int32_t input_multiplier, int32_t input_left_shift,
              int32_t input_size, const int8_t* input_data,
              int8_t* output_data) {
  tflite::reference_integer_ops::Logistic(
      input_zero_point, input_range_radius, input_multiplier, input_left_shift,
      input_size, input_data, output_data);
}

}  // namespace litert_micro
}  // namespace opt
}  // namespace coralnpu_v2
