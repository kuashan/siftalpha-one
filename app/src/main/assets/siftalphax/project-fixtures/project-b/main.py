import sys

print("SIFTALPHA_X_TEST_B_STDOUT")
print("SIFTALPHA_X_TEST_B_STDERR", file=sys.stderr)
print("SIFTALPHA_X_PROJECT_B_FAILURE")
sys.stdout.flush()
sys.stderr.flush()
raise RuntimeError("SIFTALPHA_X_TEST_B_FAILURE")
