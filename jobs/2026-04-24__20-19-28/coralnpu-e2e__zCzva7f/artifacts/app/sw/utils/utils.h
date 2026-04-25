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

#ifndef SW_UTILS_UTILS_H_
#define SW_UTILS_UTILS_H_

#include <stdint.h>

// Read the RISC-V machine cycle counter (64-bit on RV32 via mcycle/mcycleh).
static inline uint64_t mcycle_read(void) {
  uint32_t lo, hi, hi2;
  do {
    asm volatile("csrr %0, mcycleh" : "=r"(hi));
    asm volatile("csrr %0, mcycle" : "=r"(lo));
    asm volatile("csrr %0, mcycleh" : "=r"(hi2));
  } while (hi != hi2);
  return ((uint64_t)hi << 32) | lo;
}

// Read the RISC-V machine instret counter (64-bit on RV32).
static inline uint64_t minstret_read(void) {
  uint32_t lo, hi, hi2;
  do {
    asm volatile("csrr %0, minstreth" : "=r"(hi));
    asm volatile("csrr %0, minstret" : "=r"(lo));
    asm volatile("csrr %0, minstreth" : "=r"(hi2));
  } while (hi != hi2);
  return ((uint64_t)hi << 32) | lo;
}

// Reset the cycle counter by writing 0 to mcycle/mcycleh.
static inline void cycle_counter_reset(void) {
  asm volatile("csrw mcycle, zero");
  asm volatile("csrw mcycleh, zero");
}

// Reset the instruction counter by writing 0 to minstret/minstreth.
static inline void instrut_counter_reset(void) {
  asm volatile("csrw minstret, zero");
  asm volatile("csrw minstreth, zero");
}

#endif  // SW_UTILS_UTILS_H_
