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

#include <cstdint>
#include <cstdio>

// Simple hello world that adds two floats using the FPU.
// Returns 0 on success (c == 4.0f), 1 on failure.
int main() {
  float a = 1.5f;
  float b = 2.5f;
  float c = a + b;
  // Success: c should be 4.0
  return c == 4.0f ? 0 : 1;
}
