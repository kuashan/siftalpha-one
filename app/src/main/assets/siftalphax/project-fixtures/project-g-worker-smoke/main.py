import json
import os

payload = json.dumps(
    {"alpha44": "worker-smoke", "ok": True},
    sort_keys=True,
    separators=(",", ":"),
)

print("SIFTALPHA_ALPHA44_WORKER_CPYTHON_SMOKE=PASS")
print(f"SIFTALPHA_ALPHA44_WORKER_CPYTHON_PID={os.getpid()}")
print(f"SIFTALPHA_ALPHA44_WORKER_STDLIB_JSON={payload}")
