# coralnpu (Harbor task — all tests)

Restore the missing RTL and firmware for the Coral NPU so that
`bazel test //...` passes.

- Skeleton: `environment/skeleton/` — the bazel workspace with code removed.
- Full green source (reference): `environment/warm_src/` — used only to
  pre-warm the bazel output_base during image build.
- Test set: all 305 `*_test` targets surviving the default
  `-vcs,-synthesis,-power` tag filter.
- Reward: proportional (`passed / total`).

The companion task `../coralnpu-e2e/` runs only the ~189 end-to-end tests
(core-level cocotb/Verilator sims) and skips component-level unit tests.

## Build

```
../tools/sync-skeleton.sh
docker build -t coralnpu-harbor:all environment/
```

## Run the reference solution

```
docker run -d --name ref coralnpu-harbor:all tail -f /dev/null
./solution/solve.sh ref
docker exec ref bash /tests/test.sh
docker exec ref cat /logs/verifier/reward.txt    # expect 1.000000
docker rm -f ref
```
