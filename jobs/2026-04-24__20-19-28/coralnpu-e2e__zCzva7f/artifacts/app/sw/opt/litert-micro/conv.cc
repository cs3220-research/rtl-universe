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

#include "sw/opt/litert-micro/conv.h"

#include <algorithm>
#include <cstdint>
#include <riscv_vector.h>

#include "tensorflow/lite/kernels/internal/common.h"
#include "tensorflow/lite/kernels/internal/types.h"

namespace coralnpu_v2 {
namespace opt {
namespace litert_micro {

void RepackWeightsD48(const int8_t* filter_data, int16_t* repacked_weights,
                      int32_t* weight_sums, int output_depth, int filter_height,
                      int filter_width, int input_depth) {
  // No-op: reference implementation uses filters as-is.
  (void)filter_data;
  (void)repacked_weights;
  (void)weight_sums;
  (void)output_depth;
  (void)filter_height;
  (void)filter_width;
  (void)input_depth;
}

// RVV-vectorized conv2d. Vectorizes over the input-depth (channel) dimension
// to compute the dot product (input · filter) for each output position and
// output channel using widening multiply + horizontal reduction.
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
                    int8_t* output_data) {
  (void)op_data;
  (void)context;

  const int stride_width = params.stride_width;
  const int stride_height = params.stride_height;
  const int pad_width = params.padding_values.width;
  const int pad_height = params.padding_values.height;
  const int32_t input_offset = params.input_offset;
  const int32_t output_offset = params.output_offset;
  const int32_t act_min = params.quantized_activation_min;
  const int32_t act_max = params.quantized_activation_max;

  const int batches = input_shape.Dims(0);
  const int input_height = input_shape.Dims(1);
  const int input_width = input_shape.Dims(2);
  const int input_depth = input_shape.Dims(3);
  const int output_depth = output_shape.Dims(3);
  const int output_height = output_shape.Dims(1);
  const int output_width = output_shape.Dims(2);
  const int filter_height = filter_shape.Dims(1);
  const int filter_width = filter_shape.Dims(2);
  // filter_shape: [out_d, fh, fw, in_d]

  const vint32m1_t vzero = __riscv_vmv_v_x_i32m1(0, 1);

  for (int batch = 0; batch < batches; ++batch) {
    for (int out_y = 0; out_y < output_height; ++out_y) {
      for (int out_x = 0; out_x < output_width; ++out_x) {
        for (int od = 0; od < output_depth; ++od) {
          int32_t acc = bias_data[od];

          for (int fy = 0; fy < filter_height; ++fy) {
            const int in_y = out_y * stride_height - pad_height + fy;
            if (in_y < 0 || in_y >= input_height) continue;

            for (int fx = 0; fx < filter_width; ++fx) {
              const int in_x = out_x * stride_width - pad_width + fx;
              if (in_x < 0 || in_x >= input_width) continue;

              const int8_t* in_ptr =
                  input_data +
                  (batch * input_height + in_y) * input_width * input_depth +
                  in_x * input_depth;
              // filter layout: [od, fy, fx, in_d]
              const int8_t* filt_ptr =
                  filter_data +
                  (od * filter_height + fy) * filter_width * input_depth +
                  fx * input_depth;

              // Vectorize over input_depth using RVV.
              int remaining = input_depth;
              int ic = 0;
              while (remaining > 0) {
                size_t vl = __riscv_vsetvl_e8m1(remaining);

                // Load input and filter bytes.
                vint8m1_t vin8 = __riscv_vle8_v_i8m1(in_ptr + ic, vl);
                vint8m1_t vf8 = __riscv_vle8_v_i8m1(filt_ptr + ic, vl);

                // Widen input to int16 and add input_offset.
                vint16m2_t vin16 = __riscv_vsext_vf2_i16m2(vin8, vl);
                vin16 = __riscv_vadd_vx_i16m2(vin16, (int16_t)input_offset, vl);

                // Widen filter to int16.
                vint16m2_t vf16 = __riscv_vsext_vf2_i16m2(vf8, vl);

                // Widening multiply: int16 × int16 → int32.
                vint32m4_t vprod = __riscv_vwmul_vv_i32m4(vin16, vf16, vl);

                // Horizontal reduction: sum all products.
                vint32m1_t vsum =
                    __riscv_vredsum_vs_i32m4_i32m1(vprod, vzero, vl);
                acc += __riscv_vmv_x_s_i32m1_i32(vsum);

                ic += vl;
                remaining -= vl;
              }
            }
          }

          // Quantize and clamp.
          int32_t result = tflite::MultiplyByQuantizedMultiplier(
              acc, per_channel_multiplier[od], per_channel_shift[od]);
          result += output_offset;
          result = std::max(result, act_min);
          result = std::min(result, act_max);
          output_data[(batch * output_height + out_y) * output_width *
                          output_depth +
                      out_x * output_depth + od] = static_cast<int8_t>(result);
        }
      }
    }
  }
}

}  // namespace litert_micro
}  // namespace opt
}  // namespace coralnpu_v2
