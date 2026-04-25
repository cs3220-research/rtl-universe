// Hello World - add floats example
#include <stdint.h>

int main() {
  volatile float a = 1.0f;
  volatile float b = 2.0f;
  volatile float c = a + b;
  (void)c;
  return 0;
}
