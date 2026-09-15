package com.siftalpha.studio.runtime

import java.io.File

/**
 * Persistence and validation for the generic runtime identity sidecar.
 *
 * Runtime commands execute in Termux/PRoot, so the Android process does not open these files while
 * a project is running. The same small key/value format is nevertheless used by the generated
 * shell scripts and by this store's read/write API, which keeps the identity contract testable and
 * makes host/guest fields explicit.
 */
class RuntimeIdentityStore(
    private val rootDirectory: File,
) {
    fun write(identity: RuntimeIdentity): Boolean =
        validate(identity) && writeFile(identity.runtimeId, IDENTITY_FILE_NAME, encode(identity))

    fun writeHost(identity: RuntimeIdentity): Boolean =
        validate(identity) && writeFile(identity.runtimeId, IDENTITY_FILE_NAME, encodeHost(identity))

    fun writeGuest(identity: RuntimeIdentity): Boolean =
        validate(identity) && writeFile(identity.runtimeId, GUEST_IDENTITY_FILE_NAME, encodeGuest(identity))

    /**
     * Reads the combined identity. Host and guest sidecars are merged when both are present.
     * A missing, malformed, or metadata-inconsistent sidecar is treated as unavailable.
     */
    fun read(runtimeId: String): RuntimeIdentity? {
        if (!isSafeRuntimeId(runtimeId)) return null
        val runtimeDirectory = File(rootDirectory, runtimeId)
        val hostFile = File(runtimeDirectory, IDENTITY_FILE_NAME)
        val guestFile = File(runtimeDirectory, GUEST_IDENTITY_FILE_NAME)
        val host = readFile(hostFile)
        val guest = readFile(guestFile)
        if ((hostFile.exists() && host == null) || (guestFile.exists() && guest == null)) return null
        if (host == null && guest == null) return null

        val metadata = host ?: guest ?: return null
        if (!metadataMatches(metadata, runtimeId)) return null
        if (guest != null && !metadataMatches(guest, runtimeId)) return null
        if (host != null && guest != null && !sameMetadata(host, guest)) return null

        return RuntimeIdentity(
            runtimeId = runtimeId,
            runtimeToken = metadata.runtimeToken,
            startTime = metadata.startTime,
            schemaVersion = metadata.schemaVersion,
            hostSessionPid = host?.hostSessionPid ?: metadata.hostSessionPid,
            hostSessionPgid = host?.hostSessionPgid ?: metadata.hostSessionPgid,
            guestRootPid = guest?.guestRootPid ?: metadata.guestRootPid,
            guestRootPgid = guest?.guestRootPgid ?: metadata.guestRootPgid,
        ).takeIf { validate(it, expectedRuntimeId = runtimeId) }
    }

    /**
     * Structural validation. Host and guest process fields are deliberately optional because the
     * host sidecar is written before the guest runner has published its identity.
     */
    fun validate(
        identity: RuntimeIdentity?,
        expectedRuntimeId: String? = null,
        requireHost: Boolean = false,
        requireGuest: Boolean = false,
    ): Boolean {
        if (identity == null) return false
        if (!isSafeRuntimeId(identity.runtimeId)) return false
        if (expectedRuntimeId != null && identity.runtimeId != expectedRuntimeId) return false
        if (identity.runtimeToken.isBlank() || identity.runtimeToken.any { it == '\n' || it == '\r' || it == '=' }) {
            return false
        }
        if (identity.startTime < 0L) return false
        if (identity.schemaVersion !in 1..CURRENT_SCHEMA_VERSION) return false
        if (!validPid(identity.hostSessionPid) || !validPid(identity.hostSessionPgid)) return false
        if (!validPid(identity.guestRootPid) || !validPid(identity.guestRootPgid)) return false
        if (requireHost && (identity.hostSessionPid == null || identity.hostSessionPgid == null)) return false
        if (requireGuest && (identity.guestRootPid == null || identity.guestRootPgid == null)) return false
        return true
    }

    /**
     * Returns true when an identity is structurally invalid or does not match the live session
     * metadata. RuntimeToken and startTime are generation values, so both must match before a PID
     * can be trusted after a restart or possible PID reuse.
     */
    fun isExpired(
        identity: RuntimeIdentity?,
        expectedRuntimeToken: String? = null,
        expectedStartTime: Long? = null,
    ): Boolean {
        if (!validate(identity)) return true
        if (expectedRuntimeToken != null && identity!!.runtimeToken != expectedRuntimeToken) return true
        if (expectedStartTime != null && identity!!.startTime != expectedStartTime) return true
        return false
    }

    private fun writeFile(runtimeId: String, fileName: String, content: String): Boolean {
        if (!isSafeRuntimeId(runtimeId)) return false
        val runtimeDirectory = File(rootDirectory, runtimeId)
        if (!runtimeDirectory.exists() && !runtimeDirectory.mkdirs()) return false
        val target = File(runtimeDirectory, fileName)
        val temporary = File(runtimeDirectory, "$fileName.tmp-${Thread.currentThread().id}")
        return try {
            temporary.writeText(content, Charsets.UTF_8)
            if (temporary.renameTo(target)) {
                true
            } else {
                target.writeText(content, Charsets.UTF_8)
                temporary.delete()
                true
            }
        } catch (_: Exception) {
            temporary.delete()
            false
        }
    }

    private fun readFile(file: File): RuntimeIdentity? = try {
        if (!file.isFile || !file.canRead()) null else parse(file.readText(Charsets.UTF_8))
    } catch (_: Exception) {
        null
    }

    companion object {
        private const val D = "$"

        const val CURRENT_SCHEMA_VERSION = 1
        const val IDENTITY_FILE_NAME = "identity.env"
        const val GUEST_IDENTITY_FILE_NAME = "guest.identity.env"

        /** Neutral guest mount point; it is not tied to a Linux user's home directory. */
        const val GUEST_RUNTIME_ROOT = "/siftalpha-runtime"

        const val SCHEMA_KEY = "SIFTALPHA_RUNTIME_IDENTITY_SCHEMA"
        const val RUNTIME_ID_KEY = "SIFTALPHA_RUNTIME_ID"
        const val RUNTIME_TOKEN_KEY = "SIFTALPHA_RUNTIME_TOKEN"
        const val START_TIME_KEY = "SIFTALPHA_RUNTIME_START_TIME"
        const val HOST_SESSION_PID_KEY = "SIFTALPHA_HOST_SESSION_PID"
        const val HOST_SESSION_PGID_KEY = "SIFTALPHA_HOST_SESSION_PGID"
        const val GUEST_ROOT_PID_KEY = "SIFTALPHA_GUEST_ROOT_PID"
        const val GUEST_ROOT_PGID_KEY = "SIFTALPHA_GUEST_ROOT_PGID"

        /**
         * Selects the identity source without inspecting or changing runtime processes.
         *
         * A partial sidecar is reported explicitly so diagnostics can distinguish an incomplete
         * migration from a runtime that only has the legacy host PID files.
         */
        fun sourceFor(
            hostIdentityAvailable: Boolean,
            guestIdentityAvailable: Boolean,
            legacyPidAvailable: Boolean,
        ): RuntimeIdentitySource = when {
            hostIdentityAvailable && guestIdentityAvailable -> RuntimeIdentitySource.FULL_IDENTITY
            hostIdentityAvailable -> RuntimeIdentitySource.HOST_ONLY
            guestIdentityAvailable -> RuntimeIdentitySource.GUEST_ONLY
            legacyPidAvailable -> RuntimeIdentitySource.LEGACY_PID
            else -> RuntimeIdentitySource.UNAVAILABLE
        }

        /**
         * Shell-side reader used inside PRoot. Keeping the file parsing here prevents Web
         * discovery from owning a second, subtly different identity format.
         */
        fun guestIdentityLoaderShell(): String = """
            siftalpha_runtime_identity_value() {
              runtime_identity_key="${D}1"
              runtime_identity_source="${D}2"
              [ -r "${D}runtime_identity_source" ] || return 0
              sed -n "s/^${D}{runtime_identity_key}=//p" "${D}runtime_identity_source" 2>/dev/null | head -n 1
            }

            siftalpha_runtime_identity_load() {
              runtime_identity_id="${D}1"
              case "${D}runtime_identity_id" in
                ''|*[!A-Za-z0-9._-]*) return 1 ;;
              esac
              runtime_identity_dir="${GUEST_RUNTIME_ROOT}/${D}runtime_identity_id"
              runtime_identity_file="${D}runtime_identity_dir/${IDENTITY_FILE_NAME}"
              runtime_identity_guest_file="${D}runtime_identity_dir/${GUEST_IDENTITY_FILE_NAME}"
              runtime_identity_schema="${D}(siftalpha_runtime_identity_value '${SCHEMA_KEY}' "${D}runtime_identity_file")"
              runtime_identity_guest_schema="${D}(siftalpha_runtime_identity_value '${SCHEMA_KEY}' "${D}runtime_identity_guest_file")"
              runtime_identity_host_id="${D}(siftalpha_runtime_identity_value '${RUNTIME_ID_KEY}' "${D}runtime_identity_file")"
              runtime_identity_guest_id="${D}(siftalpha_runtime_identity_value '${RUNTIME_ID_KEY}' "${D}runtime_identity_guest_file")"
              runtime_identity_token="${D}(siftalpha_runtime_identity_value '${RUNTIME_TOKEN_KEY}' "${D}runtime_identity_file")"
              runtime_identity_guest_token="${D}(siftalpha_runtime_identity_value '${RUNTIME_TOKEN_KEY}' "${D}runtime_identity_guest_file")"
              runtime_identity_start="${D}(siftalpha_runtime_identity_value '${START_TIME_KEY}' "${D}runtime_identity_file")"
              runtime_identity_guest_start="${D}(siftalpha_runtime_identity_value '${START_TIME_KEY}' "${D}runtime_identity_guest_file")"
              runtime_identity_guest_pid="${D}(siftalpha_runtime_identity_value '${GUEST_ROOT_PID_KEY}' "${D}runtime_identity_guest_file")"
              runtime_identity_guest_pgid="${D}(siftalpha_runtime_identity_value '${GUEST_ROOT_PGID_KEY}' "${D}runtime_identity_guest_file")"

              [ "${D}runtime_identity_schema" = '${CURRENT_SCHEMA_VERSION}' ] || return 1
              [ "${D}runtime_identity_guest_schema" = '${CURRENT_SCHEMA_VERSION}' ] || return 1
              [ "${D}runtime_identity_host_id" = "${D}runtime_identity_id" ] || return 1
              [ "${D}runtime_identity_guest_id" = "${D}runtime_identity_id" ] || return 1
              [ -n "${D}runtime_identity_token" ] && [ "${D}runtime_identity_token" = "${D}runtime_identity_guest_token" ] || return 1
              [ -n "${D}runtime_identity_start" ] && [ "${D}runtime_identity_start" = "${D}runtime_identity_guest_start" ] || return 1
              case "${D}runtime_identity_guest_pid" in
                ''|0|*[!0-9]*) return 1 ;;
              esac
              case "${D}runtime_identity_guest_pgid" in
                ''|0) runtime_identity_guest_pgid='' ;;
                *[!0-9]*) runtime_identity_guest_pgid='' ;;
              esac

              SIFTALPHA_RUNTIME_IDENTITY_GUEST_ROOT_PID="${D}runtime_identity_guest_pid"
              SIFTALPHA_RUNTIME_IDENTITY_GUEST_ROOT_PGID="${D}runtime_identity_guest_pgid"
              SIFTALPHA_RUNTIME_IDENTITY_TOKEN="${D}runtime_identity_token"
              SIFTALPHA_RUNTIME_IDENTITY_START_TIME="${D}runtime_identity_start"
              return 0
            }
        """.trimIndent()

        /** Shell-side writer used by the guest runner to publish its generic root identity. */
        fun guestIdentityWriterShell(): String = """
            siftalpha_runtime_identity_value() {
              runtime_identity_key="${D}1"
              runtime_identity_source="${D}2"
              [ -r "${D}runtime_identity_source" ] || return 0
              sed -n "s/^${D}{runtime_identity_key}=//p" "${D}runtime_identity_source" 2>/dev/null | head -n 1
            }

            siftalpha_runtime_identity_write_guest() {
              runtime_identity_file="${D}1"
              runtime_identity_guest_file="${D}2"
              runtime_identity_expected_id="${D}3"
              [ -r "${D}runtime_identity_file" ] || return 0
              runtime_identity_schema="${D}(siftalpha_runtime_identity_value '${SCHEMA_KEY}' "${D}runtime_identity_file")"
              runtime_identity_id="${D}(siftalpha_runtime_identity_value '${RUNTIME_ID_KEY}' "${D}runtime_identity_file")"
              runtime_identity_token="${D}(siftalpha_runtime_identity_value '${RUNTIME_TOKEN_KEY}' "${D}runtime_identity_file")"
              runtime_identity_start="${D}(siftalpha_runtime_identity_value '${START_TIME_KEY}' "${D}runtime_identity_file")"
              [ "${D}runtime_identity_schema" = '${CURRENT_SCHEMA_VERSION}' ] || return 0
              [ "${D}runtime_identity_id" = "${D}runtime_identity_expected_id" ] || return 0
              [ -n "${D}runtime_identity_token" ] || return 0
              [ -n "${D}runtime_identity_start" ] || return 0

              runtime_identity_guest_pid="${D}${D}"
              runtime_identity_guest_pgid="${D}(ps -o pgid= -p "${D}runtime_identity_guest_pid" 2>/dev/null | tr -d ' ' || true)"
              if [ -z "${D}runtime_identity_guest_pgid" ]; then
                runtime_identity_guest_pgid="${D}(awk '{print ${D}5}' "/proc/${D}runtime_identity_guest_pid/stat" 2>/dev/null || true)"
              fi
              runtime_identity_tmp="${D}runtime_identity_guest_file.tmp.${D}runtime_identity_guest_pid"
              if {
                printf '${SCHEMA_KEY}=%s\n' '${CURRENT_SCHEMA_VERSION}'
                printf '${RUNTIME_ID_KEY}=%s\n' "${D}runtime_identity_id"
                printf '${RUNTIME_TOKEN_KEY}=%s\n' "${D}runtime_identity_token"
                printf '${START_TIME_KEY}=%s\n' "${D}runtime_identity_start"
                printf '${GUEST_ROOT_PID_KEY}=%s\n' "${D}runtime_identity_guest_pid"
                [ -n "${D}runtime_identity_guest_pgid" ] && printf '${GUEST_ROOT_PGID_KEY}=%s\n' "${D}runtime_identity_guest_pgid"
              } >"${D}runtime_identity_tmp" 2>/dev/null && mv -f -- "${D}runtime_identity_tmp" "${D}runtime_identity_guest_file"; then
                printf 'SIFTALPHA_RUNTIME_IDENTITY_GUEST_ROOT=RUNNER\n'
                printf 'SIFTALPHA_RUNTIME_IDENTITY_GUEST_ROOT_PID=%s\n' "${D}runtime_identity_guest_pid"
                [ -n "${D}runtime_identity_guest_pgid" ] && printf 'SIFTALPHA_RUNTIME_IDENTITY_GUEST_ROOT_PGID=%s\n' "${D}runtime_identity_guest_pgid"
              else
                printf 'SIFTALPHA_RUNTIME_IDENTITY_GUEST_ROOT=NOT_PUBLISHED\n'
              fi
              unset runtime_identity_file runtime_identity_guest_file runtime_identity_expected_id runtime_identity_schema runtime_identity_id runtime_identity_token runtime_identity_start runtime_identity_guest_pid runtime_identity_guest_pgid runtime_identity_tmp
              return 0
            }
        """.trimIndent()

        fun encode(identity: RuntimeIdentity): String = buildString {
            append(encodeMetadata(identity))
            appendPid(HOST_SESSION_PID_KEY, identity.hostSessionPid)
            appendPid(HOST_SESSION_PGID_KEY, identity.hostSessionPgid)
            appendPid(GUEST_ROOT_PID_KEY, identity.guestRootPid)
            appendPid(GUEST_ROOT_PGID_KEY, identity.guestRootPgid)
        }

        fun parse(content: String): RuntimeIdentity? {
            val values = mutableMapOf<String, String>()
            content.lineSequence().forEach { line ->
                val separator = line.indexOf('=')
                if (separator <= 0) return@forEach
                val key = line.substring(0, separator)
                if (key in KNOWN_KEYS) {
                    values[key] = line.substring(separator + 1)
                }
            }
            return try {
                RuntimeIdentity(
                    runtimeId = values[RUNTIME_ID_KEY].orEmpty(),
                    runtimeToken = values[RUNTIME_TOKEN_KEY].orEmpty(),
                    startTime = values[START_TIME_KEY]?.toLongOrNull() ?: return null,
                    schemaVersion = values[SCHEMA_KEY]?.toIntOrNull() ?: return null,
                    hostSessionPid = values.pidOrNull(HOST_SESSION_PID_KEY),
                    hostSessionPgid = values.pidOrNull(HOST_SESSION_PGID_KEY),
                    guestRootPid = values.pidOrNull(GUEST_ROOT_PID_KEY),
                    guestRootPgid = values.pidOrNull(GUEST_ROOT_PGID_KEY),
                ).takeIf { isStructurallyValid(it) }
            } catch (_: Exception) {
                null
            }
        }

        private val KNOWN_KEYS = setOf(
            SCHEMA_KEY,
            RUNTIME_ID_KEY,
            RUNTIME_TOKEN_KEY,
            START_TIME_KEY,
            HOST_SESSION_PID_KEY,
            HOST_SESSION_PGID_KEY,
            GUEST_ROOT_PID_KEY,
            GUEST_ROOT_PGID_KEY,
        )

        private fun encodeMetadata(identity: RuntimeIdentity): String = buildString {
            append(SCHEMA_KEY).append('=').append(identity.schemaVersion).append('\n')
            append(RUNTIME_ID_KEY).append('=').append(identity.runtimeId).append('\n')
            append(RUNTIME_TOKEN_KEY).append('=').append(identity.runtimeToken).append('\n')
            append(START_TIME_KEY).append('=').append(identity.startTime).append('\n')
        }

        private fun encodeHost(identity: RuntimeIdentity): String = buildString {
            append(encodeMetadata(identity))
            appendPid(HOST_SESSION_PID_KEY, identity.hostSessionPid)
            appendPid(HOST_SESSION_PGID_KEY, identity.hostSessionPgid)
        }

        private fun encodeGuest(identity: RuntimeIdentity): String = buildString {
            append(encodeMetadata(identity))
            appendPid(GUEST_ROOT_PID_KEY, identity.guestRootPid)
            appendPid(GUEST_ROOT_PGID_KEY, identity.guestRootPgid)
        }

        private fun StringBuilder.appendPid(key: String, value: Long?) {
            if (value != null) append(key).append('=').append(value).append('\n')
        }

        private fun Map<String, String>.pidOrNull(key: String): Long? {
            val raw = this[key] ?: return null
            if (raw.isBlank()) return null
            return raw.toLongOrNull()?.takeIf { it > 0L } ?: throw IllegalArgumentException("Invalid PID")
        }

        private fun validPid(value: Long?): Boolean = value == null || value > 0L

        private fun isSafeRuntimeId(value: String): Boolean =
            value.isNotBlank() && value.length <= 128 && value.all { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' }

        private fun isStructurallyValid(identity: RuntimeIdentity): Boolean =
            isSafeRuntimeId(identity.runtimeId) &&
                identity.runtimeToken.isNotBlank() &&
                identity.runtimeToken.none { it == '\n' || it == '\r' || it == '=' } &&
                identity.startTime >= 0L &&
                identity.schemaVersion in 1..CURRENT_SCHEMA_VERSION &&
                validPid(identity.hostSessionPid) &&
                validPid(identity.hostSessionPgid) &&
                validPid(identity.guestRootPid) &&
                validPid(identity.guestRootPgid)

        private fun metadataMatches(identity: RuntimeIdentity, runtimeId: String): Boolean =
            isStructurallyValid(identity) && identity.runtimeId == runtimeId

        private fun sameMetadata(first: RuntimeIdentity, second: RuntimeIdentity): Boolean =
            first.runtimeId == second.runtimeId &&
                first.runtimeToken == second.runtimeToken &&
                first.startTime == second.startTime &&
                first.schemaVersion == second.schemaVersion
    }
}
