#include <Python.h>

#include <jni.h>
#include <android/log.h>

#include <algorithm>
#include <atomic>
#include <chrono>
#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <cerrno>
#include <exception>
#include <fstream>
#include <iterator>
#include <limits>
#include <limits.h>
#include <memory>
#include <mutex>
#include <set>
#include <string>
#include <thread>
#include <vector>

#include <sys/stat.h>
#include <unistd.h>

namespace {

constexpr char kLogTag[] = "SiftAlphaX";
constexpr std::size_t kOutputLimit = 32 * 1024;
constexpr std::size_t kSourceLimit = 8 * 1024 * 1024;

enum class SessionState {
    IDLE,
    STARTING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    STOPPED,
};

const char* stateName(SessionState state) {
    switch (state) {
        case SessionState::IDLE:
            return "IDLE";
        case SessionState::STARTING:
            return "STARTING";
        case SessionState::RUNNING:
            return "RUNNING";
        case SessionState::SUCCEEDED:
            return "SUCCEEDED";
        case SessionState::FAILED:
            return "FAILED";
        case SessionState::STOPPED:
            return "STOPPED";
    }
    return "FAILED";
}

const char* resultName(SessionState state) {
    switch (state) {
        case SessionState::SUCCEEDED:
            return "SUCCESS";
        case SessionState::FAILED:
            return "FAILED";
        case SessionState::STOPPED:
            return "STOPPED";
        case SessionState::IDLE:
        case SessionState::STARTING:
        case SessionState::RUNNING:
            return "NOT_FINISHED";
    }
    return "FAILED";
}

enum class RuntimePhase {
    IDLE,
    SESSION_CREATED,
    PROJECT_SPEC_VALIDATE_BEGIN,
    PROJECT_SPEC_VALIDATED,
    WORKER_ENTERED,
    RUNTIME_INIT_BEGIN,
    CPYTHON_READY,
    GIL_ACQUIRE_BEGIN,
    GIL_ACQUIRED,
    THREAD_STATE_READY,
    RUNNING,
    PYTHON_EXEC_BEGIN,
    PYTHON_EXEC_END,
    GIL_RELEASE_BEGIN,
    GIL_RELEASED,
    TERMINAL,
    WORKER_EXIT,
};

const char* runtimePhaseName(RuntimePhase phase) {
    switch (phase) {
        case RuntimePhase::IDLE:
            return "IDLE";
        case RuntimePhase::SESSION_CREATED:
            return "SESSION_CREATED";
        case RuntimePhase::PROJECT_SPEC_VALIDATE_BEGIN:
            return "PROJECT_SPEC_VALIDATE_BEGIN";
        case RuntimePhase::PROJECT_SPEC_VALIDATED:
            return "PROJECT_SPEC_VALIDATED";
        case RuntimePhase::WORKER_ENTERED:
            return "WORKER_ENTERED";
        case RuntimePhase::RUNTIME_INIT_BEGIN:
            return "RUNTIME_INIT_BEGIN";
        case RuntimePhase::CPYTHON_READY:
            return "CPYTHON_READY";
        case RuntimePhase::GIL_ACQUIRE_BEGIN:
            return "GIL_ACQUIRE_BEGIN";
        case RuntimePhase::GIL_ACQUIRED:
            return "GIL_ACQUIRED";
        case RuntimePhase::THREAD_STATE_READY:
            return "THREAD_STATE_READY";
        case RuntimePhase::RUNNING:
            return "RUNNING";
        case RuntimePhase::PYTHON_EXEC_BEGIN:
            return "PYTHON_EXEC_BEGIN";
        case RuntimePhase::PYTHON_EXEC_END:
            return "PYTHON_EXEC_END";
        case RuntimePhase::GIL_RELEASE_BEGIN:
            return "GIL_RELEASE_BEGIN";
        case RuntimePhase::GIL_RELEASED:
            return "GIL_RELEASED";
        case RuntimePhase::TERMINAL:
            return "TERMINAL";
        case RuntimePhase::WORKER_EXIT:
            return "WORKER_EXIT";
    }
    return "IDLE";
}

enum class StopPhase {
    IDLE,
    STOP_REQUEST_RECEIVED,
    STOP_TARGET_FOUND,
    STOP_THREAD_STATE_ATTACH_BEGIN,
    STOP_THREAD_STATE_ATTACHED,
    STOP_INTERRUPT_BEGIN,
    STOP_INTERRUPT_RESULT_0,
    STOP_INTERRUPT_RESULT_1,
    STOP_INTERRUPT_RESULT_GT1,
    STOP_REQUEST_RETURNED,
};

const char* stopPhaseName(StopPhase phase) {
    switch (phase) {
        case StopPhase::IDLE:
            return "IDLE";
        case StopPhase::STOP_REQUEST_RECEIVED:
            return "STOP_REQUEST_RECEIVED";
        case StopPhase::STOP_TARGET_FOUND:
            return "STOP_TARGET_FOUND";
        case StopPhase::STOP_THREAD_STATE_ATTACH_BEGIN:
            return "STOP_THREAD_STATE_ATTACH_BEGIN";
        case StopPhase::STOP_THREAD_STATE_ATTACHED:
            return "STOP_THREAD_STATE_ATTACHED";
        case StopPhase::STOP_INTERRUPT_BEGIN:
            return "STOP_INTERRUPT_BEGIN";
        case StopPhase::STOP_INTERRUPT_RESULT_0:
            return "STOP_INTERRUPT_RESULT_0";
        case StopPhase::STOP_INTERRUPT_RESULT_1:
            return "STOP_INTERRUPT_RESULT_1";
        case StopPhase::STOP_INTERRUPT_RESULT_GT1:
            return "STOP_INTERRUPT_RESULT_GT1";
        case StopPhase::STOP_REQUEST_RETURNED:
            return "STOP_REQUEST_RETURNED";
    }
    return "IDLE";
}

enum class StopResult {
    NONE,
    REQUEST_ACCEPTED,
    INTERRUPT_DELIVERED,
    TARGET_NOT_FOUND,
    MULTIPLE_TARGETS,
    RUNTIME_FINALIZING,
    DISPATCH_FAILED,
};

const char* stopResultName(StopResult result) {
    switch (result) {
        case StopResult::NONE:
            return "NONE";
        case StopResult::REQUEST_ACCEPTED:
            return "REQUEST_ACCEPTED";
        case StopResult::INTERRUPT_DELIVERED:
            return "INTERRUPT_DELIVERED";
        case StopResult::TARGET_NOT_FOUND:
            return "TARGET_NOT_FOUND";
        case StopResult::MULTIPLE_TARGETS:
            return "MULTIPLE_TARGETS";
        case StopResult::RUNTIME_FINALIZING:
            return "RUNTIME_FINALIZING";
        case StopResult::DISPATCH_FAILED:
            return "DISPATCH_FAILED";
    }
    return "NONE";
}

std::int64_t nowEpochMillis() {
    return std::chrono::duration_cast<std::chrono::milliseconds>(
               std::chrono::system_clock::now().time_since_epoch())
        .count();
}

std::string boundedOutput(std::string value) {
    if (value.size() <= kOutputLimit) {
        return value;
    }
    value.resize(kOutputLimit);
    value.append("\n[SiftAlpha X output truncated]\n");
    return value;
}

std::string jsonEscape(const std::string& value) {
    std::string escaped;
    escaped.reserve(value.size() + 2);
    for (unsigned char character : value) {
        switch (character) {
            case '\\':
                escaped.append("\\\\");
                break;
            case '"':
                escaped.append("\\\"");
                break;
            case '\b':
                escaped.append("\\b");
                break;
            case '\f':
                escaped.append("\\f");
                break;
            case '\n':
                escaped.append("\\n");
                break;
            case '\r':
                escaped.append("\\r");
                break;
            case '\t':
                escaped.append("\\t");
                break;
            default:
                if (character < 0x20) {
                    escaped.append("?");
                } else {
                    escaped.push_back(static_cast<char>(character));
                }
                break;
        }
    }
    return escaped;
}

std::string jsonString(const std::string& value) {
    return "\"" + jsonEscape(value) + "\"";
}

std::string jsonLong(std::int64_t value) {
    return std::to_string(value);
}

struct SessionSnapshot {
    std::string sessionId;
    std::string projectIdentity;
    std::string executionRoot;
    std::string entrypoint;
    std::string workingDirectory;
    std::int64_t generation = 0;
    std::int64_t startedAtEpochMs = 0;
    std::int64_t finishedAtEpochMs = 0;
    SessionState state = SessionState::IDLE;
    RuntimePhase runtimePhase = RuntimePhase::IDLE;
    StopPhase stopPhase = StopPhase::IDLE;
    StopResult stopResult = StopResult::NONE;
    int exitCode = 0;
    bool hasExitCode = false;
    std::string stdoutText;
    std::string stderrText;
};

struct Session {
    std::mutex mutex;
    std::string sessionId;
    std::string home;
    std::string projectIdentity;
    std::string executionRoot;
    std::string entrypoint;
    std::string workingDirectory;
    std::int64_t generation = 0;
    std::int64_t startedAtEpochMs = 0;
    std::int64_t finishedAtEpochMs = 0;
    SessionState state = SessionState::IDLE;
    RuntimePhase runtimePhase = RuntimePhase::IDLE;
    StopPhase stopPhase = StopPhase::IDLE;
    StopResult stopResult = StopResult::NONE;
    int exitCode = 0;
    bool hasExitCode = false;
    std::string stdoutText;
    std::string stderrText;
    std::atomic<bool> stopRequested{false};
    std::atomic<bool> stopDelivered{false};
    std::atomic<bool> pythonReady{false};
    std::atomic<unsigned long> pythonThreadId{0};
};

std::mutex gSessionMutex;
std::shared_ptr<Session> gSession;
SessionSnapshot gLastTerminalSnapshot;

// CPython is initialized once for the process. Py_InitializeFromConfig() creates
// the main thread state on the calling worker and returns with it attached. The
// bootstrap state is detached immediately with PyEval_SaveThread() and retained
// for the process lifetime; it is never reused for an execution session and
// there is deliberately no Py_FinalizeEx() in this Android process.
std::once_flag gPythonRuntimeOnce;
std::mutex gPythonRuntimeMutex;
bool gPythonRuntimeReady = false;
std::string gPythonRuntimeError;
PyThreadState* gPythonBootstrapThreadState = nullptr;

void setRuntimePhase(Session* session, RuntimePhase phase) {
    {
        std::lock_guard<std::mutex> lock(session->mutex);
        session->runtimePhase = phase;
    }
    __android_log_print(
        ANDROID_LOG_INFO,
        kLogTag,
        "SIFTALPHA_X_RUNTIME_PHASE=%s SIFTALPHA_X_SESSION_ID=%s "
        "SIFTALPHA_X_GENERATION=%lld",
        runtimePhaseName(phase),
        session->sessionId.c_str(),
        static_cast<long long>(session->generation));
}

void setStopPhase(Session* session, StopPhase phase) {
    StopResult result;
    {
        std::lock_guard<std::mutex> lock(session->mutex);
        session->stopPhase = phase;
        result = session->stopResult;
    }
    __android_log_print(
        ANDROID_LOG_INFO,
        kLogTag,
        "SIFTALPHA_X_STOP_PHASE=%s SIFTALPHA_X_STOP_RESULT=%s "
        "SIFTALPHA_X_SESSION_ID=%s SIFTALPHA_X_GENERATION=%lld",
        stopPhaseName(phase),
        stopResultName(result),
        session->sessionId.c_str(),
        static_cast<long long>(session->generation));
}

void setStopResult(Session* session, StopResult result) {
    StopPhase phase;
    {
        std::lock_guard<std::mutex> lock(session->mutex);
        session->stopResult = result;
        phase = session->stopPhase;
    }
    __android_log_print(
        ANDROID_LOG_INFO,
        kLogTag,
        "SIFTALPHA_X_STOP_PHASE=%s SIFTALPHA_X_STOP_RESULT=%s "
        "SIFTALPHA_X_SESSION_ID=%s SIFTALPHA_X_GENERATION=%lld",
        stopPhaseName(phase),
        stopResultName(result),
        session->sessionId.c_str(),
        static_cast<long long>(session->generation));
}

void setState(Session* session, SessionState state) {
    std::lock_guard<std::mutex> lock(session->mutex);
    session->state = state;
}

void setFailure(Session* session, const std::string& message) {
    std::lock_guard<std::mutex> lock(session->mutex);
    session->state = SessionState::FAILED;
    session->hasExitCode = true;
    session->exitCode = 1;
    session->stderrText = boundedOutput(session->stderrText + message + "\n");
    session->finishedAtEpochMs = nowEpochMillis();
}

void finishSession(
    Session* session,
    SessionState state,
    int exitCode,
    const std::string& stdoutText,
    const std::string& stderrText) {
    std::lock_guard<std::mutex> lock(session->mutex);
    session->stdoutText = stdoutText;
    session->stderrText = stderrText;
    session->state = state;
    session->hasExitCode = true;
    session->exitCode = exitCode;
    session->finishedAtEpochMs = nowEpochMillis();
}

SessionSnapshot snapshotData(Session* session) {
    std::lock_guard<std::mutex> lock(session->mutex);
    SessionSnapshot snapshot;
    snapshot.sessionId = session->sessionId;
    snapshot.projectIdentity = session->projectIdentity;
    snapshot.executionRoot = session->executionRoot;
    snapshot.entrypoint = session->entrypoint;
    snapshot.workingDirectory = session->workingDirectory;
    snapshot.generation = session->generation;
    snapshot.startedAtEpochMs = session->startedAtEpochMs;
    snapshot.finishedAtEpochMs = session->finishedAtEpochMs;
    snapshot.state = session->state;
    snapshot.runtimePhase = session->runtimePhase;
    snapshot.stopPhase = session->stopPhase;
    snapshot.stopResult = session->stopResult;
    snapshot.exitCode = session->exitCode;
    snapshot.hasExitCode = session->hasExitCode;
    snapshot.stdoutText = session->stdoutText;
    snapshot.stderrText = session->stderrText;
    return snapshot;
}

void publishTerminalSession(const std::shared_ptr<Session>& session) {
    std::lock_guard<std::mutex> lock(gSessionMutex);
    const SessionSnapshot terminal = snapshotData(session.get());
    if (terminal.generation >= gLastTerminalSnapshot.generation) {
        gLastTerminalSnapshot = terminal;
    }
    if (gSession.get() == session.get()) {
        gSession.reset();
    }
}

void refreshLastTerminalSnapshot(const std::shared_ptr<Session>& session) {
    std::lock_guard<std::mutex> lock(gSessionMutex);
    const SessionSnapshot latest = snapshotData(session.get());
    if (latest.state == SessionState::SUCCEEDED ||
        latest.state == SessionState::FAILED ||
        latest.state == SessionState::STOPPED) {
        if (latest.generation >= gLastTerminalSnapshot.generation) {
            gLastTerminalSnapshot = latest;
        }
    }
}

void logSessionResult(Session* session) {
    SessionState finalState;
    RuntimePhase runtimePhase;
    StopPhase stopPhase;
    StopResult stopResult;
    std::string sessionId;
    {
        std::lock_guard<std::mutex> lock(session->mutex);
        finalState = session->state;
        runtimePhase = session->runtimePhase;
        stopPhase = session->stopPhase;
        stopResult = session->stopResult;
        sessionId = session->sessionId;
    }
    __android_log_print(ANDROID_LOG_INFO, kLogTag,
                        "SIFTALPHA_X_STDOUT_BEGIN SIFTALPHA_X_SESSION_ID=%s",
                        sessionId.c_str());
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "SIFTALPHA_X_STDOUT_END");
    __android_log_print(ANDROID_LOG_INFO, kLogTag,
                        "SIFTALPHA_X_STDERR_BEGIN SIFTALPHA_X_SESSION_ID=%s",
                        sessionId.c_str());
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "SIFTALPHA_X_STDERR_END");
    __android_log_print(ANDROID_LOG_INFO, kLogTag,
                        "SIFTALPHA_X_STATE=%s SIFTALPHA_X_RESULT=%s "
                        "SIFTALPHA_X_RUNTIME_PHASE=%s "
                        "SIFTALPHA_X_STOP_PHASE=%s SIFTALPHA_X_STOP_RESULT=%s "
                        "SIFTALPHA_X_SESSION_ID=%s",
                        stateName(finalState),
                        resultName(finalState),
                        runtimePhaseName(runtimePhase),
                        stopPhaseName(stopPhase),
                        stopResultName(stopResult),
                        sessionId.c_str());
}

std::string jstringToUtf8(JNIEnv* env, jstring value) {
    if (value == nullptr) {
        return {};
    }
    const char* chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) {
        return {};
    }
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

void appendPythonTraceback() {
    if (PyErr_Occurred() != nullptr) {
        // sys.stderr is a StringIO installed by the worker before execution.
        PyErr_PrintEx(0);
    }
}

std::string stringIoValue(PyObject* capture) {
    if (capture == nullptr) {
        return {};
    }
    PyObject* value = PyObject_CallMethod(capture, "getvalue", nullptr);
    if (value == nullptr) {
        appendPythonTraceback();
        return {};
    }
    const char* utf8 = PyUnicode_Check(value) ? PyUnicode_AsUTF8(value) : nullptr;
    std::string result = utf8 == nullptr ? std::string() : std::string(utf8);
    Py_DECREF(value);
    return boundedOutput(result);
}

bool installCapture(
    PyObject* sysModule,
    PyObject* ioModule,
    PyObject** originalStdout,
    PyObject** originalStderr,
    PyObject** capturedStdout,
    PyObject** capturedStderr) {
    *originalStdout = PyObject_GetAttrString(sysModule, "stdout");
    *originalStderr = PyObject_GetAttrString(sysModule, "stderr");
    *capturedStdout = PyObject_CallMethod(ioModule, "StringIO", nullptr);
    *capturedStderr = PyObject_CallMethod(ioModule, "StringIO", nullptr);
    if (*originalStdout == nullptr || *originalStderr == nullptr ||
        *capturedStdout == nullptr || *capturedStderr == nullptr) {
        appendPythonTraceback();
        return false;
    }
    if (PyObject_SetAttrString(sysModule, "stdout", *capturedStdout) != 0 ||
        PyObject_SetAttrString(sysModule, "stderr", *capturedStderr) != 0) {
        appendPythonTraceback();
        return false;
    }
    return true;
}

void restoreCapture(
    PyObject* sysModule,
    PyObject* originalStdout,
    PyObject* originalStderr,
    PyObject* capturedStdout,
    PyObject* capturedStderr) {
    if (sysModule != nullptr && originalStdout != nullptr) {
        PyObject_SetAttrString(sysModule, "stdout", originalStdout);
    }
    if (sysModule != nullptr && originalStderr != nullptr) {
        PyObject_SetAttrString(sysModule, "stderr", originalStderr);
    }
    Py_XDECREF(originalStdout);
    Py_XDECREF(originalStderr);
    Py_XDECREF(capturedStdout);
    Py_XDECREF(capturedStderr);
}

class WorkingDirectoryGuard {
public:
    explicit WorkingDirectoryGuard(const std::string& directory) {
        char current[PATH_MAX];
        if (getcwd(current, sizeof(current)) != nullptr) {
            previous_ = current;
        }
        changed_ = chdir(directory.c_str()) == 0;
    }

    ~WorkingDirectoryGuard() {
        restore();
    }

    void restore() {
        if (changed_ && !previous_.empty()) {
            chdir(previous_.c_str());
        }
        changed_ = false;
    }

    bool changed() const {
        return changed_;
    }

private:
    std::string previous_;
    bool changed_ = false;
};


bool ensurePythonRuntime(const std::string& home, std::string* failure) {
    std::call_once(gPythonRuntimeOnce, [&]() {
        if (Py_IsInitialized()) {
            std::lock_guard<std::mutex> lock(gPythonRuntimeMutex);
            gPythonRuntimeReady = true;
            return;
        }

        PyThreadState* bootstrapState = nullptr;
        std::string initializationError;
        try {
            // Keep the initialization-created main thread state off every execution
            // worker. The temporary bootstrap thread exits only after SaveThread has
            // detached the state and released the GIL.
            std::thread bootstrapThread([&]() {
                PyConfig config;
                PyConfig_InitPythonConfig(&config);
                config.use_environment = 0;
                config.user_site_directory = 0;
                config.install_signal_handlers = 0;
                config.parse_argv = 0;

                char program[] = "siftalpha-x";
                char* argv[] = {program, nullptr};
                PyStatus status = PyConfig_SetBytesArgv(&config, 1, argv);
                if (PyStatus_Exception(status)) {
                    initializationError =
                        status.err_msg == nullptr ? "CPython argv setup failed" : status.err_msg;
                    PyConfig_Clear(&config);
                    return;
                }

                status = PyConfig_SetBytesString(&config, &config.home, home.c_str());
                if (PyStatus_Exception(status)) {
                    initializationError =
                        status.err_msg == nullptr ? "CPython home setup failed" : status.err_msg;
                    PyConfig_Clear(&config);
                    return;
                }

                status = Py_InitializeFromConfig(&config);
                PyConfig_Clear(&config);
                if (PyStatus_Exception(status)) {
                    initializationError =
                        status.err_msg == nullptr ? "CPython initialization failed" : status.err_msg;
                    return;
                }

                // Py_InitializeFromConfig() created and attached the main thread
                // state to this temporary bootstrap thread. Release the GIL and
                // detach it before that thread exits.
                bootstrapState = PyEval_SaveThread();
                if (bootstrapState == nullptr) {
                    initializationError = "CPython bootstrap thread state was not created";
                }
            });
            bootstrapThread.join();
        } catch (const std::exception& exception) {
            initializationError = std::string("CPython bootstrap thread failed: ") + exception.what();
        }

        std::lock_guard<std::mutex> lock(gPythonRuntimeMutex);
        if (bootstrapState != nullptr && initializationError.empty()) {
            // This detached main thread state is retained for the process lifetime.
            // It is the state required by a future Py_FinalizeEx(), which this
            // Android process deliberately never performs.
            gPythonBootstrapThreadState = bootstrapState;
            gPythonRuntimeReady = true;
        } else {
            gPythonRuntimeError = initializationError.empty()
                ? "SIFTALPHA_X_CPYTHON_RUNTIME_INIT_FAILED"
                : initializationError;
        }
    });

    std::lock_guard<std::mutex> lock(gPythonRuntimeMutex);
    if (gPythonRuntimeReady) {
        return true;
    }
    if (failure != nullptr) {
        *failure = gPythonRuntimeError.empty()
            ? "SIFTALPHA_X_CPYTHON_RUNTIME_INIT_FAILED"
            : gPythonRuntimeError;
    }
    return false;
}
std::string parentDirectory(const std::string& path) {
    const std::size_t separator = path.find_last_of('/');
    if (separator == std::string::npos) {
        return {};
    }
    if (separator == 0) {
        return "/";
    }
    return path.substr(0, separator);
}

bool pathWithin(const std::string& root, const std::string& candidate) {
    if (root == candidate) {
        return true;
    }
    return candidate.size() > root.size() &&
        candidate.compare(0, root.size(), root) == 0 &&
        candidate[root.size()] == '/';
}

bool canonicalPath(const std::string& path, std::string* result);

bool validatePathComponents(
    const std::string& path,
    const std::string& symlinkProtectedRoot,
    bool allowMissingLeaf,
    bool* missingLeaf,
    std::string* failure) {
    if (missingLeaf != nullptr) {
        *missingLeaf = false;
    }
    if (path.empty() || path.front() != '/') {
        if (failure != nullptr) {
            *failure = "absolute path required";
        }
        return false;
    }

    std::size_t position = 1;
    std::string current = "/";
    while (position < path.size()) {
        while (position < path.size() && path[position] == '/') {
            ++position;
        }
        if (position >= path.size()) {
            break;
        }
        const std::size_t end = path.find('/', position);
        const std::size_t componentEnd =
            end == std::string::npos ? path.size() : end;
        const std::string component = path.substr(position, componentEnd - position);
        if (component.empty() || component == ".") {
            position = componentEnd;
            continue;
        }
        if (component == "..") {
            if (failure != nullptr) {
                *failure = "path traversal is not allowed";
            }
            return false;
        }
        if (current.size() > 1) {
            current.append("/");
        }
        current.append(component);

        struct stat info {};
        if (lstat(current.c_str(), &info) != 0) {
            if (errno == ENOENT && allowMissingLeaf && current == path) {
                if (missingLeaf != nullptr) {
                    *missingLeaf = true;
                }
                return true;
            }
            if (failure != nullptr) {
                *failure = "missing or inaccessible path component: " + current;
            }
            return false;
        }
        // Android may expose app-private paths through framework-managed symlink
        // ancestors such as /data/data. Only the project/staging subtree is
        // controlled by this execution boundary; canonical containment checks below
        // still apply to every final target. The protected root is canonicalized by
        // validateExecutionSpec(), so an alias spelling cannot disable this guard.
        if (S_ISLNK(info.st_mode)) {
            const std::string parent = parentDirectory(current);
            std::string canonicalParent;
            if (canonicalPath(parent, &canonicalParent) &&
                pathWithin(symlinkProtectedRoot, canonicalParent)) {
                if (failure != nullptr) {
                    *failure = "symlink path components are not supported: " + current;
                }
                return false;
            }
        }
        position = componentEnd;
    }
    return true;
}

bool canonicalPath(const std::string& path, std::string* result) {
    char resolved[PATH_MAX];
    if (realpath(path.c_str(), resolved) == nullptr) {
        return false;
    }
    if (result != nullptr) {
        *result = resolved;
    }
    return true;
}

bool validateExecutionSpec(
    const std::shared_ptr<Session>& session,
    std::string* failure) {
    setRuntimePhase(session.get(), RuntimePhase::PROJECT_SPEC_VALIDATE_BEGIN);
    const std::string root = session->executionRoot;
    const std::string entrypoint = session->entrypoint;
    const std::string workingDirectory = session->workingDirectory;
    if (root.empty() || entrypoint.empty() || workingDirectory.empty()) {
        if (failure != nullptr) {
            *failure = "SIFTALPHA_X_PROJECT_SPEC_ERROR=required field is empty";
        }
        return false;
    }

    const std::string projectsBase = parentDirectory(session->home) + "/projects";
    struct stat projectsInfo {};
    if (lstat(projectsBase.c_str(), &projectsInfo) != 0 ||
        !S_ISDIR(projectsInfo.st_mode)) {
        if (failure != nullptr) {
            *failure = "SIFTALPHA_X_PROJECT_SPEC_ERROR=staging directory is unavailable";
        }
        return false;
    }

    std::string canonicalBase;
    if (!canonicalPath(projectsBase, &canonicalBase)) {
        if (failure != nullptr) {
            *failure = "SIFTALPHA_X_PROJECT_SPEC_ERROR=staging directory cannot be resolved";
        }
        return false;
    }

    struct stat rootInfo {};
    if (lstat(root.c_str(), &rootInfo) != 0 || !S_ISDIR(rootInfo.st_mode)) {
        if (failure != nullptr) {
            *failure = "SIFTALPHA_X_PROJECT_SPEC_ERROR=execution root is not a directory";
        }
        return false;
    }

    std::string componentFailure;
    bool ignoredMissing = false;
    // Android may spell the same app-private directory as /data/data or /data/user/0.
    // The canonical containment check is the security boundary; raw spelling is not.
    if (!validatePathComponents(
            root,
            canonicalBase,
            false,
            &ignoredMissing,
            &componentFailure)) {
        if (failure != nullptr) {
            *failure = "SIFTALPHA_X_PROJECT_SPEC_ERROR=" + componentFailure;
        }
        return false;
    }

    std::string canonicalRoot;
    if (!canonicalPath(root, &canonicalRoot) ||
        !pathWithin(canonicalBase, canonicalRoot)) {
        if (failure != nullptr) {
            *failure = "SIFTALPHA_X_PROJECT_SPEC_ERROR=execution root is outside app-private staging";
        }
        return false;
    }

    if (entrypoint.front() != '/') {
        if (failure != nullptr) {
            *failure = "SIFTALPHA_X_PROJECT_SPEC_ERROR=entrypoint is outside execution root";
        }
        return false;
    }
    bool missingEntrypoint = false;
    componentFailure.clear();
    if (!validatePathComponents(
            entrypoint,
            canonicalBase,
            true,
            &missingEntrypoint,
            &componentFailure)) {
        if (failure != nullptr) {
            *failure = "SIFTALPHA_X_PROJECT_SPEC_ERROR=" + componentFailure;
        }
        return false;
    }
    if (missingEntrypoint) {
        if (failure != nullptr) {
            *failure = "SIFTALPHA_X_ENTRYPOINT_ERROR=missing entrypoint";
        }
        return false;
    }
    struct stat entrypointInfo {};
    if (lstat(entrypoint.c_str(), &entrypointInfo) != 0 ||
        !S_ISREG(entrypointInfo.st_mode)) {
        if (failure != nullptr) {
            *failure = "SIFTALPHA_X_ENTRYPOINT_ERROR=entrypoint is not a regular file";
        }
        return false;
    }
    std::string canonicalEntrypoint;
    if (!canonicalPath(entrypoint, &canonicalEntrypoint) ||
        !pathWithin(canonicalRoot, canonicalEntrypoint)) {
        if (failure != nullptr) {
            *failure = "SIFTALPHA_X_ENTRYPOINT_ERROR=entrypoint escapes execution root";
        }
        return false;
    }

    if (workingDirectory.front() != '/') {
        if (failure != nullptr) {
            *failure = "SIFTALPHA_X_PROJECT_SPEC_ERROR=working directory is outside execution root";
        }
        return false;
    }
    componentFailure.clear();
    if (!validatePathComponents(
            workingDirectory,
            canonicalBase,
            false,
            &ignoredMissing,
            &componentFailure)) {
        if (failure != nullptr) {
            *failure = "SIFTALPHA_X_PROJECT_SPEC_ERROR=" + componentFailure;
        }
        return false;
    }
    struct stat workingDirectoryInfo {};
    if (lstat(workingDirectory.c_str(), &workingDirectoryInfo) != 0 ||
        !S_ISDIR(workingDirectoryInfo.st_mode)) {
        if (failure != nullptr) {
            *failure = "SIFTALPHA_X_PROJECT_SPEC_ERROR=working directory is not a directory";
        }
        return false;
    }
    std::string canonicalWorkingDirectory;
    if (!canonicalPath(workingDirectory, &canonicalWorkingDirectory) ||
        !pathWithin(canonicalRoot, canonicalWorkingDirectory)) {
        if (failure != nullptr) {
            *failure = "SIFTALPHA_X_PROJECT_SPEC_ERROR=working directory escapes execution root";
        }
        return false;
    }

    setRuntimePhase(session.get(), RuntimePhase::PROJECT_SPEC_VALIDATED);
    return true;
}

bool readProjectSource(
    const std::string& path,
    std::string* source,
    std::string* failure) {
    std::ifstream input(path, std::ios::binary);
    if (!input.is_open()) {
        if (failure != nullptr) {
            *failure = "SIFTALPHA_X_ENTRYPOINT_ERROR=unable to open entrypoint";
        }
        return false;
    }
    input.seekg(0, std::ios::end);
    const std::streamoff size = input.tellg();
    if (size < 0 || static_cast<std::uint64_t>(size) > kSourceLimit) {
        if (failure != nullptr) {
            *failure = "SIFTALPHA_X_ENTRYPOINT_ERROR=entrypoint exceeds source limit";
        }
        return false;
    }
    input.seekg(0, std::ios::beg);
    source->assign(
        std::istreambuf_iterator<char>(input),
        std::istreambuf_iterator<char>());
    if (input.bad()) {
        if (failure != nullptr) {
            *failure = "SIFTALPHA_X_ENTRYPOINT_ERROR=unable to read entrypoint";
        }
        return false;
    }
    if (source->find('\0') != std::string::npos) {
        if (failure != nullptr) {
            *failure = "SIFTALPHA_X_ENTRYPOINT_ERROR=NUL byte is not valid Python source";
        }
        return false;
    }
    return true;
}

class ScopedEnvironmentVariable {
public:
    ScopedEnvironmentVariable(const char* name, const std::string& value)
        : name_(name) {
        const char* previous = getenv(name);
        if (previous != nullptr) {
            hadPrevious_ = true;
            previous_ = previous;
        }
        setenv(name, value.c_str(), 1);
    }

    ~ScopedEnvironmentVariable() {
        if (hadPrevious_) {
            setenv(name_.c_str(), previous_.c_str(), 1);
        } else {
            unsetenv(name_.c_str());
        }
    }

private:
    std::string name_;
    std::string previous_;
    bool hadPrevious_ = false;
};

bool dictSetUnicode(PyObject* dictionary, const char* key, const std::string& value) {
    PyObject* object = PyUnicode_FromString(value.c_str());
    if (object == nullptr) {
        return false;
    }
    const int result = PyDict_SetItemString(dictionary, key, object);
    Py_DECREF(object);
    return result == 0;
}

bool dictSetNone(PyObject* dictionary, const char* key) {
    return PyDict_SetItemString(dictionary, key, Py_None) == 0;
}

bool installExecutionMain(
    PyObject* sysModule,
    PyObject** originalMain,
    PyObject** executionMain,
    PyObject** globals) {
    PyObject* modules = PyObject_GetAttrString(sysModule, "modules");
    if (modules == nullptr || !PyDict_Check(modules)) {
        Py_XDECREF(modules);
        return false;
    }

    *originalMain = PyDict_GetItemString(modules, "__main__");
    Py_XINCREF(*originalMain);
    *executionMain = PyModule_New("__main__");
    if (*executionMain == nullptr) {
        Py_DECREF(modules);
        return false;
    }
    if (PyDict_SetItemString(modules, "__main__", *executionMain) != 0) {
        Py_DECREF(modules);
        Py_DECREF(*executionMain);
        *executionMain = nullptr;
        Py_XDECREF(*originalMain);
        *originalMain = nullptr;
        return false;
    }

    *globals = PyModule_GetDict(*executionMain);
    const bool initialized =
        *globals != nullptr &&
        dictSetUnicode(*globals, "__name__", "__main__") &&
        dictSetNone(*globals, "__package__") &&
        dictSetNone(*globals, "__loader__") &&
        dictSetNone(*globals, "__spec__") &&
        dictSetNone(*globals, "__cached__") &&
        PyDict_SetItemString(*globals, "__builtins__", PyEval_GetBuiltins()) == 0;
    Py_DECREF(modules);
    return initialized;
}

bool initializeExecutionGlobals(
    PyObject* globals,
    const std::string& entrypoint) {
    return globals != nullptr &&
        dictSetUnicode(globals, "__file__", entrypoint);
}

bool configureExecutionSys(
    PyObject* sysModule,
    PyObject* originalPath,
    PyObject* originalArgv,
    const std::string& executionRoot,
    const std::string& entrypoint) {
    PyObject* newPath = PySequence_List(originalPath);
    if (newPath == nullptr) {
        return false;
    }
    const std::string entrypointDirectory = parentDirectory(entrypoint);
    PyObject* entrypointDirectoryObject =
        PyUnicode_FromString(entrypointDirectory.c_str());
    PyObject* executionRootObject =
        PyUnicode_FromString(executionRoot.c_str());
    bool success =
        entrypointDirectoryObject != nullptr &&
        executionRootObject != nullptr &&
        PyList_Insert(newPath, 0, executionRootObject) == 0 &&
        PyList_Insert(newPath, 0, entrypointDirectoryObject) == 0;
    Py_XDECREF(entrypointDirectoryObject);
    Py_XDECREF(executionRootObject);
    if (success) {
        success = PyObject_SetAttrString(sysModule, "path", newPath) == 0;
    }
    Py_DECREF(newPath);
    if (!success) {
        return false;
    }

    PyObject* newArgv = PyList_New(1);
    if (newArgv == nullptr) {
        return false;
    }
    PyObject* entrypointObject = PyUnicode_FromString(entrypoint.c_str());
    if (entrypointObject == nullptr) {
        Py_DECREF(newArgv);
        return false;
    }
    PyList_SET_ITEM(newArgv, 0, entrypointObject);
    success = PyObject_SetAttrString(sysModule, "argv", newArgv) == 0;
    Py_DECREF(newArgv);
    return success;
}

void restoreExecutionMain(
    PyObject* sysModule,
    PyObject* originalMain) {
    PyObject* modules = PyObject_GetAttrString(sysModule, "modules");
    if (modules != nullptr && PyDict_Check(modules)) {
        if (originalMain != nullptr) {
            PyDict_SetItemString(modules, "__main__", originalMain);
        } else {
            PyDict_DelItemString(modules, "__main__");
        }
    }
    Py_XDECREF(modules);
    if (PyErr_Occurred()) {
        PyErr_Clear();
    }
}

void clearProjectModules(PyObject* modules, const std::string& projectsBase) {
    if (modules == nullptr || !PyDict_Check(modules)) {
        return;
    }
    PyObject* keys = PyDict_Keys(modules);
    if (keys == nullptr) {
        PyErr_Clear();
        return;
    }
    const Py_ssize_t keyCount = PyList_Size(keys);
    for (Py_ssize_t index = 0; index < keyCount; ++index) {
        PyObject* key = PyList_GetItem(keys, index);
        PyObject* module = PyDict_GetItem(modules, key);
        if (module == nullptr) {
            continue;
        }
        PyObject* moduleFile = PyObject_GetAttrString(module, "__file__");
        if (moduleFile != nullptr && PyUnicode_Check(moduleFile)) {
            const char* fileName = PyUnicode_AsUTF8(moduleFile);
            if (fileName != nullptr && pathWithin(projectsBase, fileName)) {
                PyDict_DelItem(modules, key);
                PyErr_Clear();
            }
        }
        Py_XDECREF(moduleFile);
        PyErr_Clear();
    }
    Py_DECREF(keys);
}

bool consumeSystemExit(
    PyObject* stderrCapture,
    int* exitCode) {
    if (!PyErr_ExceptionMatches(PyExc_SystemExit)) {
        return false;
    }

    PyObject* exceptionType = nullptr;
    PyObject* exceptionValue = nullptr;
    PyObject* traceback = nullptr;
    PyErr_Fetch(&exceptionType, &exceptionValue, &traceback);
    PyErr_NormalizeException(&exceptionType, &exceptionValue, &traceback);

    PyObject* codeObject = exceptionValue == nullptr
        ? nullptr
        : PyObject_GetAttrString(exceptionValue, "code");
    if (codeObject == nullptr) {
        PyErr_Clear();
    }

    bool integerCode = codeObject == nullptr || codeObject == Py_None;
    long requestedCode = 0;
    if (!integerCode) {
        if (PyLong_Check(codeObject)) {
            requestedCode = PyLong_AsLong(codeObject);
            if (PyErr_Occurred()) {
                PyErr_Clear();
                integerCode = false;
            }
        } else {
            integerCode = false;
        }
    }
    if (integerCode) {
        if (requestedCode > std::numeric_limits<int>::max() ||
            requestedCode < std::numeric_limits<int>::min()) {
            *exitCode = 1;
        } else {
            *exitCode = static_cast<int>(requestedCode);
        }
    } else {
        *exitCode = 1;
        if (stderrCapture != nullptr) {
            PyFile_WriteObject(
                codeObject == nullptr ? Py_None : codeObject,
                stderrCapture,
                Py_PRINT_RAW);
            PyFile_WriteString("\n", stderrCapture);
            PyErr_Clear();
        }
    }

    Py_XDECREF(codeObject);
    Py_XDECREF(exceptionType);
    Py_XDECREF(exceptionValue);
    Py_XDECREF(traceback);
    return true;
}

void runSession(const std::shared_ptr<Session>& session) {
    setRuntimePhase(session.get(), RuntimePhase::WORKER_ENTERED);
    __android_log_print(ANDROID_LOG_INFO, kLogTag,
                        "SIFTALPHA_X_ENGINE=CPYTHON SIFTALPHA_X_TERMUX=NOT_USED "
                        "SIFTALPHA_X_RUN_COMMAND=NOT_USED SIFTALPHA_X_PROOT=NOT_USED "
                        "SIFTALPHA_X_UBUNTU=NOT_USED SIFTALPHA_X_ABI=arm64-v8a "
                        "SIFTALPHA_X_PYTHON_VERSION=3.14.7 SIFTALPHA_X_PROJECT_ID=%s "
                        "SIFTALPHA_X_EXECUTION_ROOT=%s SIFTALPHA_X_ENTRYPOINT=%s "
                        "SIFTALPHA_X_WORKING_DIRECTORY=%s "
                        "SIFTALPHA_X_SESSION_ID=%s SIFTALPHA_X_GENERATION=%lld",
                        session->projectIdentity.c_str(),
                        session->executionRoot.c_str(),
                        session->entrypoint.c_str(),
                        session->workingDirectory.c_str(),
                        session->sessionId.c_str(),
                        static_cast<long long>(session->generation));

    std::string tmpDirectory = session->home + "/tmp";
    mkdir(tmpDirectory.c_str(), 0700);
    ScopedEnvironmentVariable temporaryDirectory("TMPDIR", tmpDirectory);
    auto publishPrePythonFailure = [&](const std::string& message) {
        setFailure(session.get(), message);
        setRuntimePhase(session.get(), RuntimePhase::TERMINAL);
        publishTerminalSession(session);
        logSessionResult(session.get());
        setRuntimePhase(session.get(), RuntimePhase::WORKER_EXIT);
    };

    // Validate the entire execution specification and read the explicit target before
    // changing the process cwd. A rejected project path must never be entered first.
    std::string specFailure;
    if (!validateExecutionSpec(session, &specFailure)) {
        publishPrePythonFailure(specFailure);
        return;
    }

    std::string source;
    if (!readProjectSource(session->entrypoint, &source, &specFailure)) {
        publishPrePythonFailure(specFailure);
        return;
    }

    WorkingDirectoryGuard workingDirectory(session->workingDirectory);
    auto finishBeforePython = [&](const std::string& message) {
        workingDirectory.restore();
        setFailure(session.get(), message);
        setRuntimePhase(session.get(), RuntimePhase::TERMINAL);
        publishTerminalSession(session);
        logSessionResult(session.get());
        setRuntimePhase(session.get(), RuntimePhase::WORKER_EXIT);
    };
    if (!workingDirectory.changed()) {
        finishBeforePython("SIFTALPHA_X_CWD_ERROR=unable to enter project working directory");
        return;
    }

    setRuntimePhase(session.get(), RuntimePhase::RUNTIME_INIT_BEGIN);
    std::string runtimeFailure;
    if (!ensurePythonRuntime(session->home, &runtimeFailure)) {
        finishBeforePython(runtimeFailure);
        return;
    }
    setRuntimePhase(session.get(), RuntimePhase::CPYTHON_READY);

    if (Py_IsFinalizing()) {
        finishBeforePython("SIFTALPHA_X_CPYTHON_RUNTIME_FINALIZING");
        return;
    }

    setRuntimePhase(session.get(), RuntimePhase::GIL_ACQUIRE_BEGIN);
    PyGILState_STATE pythonGilState = PyGILState_Ensure();
    setRuntimePhase(session.get(), RuntimePhase::GIL_ACQUIRED);
    session->pythonThreadId.store(
        PyThread_get_thread_ident(),
        std::memory_order_release);
    session->pythonReady.store(true, std::memory_order_release);
    setRuntimePhase(session.get(), RuntimePhase::THREAD_STATE_READY);

    PyObject* sysModule = PyImport_ImportModule("sys");
    PyObject* ioModule = PyImport_ImportModule("io");
    PyObject* originalStdout = nullptr;
    PyObject* originalStderr = nullptr;
    PyObject* capturedStdout = nullptr;
    PyObject* capturedStderr = nullptr;
    const bool captureInstalled = sysModule != nullptr &&
        ioModule != nullptr &&
        installCapture(
            sysModule,
            ioModule,
            &originalStdout,
            &originalStderr,
            &capturedStdout,
            &capturedStderr);

    SessionState terminalState = SessionState::FAILED;
    int terminalExitCode = 1;
    std::string stdoutText;
    std::string stderrText;
    std::string failureMessage;
    PyObject* sysModules = nullptr;
    PyObject* originalPath = nullptr;
    PyObject* originalArgv = nullptr;
    PyObject* originalMain = nullptr;
    PyObject* executionMain = nullptr;
    PyObject* globals = nullptr;
    bool mainInstalled = false;
    bool executionContextReady = false;

    if (!captureInstalled) {
        if (PyErr_Occurred()) {
            appendPythonTraceback();
        }
        failureMessage = "SIFTALPHA_X_CAPTURE_ERROR=unable to install stdout/stderr capture";
    } else if (session->stopRequested.load(std::memory_order_acquire)) {
        terminalState = SessionState::STOPPED;
        terminalExitCode = 130;
    } else {
        sysModules = PyObject_GetAttrString(sysModule, "modules");
        originalPath = PyObject_GetAttrString(sysModule, "path");
        originalArgv = PyObject_GetAttrString(sysModule, "argv");
        if (sysModules == nullptr || originalPath == nullptr || originalArgv == nullptr) {
            if (PyErr_Occurred()) {
                appendPythonTraceback();
            }
            failureMessage = "SIFTALPHA_X_CONTEXT_ERROR=unable to read Python execution context";
        } else {
            const std::string projectsBase = parentDirectory(session->home) + "/projects";
            clearProjectModules(sysModules, projectsBase);
            mainInstalled = installExecutionMain(
                sysModule,
                &originalMain,
                &executionMain,
                &globals);
            executionContextReady = mainInstalled &&
                configureExecutionSys(
                    sysModule,
                    originalPath,
                    originalArgv,
                    session->executionRoot,
                    session->entrypoint) &&
                initializeExecutionGlobals(globals, session->entrypoint);
            if (!executionContextReady) {
                if (PyErr_Occurred()) {
                    appendPythonTraceback();
                }
                failureMessage = "SIFTALPHA_X_CONTEXT_ERROR=unable to prepare isolated Python namespace";
            } else {
                setState(session.get(), SessionState::RUNNING);
                setRuntimePhase(session.get(), RuntimePhase::RUNNING);
                setRuntimePhase(session.get(), RuntimePhase::PYTHON_EXEC_BEGIN);
                PyObject* filename = PyUnicode_FromString(session->entrypoint.c_str());
                PyObject* code = filename == nullptr
                    ? nullptr
                    : Py_CompileStringObject(
                        source.c_str(),
                        filename,
                        Py_file_input,
                        nullptr,
                        -1);
                Py_XDECREF(filename);
                PyObject* result = code == nullptr
                    ? nullptr
                    : PyEval_EvalCode(code, globals, globals);
                Py_XDECREF(code);
                setRuntimePhase(session.get(), RuntimePhase::PYTHON_EXEC_END);

                const bool controlledStop =
                    session->stopDelivered.load(std::memory_order_acquire);
                int systemExitCode = 1;
                const bool systemExit =
                    result == nullptr &&
                    consumeSystemExit(capturedStderr, &systemExitCode);
                if (result != nullptr) {
                    terminalState = controlledStop
                        ? SessionState::STOPPED
                        : SessionState::SUCCEEDED;
                    terminalExitCode = controlledStop ? 130 : 0;
                    Py_DECREF(result);
                } else if (controlledStop) {
                    PyErr_Clear();
                    terminalState = SessionState::STOPPED;
                    terminalExitCode = 130;
                } else if (systemExit) {
                    terminalState = systemExitCode == 0
                        ? SessionState::SUCCEEDED
                        : SessionState::FAILED;
                    terminalExitCode = systemExitCode;
                } else {
                    appendPythonTraceback();
                    terminalState = SessionState::FAILED;
                    terminalExitCode = 1;
                }

                stdoutText = stringIoValue(capturedStdout);
                stderrText = stringIoValue(capturedStderr);
                if (controlledStop) {
                    stderrText = boundedOutput(
                        stderrText + "SIFTALPHA_X_STOP=COOPERATIVE\n");
                }
            }
        }
    }

    if (captureInstalled) {
        if (sysModules != nullptr) {
            clearProjectModules(
                sysModules,
                parentDirectory(session->home) + "/projects");
        }
        if (originalPath != nullptr) {
            PyObject_SetAttrString(sysModule, "path", originalPath);
        }
        if (originalArgv != nullptr) {
            PyObject_SetAttrString(sysModule, "argv", originalArgv);
        }
        if (mainInstalled) {
            restoreExecutionMain(sysModule, originalMain);
        }
        if (stdoutText.empty() && capturedStdout != nullptr) {
            stdoutText = stringIoValue(capturedStdout);
        }
        if (stderrText.empty() && capturedStderr != nullptr) {
            stderrText = stringIoValue(capturedStderr);
        }
    }
    if (!failureMessage.empty()) {
        stderrText = boundedOutput(stderrText + failureMessage + "\n");
    }
    Py_XDECREF(sysModules);
    Py_XDECREF(originalPath);
    Py_XDECREF(originalArgv);
    Py_XDECREF(originalMain);
    Py_XDECREF(executionMain);
    restoreCapture(
        sysModule,
        originalStdout,
        originalStderr,
        capturedStdout,
        capturedStderr);
    Py_XDECREF(sysModule);
    Py_XDECREF(ioModule);
    if (PyErr_Occurred()) {
        PyErr_Clear();
    }

    session->pythonReady.store(false, std::memory_order_release);
    setRuntimePhase(session.get(), RuntimePhase::GIL_RELEASE_BEGIN);
    PyGILState_Release(pythonGilState);
    setRuntimePhase(session.get(), RuntimePhase::GIL_RELEASED);
    session->pythonThreadId.store(0, std::memory_order_release);
    workingDirectory.restore();

    finishSession(
        session.get(),
        terminalState,
        terminalExitCode,
        stdoutText,
        stderrText);
    setRuntimePhase(session.get(), RuntimePhase::TERMINAL);
    publishTerminalSession(session);
    logSessionResult(session.get());
    setRuntimePhase(session.get(), RuntimePhase::WORKER_EXIT);
}

std::string snapshotJson(const SessionSnapshot& snapshot) {
    std::string json = "{";
    json += "\"sessionId\":" + jsonString(snapshot.sessionId);
    json += ",\"projectIdentity\":" + jsonString(snapshot.projectIdentity);
    json += ",\"executionRoot\":" + jsonString(snapshot.executionRoot);
    json += ",\"entrypoint\":" + jsonString(snapshot.entrypoint);
    json += ",\"workingDirectory\":" + jsonString(snapshot.workingDirectory);
    json += ",\"generation\":" + jsonLong(snapshot.generation);
    json += ",\"state\":" + jsonString(stateName(snapshot.state));
    json += ",\"runtimePhase\":" + jsonString(runtimePhaseName(snapshot.runtimePhase));
    json += ",\"stopPhase\":" + jsonString(stopPhaseName(snapshot.stopPhase));
    json += ",\"stopResult\":" + jsonString(stopResultName(snapshot.stopResult));
    json += ",\"startedAtEpochMs\":" + jsonLong(snapshot.startedAtEpochMs);
    if (snapshot.finishedAtEpochMs == 0) {
        json += ",\"finishedAtEpochMs\":null";
    } else {
        json += ",\"finishedAtEpochMs\":" + jsonLong(snapshot.finishedAtEpochMs);
    }
    if (snapshot.hasExitCode) {
        json += ",\"exitCode\":" + std::to_string(snapshot.exitCode);
    } else {
        json += ",\"exitCode\":null";
    }
    json += ",\"stdout\":" + jsonString(snapshot.stdoutText);
    json += ",\"stderr\":" + jsonString(snapshot.stderrText);
    json += "}";
    return json;
}

void deliverPythonStop(const std::shared_ptr<Session>& session) {
    setStopPhase(session.get(), StopPhase::STOP_THREAD_STATE_ATTACH_BEGIN);
    if (Py_IsFinalizing()) {
        setStopResult(session.get(), StopResult::RUNTIME_FINALIZING);
        setStopPhase(session.get(), StopPhase::STOP_REQUEST_RETURNED);
        refreshLastTerminalSnapshot(session);
        return;
    }

    const unsigned long targetThreadId =
        session->pythonThreadId.load(std::memory_order_acquire);
    if (targetThreadId == 0) {
        setStopPhase(session.get(), StopPhase::STOP_INTERRUPT_RESULT_0);
        setStopResult(session.get(), StopResult::TARGET_NOT_FOUND);
        setStopPhase(session.get(), StopPhase::STOP_REQUEST_RETURNED);
        refreshLastTerminalSnapshot(session);
        return;
    }

    // This control thread is intentionally separate from the Android caller. The
    // CPython API may wait for the GIL, but the UI thread must remain bounded.
    PyGILState_STATE gilState = PyGILState_Ensure();
    setStopPhase(session.get(), StopPhase::STOP_THREAD_STATE_ATTACHED);

    // The execution worker can finish while this control thread is acquiring the
    // GIL. Recheck the owning Session before targeting a potentially reused thread ID.
    if (!session->pythonReady.load(std::memory_order_acquire)) {
        PyGILState_Release(gilState);
        setStopPhase(session.get(), StopPhase::STOP_INTERRUPT_RESULT_0);
        setStopResult(session.get(), StopResult::TARGET_NOT_FOUND);
        setStopPhase(session.get(), StopPhase::STOP_REQUEST_RETURNED);
        refreshLastTerminalSnapshot(session);
        return;
    }

    setStopPhase(session.get(), StopPhase::STOP_INTERRUPT_BEGIN);
    const int affected = PyThreadState_SetAsyncExc(
        targetThreadId,
        PyExc_KeyboardInterrupt);
    if (affected == 0) {
        setStopPhase(session.get(), StopPhase::STOP_INTERRUPT_RESULT_0);
        setStopResult(session.get(), StopResult::TARGET_NOT_FOUND);
    } else if (affected == 1) {
        session->stopDelivered.store(true, std::memory_order_release);
        setStopPhase(session.get(), StopPhase::STOP_INTERRUPT_RESULT_1);
        setStopResult(session.get(), StopResult::INTERRUPT_DELIVERED);
    } else {
        // The target ID should identify exactly one thread state. Roll back an
        // unexpected multi-state match while this control thread still owns the GIL.
        setStopPhase(session.get(), StopPhase::STOP_INTERRUPT_RESULT_GT1);
        setStopResult(session.get(), StopResult::MULTIPLE_TARGETS);
        PyThreadState_SetAsyncExc(targetThreadId, nullptr);
    }
    PyGILState_Release(gilState);
    setStopPhase(session.get(), StopPhase::STOP_REQUEST_RETURNED);
    refreshLastTerminalSnapshot(session);
}

}  // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_com_siftalpha_studio_siftalphax_EmbeddedPythonBridge_nativeStart(
    JNIEnv* env,
    jclass,
    jstring home,
    jstring projectIdentity,
    jstring executionRoot,
    jstring entrypoint,
    jstring workingDirectory,
    jstring sessionId,
    jlong generation) {
    const std::string homePath = jstringToUtf8(env, home);
    const std::string projectId = jstringToUtf8(env, projectIdentity);
    const std::string rootPath = jstringToUtf8(env, executionRoot);
    const std::string entrypointPath = jstringToUtf8(env, entrypoint);
    const std::string workingDirectoryPath = jstringToUtf8(env, workingDirectory);
    const std::string id = jstringToUtf8(env, sessionId);
    if (homePath.empty() || projectId.empty() || rootPath.empty() ||
        entrypointPath.empty() || workingDirectoryPath.empty() ||
        id.empty() || generation <= 0) {
        return JNI_FALSE;
    }

    std::shared_ptr<Session> session;
    {
        std::lock_guard<std::mutex> lock(gSessionMutex);
        if (gSession != nullptr) {
            SessionState state;
            {
                std::lock_guard<std::mutex> sessionLock(gSession->mutex);
                state = gSession->state;
            }
            if (state != SessionState::SUCCEEDED &&
                state != SessionState::FAILED &&
                state != SessionState::STOPPED) {
                return JNI_FALSE;
            }
            const SessionSnapshot terminal = snapshotData(gSession.get());
            if (terminal.generation >= gLastTerminalSnapshot.generation) {
                gLastTerminalSnapshot = terminal;
            }
            gSession.reset();
        }
        session = std::make_shared<Session>();
        session->sessionId = id;
        session->home = homePath;
        session->projectIdentity = projectId;
        session->executionRoot = rootPath;
        session->entrypoint = entrypointPath;
        session->workingDirectory = workingDirectoryPath;
        session->generation = static_cast<std::int64_t>(generation);
        session->startedAtEpochMs = nowEpochMillis();
        session->state = SessionState::STARTING;
        gSession = session;
    }
    setRuntimePhase(session.get(), RuntimePhase::SESSION_CREATED);
    std::thread([session]() {
        runSession(session);
    }).detach();
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_siftalpha_studio_siftalphax_EmbeddedPythonBridge_nativeRequestStop(
    JNIEnv*,
    jclass) {
    std::shared_ptr<Session> session;
    {
        std::lock_guard<std::mutex> lock(gSessionMutex);
        session = gSession;
    }
    if (session == nullptr) {
        return JNI_FALSE;
    }

    {
        std::lock_guard<std::mutex> sessionLock(session->mutex);
        if (session->state != SessionState::STARTING &&
            session->state != SessionState::RUNNING) {
            return JNI_FALSE;
        }
        if (session->stopRequested.exchange(true, std::memory_order_acq_rel)) {
            return JNI_TRUE;
        }
    }

    setStopPhase(session.get(), StopPhase::STOP_REQUEST_RECEIVED);
    setStopResult(session.get(), StopResult::REQUEST_ACCEPTED);
    if (!session->pythonReady.load(std::memory_order_acquire)) {
        setStopPhase(session.get(), StopPhase::STOP_REQUEST_RETURNED);
        return JNI_TRUE;
    }
    if (session->pythonThreadId.load(std::memory_order_acquire) == 0) {
        setStopResult(session.get(), StopResult::DISPATCH_FAILED);
        setStopPhase(session.get(), StopPhase::STOP_REQUEST_RETURNED);
        refreshLastTerminalSnapshot(session);
        return JNI_FALSE;
    }

    setStopPhase(session.get(), StopPhase::STOP_TARGET_FOUND);
    try {
        std::thread([session]() {
            deliverPythonStop(session);
        }).detach();
    } catch (const std::exception&) {
        setStopResult(session.get(), StopResult::DISPATCH_FAILED);
        setStopPhase(session.get(), StopPhase::STOP_REQUEST_RETURNED);
        refreshLastTerminalSnapshot(session);
        return JNI_FALSE;
    }

    // Acceptance means the request was recorded and dispatch was scheduled. The
    // terminal STOPPED state is published only by the execution worker.
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_siftalpha_studio_siftalphax_EmbeddedPythonBridge_nativeSnapshot(
    JNIEnv* env,
    jclass) {
    std::shared_ptr<Session> session;
    SessionSnapshot terminal;
    {
        std::lock_guard<std::mutex> lock(gSessionMutex);
        session = gSession;
        if (session == nullptr) {
            terminal = gLastTerminalSnapshot;
        }
    }
    const std::string json = session == nullptr
        ? snapshotJson(terminal)
        : snapshotJson(snapshotData(session.get()));
    return env->NewStringUTF(json.c_str());
}
