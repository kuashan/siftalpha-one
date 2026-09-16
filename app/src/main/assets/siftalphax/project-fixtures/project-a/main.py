import json
import os
import platform
import sys

SIFTALPHA_X_PROJECT_A_GLOBAL = "PROJECT_A_ONLY"

import helper

print("SIFTALPHA_X_PYTHON_OK")
print("SIFTALPHA_X_PYTHON_VERSION=" + sys.version.split()[0])
print("SIFTALPHA_X_SYS_PLATFORM=" + sys.platform)
print("SIFTALPHA_X_PLATFORM_MACHINE=" + platform.machine())
print("SIFTALPHA_X_CWD=" + os.getcwd())
print("SIFTALPHA_X_SYS_PATH=" + os.pathsep.join(sys.path))
print("SIFTALPHA_X_STDLIB_OK=" + json.__name__)
print("SIFTALPHA_X_PROJECT_A_SUCCESS")
print("SIFTALPHA_X_PROJECT_HELPER=" + helper.VALUE)
print("SIFTALPHA_X_PROJECT_NAME=" + __name__)
print("SIFTALPHA_X_PROJECT_FILE=" + __file__)
print("SIFTALPHA_X_PROJECT_CWD=" + os.getcwd())
print("SIFTALPHA_X_PROJECT_ARGV0=" + sys.argv[0])
sys.stdout.flush()
