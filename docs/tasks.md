# Nametag rendering and lifecycle tasks

Baseline: upstream main 9f54d4fb27419eff741221ee8cd7583898562a35.

- [x] Spec: retain REQ-001 through REQ-008 and distinguish server behavior from client acceptance.
- [x] Inspect current upstream, original dirty checkout, build/runtime profiles, and network submodule ownership.
- [x] Prove: add focused tests for cleanup, per-viewer presentation, recovery, visibility gates, and recording preferences; compare applicable tests with the unmodified baseline.
- [x] Engine: preserve the fixes and move new delayed recovery callbacks onto the owner's scheduler after four scheduler regression failures.
- [x] Arch: inspect platform boundaries and unchanged config/permissions/data; run the declared Paper 26.2 build profile and a separate compile/test build against PacketEvents 2.14.0.
- [x] Refine locally: external EARS validation and full clean builds/tests pass.
- [x] Submit reviewable upstream draft PR through an authenticated fork: https://github.com/alexdev03/UnlimitedNametags/pull/101.
- [x] Inspect PR head/review findings and record delivery evidence. The PR base is current main, mergeable without conflicts; no reviews or inline findings were present at inspection. Current CI and local artifact evidence must be checked against the exact head and recorded with delivery, separately from client acceptance.
- [ ] GitHub clean build reaches a terminal successful result for the final PR head. Do not infer success from local tests or an earlier head; use the attached verification workflow run.
- [ ] Merge and production delivery: require explicit authorization and a clean build of the exact merged source.
- [ ] Player acceptance: validate the documented client matrix with EntityCulling enabled and disabled.

No repository-local EARS/state tooling exists in the baseline. An available external EARS helper may validate requirements; this task/evidence record substitutes for absent state tooling without claiming it passed.
