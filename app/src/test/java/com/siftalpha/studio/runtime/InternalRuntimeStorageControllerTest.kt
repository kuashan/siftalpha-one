package com.siftalpha.studio.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import com.siftalpha.studio.storage.SiftAlphaStorage

class InternalRuntimeStorageControllerTest {

    @Test
    fun environmentIdIsStableSha256() {
        assertEquals(
            "f0e3d7ef6ebaff4f5085be867a2949f020ef7a42fe13842dcc070dcfca8b2b04",
            InternalRuntimeStorageController.environmentId("primary:AcodeProjects/easy_tdx_1-main"),
        )
    }

    @Test
    fun clearReproducibleCachesPreservesRuntimeAndProjectData() {
        val root = Files.createTempDirectory("siftalpha-internal-storage").toFile()
        try {
            val filesRoot = File(root, "SiftAlphaX")
            val wheel = File(filesRoot, "wheelhouse/pkg.whl").apply {
                parentFile!!.mkdirs()
                writeBytes(ByteArray(2048))
            }
            val pip = File(filesRoot, "runtimes/alpine/rootfs/root/.cache/pip/http/pkg").apply {
                parentFile!!.mkdirs()
                writeBytes(ByteArray(1024))
            }
            val npm = File(filesRoot, "runtimes/alpine/rootfs/root/.npm/_cacache/content/pkg").apply {
                parentFile!!.mkdirs()
                writeBytes(ByteArray(1024))
            }
            val busybox = File(filesRoot, "runtimes/alpine/rootfs/bin/busybox").apply {
                parentFile!!.mkdirs()
                writeText("runtime")
            }
            val env = File(filesRoot, "environments/alpine/project/venv/bin/python").apply {
                parentFile!!.mkdirs()
                writeText("project")
            }

            val controller = InternalRuntimeStorageController(root)
            controller.clearWheelCache()
            controller.clearAlpinePipCache()
            controller.clearAlpineNpmCache()

            assertFalse(wheel.exists())
            assertFalse(pip.exists())
            assertFalse(npm.exists())
            assertTrue(busybox.isFile)
            assertTrue(env.isFile)
        } finally {
            root.deleteRecursively()
        }
    }
}
