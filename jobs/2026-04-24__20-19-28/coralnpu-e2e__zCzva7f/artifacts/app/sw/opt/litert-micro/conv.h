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

#ifndef SW_OPT_LITERT_MICRO_CONV_H_
#define SW_OPT_LITERT_MICRO_CONV_H_

#include <cstdint>

#include "tensorflow/lite/c/common.h"
#include "tensorflow/lite/kernels/internal/types.h"

namespace coralnpu_v2 {
namespace opt {
namespace litert_micro {

struct OpDataConvCustom {
  int32_t* per_channel_output_multiplier;
  int32_t* per_channel_output_shift;
  int32_t output_activation_min;
  int32_t output_activation_max;

  // Scratch buffer indices for TfLiteContext
  int accs_buffer_index;
  int tiled_input_buffer_index;
  int generic_tiled_buffer_index;

  // Repacked weight buffers (allocated in persistent arena)
  int16_t* repacked_weights;       // for D48 path
  int32_t* weight_sums;            // for D48 path
  int8_t* repacked_weights_generic; // for generic 4x4 path
};

// Repack weights for the D48 optimized path.
void RepackWeightsD48(const int8_t* filter_data, int16_t* repacked_weights,
                      int32_t* weight_sums, int output_depth, int filter_height,
                      int filter_width, int input_depth);

void ConvPerChannel(const tflite::ConvParams& params,
                    const OpDataConvCustom& op_data,
                    const int32_t* per_channel_multiplier,
                    const int32_t* per_channel_shift,
                    TfLiteContext* context,
                    const tflite::RuntimeShape& input_shape,
                    const int8_t* input_data,
                    const tflite::RuntimeShape& filter_shape,
                    const int8_t* filter_data,
                    const tflite::RuntimeShape& bias_shape,
                    const int32_t* bias_data,
                    const tflite::RuntimeShape& output_shape,
                    int8_t* output_data);

}  // namespace litert_micro
}  // namespace opt
}  // namespace coralnpu_v2

#endif  // SW_OPT_LITERT_MICRO_CONV_H_
