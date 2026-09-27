package com.siftalpha.studio.runtime

/**
 * Reproducible External Runtime baseline.
 *
 * Keep the SiftAlpha-owned container separate from any user-managed "ubuntu" PRoot so migration
 * never deletes or resets the user's existing rootfs.
 */
object ExternalRuntimeBaseline {
    const val CONTAINER_NAME = "siftalpha-ubuntu-24.04"
    const val IMAGE = "ubuntu:24.04"
    const val UBUNTU_VERSION_ID = "24.04"
    const val BOOTSTRAP_PYTHON = "/usr/bin/python3.12"
    const val BASELINE_ID = "external-runtime-v1-ubuntu-24.04"
}
