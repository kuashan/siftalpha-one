package com.siftalpha.macos

import com.siftalpha.core.storage.PlatformStateStorage
import com.siftalpha.core.storage.StateStorageMutation
import com.siftalpha.core.storage.StoredStateValue
import com.siftalpha.core.storage.readText
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Base64
import java.util.Properties

class MacFileStateStorage(
    private val file: File,
) : PlatformStateStorage {
    @Synchronized
    override fun read(key: String): StoredStateValue? =
        load()[key]?.let(::decode)

    @Synchronized
    override fun mutate(mutation: StateStorageMutation) {
        val values = load()
        mutation.removals.forEach(values::remove)
        mutation.writes.forEach { (key, value) -> values[key] = encode(value) }
        commit(values)
    }

    private fun load(): MutableMap<String, String> {
        if (!file.isFile) return linkedMapOf()
        val properties = Properties()
        runCatching { file.inputStream().use { input -> properties.load(input) } }
            .getOrElse { return linkedMapOf() }
        return properties.stringPropertyNames()
            .sorted()
            .associateWithTo(linkedMapOf()) { key -> properties.getProperty(key).orEmpty() }
    }

    private fun commit(values: Map<String, String>) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, file.name + ".tmp-" + ProcessHandle.current().pid())
        val properties = Properties()
        values.forEach(properties::setProperty)
        temp.outputStream().use { output -> properties.store(output, "SiftAlpha X state") }
        try {
            Files.move(
                temp.toPath(),
                file.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: Throwable) {
            Files.move(
                temp.toPath(),
                file.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        } finally {
            temp.delete()
        }
    }

    private fun encode(value: StoredStateValue): String = when (value) {
        is StoredStateValue.Text -> "T:" + value.value
        is StoredStateValue.Bool -> "B:" + value.value
        is StoredStateValue.LongNumber -> "L:" + value.value
        is StoredStateValue.IntNumber -> "I:" + value.value
    }

    private fun decode(raw: String): StoredStateValue? = when {
        raw.startsWith("T:") -> StoredStateValue.Text(raw.substring(2))
        raw.startsWith("B:") -> raw.substring(2).toBooleanStrictOrNull()?.let(StoredStateValue::Bool)
        raw.startsWith("L:") -> raw.substring(2).toLongOrNull()?.let(StoredStateValue::LongNumber)
        raw.startsWith("I:") -> raw.substring(2).toIntOrNull()?.let(StoredStateValue::IntNumber)
        else -> null
    }
}

internal class MacProjectCatalog(
    private val storage: PlatformStateStorage,
) {
    fun paths(): List<String> =
        storage.readText(KEY)
            .orEmpty()
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .mapNotNull(::decodePath)
            .distinct()
            .toList()

    fun add(path: String) = write(paths().filterNot { it == path } + path)

    fun remove(path: String) = write(paths().filterNot { it == path })

    private fun write(paths: List<String>) {
        if (paths.isEmpty()) {
            storage.mutate(StateStorageMutation(removals = setOf(KEY)))
        } else {
            storage.mutate(
                StateStorageMutation(
                    writes = mapOf(
                        KEY to StoredStateValue.Text(
                            paths.distinct().joinToString("\n", transform = ::encodePath),
                        ),
                    ),
                ),
            )
        }
    }

    private fun encodePath(path: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(path.toByteArray(Charsets.UTF_8))

    private fun decodePath(value: String): String? =
        runCatching {
            Base64.getUrlDecoder().decode(value).toString(Charsets.UTF_8)
        }.getOrNull()?.takeIf(String::isNotBlank)

    companion object {
        private const val KEY = "macos.project_catalog.v1"
    }
}
