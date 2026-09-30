# switchapk: agent guide

switchapk runs Android APKs on the Nintendo Switch (homebrew NRO): a
Dalvik VM in C, our own Java class library and Android framework, a
software 2D renderer, a native-library loader, and a libnx platform layer.

## Read first (in this order)

1. `docs/PLAN.md`: vision, milestones, current state, checklist, next steps
2. `docs/WORKSTREAMS.md`: work packages, dependencies, who owns what; claim before starting
3. `docs/ARCHITECTURE.md`: subsystem design and cross-component contracts
4. `docs/CONVENTIONS.md`: rules (API fidelity, natives, tests, git)
5. `docs/DEV_SETUP.md`: fetching toolchains, building, running, debugging
6. `docs/DECISIONS.md` and `docs/SESSION_LOG.md` for history

## Non-negotiable rules

- Match real Android API signatures exactly (check with `javap` against
  `build/toolchains/sdk/android.jar`). The VM silently stubs mismatched
  calls, so mistakes do not crash, they just do nothing.
- Keep the build green: `make` and `tests/run_dex_test.sh tests/dex/VmTest.java`
  (once WS0 has landed, both must pass on every push).
- Natives that block must release the GIL; Java fields read from C are
  part of the interface (see ARCHITECTURE.md, update both sides together).
- Claim a workstream in `docs/WORKSTREAMS.md` before working; update
  `docs/PLAN.md` checklists and `docs/SESSION_LOG.md` when you finish.
- Record cross-component interface changes in PLAN.md "Interface changes
  log" and ARCHITECTURE.md in the same commit.
- Toolchains live in `build/toolchains` (fetched by
  `tools/fetch_toolchains.py`); never commit them.
- No em dashes in docs, comments, commit messages or replies.
- Do not create pull requests unless the user asks. No model names in
  commits or code.

## Quick start

```
python3 tools/fetch_toolchains.py sdk
python3 tools/fetch_toolchains.py sqlite
make
tests/run_dex_test.sh tests/dex/VmTest.java
```
