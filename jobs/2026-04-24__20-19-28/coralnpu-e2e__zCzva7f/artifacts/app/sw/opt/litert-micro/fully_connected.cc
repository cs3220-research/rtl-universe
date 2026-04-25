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

#include "sw/opt/litert-micro/fully_connected.h"

#include "tensorflow/lite/kernels/internal/reference/integer_ops/fully_connected.h"

namespace coralnpu_v2 {
namespace opt {
namespace litert_micro {

void FullyConnected(const tflite::FullyConnectedParams& params,
                    const tflite::RuntimeShape& input_shape,
                    const int8_t* input_data,
                    const tflite::RuntimeShape& filter_shape,
                    const int8_t* filter_data,
                    const tflite::RuntimeShape& bias_shape,
                    const int32_t* bias_data,
                    const tflite::RuntimeShape& output_shape,
                    int8_t* output_data) {
  tflite::reference_integer_ops::FullyConnected(params, input_shape, input_data,
                                                filter_shape, filter_data,
                                                bias_shape, bias_data,
                                                output_shape, output_data);
}

}  // namespace litert_micro
}  // namespace opt
}  // namespace coralnpu_v2
