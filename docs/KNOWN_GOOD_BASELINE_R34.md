# SiftAlpha alpha43-r34 Known Good Functional Baseline

Status: FROZEN / USER-DESIGNATED KNOWN GOOD BASELINE

## Frozen identity

- Repository: `kuashan/siftalpha-one`
- Source branch at acceptance: `codex/siftalpha-no-worker-alpha43`
- Frozen baseline branch: `baseline/alpha43-r34-known-good`
- Baseline commit: `466e33d5bb0f18bcc1bfa537f5d7ebe5aef93f1c`
- Baseline tree: `6ec24ca0a5a9cca585946306da61fc36741e2aa4`
- versionCode: `160`
- versionName: `0.8.0-alpha43-r34`
- APK SHA-256: `2d91657c07dc7cf75af2545f45587f96d15897ebb3fa5846fdb72cac417d8d0e`
- Stable test certificate SHA-256: `3bf440487ce3f9010c5843fc1b46abc79e105886ce339c4c075e7635c5542928`

## Cloud evidence

- W0 Cloud Build Run #240: PASS.
- Internal Alpine Probe Run #41: PASS.
- `testDebugUnitTest`: PASS.
- `assembleDebug`: PASS.
- Repository validators and stable-signing evidence: PASS.

## Real-device status

On 2026-09-20 the user designated r34 as the current known-good functional baseline after real-device use and described it as almost completely usable.

Important device-policy evidence retained from r33/r34 testing:

- Internal Runtime / Internal Alpine foreground-service ownership remains functional.
- Same-device localhost Web continuity works when the device battery policy for SiftAlpha is set to unrestricted.
- The background restriction seen under the default device power policy is therefore treated as an Android/OEM policy issue, not as evidence that r34 Runtime ownership or localhost Web serving is fundamentally broken.
- r34 restores generic late Web discovery from both stdout and stderr while retaining the existing Endpoint Probe truth gate.

## Baseline relationship

- r24 remains the historical Web-behavior reference and should still be used when investigating Web regressions.
- r34 supersedes r24 as the current product-level Known Good Functional Baseline because it includes the later Internal Runtime ownership, UI stability, background diagnostics, project cache, and stderr Web-discovery work that has now been exercised on a real device.
- The baseline branch is evidence, not a development branch. Do not advance, rewrite, rebase, force-push, or merge work into `baseline/alpha43-r34-known-good`.

## Rules for future work

1. New development continues on the active development branch, never on the frozen baseline branch.
2. Any regression after r34 must be compared against commit `466e33d5bb0f18bcc1bfa537f5d7ebe5aef93f1c`.
3. Do not change r34's applicationId, Runtime architecture, Web truth model, project-scoped STOP semantics, or Worker freeze when diagnosing later regressions unless a new task explicitly requires it.
4. Future installable builds must increase versionCode above `160`.
5. A later build replaces r34 as the current baseline only after cloud verification and explicit real-device acceptance.
6. Do not auto-merge a later build merely because CI passes.
