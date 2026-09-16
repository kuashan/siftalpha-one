import sys
import time

print("SIFTALPHA_X_TEST_C_STARTED")
print("SIFTALPHA_X_PROJECT_C_FILE=" + __file__)
sys.stdout.flush()
try:
    while True:
        print("SIFTALPHA_X_TEST_C_TICK")
        sys.stdout.flush()
        time.sleep(0.1)
except KeyboardInterrupt:
    print("SIFTALPHA_X_TEST_C_COOPERATIVE_STOP")
    sys.stdout.flush()
