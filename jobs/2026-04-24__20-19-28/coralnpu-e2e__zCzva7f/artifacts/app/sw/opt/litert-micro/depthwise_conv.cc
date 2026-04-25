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

#include "sw/opt/litert-micro/depthwise_conv.h"

#include <algorithm>
#include <cstdint>
#include <riscv_vector.h>

#include "tensorflow/lite/kernels/internal/common.h"
#include "tensorflow/lite/kernels/internal/types.h"

namespace coralnpu_v2 {
namespace opt {
namespace litert_micro {

// Vectorized equivalent of tflite::MultiplyByQuantizedMultiplier for vl
// elements. Takes pre-loaded vmult and vshift (≤ 0) vectors.
// Multipliers are assumed non-negative (always true in TFLite quantization).
static inline vint8m1_t VecQuantizeFromVec(vint32m4_t vacc, vint32m4_t vmult,
                                            vint32m4_t vshift,
                                            int32_t output_offset,
                                            int32_t act_min, int32_t act_max,
                                            size_t vl) {
  // right_shift = -shift (positive)
  vuint32m4_t vrs = __riscv_vreinterpret_v_i32m4_u32m4(
      __riscv_vneg_v_i32m4(vshift, vl));

  // === SaturatingRoundingDoublingHighMul(vacc, vmult) ===
  // High and low 32-bit halves of vacc * vmult (signed).
  vint32m4_t vmulhi = __riscv_vmulh_vv_i32m4(vacc, vmult, vl);
  vuint32m4_t vmul_lo = __riscv_vreinterpret_v_i32m4_u32m4(
      __riscv_vmul_vv_i32m4(vacc, vmult, vl));

  // nudge = 0x40000000 if product >= 0 (sign of acc since mult >= 0),
  //       = 0xC0000000 otherwise.
  vbool8_t vneg_prod = __riscv_vmslt_vx_i32m4_b8(vacc, 0, vl);
  vuint32m4_t vnudge = __riscv_vmerge_vxm_u32m4(
      __riscv_vmv_v_x_u32m4(0x40000000u, vl), (uint32_t)0xC0000000u,
      vneg_prod, vl);

  // sum_lo = lo32 + nudge (with unsigned overflow carry detection).
  vuint32m4_t vsum_lo = __riscv_vadd_vv_u32m4(vmul_lo, vnudge, vl);
  vbool8_t vcarry = __riscv_vmsltu_vv_u32m4_b8(vsum_lo, vmul_lo, vl);

  // srdmh = mulhi*2 + bit31(sum_lo) + carry
  //       = lower 32 bits of ((mulhi:lo32) + nudge) >> 31
  vint32m4_t vsrdmh = __riscv_vadd_vv_i32m4(
      __riscv_vsll_vx_i32m4(vmulhi, 1, vl),
      __riscv_vreinterpret_v_u32m4_i32m4(
          __riscv_vsrl_vx_u32m4(vsum_lo, 31, vl)),
      vl);
  vsrdmh = __riscv_vadd_vv_i32m4(
      vsrdmh,
      __riscv_vmerge_vxm_i32m4(__riscv_vmv_v_x_i32m4(0, vl), 1, vcarry, vl),
      vl);

  // === RoundingDivideByPOT(vsrdmh, vrs) ===
  // mask = (1 << right_shift) - 1
  vuint32m4_t vmask = __riscv_vsub_vx_u32m4(
      __riscv_vsll_vv_u32m4(__riscv_vmv_v_x_u32m4(1u, vl), vrs, vl), 1u, vl);
  // remainder = srdmh & mask
  vuint32m4_t vrem = __riscv_vand_vv_u32m4(
      __riscv_vreinterpret_v_i32m4_u32m4(vsrdmh), vmask, vl);
  // threshold = (mask >> 1) + (srdmh < 0 ? 1 : 0)
  vbool8_t vneg_srdmh = __riscv_vmslt_vx_i32m4_b8(vsrdmh, 0, vl);
  vuint32m4_t vthresh = __riscv_vadd_vv_u32m4(
      __riscv_vsrl_vx_u32m4(vmask, 1, vl),
      __riscv_vreinterpret_v_i32m4_u32m4(__riscv_vmerge_vxm_i32m4(
          __riscv_vmv_v_x_i32m4(0, vl), 1, vneg_srdmh, vl)),
      vl);
  // round_up = remainder > threshold; result = (srdmh >> right_shift) + round_up
  vbool8_t vround_up = __riscv_vmsgtu_vv_u32m4_b8(vrem, vthresh, vl);
  vint32m4_t vresult = __riscv_vadd_vv_i32m4(
      __riscv_vsra_vv_i32m4(vsrdmh, vrs, vl),
      __riscv_vmerge_vxm_i32m4(__riscv_vmv_v_x_i32m4(0, vl), 1, vround_up, vl),
      vl);

  // Add output_offset and clamp.
  vresult = __riscv_vadd_vx_i32m4(vresult, output_offset, vl);
  vresult = __riscv_vmax_vx_i32m4(vresult, act_min, vl);
  vresult = __riscv_vmin_vx_i32m4(vresult, act_max, vl);

  // Narrow int32 -> int16 -> int8.
  vint16m2_t vres16 = __riscv_vnsra_wx_i16m2(vresult, 0, vl);
  return __riscv_vnsra_wx_i8m1(vres16, 0, vl);
}

// Vectorized quantization with contiguous multiplier/shift loads.
static inline vint8m1_t VecQuantize(vint32m4_t vacc, const int32_t* mult_ptr,
                                     const int32_t* shift_ptr,
                                     int32_t output_offset, int32_t act_min,
                                     int32_t act_max, size_t vl) {
  return VecQuantizeFromVec(vacc, __riscv_vle32_v_i32m4(mult_ptr, vl),
                             __riscv_vle32_v_i32m4(shift_ptr, vl),
                             output_offset, act_min, act_max, vl);
}

// Vectorized quantization with stride-2 multiplier/shift loads (for DM=2).
static inline vint8m1_t VecQuantizeStrided2(vint32m4_t vacc,
                                             const int32_t* mult_ptr,
                                             const int32_t* shift_ptr,
                                             int32_t output_offset,
                                             int32_t act_min, int32_t act_max,
                                             size_t vl) {
  return VecQuantizeFromVec(
      vacc,
      __riscv_vlse32_v_i32m4(mult_ptr, 2 * sizeof(int32_t), vl),
      __riscv_vlse32_v_i32m4(shift_ptr, 2 * sizeof(int32_t), vl),
      output_offset, act_min, act_max, vl);
}

// RVV-vectorized depthwise conv (depth_multiplier == 1, contiguous channels).
// Accumulates int8 x int8 -> int32 using vwmacc, then applies vectorized
// per-channel quantization to produce int8 output directly.
static void DepthwiseConvPerChannelDM1(
    const tflite::DepthwiseParams& params, const int32_t* output_multiplier,
    const int32_t* output_shift, const tflite::RuntimeShape& input_shape,
    const int8_t* input_data, const tflite::RuntimeShape& filter_shape,
    const int8_t* filter_data, const tflite::RuntimeShape& bias_shape,
    const int32_t* bias_data, const tflite::RuntimeShape& output_shape,
    int8_t* output_data) {
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
  const int filter_height = filter_shape.Dims(1);
  const int filter_width = filter_shape.Dims(2);
  const int output_height = output_shape.Dims(1);
  const int output_width = output_shape.Dims(2);
  const int output_depth = output_shape.Dims(3);  // == input_depth for dm=1

  for (int batch = 0; batch < batches; ++batch) {
    for (int out_y = 0; out_y < output_height; ++out_y) {
      for (int out_x = 0; out_x < output_width; ++out_x) {
        int od = 0;
        int remaining = output_depth;
        while (remaining > 0) {
          size_t vl = __riscv_vsetvl_e8m1(remaining);

          // Load bias values for this channel group.
          vint32m4_t vacc = __riscv_vle32_v_i32m4(bias_data + od, vl);

          for (int fy = 0; fy < filter_height; ++fy) {
            const int in_y = out_y * stride_height - pad_height + fy;
            if (in_y < 0 || in_y >= input_height) continue;

            for (int fx = 0; fx < filter_width; ++fx) {
              const int in_x = out_x * stride_width - pad_width + fx;
              if (in_x < 0 || in_x >= input_width) continue;

              const int8_t* in_ptr =
                  input_data + (batch * input_height + in_y) * input_width *
                                   input_depth +
                  in_x * input_depth + od;
              const int8_t* filt_ptr =
                  filter_data + (fy * filter_width + fx) * output_depth + od;

              vint8m1_t vin8 = __riscv_vle8_v_i8m1(in_ptr, vl);
              vint16m2_t vin16 = __riscv_vsext_vf2_i16m2(vin8, vl);
              vin16 = __riscv_vadd_vx_i16m2(vin16, (int16_t)input_offset, vl);

              vint8m1_t vf8 = __riscv_vle8_v_i8m1(filt_ptr, vl);
              vint16m2_t vf16 = __riscv_vsext_vf2_i16m2(vf8, vl);

              vacc = __riscv_vwmacc_vv_i32m4(vacc, vin16, vf16, vl);
            }
          }

          // Vectorized quantization + store to output.
          int8_t* out_ptr =
              output_data + (batch * output_height + out_y) * output_width *
                                output_depth +
              out_x * output_depth + od;
          __riscv_vse8_v_i8m1(out_ptr,
                               VecQuantize(vacc, output_multiplier + od,
                                           output_shift + od, output_offset,
                                           act_min, act_max, vl),
                               vl);
          od += vl;
          remaining -= vl;
        }
      }
    }
  }
}

// RVV-vectorized depthwise conv for depth_multiplier == 2.
// For each group of vl input channels ic..ic+vl-1, accumulates into vacc_even
// (output channels 2*ic, 2*ic+2, ...) and vacc_odd (2*ic+1, 2*ic+3, ...) using
// stride-2 filter loads. Applies vectorized quantization and stores with stride 2.
static void DepthwiseConvPerChannelDM2(
    const tflite::DepthwiseParams& params, const int32_t* output_multiplier,
    const int32_t* output_shift, const tflite::RuntimeShape& input_shape,
    const int8_t* input_data, const tflite::RuntimeShape& filter_shape,
    const int8_t* filter_data, const tflite::RuntimeShape& bias_shape,
    const int32_t* bias_data, const tflite::RuntimeShape& output_shape,
    int8_t* output_data) {
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
  const int filter_height = filter_shape.Dims(1);
  const int filter_width = filter_shape.Dims(2);
  const int output_height = output_shape.Dims(1);
  const int output_width = output_shape.Dims(2);
  const int output_depth = output_shape.Dims(3);  // == input_depth * 2

  for (int batch = 0; batch < batches; ++batch) {
    for (int out_y = 0; out_y < output_height; ++out_y) {
      for (int out_x = 0; out_x < output_width; ++out_x) {
        int8_t* out_base =
            output_data + (batch * output_height + out_y) * output_width *
                              output_depth +
            out_x * output_depth;

        int ic = 0;
        int remaining = input_depth;
        while (remaining > 0) {
          size_t vl = __riscv_vsetvl_e8m1(remaining);

          // Bias: even output channels (bias[2*ic], bias[2*ic+2], ...) via stride-2.
          vint32m4_t vacc_even = __riscv_vlse32_v_i32m4(
              bias_data + 2 * ic, 2 * sizeof(int32_t), vl);
          // Bias: odd output channels (bias[2*ic+1], bias[2*ic+3], ...).
          vint32m4_t vacc_odd = __riscv_vlse32_v_i32m4(
              bias_data + 2 * ic + 1, 2 * sizeof(int32_t), vl);

          for (int fy = 0; fy < filter_height; ++fy) {
            const int in_y = out_y * stride_height - pad_height + fy;
            if (in_y < 0 || in_y >= input_height) continue;

            for (int fx = 0; fx < filter_width; ++fx) {
              const int in_x = out_x * stride_width - pad_width + fx;
              if (in_x < 0 || in_x >= input_width) continue;

              // Load vl input values (channels ic..ic+vl-1, contiguous).
              const int8_t* in_ptr =
                  input_data + (batch * input_height + in_y) * input_width *
                                   input_depth +
                  in_x * input_depth + ic;
              vint8m1_t vin8 = __riscv_vle8_v_i8m1(in_ptr, vl);
              vint16m2_t vin16 = __riscv_vsext_vf2_i16m2(vin8, vl);
              vin16 = __riscv_vadd_vx_i16m2(vin16, (int16_t)input_offset, vl);

              // Filter layout [1, fh, fw, output_depth]: even/odd channels
              // for ic group are stride-2 in the filter array.
              const int8_t* filt_base =
                  filter_data + (fy * filter_width + fx) * output_depth +
                  2 * ic;
              vint8m1_t vf_even8 = __riscv_vlse8_v_i8m1(filt_base, 2, vl);
              vint16m2_t vf_even16 = __riscv_vsext_vf2_i16m2(vf_even8, vl);
              vint8m1_t vf_odd8 = __riscv_vlse8_v_i8m1(filt_base + 1, 2, vl);
              vint16m2_t vf_odd16 = __riscv_vsext_vf2_i16m2(vf_odd8, vl);

              vacc_even = __riscv_vwmacc_vv_i32m4(vacc_even, vin16, vf_even16, vl);
              vacc_odd  = __riscv_vwmacc_vv_i32m4(vacc_odd,  vin16, vf_odd16,  vl);
            }
          }

          // Quantize even channels and store with stride 2 in output.
          __riscv_vsse8_v_i8m1(
              out_base + 2 * ic, 2,
              VecQuantizeStrided2(vacc_even, output_multiplier + 2 * ic,
                                  output_shift + 2 * ic, output_offset,
                                  act_min, act_max, vl),
              vl);
          // Quantize odd channels and store with stride 2 in output.
          __riscv_vsse8_v_i8m1(
              out_base + 2 * ic + 1, 2,
              VecQuantizeStrided2(vacc_odd, output_multiplier + 2 * ic + 1,
                                  output_shift + 2 * ic + 1, output_offset,
                                  act_min, act_max, vl),
              vl);

          ic += vl;
          remaining -= vl;
        }
      }
    }
  }
}

void DepthwiseConvPerChannel(
    const tflite::DepthwiseParams& params, const int32_t* output_multiplier,
    const int32_t* output_shift, const tflite::RuntimeShape& input_shape,
    const int8_t* input_data, const tflite::RuntimeShape& filter_shape,
    const int8_t* filter_data, const tflite::RuntimeShape& bias_shape,
    const int32_t* bias_data, const tflite::RuntimeShape& output_shape,
    int8_t* output_data, int32_t* scratch_buf) {
  (void)scratch_buf;
  if (params.depth_multiplier == 1) {
    DepthwiseConvPerChannelDM1(params, output_multiplier, output_shift,
                               input_shape, input_data, filter_shape,
                               filter_data, bias_shape, bias_data, output_shape,
                               output_data);
  } else if (params.depth_multiplier == 2) {
    DepthwiseConvPerChannelDM2(params, output_multiplier, output_shift,
                               input_shape, input_data, filter_shape,
                               filter_data, bias_shape, bias_data, output_shape,
                               output_data);
  } else {
    // Scalar fallback for other depth multipliers.
    const int stride_width = params.stride_width;
    const int stride_height = params.stride_height;
    const int pad_width = params.padding_values.width;
    const int pad_height = params.padding_values.height;
    const int depth_multiplier = params.depth_multiplier;
    const int32_t input_offset = params.input_offset;
    const int32_t output_offset = params.output_offset;
    const int32_t act_min = params.quantized_activation_min;
    const int32_t act_max = params.quantized_activation_max;

    const int batches = input_shape.Dims(0);
    const int input_height = input_shape.Dims(1);
    const int input_width = input_shape.Dims(2);
    const int input_depth = input_shape.Dims(3);
    const int filter_height = filter_shape.Dims(1);
    const int filter_width = filter_shape.Dims(2);
    const int output_height = output_shape.Dims(1);
    const int output_width = output_shape.Dims(2);
    for (int batch = 0; batch < batches; ++batch) {
      for (int out_y = 0; out_y < output_height; ++out_y) {
        for (int out_x = 0; out_x < output_width; ++out_x) {
          for (int ic = 0; ic < input_depth; ++ic) {
            for (int m = 0; m < depth_multiplier; ++m) {
              const int od = ic * depth_multiplier + m;
              int32_t acc = bias_data[od];
              for (int fy = 0; fy < filter_height; ++fy) {
                const int in_y = out_y * stride_height - pad_height + fy;
                if (in_y < 0 || in_y >= input_height) continue;
                for (int fx = 0; fx < filter_width; ++fx) {
                  const int in_x = out_x * stride_width - pad_width + fx;
                  if (in_x < 0 || in_x >= input_width) continue;
                  int32_t in_val =
                      input_data[tflite::Offset(input_shape, batch, in_y, in_x,
                                                ic)] +
                      input_offset;
                  int32_t filt_val = filter_data[tflite::Offset(
                      filter_shape, 0, fy, fx, od)];
                  acc += in_val * filt_val;
                }
              }
              int32_t result = tflite::MultiplyByQuantizedMultiplier(
                  acc, output_multiplier[od], output_shift[od]);
              result += output_offset;
              result = std::max(result, act_min);
              result = std::min(result, act_max);
              output_data[tflite::Offset(output_shape, batch, out_y, out_x,
                                         od)] = static_cast<int8_t>(result);
            }
          }
        }
      }
    }
  }
}

}  // namespace litert_micro
}  // namespace opt
}  // namespace coralnpu_v2
