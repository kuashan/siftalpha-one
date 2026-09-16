import sys

if "SIFTALPHA_X_PROJECT_A_GLOBAL" in globals():
    raise RuntimeError("SIFTALPHA_X_NAMESPACE_LEAK")

import helper

print("SIFTALPHA_X_PROJECT_D_ISOLATION_OK")
print("SIFTALPHA_X_PROJECT_HELPER=" + helper.VALUE)
print("SIFTALPHA_X_PROJECT_NAME=" + __name__)
print("SIFTALPHA_X_PROJECT_FILE=" + __file__)
print("SIFTALPHA_X_PROJECT_ARGV0=" + sys.argv[0])
sys.stdout.flush()
