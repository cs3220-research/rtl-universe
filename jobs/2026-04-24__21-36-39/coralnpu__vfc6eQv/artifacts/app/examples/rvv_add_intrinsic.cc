// RVV add using intrinsics
#include <stdint.h>
#include <riscv_vector.h>

void rvv_add(float* a, float* b, float* c, int n) {
  while (n > 0) {
    size_t vl = __riscv_vsetvl_e32m1(n);
    vfloat32m1_t va = __riscv_vle32_v_f32m1(a, vl);
    vfloat32m1_t vb = __riscv_vle32_v_f32m1(b, vl);
    vfloat32m1_t vc = __riscv_vfadd_vv_f32m1(va, vb, vl);
    __riscv_vse32_v_f32m1(c, vc, vl);
    a += vl; b += vl; c += vl; n -= vl;
  }
}

int main() {
  float a[4] = {1.0f, 2.0f, 3.0f, 4.0f};
  float b[4] = {5.0f, 6.0f, 7.0f, 8.0f};
  float c[4] = {0};
  rvv_add(a, b, c, 4);
  return 0;
}
