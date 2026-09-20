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
