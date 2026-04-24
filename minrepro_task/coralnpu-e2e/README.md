# coralnpu-e2e (Harbor task — E2E tests only)

Restore the missing RTL and firmware for the Coral NPU so that the
end-to-end test subset passes.

- Skeleton: `environment/skeleton/` — same as the all-mode task.
- E2E filter: `( //tests/cocotb/... except //tests/cocotb/tlul/... ) union
  //tests/verilator_sim:core_mini_axi_non_incr_tests union
  //tests/verilator_sim:backdoor_load_test`
- Reward: proportional (`e2e_passed / e2e_total`, ~189 targets).

The companion task `../coralnpu/` runs the full 305-target suite.

## Build

```
../tools/sync-skeleton.sh
docker build -t coralnpu-harbor:e2e environment/
```

## Run the reference solution

```
docker run -d --name ref coralnpu-harbor:e2e tail -f /dev/null
./solution/solve.sh ref
docker exec ref bash /tests/test.sh
docker exec ref cat /logs/verifier/reward.txt
docker rm -f ref
```
