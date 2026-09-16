#include <Python.h>

#include <jni.h>
#include <android/log.h>

#include <atomic>
#include <chrono>
#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <limits.h>
#include <memory>
#include <mutex>
#include <string>
#include <thread>

#include <sys/stat.h>
#include <unistd.h>

namespace {

constexpr char kLogTag[] = "SiftAlphaX";
constexpr std::size_t kOutputLimit = 32 * 1024;

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
    std::int64_t generation = 0;
    std::int64_t startedAtEpochMs = 0;
    std::int64_t finishedAtEpochMs = 0;
    SessionState state = SessionState::IDLE;
    int exitCode = 0;
    bool hasExitCode = false;
    std::string stdoutText;
    std::string stderrText;
};

struct Session {
    std::mutex mutex;
    std::string sessionId;
    std::string home;
    std::string script;
    std::int64_t generation = 0;
    std::int64_t startedAtEpochMs = 0;
    std::int64_t finishedAtEpochMs = 0;
    SessionState state = SessionState::IDLE;
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
    snapshot.generation = session->generation;
    snapshot.startedAtEpochMs = session->startedAtEpochMs;
    snapshot.finishedAtEpochMs = session->finishedAtEpochMs;
    snapshot.state = session->state;
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

void logSessionResult(Session* session) {
    SessionState finalState;
    std::string sessionId;
    {
        std::lock_guard<std::mutex> lock(session->mutex);
        finalState = session->state;
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
                        "SIFTALPHA_X_STATE=%s SIFTALPHA_X_RESULT=%s SIFTALPHA_X_SESSION_ID=%s",
                        stateName(finalState), resultName(finalState), sessionId.c_str());
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

void runSession(const std::shared_ptr<Session>& session) {
    __android_log_print(ANDROID_LOG_INFO, kLogTag,
                        "SIFTALPHA_X_ENGINE=CPYTHON SIFTALPHA_X_TERMUX=NOT_USED "
                        "SIFTALPHA_X_RUN_COMMAND=NOT_USED SIFTALPHA_X_PROOT=NOT_USED "
                        "SIFTALPHA_X_UBUNTU=NOT_USED SIFTALPHA_X_ABI=arm64-v8a "
                        "SIFTALPHA_X_PYTHON_VERSION=3.14.7 SIFTALPHA_X_SESSION_ID=%s "
                        "SIFTALPHA_X_GENERATION=%lld",
                        session->sessionId.c_str(),
                        static_cast<long long>(session->generation));

    std::string tmpDirectory = session->home + "/tmp";
    mkdir(tmpDirectory.c_str(), 0700);
    setenv("TMPDIR", tmpDirectory.c_str(), 1);
    WorkingDirectoryGuard workingDirectory(session->home);
    auto failBeforePython = [&](const std::string& message) {
        workingDirectory.restore();
        setFailure(session.get(), message);
        publishTerminalSession(session);
        logSessionResult(session.get());
    };
    if (!workingDirectory.changed()) {
        failBeforePython("SIFTALPHA_X_CWD_ERROR=unable to enter app-private runtime directory");
        return;
    }

    if (!Py_IsInitialized()) {
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
            std::string message = status.err_msg == nullptr ? "CPython argv setup failed" : status.err_msg;
            PyConfig_Clear(&config);
            failBeforePython(message);
            return;
        }
        status = PyConfig_SetBytesString(&config, &config.home, session->home.c_str());
        if (PyStatus_Exception(status)) {
            std::string message = status.err_msg == nullptr ? "CPython home setup failed" : status.err_msg;
            PyConfig_Clear(&config);
            failBeforePython(message);
            return;
        }
        status = Py_InitializeFromConfig(&config);
        PyConfig_Clear(&config);
        if (PyStatus_Exception(status)) {
            std::string message = status.err_msg == nullptr ? "CPython initialization failed" : status.err_msg;
            failBeforePython(message);
            return;
        }
    }

    PyGILState_STATE pythonGilState = PyGILState_Ensure();
    PyThreadState* threadState = PyThreadState_Get();
    session->pythonThreadId.store(
        static_cast<unsigned long>(PyThreadState_GetID(threadState)),
        std::memory_order_release);
    session->pythonReady.store(true, std::memory_order_release);

    PyObject* sysModule = PyImport_ImportModule("sys");
    PyObject* ioModule = PyImport_ImportModule("io");
    PyObject* originalStdout = nullptr;
    PyObject* originalStderr = nullptr;
    PyObject* capturedStdout = nullptr;
    PyObject* capturedStderr = nullptr;
    bool captureInstalled = sysModule != nullptr && ioModule != nullptr &&
        installCapture(sysModule, ioModule, &originalStdout, &originalStderr,
                       &capturedStdout, &capturedStderr);

    SessionState terminalState = SessionState::FAILED;
    int terminalExitCode = 1;
    std::string stdoutText;
    std::string stderrText;
    std::string failureMessage;
    if (!captureInstalled) {
        appendPythonTraceback();
        failureMessage = "SIFTALPHA_X_CAPTURE_ERROR=unable to install stdout/stderr capture";
    } else if (session->stopRequested.load(std::memory_order_acquire)) {
        terminalState = SessionState::STOPPED;
        terminalExitCode = 130;
    } else {
        setState(session.get(), SessionState::RUNNING);
        PyObject* mainModule = PyImport_AddModule("__main__");
        PyObject* mainDict = mainModule == nullptr ? nullptr : PyModule_GetDict(mainModule);
        PyObject* result = mainDict == nullptr
            ? nullptr
            : PyRun_StringFlags(session->script.c_str(), Py_file_input, mainDict, mainDict, nullptr);
        if (result != nullptr) {
            Py_DECREF(result);
        } else {
            appendPythonTraceback();
        }

        stdoutText = stringIoValue(capturedStdout);
        stderrText = stringIoValue(capturedStderr);
        if (session->stopDelivered.load(std::memory_order_acquire)) {
            terminalState = SessionState::STOPPED;
            terminalExitCode = 130;
        } else if (result != nullptr) {
            terminalState = SessionState::SUCCEEDED;
            terminalExitCode = 0;
        } else {
            terminalState = SessionState::FAILED;
            terminalExitCode = 1;
        }
    }

    restoreCapture(sysModule, originalStdout, originalStderr, capturedStdout, capturedStderr);
    Py_XDECREF(sysModule);
    Py_XDECREF(ioModule);
    session->pythonReady.store(false, std::memory_order_release);
    PyGILState_Release(pythonGilState);
    workingDirectory.restore();

    if (!failureMessage.empty()) {
        setFailure(session.get(), failureMessage);
    } else {
        finishSession(session.get(), terminalState, terminalExitCode, stdoutText, stderrText);
    }
    publishTerminalSession(session);
    logSessionResult(session.get());
}

std::string snapshotJson(const SessionSnapshot& snapshot) {
    std::string json = "{";
    json += "\"sessionId\":" + jsonString(snapshot.sessionId);
    json += ",\"generation\":" + jsonLong(snapshot.generation);
    json += ",\"state\":" + jsonString(stateName(snapshot.state));
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

int requestPythonStop(Session* session) {
    if (!session->pythonReady.load(std::memory_order_acquire)) {
        return 1;
    }
    unsigned long threadId = session->pythonThreadId.load(std::memory_order_acquire);
    if (threadId == 0) {
        return 1;
    }

    // This is cooperative: the fixed TEST C script receives KeyboardInterrupt at
    // a Python interruption point. It is not a general forced native-thread kill.
    PyGILState_STATE gilState = PyGILState_Ensure();
    int affected = PyThreadState_SetAsyncExc(threadId, PyExc_KeyboardInterrupt);
    if (affected == 1) {
        session->stopDelivered.store(true, std::memory_order_release);
    }
    PyGILState_Release(gilState);
    if (affected == 1) {
        return 0;
    }
    if (affected > 1) {
        PyGILState_STATE rollbackGilState = PyGILState_Ensure();
        PyThreadState_SetAsyncExc(threadId, nullptr);
        PyGILState_Release(rollbackGilState);
    }
    return 1;
}

}  // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_com_siftalpha_studio_siftalphax_EmbeddedPythonBridge_nativeStart(
    JNIEnv* env,
    jclass,
    jstring home,
    jstring script,
    jstring sessionId,
    jlong generation) {
    const std::string homePath = jstringToUtf8(env, home);
    const std::string scriptText = jstringToUtf8(env, script);
    const std::string id = jstringToUtf8(env, sessionId);
    if (homePath.empty() || scriptText.empty() || id.empty() || generation <= 0) {
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
        session->script = scriptText;
        session->generation = static_cast<std::int64_t>(generation);
        session->startedAtEpochMs = nowEpochMillis();
        session->state = SessionState::STARTING;
        gSession = session;
    }
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
    SessionState state;
    {
        std::lock_guard<std::mutex> sessionLock(session->mutex);
        state = session->state;
    }
    if (state != SessionState::STARTING && state != SessionState::RUNNING) {
        return JNI_FALSE;
    }
    session->stopRequested.store(true, std::memory_order_release);
    if (!session->pythonReady.load(std::memory_order_acquire)) {
        return JNI_TRUE;
    }
    return requestPythonStop(session.get()) == 0 ? JNI_TRUE : JNI_FALSE;
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
