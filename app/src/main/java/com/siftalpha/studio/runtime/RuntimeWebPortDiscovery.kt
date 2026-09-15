package com.siftalpha.studio.runtime

internal enum class RuntimeWebDiscoveryDiagnosticStatus {
    NO_PROJECT_PIDS,
    PROCFS_UNREADABLE,
    NO_SOCKET_INODES,
    NO_INODE_MATCH,
    NO_LISTEN_PORT,
    LISTEN_PORT_FOUND,
    NO_HTTP_ENDPOINT,
    PASS,
}

internal data class RuntimeWebDiscoveryObservation(
    val projectPidCount: Int,
    val procfsReadable: Boolean,
    val socketInodeCount: Int,
    val inodeMatchCount: Int,
    val listenPortCount: Int,
    val httpEndpointReachable: Boolean? = null,
)

/**
 * Runtime-owned Web listener discovery used by LOGS/browser inspection.
 *
 * Discovery is deliberately bounded and project-process-scoped. The fast path inspects Termux
 * procfs for the managed PID/PGID. PRoot can hide socket fd metadata from that host-side view, so a
 * second pass executes the same PID-scoped inspection from inside the Ubuntu guest. This is not a
 * 1..65535 loopback scan and does not fall back to unrelated listeners owned by the Termux UID.
 */
object RuntimeWebPortDiscovery {
    private const val D = "$"
    private const val MAX_RUNTIME_CANDIDATES = 6

    private val preferredPorts = listOf(
        5173, // Vite
        3000, // Next / common Node dev server
        8000, // FastAPI / common app server
        8080,
        8501, // Streamlit
        7860, // Gradio
        8050, // Dash
        5000, // Flask / common Node server
        8888,
        5001,
        8001,
        8081,
        3001,
    )

    fun rankCandidates(candidates: Collection<Int>): List<Int> {
        val valid = candidates.filter { it in 1..65535 }.distinct()
        val preferred = preferredPorts.filter { it in valid }
        val remainder = valid.filterNot { it in preferredPorts }.sorted()
        return preferred + remainder
    }

    internal fun diagnosticStatus(
        observation: RuntimeWebDiscoveryObservation,
    ): RuntimeWebDiscoveryDiagnosticStatus = when {
        observation.projectPidCount <= 0 -> RuntimeWebDiscoveryDiagnosticStatus.NO_PROJECT_PIDS
        !observation.procfsReadable -> RuntimeWebDiscoveryDiagnosticStatus.PROCFS_UNREADABLE
        observation.socketInodeCount <= 0 -> RuntimeWebDiscoveryDiagnosticStatus.NO_SOCKET_INODES
        observation.inodeMatchCount <= 0 -> RuntimeWebDiscoveryDiagnosticStatus.NO_INODE_MATCH
        observation.listenPortCount <= 0 -> RuntimeWebDiscoveryDiagnosticStatus.NO_LISTEN_PORT
        observation.httpEndpointReachable == null ->
            RuntimeWebDiscoveryDiagnosticStatus.LISTEN_PORT_FOUND
        observation.httpEndpointReachable == true ->
            RuntimeWebDiscoveryDiagnosticStatus.PASS
        else -> RuntimeWebDiscoveryDiagnosticStatus.NO_HTTP_ENDPOINT
    }

    fun shellSnippet(): String {
        val preferred = preferredPorts.joinToString(" ")
        val guestScript = guestDiscoveryScript(preferred)
        val quotedGuestScript = shellQuote(guestScript)
        return """
            runtime_identity_id="${D}{runtime_identity_id:-}"
            runtime_identity_dir="${D}{runtime_identity_dir:-}"

            siftalpha_web_runtime_pids() {
              web_root_pid="${D}1"
              web_pgid="${D}2"
              {
                [ -n "${D}web_root_pid" ] && printf '%s\n' "${D}web_root_pid"
                [ -n "${D}web_root_pid" ] && siftalpha_descendants "${D}web_root_pid" 2>/dev/null || true
                if [ -n "${D}web_pgid" ] && command -v ps >/dev/null 2>&1; then
                  ps -eo pid=,pgid= 2>/dev/null | awk -v g="${D}web_pgid" '${D}2 == g { print ${D}1 }' || true
                fi
              } | awk 'NF && !seen[${D}1]++'
            }

            siftalpha_web_socket_inodes_for_pids() {
              for web_pid in "${D}@"; do
                [ -d "/proc/${D}web_pid/fd" ] || continue
                for web_fd in /proc/${D}web_pid/fd/*; do
                  web_link="${D}(readlink "${D}web_fd" 2>/dev/null || true)"
                  case "${D}web_link" in
                    socket:\[*\])
                      web_inode="${D}{web_link#socket:[}"
                      web_inode="${D}{web_inode%]}"
                      case "${D}web_inode" in
                        ''|*[!0-9]*) ;;
                        *) printf '%s\n' "${D}web_inode" ;;
                      esac
                      ;;
                  esac
                done
              done | awk 'NF && !seen[${D}1]++'
            }

            siftalpha_web_debug_scope() {
              local web_debug_source="${D}1"
              local web_debug_root_pid="${D}2"
              local web_debug_root_pgid="${D}3"
              local web_debug_pids="${D}4"
              local web_debug_inodes="${D}5"
              local web_debug_self_pid="${D}${D}"
              local web_debug_self_pgid
              local web_debug_inode_count
              local web_debug_pid_count=0
              local web_debug_fd_count_total=0
              local web_debug_readlink_success_total=0
              local web_debug_socket_links_total=0
              local web_debug_pid web_debug_proc_dir web_debug_fd_dir web_debug_proc_present
              local web_debug_fd_present web_debug_fd_enum web_debug_entries web_debug_fd_count
              local web_debug_readlink_success web_debug_socket_links web_debug_fd web_debug_link
              local web_debug_inode web_debug_tcp_exists web_debug_tcp_readable
              local web_debug_tcp6_exists web_debug_tcp6_readable
              web_debug_self_pgid="${D}(ps -o pgid= -p "${D}${D}" 2>/dev/null | tr -d ' ' || true)"
              web_debug_inode_count="${D}(printf '%s\n' "${D}web_debug_inodes" | awk 'NF { count++ } END { print count + 0 }' || printf '0')"
              printf 'SIFTALPHA_WEB_DEBUG_SCOPE=%s\n' "${D}web_debug_source"
              printf 'SIFTALPHA_WEB_DEBUG_SELF_PID=%s\n' "${D}web_debug_self_pid"
              printf 'SIFTALPHA_WEB_DEBUG_SELF_PGID=%s\n' "${D}web_debug_self_pgid"
              printf 'SIFTALPHA_WEB_DEBUG_ROOT_PID=%s\n' "${D}web_debug_root_pid"
              printf 'SIFTALPHA_WEB_DEBUG_ROOT_PGID=%s\n' "${D}web_debug_root_pgid"
              printf 'SIFTALPHA_WEB_DEBUG_PIDS=%s\n' "${D}web_debug_pids"
              printf 'SIFTALPHA_WEB_DEBUG_SOCKET_INODES=%s\n' "${D}web_debug_inode_count"
              for web_debug_pid in ${D}web_debug_pids; do
                web_debug_pid_count="${D}((web_debug_pid_count + 1))"
                web_debug_proc_dir="/proc/${D}web_debug_pid"
                web_debug_fd_dir="${D}web_debug_proc_dir/fd"
                web_debug_proc_present=NO
                web_debug_fd_present=NO
                web_debug_fd_enum=NO
                web_debug_entries=''
                web_debug_fd_count=0
                web_debug_readlink_success=0
                web_debug_socket_links=0
                if [ -d "${D}web_debug_proc_dir" ]; then
                  web_debug_proc_present=YES
                  if [ -d "${D}web_debug_fd_dir" ]; then
                    web_debug_fd_present=YES
                    if web_debug_entries="${D}(ls -1A "${D}web_debug_fd_dir" 2>/dev/null)"; then
                      web_debug_fd_enum=YES
                      web_debug_fd_count="${D}(printf '%s\n' "${D}web_debug_entries" | awk 'NF { count++ } END { print count + 0 }' || printf '0')"
                      for web_debug_fd in "${D}web_debug_fd_dir"/*; do
                        if [ -e "${D}web_debug_fd" ] || [ -L "${D}web_debug_fd" ]; then
                          if web_debug_link="${D}(readlink "${D}web_debug_fd" 2>/dev/null)"; then
                            web_debug_readlink_success="${D}((web_debug_readlink_success + 1))"
                            case "${D}web_debug_link" in
                              socket:\[*\])
                                web_debug_inode="${D}{web_debug_link#socket:[}"
                                web_debug_inode="${D}{web_debug_inode%]}"
                                case "${D}web_debug_inode" in
                                  ''|*[!0-9]*) ;;
                                  *) web_debug_socket_links="${D}((web_debug_socket_links + 1))" ;;
                                esac
                                ;;
                            esac
                          fi
                        fi
                      done
                    fi
                  fi
                fi
                web_debug_fd_count_total="${D}((web_debug_fd_count_total + web_debug_fd_count))"
                web_debug_readlink_success_total="${D}((web_debug_readlink_success_total + web_debug_readlink_success))"
                web_debug_socket_links_total="${D}((web_debug_socket_links_total + web_debug_socket_links))"
                printf 'SIFTALPHA_WEB_DEBUG_PID=%s PROC_DIR=%s FD_DIR=%s FD_ENUM=%s FD_COUNT=%s READLINK_SUCCESS=%s SOCKET_LINKS=%s\n' \
                  "${D}web_debug_pid" "${D}web_debug_proc_present" "${D}web_debug_fd_present" \
                  "${D}web_debug_fd_enum" "${D}web_debug_fd_count" \
                  "${D}web_debug_readlink_success" "${D}web_debug_socket_links"
              done
              printf 'SIFTALPHA_WEB_DEBUG_PID_COUNT=%s\n' "${D}web_debug_pid_count"
              printf 'SIFTALPHA_WEB_DEBUG_FD_COUNT_TOTAL=%s\n' "${D}web_debug_fd_count_total"
              printf 'SIFTALPHA_WEB_DEBUG_READLINK_SUCCESS_TOTAL=%s\n' "${D}web_debug_readlink_success_total"
              printf 'SIFTALPHA_WEB_DEBUG_SOCKET_LINKS_TOTAL=%s\n' "${D}web_debug_socket_links_total"
              if [ -e /proc/net/tcp ]; then web_debug_tcp_exists=YES; else web_debug_tcp_exists=NO; fi
              if [ -r /proc/net/tcp ]; then web_debug_tcp_readable=YES; else web_debug_tcp_readable=NO; fi
              printf 'SIFTALPHA_WEB_DEBUG_TCP_EXISTS=%s\n' "${D}web_debug_tcp_exists"
              printf 'SIFTALPHA_WEB_DEBUG_TCP_READABLE=%s\n' "${D}web_debug_tcp_readable"
              if [ -e /proc/net/tcp6 ]; then web_debug_tcp6_exists=YES; else web_debug_tcp6_exists=NO; fi
              if [ -r /proc/net/tcp6 ]; then web_debug_tcp6_readable=YES; else web_debug_tcp6_readable=NO; fi
              printf 'SIFTALPHA_WEB_DEBUG_TCP6_EXISTS=%s\n' "${D}web_debug_tcp6_exists"
              printf 'SIFTALPHA_WEB_DEBUG_TCP6_READABLE=%s\n' "${D}web_debug_tcp6_readable"
            }

            siftalpha_web_ports_for_inodes() {

              web_inodes=" ${D}1 "
              [ "${D}web_inodes" != '  ' ] || return 0
              for web_table in /proc/net/tcp /proc/net/tcp6; do
                [ -r "${D}web_table" ] || continue
                awk 'NR > 1 && ${D}4 == "0A" { split(${D}2, a, ":"); print a[2], ${D}10 }' "${D}web_table" 2>/dev/null || true
              done | while read -r web_hex_port web_inode; do
                [ -n "${D}web_hex_port" ] && [ -n "${D}web_inode" ] || continue
                case "${D}web_inodes" in
                  *" ${D}web_inode "*)
                    web_port="${D}((16#${D}web_hex_port))"
                    if [ "${D}web_port" -ge 1 ] 2>/dev/null && [ "${D}web_port" -le 65535 ] 2>/dev/null; then
                      printf '%s\n' "${D}web_port"
                    fi
                    ;;
                esac
              done | sort -n -u
            }

            siftalpha_web_diagnostic_status_for_scope() {
              web_diagnostic_source="${D}1"
              web_diagnostic_pids="${D}2"
              web_diagnostic_inodes="${D}3"
              siftalpha_web_debug_scope \
                "${D}web_diagnostic_source" "${D}4" "${D}5" \
                "${D}web_diagnostic_pids" "${D}web_diagnostic_inodes"
              if [ -z "${D}{web_diagnostic_pids// }" ]; then
                printf 'SIFTALPHA_WEB_DISCOVERY_STATUS=NO_PROJECT_PIDS source=%s\n' "${D}web_diagnostic_source"
                return 0
              fi

              web_diagnostic_fd_directory_present=0
              for web_pid in ${D}web_diagnostic_pids; do
                if [ -d "/proc/${D}web_pid/fd" ]; then
                  web_diagnostic_fd_directory_present=1
                  break
                fi
              done
              if [ "${D}web_diagnostic_fd_directory_present" -eq 0 ]; then
                printf 'SIFTALPHA_WEB_DISCOVERY_STATUS=PROCFS_UNREADABLE source=%s\n' "${D}web_diagnostic_source"
                return 0
              fi

              if [ -z "${D}{web_diagnostic_inodes// }" ]; then
                printf 'SIFTALPHA_WEB_DISCOVERY_STATUS=NO_SOCKET_INODES source=%s\n' "${D}web_diagnostic_source"
                return 0
              fi

              web_diagnostic_tables_readable=0
              web_diagnostic_inode_matches=0
              web_diagnostic_listen_ports=0
              for web_table in /proc/net/tcp /proc/net/tcp6; do
                [ -r "${D}web_table" ] || continue
                web_diagnostic_tables_readable=1
                while read -r web_state web_inode; do
                  [ -n "${D}web_inode" ] || continue
                  case " ${D}web_diagnostic_inodes " in
                    *" ${D}web_inode "*)
                      web_diagnostic_inode_matches="${D}((web_diagnostic_inode_matches + 1))"
                      [ "${D}web_state" = '0A' ] && web_diagnostic_listen_ports="${D}((web_diagnostic_listen_ports + 1))"
                      ;;
                  esac
                done < <(awk 'NR > 1 { print ${D}4, ${D}10 }' "${D}web_table" 2>/dev/null || true)
              done

              if [ "${D}web_diagnostic_tables_readable" -eq 0 ]; then
                printf 'SIFTALPHA_WEB_DISCOVERY_STATUS=PROCFS_UNREADABLE source=%s\n' "${D}web_diagnostic_source"
              elif [ "${D}web_diagnostic_inode_matches" -eq 0 ]; then
                printf 'SIFTALPHA_WEB_DISCOVERY_STATUS=NO_INODE_MATCH source=%s\n' "${D}web_diagnostic_source"
              elif [ "${D}web_diagnostic_listen_ports" -eq 0 ]; then
                printf 'SIFTALPHA_WEB_DISCOVERY_STATUS=NO_LISTEN_PORT source=%s\n' "${D}web_diagnostic_source"
              else
                printf 'SIFTALPHA_WEB_DISCOVERY_STATUS=LISTEN_PORT_FOUND source=%s\n' "${D}web_diagnostic_source"
                printf 'SIFTALPHA_WEB_DISCOVERY_STAGE=LISTEN_FOUND source=%s\n' "${D}web_diagnostic_source"
              fi
              return 0
            }

            siftalpha_web_candidate_ports() {
              web_root_pid="${D}1"
              web_pgid="${D}2"
              web_pids="${D}(siftalpha_web_runtime_pids "${D}web_root_pid" "${D}web_pgid" | tr '\n' ' ')"
              [ -n "${D}{web_pids// }" ] || return 0
              # shellcheck disable=SC2086
              web_inodes="${D}(siftalpha_web_socket_inodes_for_pids ${D}web_pids | tr '\n' ' ')"
              siftalpha_web_ports_for_inodes "${D}web_inodes"
            }

            siftalpha_web_http_probe() {
              web_port="${D}1"
              if command -v timeout >/dev/null 2>&1; then
                web_first_line="${D}(timeout 1 bash -c 'exec 3<>"/dev/tcp/127.0.0.1/${D}1" || exit 1; printf "GET / HTTP/1.0\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n" >&3; IFS= read -r line <&3 || true; printf "%s" "${D}line"' _ "${D}web_port" 2>/dev/null | tr -d '\r' | head -n 1 || true)"
              elif command -v busybox >/dev/null 2>&1; then
                web_first_line="${D}(busybox timeout 1 bash -c 'exec 3<>"/dev/tcp/127.0.0.1/${D}1" || exit 1; printf "GET / HTTP/1.0\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n" >&3; IFS= read -r line <&3 || true; printf "%s" "${D}line"' _ "${D}web_port" 2>/dev/null | tr -d '\r' | head -n 1 || true)"
              else
                return 1
              fi
              case "${D}web_first_line" in
                HTTP/*) return 0 ;;
                *) return 1 ;;
              esac
            }

            siftalpha_web_order_ports() {
              web_ports="${D}1"
              web_ordered=''
              for web_preferred in $preferred; do
                case " ${D}web_ports " in
                  *" ${D}web_preferred "*) web_ordered="${D}web_ordered ${D}web_preferred" ;;
                esac
              done
              for web_port in ${D}web_ports; do
                case " ${D}web_ordered " in
                  *" ${D}web_port "*) ;;
                  *) web_ordered="${D}web_ordered ${D}web_port" ;;
                esac
              done
              printf '%s\n' "${D}web_ordered"
            }

            siftalpha_web_probe_ports() {
              web_source="${D}1"
              shift
              web_ports="${D}*"
              [ -n "${D}{web_ports// }" ] || return 2
              web_ordered="${D}(siftalpha_web_order_ports "${D}web_ports")"
              web_checked=0
              for web_port in ${D}web_ordered; do
                if [ "${D}web_checked" -ge $MAX_RUNTIME_CANDIDATES ]; then
                  echo 'SIFTALPHA_WEB_RUNTIME_CANDIDATE_LIMIT=REACHED'
                  break
                fi
                web_checked="${D}((web_checked + 1))"
                printf 'SIFTALPHA_WEB_PORT_CANDIDATE=%s source=%s\n' "${D}web_port" "${D}web_source"
                if siftalpha_web_http_probe "${D}web_port"; then
                  printf 'SIFTALPHA_WEB_DISCOVERY_STATUS=PASS source=%s port=%s\n' "${D}web_source" "${D}web_port"
                  printf 'SIFTALPHA_WEB_AUTODISCOVERY=PASS source=%s port=%s\n' "${D}web_source" "${D}web_port"
                  printf 'SIFTALPHA_WEB_URL=http://127.0.0.1:%s\n' "${D}web_port"
                  return 0
                fi
              done
              return 1
            }

            siftalpha_web_guest_fallback() {
              web_root_pid="${D}1"
              web_pgid="${D}2"
              identity_host_available=0
              identity_guest_available=0
              legacy_pid_available=0
              identity_resolution='${RuntimeIdentitySource.UNAVAILABLE.name}'
              identity_mode='unavailable'
              if [ -n "${D}runtime_identity_id" ] && \
                 [ -n "${D}runtime_identity_dir" ] && \
                 [ -r "${D}runtime_identity_dir/${RuntimeIdentityStore.IDENTITY_FILE_NAME}" ]; then
                identity_host_available=1
              fi
              if [ -n "${D}runtime_identity_id" ] && \
                 [ -n "${D}runtime_identity_dir" ] && \
                 [ -r "${D}runtime_identity_dir/${RuntimeIdentityStore.GUEST_IDENTITY_FILE_NAME}" ]; then
                identity_guest_available=1
              fi
              if [ -n "${D}web_root_pid" ] || [ -n "${D}web_pgid" ]; then
                legacy_pid_available=1
              fi
              if [ "${D}identity_host_available" -eq 1 ] && [ "${D}identity_guest_available" -eq 1 ]; then
                identity_resolution='${RuntimeIdentitySource.FULL_IDENTITY.name}'
                identity_mode='identity'
              elif [ "${D}identity_host_available" -eq 1 ]; then
                identity_resolution='${RuntimeIdentitySource.HOST_ONLY.name}'
                identity_mode='partial_identity'
              elif [ "${D}identity_guest_available" -eq 1 ]; then
                identity_resolution='${RuntimeIdentitySource.GUEST_ONLY.name}'
                identity_mode='partial_identity'
              elif [ "${D}legacy_pid_available" -eq 1 ]; then
                identity_resolution='${RuntimeIdentitySource.LEGACY_PID.name}'
                identity_mode='legacy'
              fi
              if [ "${D}identity_mode" != 'identity' ]; then
                printf 'SIFTALPHA_RUNTIME_IDENTITY_RESOLUTION=%s\n' "${D}identity_resolution"
              fi

              siftalpha_web_legacy_fallback() {
                echo 'SIFTALPHA_RUNTIME_IDENTITY_SOURCE=${RuntimeIdentityUsage.LEGACY_PID.name}'
                echo 'SIFTALPHA_RUNTIME_IDENTITY_FALLBACK=${RuntimeIdentityUsage.LEGACY_PID.name}'
                web_guest_output="${D}(proot-distro login --bind "${D}ROOT:/root/projects" ubuntu -- bash -lc $quotedGuestScript siftalpha-web legacy "${D}web_root_pid" "${D}web_pgid" 2>/dev/null || true)"
                [ -n "${D}web_guest_output" ] && printf '%s\n' "${D}web_guest_output"
                case "${D}web_guest_output" in
                  *'SIFTALPHA_WEB_AUTODISCOVERY=PASS source=PROOT_PROJECT_PID_SCOPE'*) web_guest_success=1 ;;
                esac
              }

              if ! command -v proot-distro >/dev/null 2>&1; then
                echo 'SIFTALPHA_RUNTIME_IDENTITY_SOURCE=${RuntimeIdentityUsage.UNAVAILABLE.name}'
                echo 'SIFTALPHA_WEB_GUEST_SCOPE=UNAVAILABLE reason=PROOT_DISTRO_MISSING'
                return 2
              fi
              if [ "${D}identity_mode" = 'identity' ]; then
                web_guest_output="${D}(proot-distro login \
                  --bind "${D}ROOT:/root/projects" \
                  --bind "${D}runtime_identity_dir:${RuntimeIdentityStore.GUEST_RUNTIME_ROOT}/${D}runtime_identity_id" \
                  ubuntu -- bash -lc $quotedGuestScript siftalpha-web identity "${D}runtime_identity_id" 2>/dev/null || true)"
                [ -n "${D}web_guest_output" ] && printf '%s\n' "${D}web_guest_output"
                web_guest_success=0
                case "${D}web_guest_output" in
                  *'SIFTALPHA_RUNTIME_IDENTITY_SOURCE=${RuntimeIdentityUsage.FULL_IDENTITY.name}'*) web_guest_success=1 ;;
                  *)
                    if [ "${D}legacy_pid_available" -eq 1 ]; then
                      siftalpha_web_legacy_fallback
                    else
                      echo 'SIFTALPHA_RUNTIME_IDENTITY_SOURCE=${RuntimeIdentityUsage.UNAVAILABLE.name}'
                    fi
                    ;;
                esac
              elif [ "${D}identity_mode" = 'legacy' ]; then
                web_guest_success=0
                siftalpha_web_legacy_fallback
              elif [ "${D}identity_mode" = 'partial_identity' ]; then
                web_guest_success=0
                if [ "${D}legacy_pid_available" -eq 1 ]; then
                  siftalpha_web_legacy_fallback
                else
                  echo 'SIFTALPHA_RUNTIME_IDENTITY_SOURCE=${RuntimeIdentityUsage.UNAVAILABLE.name}'
                fi
              else
                web_guest_success=0
                echo 'SIFTALPHA_RUNTIME_IDENTITY_SOURCE=${RuntimeIdentityUsage.UNAVAILABLE.name}'
              fi
              [ "${D}web_guest_success" -eq 1 ] && return 0
              return 1
            }

            siftalpha_web_autodiscover() {
              web_root_pid="${D}1"
              web_pgid="${D}2"
              web_primary_pids="${D}(siftalpha_web_runtime_pids "${D}web_root_pid" "${D}web_pgid" | tr '\n' ' ')"
              # shellcheck disable=SC2086
              web_primary_inodes="${D}(siftalpha_web_socket_inodes_for_pids ${D}web_primary_pids | tr '\n' ' ')"
              siftalpha_web_diagnostic_status_for_scope PROJECT_PID_SCOPE "${D}web_primary_pids" "${D}web_primary_inodes" "${D}web_root_pid" "${D}web_pgid"
              web_ports="${D}(siftalpha_web_candidate_ports "${D}web_root_pid" "${D}web_pgid" | tr '\n' ' ')"

              if [ -n "${D}{web_ports// }" ]; then
                if siftalpha_web_probe_ports PROJECT_PID_SCOPE ${D}web_ports; then
                  return 0
                fi
                printf 'SIFTALPHA_WEB_DISCOVERY_STATUS=NO_HTTP_ENDPOINT source=PROJECT_PID_SCOPE\n'
                echo 'SIFTALPHA_WEB_PRIMARY_SCOPE=NO_HTTP_ENDPOINT'
              else
                echo 'SIFTALPHA_WEB_PRIMARY_SCOPE=NO_LISTEN_PORT'
              fi

              if siftalpha_web_guest_fallback "${D}web_root_pid" "${D}web_pgid"; then
                return 0
              fi

              if [ -n "${D}{web_ports// }" ]; then
                echo 'SIFTALPHA_WEB_AUTODISCOVERY=NO_HTTP_ENDPOINT source=PROJECT_AND_PROOT_PID_SCOPE'
              else
                echo 'SIFTALPHA_WEB_AUTODISCOVERY=NO_LISTEN_PORT source=PROJECT_AND_PROOT_PID_SCOPE'
              fi
              return 0
            }

            siftalpha_web_autodiscover "${D}pid" "${D}pgid" || true
        """.trimIndent()
    }

    private fun guestDiscoveryScript(preferred: String): String = """
        ${RuntimeIdentityStore.guestIdentityLoaderShell()}

        guest_mode="${D}1"
        web_root_pid=''
        web_pgid=''

        if [ "${D}guest_mode" = 'identity' ]; then
          guest_identity_id="${D}2"
          if ! siftalpha_runtime_identity_load "${D}guest_identity_id"; then
            echo 'SIFTALPHA_RUNTIME_IDENTITY_RESOLUTION=${RuntimeIdentitySource.METADATA_MISMATCH.name}'
            echo 'SIFTALPHA_WEB_DISCOVERY_STATUS=NO_PROJECT_PIDS source=PROOT_PROJECT_PID_SCOPE reason=RUNTIME_IDENTITY_INVALID'
            echo 'SIFTALPHA_WEB_GUEST_SCOPE=RUNTIME_IDENTITY_INVALID'
            exit 0
          fi
          web_root_pid="${D}SIFTALPHA_RUNTIME_IDENTITY_GUEST_ROOT_PID"
          web_pgid="${D}SIFTALPHA_RUNTIME_IDENTITY_GUEST_ROOT_PGID"
          if [ ! -d "/proc/${D}web_root_pid" ]; then
            echo 'SIFTALPHA_RUNTIME_IDENTITY_SOURCE=${RuntimeIdentityUsage.FULL_IDENTITY.name}'
            echo 'SIFTALPHA_RUNTIME_IDENTITY_GUEST_ROOT=NOT_ALIVE'
            echo 'SIFTALPHA_WEB_DISCOVERY_STATUS=NO_PROJECT_PIDS source=PROOT_PROJECT_PID_SCOPE reason=RUNTIME_ROOT_NOT_ALIVE'
            echo 'SIFTALPHA_WEB_GUEST_SCOPE=RUNTIME_ROOT_NOT_ALIVE'
            exit 0
          fi
          echo 'SIFTALPHA_RUNTIME_IDENTITY_RESOLUTION=${RuntimeIdentitySource.FULL_IDENTITY.name}'
          echo 'SIFTALPHA_RUNTIME_IDENTITY_SOURCE=${RuntimeIdentityUsage.FULL_IDENTITY.name}'
          echo 'SIFTALPHA_RUNTIME_IDENTITY_GUEST_ROOT=ALIVE'
          echo 'SIFTALPHA_WEB_IDENTITY=RUNTIME_IDENTITY'
        elif [ "${D}guest_mode" = 'legacy' ]; then
          web_root_pid="${D}2"
          web_pgid="${D}3"
        else
          # Keep the old argument shape usable for runtimes created by older app versions.
          web_root_pid="${D}1"
          web_pgid="${D}2"
        fi

        guest_descendants() {
          guest_parent="${D}1"
          guest_frontier="${D}guest_parent"
          guest_seen=" ${D}guest_parent "
          while [ -n "${D}{guest_frontier// }" ]; do
            guest_next=''
            for guest_pid in ${D}guest_frontier; do
              guest_children="${D}(cat "/proc/${D}guest_pid/task/${D}guest_pid/children" 2>/dev/null || true)"
              for guest_child in ${D}guest_children; do
                case "${D}guest_seen" in
                  *" ${D}guest_child "*) ;;
                  *)
                    guest_seen="${D}guest_seen${D}guest_child "
                    guest_next="${D}guest_next ${D}guest_child"
                    printf '%s\n' "${D}guest_child"
                    ;;
                esac
              done
            done
            guest_frontier="${D}guest_next"
          done
        }

        guest_pids="${D}(
          {
            [ -n "${D}web_root_pid" ] && printf '%s\n' "${D}web_root_pid"
            [ -n "${D}web_root_pid" ] && guest_descendants "${D}web_root_pid"
            if [ -n "${D}web_pgid" ] && command -v ps >/dev/null 2>&1; then
              ps -eo pid=,pgid= 2>/dev/null | awk -v g="${D}web_pgid" '${D}2 == g { print ${D}1 }' || true
            fi
          } | awk 'NF && !seen[${D}1]++' | tr '\n' ' '
        )"
        siftalpha_web_debug_scope() {
          local web_debug_source="${D}1"
          local web_debug_root_pid="${D}2"
          local web_debug_root_pgid="${D}3"
          local web_debug_pids="${D}4"
          local web_debug_inodes="${D}5"
          local web_debug_self_pid="${D}${D}"
          local web_debug_self_pgid
          local web_debug_inode_count
          local web_debug_pid_count=0
          local web_debug_fd_count_total=0
          local web_debug_readlink_success_total=0
          local web_debug_socket_links_total=0
          local web_debug_pid web_debug_proc_dir web_debug_fd_dir web_debug_proc_present
          local web_debug_fd_present web_debug_fd_enum web_debug_entries web_debug_fd_count
          local web_debug_readlink_success web_debug_socket_links web_debug_fd web_debug_link
          local web_debug_inode web_debug_tcp_exists web_debug_tcp_readable
          local web_debug_tcp6_exists web_debug_tcp6_readable
          web_debug_self_pgid="${D}(ps -o pgid= -p "${D}${D}" 2>/dev/null | tr -d ' ' || true)"
          web_debug_inode_count="${D}(printf '%s\n' "${D}web_debug_inodes" | awk 'NF { count++ } END { print count + 0 }' || printf '0')"
          printf 'SIFTALPHA_WEB_DEBUG_SCOPE=%s\n' "${D}web_debug_source"
          printf 'SIFTALPHA_WEB_DEBUG_SELF_PID=%s\n' "${D}web_debug_self_pid"
          printf 'SIFTALPHA_WEB_DEBUG_SELF_PGID=%s\n' "${D}web_debug_self_pgid"
          printf 'SIFTALPHA_WEB_DEBUG_ROOT_PID=%s\n' "${D}web_debug_root_pid"
          printf 'SIFTALPHA_WEB_DEBUG_ROOT_PGID=%s\n' "${D}web_debug_root_pgid"
          printf 'SIFTALPHA_WEB_DEBUG_PIDS=%s\n' "${D}web_debug_pids"
          printf 'SIFTALPHA_WEB_DEBUG_SOCKET_INODES=%s\n' "${D}web_debug_inode_count"
          for web_debug_pid in ${D}web_debug_pids; do
            web_debug_pid_count="${D}((web_debug_pid_count + 1))"
            web_debug_proc_dir="/proc/${D}web_debug_pid"
            web_debug_fd_dir="${D}web_debug_proc_dir/fd"
            web_debug_proc_present=NO
            web_debug_fd_present=NO
            web_debug_fd_enum=NO
            web_debug_entries=''
            web_debug_fd_count=0
            web_debug_readlink_success=0
            web_debug_socket_links=0
            if [ -d "${D}web_debug_proc_dir" ]; then
              web_debug_proc_present=YES
              if [ -d "${D}web_debug_fd_dir" ]; then
                web_debug_fd_present=YES
                if web_debug_entries="${D}(ls -1A "${D}web_debug_fd_dir" 2>/dev/null)"; then
                  web_debug_fd_enum=YES
                  web_debug_fd_count="${D}(printf '%s\n' "${D}web_debug_entries" | awk 'NF { count++ } END { print count + 0 }' || printf '0')"
                  for web_debug_fd in "${D}web_debug_fd_dir"/*; do
                    if [ -e "${D}web_debug_fd" ] || [ -L "${D}web_debug_fd" ]; then
                      if web_debug_link="${D}(readlink "${D}web_debug_fd" 2>/dev/null)"; then
                        web_debug_readlink_success="${D}((web_debug_readlink_success + 1))"
                        case "${D}web_debug_link" in
                          socket:\[*\])
                            web_debug_inode="${D}{web_debug_link#socket:[}"
                            web_debug_inode="${D}{web_debug_inode%]}"
                            case "${D}web_debug_inode" in
                              ''|*[!0-9]*) ;;
                              *) web_debug_socket_links="${D}((web_debug_socket_links + 1))" ;;
                            esac
                            ;;
                        esac
                      fi
                    fi
                  done
                fi
              fi
            fi
            web_debug_fd_count_total="${D}((web_debug_fd_count_total + web_debug_fd_count))"
            web_debug_readlink_success_total="${D}((web_debug_readlink_success_total + web_debug_readlink_success))"
            web_debug_socket_links_total="${D}((web_debug_socket_links_total + web_debug_socket_links))"
            printf 'SIFTALPHA_WEB_DEBUG_PID=%s PROC_DIR=%s FD_DIR=%s FD_ENUM=%s FD_COUNT=%s READLINK_SUCCESS=%s SOCKET_LINKS=%s\n' \
              "${D}web_debug_pid" "${D}web_debug_proc_present" "${D}web_debug_fd_present" \
              "${D}web_debug_fd_enum" "${D}web_debug_fd_count" \
              "${D}web_debug_readlink_success" "${D}web_debug_socket_links"
          done
          printf 'SIFTALPHA_WEB_DEBUG_PID_COUNT=%s\n' "${D}web_debug_pid_count"
          printf 'SIFTALPHA_WEB_DEBUG_FD_COUNT_TOTAL=%s\n' "${D}web_debug_fd_count_total"
          printf 'SIFTALPHA_WEB_DEBUG_READLINK_SUCCESS_TOTAL=%s\n' "${D}web_debug_readlink_success_total"
          printf 'SIFTALPHA_WEB_DEBUG_SOCKET_LINKS_TOTAL=%s\n' "${D}web_debug_socket_links_total"
          if [ -e /proc/net/tcp ]; then web_debug_tcp_exists=YES; else web_debug_tcp_exists=NO; fi
          if [ -r /proc/net/tcp ]; then web_debug_tcp_readable=YES; else web_debug_tcp_readable=NO; fi
          printf 'SIFTALPHA_WEB_DEBUG_TCP_EXISTS=%s\n' "${D}web_debug_tcp_exists"
          printf 'SIFTALPHA_WEB_DEBUG_TCP_READABLE=%s\n' "${D}web_debug_tcp_readable"
          if [ -e /proc/net/tcp6 ]; then web_debug_tcp6_exists=YES; else web_debug_tcp6_exists=NO; fi
          if [ -r /proc/net/tcp6 ]; then web_debug_tcp6_readable=YES; else web_debug_tcp6_readable=NO; fi
          printf 'SIFTALPHA_WEB_DEBUG_TCP6_EXISTS=%s\n' "${D}web_debug_tcp6_exists"
          printf 'SIFTALPHA_WEB_DEBUG_TCP6_READABLE=%s\n' "${D}web_debug_tcp6_readable"
        }

        if [ -z "${D}{guest_pids// }" ]; then
          siftalpha_web_debug_scope PROOT_PROJECT_PID_SCOPE "${D}web_root_pid" "${D}web_pgid" "${D}guest_pids" ''
          echo 'SIFTALPHA_WEB_DISCOVERY_STATUS=NO_PROJECT_PIDS source=PROOT_PROJECT_PID_SCOPE'
          echo 'SIFTALPHA_WEB_GUEST_SCOPE=NO_PROJECT_PIDS'
          exit 0
        fi

        guest_fd_directory_present=0
        for guest_pid in ${D}guest_pids; do
          if [ -d "/proc/${D}guest_pid/fd" ]; then
            guest_fd_directory_present=1
            break
          fi
        done
        if [ "${D}guest_fd_directory_present" -eq 0 ]; then
          siftalpha_web_debug_scope PROOT_PROJECT_PID_SCOPE "${D}web_root_pid" "${D}web_pgid" "${D}guest_pids" ''
          echo 'SIFTALPHA_WEB_DISCOVERY_STATUS=PROCFS_UNREADABLE source=PROOT_PROJECT_PID_SCOPE'
          echo 'SIFTALPHA_WEB_GUEST_SCOPE=NO_SOCKET_INODES'
          exit 0
        fi

        guest_inodes="${D}(
          for guest_pid in ${D}guest_pids; do
            [ -d "/proc/${D}guest_pid/fd" ] || continue
            for guest_fd in /proc/${D}guest_pid/fd/*; do
              guest_link="${D}(readlink "${D}guest_fd" 2>/dev/null || true)"
              case "${D}guest_link" in
                socket:\[*\])
                  guest_inode="${D}{guest_link#socket:[}"
                  guest_inode="${D}{guest_inode%]}"
                  case "${D}guest_inode" in
                    ''|*[!0-9]*) ;;
                    *) printf '%s\n' "${D}guest_inode" ;;
                  esac
                  ;;
              esac
            done
          done | awk 'NF && !seen[${D}1]++' | tr '\n' ' '
        )"
        siftalpha_web_debug_scope PROOT_PROJECT_PID_SCOPE "${D}web_root_pid" "${D}web_pgid" "${D}guest_pids" "${D}guest_inodes"
        if [ -z "${D}{guest_inodes// }" ]; then
          echo 'SIFTALPHA_WEB_DISCOVERY_STATUS=NO_SOCKET_INODES source=PROOT_PROJECT_PID_SCOPE'
          echo 'SIFTALPHA_WEB_GUEST_SCOPE=NO_SOCKET_INODES'
          exit 0
        fi

        guest_diagnostic_tables_readable=0
        guest_diagnostic_inode_matches=0
        guest_diagnostic_listen_ports=0
        for guest_table in /proc/net/tcp /proc/net/tcp6; do
          [ -r "${D}guest_table" ] || continue
          guest_diagnostic_tables_readable=1
          while read -r guest_state guest_inode; do
            [ -n "${D}guest_inode" ] || continue
            case " ${D}guest_inodes " in
              *" ${D}guest_inode "*)
                guest_diagnostic_inode_matches="${D}((guest_diagnostic_inode_matches + 1))"
                [ "${D}guest_state" = '0A' ] && guest_diagnostic_listen_ports="${D}((guest_diagnostic_listen_ports + 1))"
                ;;
            esac
          done < <(awk 'NR > 1 { print ${D}4, ${D}10 }' "${D}guest_table" 2>/dev/null || true)
        done
        if [ "${D}guest_diagnostic_tables_readable" -eq 0 ]; then
          echo 'SIFTALPHA_WEB_DISCOVERY_STATUS=PROCFS_UNREADABLE source=PROOT_PROJECT_PID_SCOPE'
        elif [ "${D}guest_diagnostic_inode_matches" -eq 0 ]; then
          echo 'SIFTALPHA_WEB_DISCOVERY_STATUS=NO_INODE_MATCH source=PROOT_PROJECT_PID_SCOPE'
        elif [ "${D}guest_diagnostic_listen_ports" -eq 0 ]; then
          echo 'SIFTALPHA_WEB_DISCOVERY_STATUS=NO_LISTEN_PORT source=PROOT_PROJECT_PID_SCOPE'
        else
          echo 'SIFTALPHA_WEB_DISCOVERY_STATUS=LISTEN_PORT_FOUND source=PROOT_PROJECT_PID_SCOPE'
          echo 'SIFTALPHA_WEB_DISCOVERY_STAGE=LISTEN_FOUND source=PROOT_PROJECT_PID_SCOPE'
        fi

        guest_ports="${D}(
          for guest_table in /proc/net/tcp /proc/net/tcp6; do
            [ -r "${D}guest_table" ] || continue
            awk 'NR > 1 && ${D}4 == "0A" { split(${D}2, a, ":"); print a[2], ${D}10 }' "${D}guest_table" 2>/dev/null || true
          done | while read -r guest_hex_port guest_inode; do
            case " ${D}guest_inodes " in
              *" ${D}guest_inode "*)
                guest_port="${D}((16#${D}guest_hex_port))"
                [ "${D}guest_port" -ge 1 ] 2>/dev/null && [ "${D}guest_port" -le 65535 ] 2>/dev/null && printf '%s\n' "${D}guest_port"
                ;;
            esac
          done | sort -n -u | tr '\n' ' '
        )"
        if [ -z "${D}{guest_ports// }" ]; then
          echo 'SIFTALPHA_WEB_GUEST_SCOPE=NO_LISTEN_PORT'
          exit 0
        fi

        guest_ordered=''
        for guest_preferred in $preferred; do
          case " ${D}guest_ports " in
            *" ${D}guest_preferred "*) guest_ordered="${D}guest_ordered ${D}guest_preferred" ;;
          esac
        done
        for guest_port in ${D}guest_ports; do
          case " ${D}guest_ordered " in
            *" ${D}guest_port "*) ;;
            *) guest_ordered="${D}guest_ordered ${D}guest_port" ;;
          esac
        done

        guest_checked=0
        for guest_port in ${D}guest_ordered; do
          if [ "${D}guest_checked" -ge $MAX_RUNTIME_CANDIDATES ]; then
            echo 'SIFTALPHA_WEB_RUNTIME_CANDIDATE_LIMIT=REACHED source=PROOT_PROJECT_PID_SCOPE'
            break
          fi
          guest_checked="${D}((guest_checked + 1))"
          printf 'SIFTALPHA_WEB_PORT_CANDIDATE=%s source=PROOT_PROJECT_PID_SCOPE\n' "${D}guest_port"
          guest_first_line="${D}(timeout 1 bash -c 'exec 3<>"/dev/tcp/127.0.0.1/${D}1" || exit 1; printf "GET / HTTP/1.0\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n" >&3; IFS= read -r line <&3 || true; printf "%s" "${D}line"' _ "${D}guest_port" 2>/dev/null | tr -d '\r' | head -n 1 || true)"
          case "${D}guest_first_line" in
            HTTP/*)
              printf 'SIFTALPHA_WEB_DISCOVERY_STATUS=PASS source=PROOT_PROJECT_PID_SCOPE port=%s\n' "${D}guest_port"
              printf 'SIFTALPHA_WEB_AUTODISCOVERY=PASS source=PROOT_PROJECT_PID_SCOPE port=%s\n' "${D}guest_port"
              printf 'SIFTALPHA_WEB_URL=http://127.0.0.1:%s\n' "${D}guest_port"
              exit 0
              ;;
          esac
        done
        echo 'SIFTALPHA_WEB_DISCOVERY_STATUS=NO_HTTP_ENDPOINT source=PROOT_PROJECT_PID_SCOPE'
        echo 'SIFTALPHA_WEB_GUEST_SCOPE=NO_HTTP_ENDPOINT'
    """.trimIndent()

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\"'\"'") + "'"
}
