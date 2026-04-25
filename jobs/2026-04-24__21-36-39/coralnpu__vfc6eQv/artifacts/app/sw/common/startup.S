.global _start
_start:
  la sp, _stack_top
  jal ra, main
1: j 1b
