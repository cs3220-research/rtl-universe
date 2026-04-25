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
#include <cstdio>

namespace utils {

// Absolute value of a 32-bit signed integer.
inline int32_t abs32(int32_t x) { return x < 0 ? -x : x; }

// Minimum of two 32-bit signed integers.
inline int32_t min32(int32_t a, int32_t b) { return a < b ? a : b; }

// Maximum of two 32-bit signed integers.
inline int32_t max32(int32_t a, int32_t b) { return a > b ? a : b; }

// Clamp x to the closed interval [lo, hi].
inline int32_t clamp32(int32_t x, int32_t lo, int32_t hi) {
  return min32(hi, max32(lo, x));
}

// Copy n int32 values from src to dst.
inline void copy_int32(const int32_t* src, int32_t* dst, size_t n) {
  for (size_t i = 0; i < n; i++) dst[i] = src[i];
}

// Fill n int32 values in dst with value.
inline void fill_int32(int32_t* dst, int32_t value, size_t n) {
  for (size_t i = 0; i < n; i++) dst[i] = value;
}

// Sum n int32 values in src.
inline int32_t sum_int32(const int32_t* src, size_t n) {
  int32_t acc = 0;
  for (size_t i = 0; i < n; i++) acc += src[i];
  return acc;
}

}  // namespace utils
