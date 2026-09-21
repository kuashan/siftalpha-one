# SiftAlpha Known-Good Baselines

This file records frozen source points that have both cloud evidence and the required real-device acceptance. Baseline branches are archival references and must not be moved for ordinary development.

## R46.3 — Stable Web Recognition Baseline

- Date accepted: 2026-09-20
- Version: `0.8.0-alpha43-r46.3`
- versionCode: `179`
- Accepted source commit: `302bc7e23b1fb6beedb7776075a7c426f7d0403b`
- Frozen baseline branch: `baseline/r46.3-web-recognition`
- Development branch at acceptance: `codex/siftalpha-no-worker-alpha43`
- W0 Cloud Build: `#330` — SUCCESS
- Artifact: `siftalpha-w0-330`
- Real-device acceptance: PASS

### Accepted behavior

1. Fresh-install / no-history Web recognition can quickly identify the project's actual Web launch path instead of falling back to an unrelated one-shot Python script.
2. The common-Web fast path and the deeper recognition fallback remain evidence-based; a dependency name alone is not enough to assert Web capability.
3. Learned Web launch data is only promoted to VERIFIED after the current project-owned endpoint passes Endpoint Probe.
4. External Python virtual environments are built at their final stable prefix. A completed venv is never relocated after pip has generated console scripts.
5. pip-generated Console Script entry points therefore retain a valid interpreter shebang instead of pointing at a deleted temporary `.prepare-<pid>` prefix.
6. PREPARE still preserves rollback safety: the previous environment and READY marker are retained until the replacement environment has been validated.
7. The real-device acceptance project confirmed that SiftAlpha selected and launched the correct native Web path quickly after the generic venv repair.
8. No project-specific source patch or easy_tdx-only command rewrite is part of this baseline.

### Baseline rule

`baseline/r46.3-web-recognition` is immutable. Future work starts from or compares against this exact source point. If a later development round causes a serious regression, compare it against commit `302bc7e23b1fb6beedb7776075a7c426f7d0403b` before changing established Runtime/Web semantics.

The next installable development version must use versionCode `180` or higher. versionCode `179` remains the identity of this accepted baseline.


## R47 — Environment Detection / Environment Plan / Prepare Baseline

- Date accepted: 2026-09-21
- Version: `0.8.0-alpha43-r47`
- versionCode: `180`
- Accepted source commit: `d611d18d08cf03b411cc687259e507ca91fcbaa8`
- Frozen baseline branch: `baseline/r47-environment-plan`
- Development branch at acceptance: `codex/siftalpha-no-worker-alpha43`
- W0 Cloud Build: `#365` — SUCCESS
- Artifact: `siftalpha-w0-365`
- APK SHA-256: `bbdf8d8b13b5b5bfb73f70162b710ff44baae0851ea5b416489f396ff99fe89e`
- Real-device acceptance: PASS

### Accepted architecture

1. Environment Detection（环境检测） is the shared project-understanding layer for Internal R（内部运行环境） and External Provider（外部运行环境提供者）.
2. Environment Plan（环境计划） is the shared machine-readable contract between Detection（检测） and Prepare（准备环境）.
3. Prepare（准备环境） consumes the Plan instead of rediscovering project meaning during installation.
4. Embedded CPython（内置 CPython） may perform deep compatibility resolution before installation and can reuse the exact resolved dependency plan during Prepare.
5. Internal Alpine（内部 Alpine） and External Python（外部 Python） both consume planned Python extras rather than reopening pyproject metadata at install time to rediscover them.
6. Backend-specific compatibility resolution remains backend-specific, while project facts and the Prepare contract remain shared.
7. External Python READY（就绪） metadata is bound to the Environment Plan ID（环境计划身份） with compatible legacy-marker migration.
8. Existing Runtime Identity（运行身份）, Session（会话）, Generation（代际）, Web Discovery（网页发现）, Endpoint Probe（端点探测）, project-scoped STOP（停止） and Worker freeze semantics remain unchanged.

### Baseline rule

`baseline/r47-environment-plan` is immutable and points directly at the real-device-accepted functional source commit `d611d18d08cf03b411cc687259e507ca91fcbaa8`. Documentation-only commits made after acceptance do not move this branch.
