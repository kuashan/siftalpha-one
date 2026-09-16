package com.siftalpha.studio.siftalphax

import com.siftalpha.studio.R

/** Fixed, reviewable scripts shipped with the APK; this PoC accepts no arbitrary code input. */
enum class EmbeddedPythonScenario(
    val labelResource: Int,
    val script: String,
) {
    NORMAL(
        R.string.siftalpha_x_test_a,
        """
        import json
        import os
        import platform
        import sys

        print("SIFTALPHA_X_PYTHON_OK")
        print("SIFTALPHA_X_PYTHON_VERSION=" + sys.version.split()[0])
        print("SIFTALPHA_X_SYS_PLATFORM=" + sys.platform)
        print("SIFTALPHA_X_PLATFORM_MACHINE=" + platform.machine())
        print("SIFTALPHA_X_CWD=" + os.getcwd())
        print("SIFTALPHA_X_SYS_PATH=" + os.pathsep.join(sys.path))
        print("SIFTALPHA_X_STDLIB_OK=" + json.__name__)
        sys.stdout.flush()
        """.trimIndent(),
    ),
    STDOUT_STDERR_FAILURE(
        R.string.siftalpha_x_test_b,
        """
        import sys

        print("SIFTALPHA_X_TEST_B_STDOUT")
        print("SIFTALPHA_X_TEST_B_STDERR", file=sys.stderr)
        sys.stdout.flush()
        sys.stderr.flush()
        raise RuntimeError("SIFTALPHA_X_TEST_B_FAILURE")
        """.trimIndent(),
    ),
    LONG_RUNNING(
        R.string.siftalpha_x_test_c,
        """
        import sys
        import time

        print("SIFTALPHA_X_TEST_C_STARTED")
        sys.stdout.flush()
        try:
            while True:
                print("SIFTALPHA_X_TEST_C_TICK")
                sys.stdout.flush()
                time.sleep(0.1)
        except KeyboardInterrupt:
            print("SIFTALPHA_X_TEST_C_COOPERATIVE_STOP")
            sys.stdout.flush()
        """.trimIndent(),
    ),
}
