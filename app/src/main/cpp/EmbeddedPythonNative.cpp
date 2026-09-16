#include <Python.h>

#include <jni.h>
#include <android/log.h>

#include <atomic>
#include <chrono>
#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <limits.h>
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
Session* gSession = nullptr;

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
        if (changed_ && !previous_.empty()) {
            chdir(previous_.c_str());
        }
    }

    bool changed() const {
        return changed_;
    }

private:
    std::string previous_;
    bool changed_ = false;
};

void runSession(Session* session) {
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
    if (!workingDirectory.changed()) {
        setFailure(session, "SIFTALPHA_X_CWD_ERROR=unable to enter app-private runtime directory");
        logSessionResult(session);
        return;
    }

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
        setFailure(session, message);
        logSessionResult(session);
        return;
    }
    status = PyConfig_SetBytesString(&config, &config.home, session->home.c_str());
    if (PyStatus_Exception(status)) {
        std::string message = status.err_msg == nullptr ? "CPython home setup failed" : status.err_msg;
        PyConfig_Clear(&config);
        setFailure(session, message);
        logSessionResult(session);
        return;
    }
    status = Py_InitializeFromConfig(&config);
    PyConfig_Clear(&config);
    if (PyStatus_Exception(status)) {
        std::string message = status.err_msg == nullptr ? "CPython initialization failed" : status.err_msg;
        setFailure(session, message);
        logSessionResult(session);
        return;
    }

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

    if (!captureInstalled) {
        appendPythonTraceback();
        setFailure(session, "SIFTALPHA_X_CAPTURE_ERROR=unable to install stdout/stderr capture");
    } else if (session->stopRequested.load(std::memory_order_acquire)) {
        std::lock_guard<std::mutex> lock(session->mutex);
        session->state = SessionState::STOPPED;
        session->hasExitCode = true;
        session->exitCode = 130;
        session->finishedAtEpochMs = nowEpochMillis();
    } else {
        setState(session, SessionState::RUNNING);
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

        std::string stdoutText = stringIoValue(capturedStdout);
        std::string stderrText = stringIoValue(capturedStderr);
        {
            std::lock_guard<std::mutex> lock(session->mutex);
            session->stdoutText = stdoutText;
            session->stderrText = stderrText;
            session->hasExitCode = true;
            if (session->stopDelivered.load(std::memory_order_acquire)) {
                session->state = SessionState::STOPPED;
                session->exitCode = 130;
            } else if (result != nullptr) {
                session->state = SessionState::SUCCEEDED;
                session->exitCode = 0;
            } else {
                session->state = SessionState::FAILED;
                session->exitCode = 1;
            }
            session->finishedAtEpochMs = nowEpochMillis();
        }
    }

    restoreCapture(sysModule, originalStdout, originalStderr, capturedStdout, capturedStderr);
    Py_XDECREF(sysModule);
    Py_XDECREF(ioModule);
    session->pythonReady.store(false, std::memory_order_release);
    logSessionResult(session);
}

std::string snapshotJson(Session* session) {
    std::lock_guard<std::mutex> lock(session->mutex);
    std::string json = "{";
    json += "\"sessionId\":" + jsonString(session->sessionId);
    json += ",\"generation\":" + jsonLong(session->generation);
    json += ",\"state\":" + jsonString(stateName(session->state));
    json += ",\"startedAtEpochMs\":" + jsonLong(session->startedAtEpochMs);
    if (session->finishedAtEpochMs == 0) {
        json += ",\"finishedAtEpochMs\":null";
    } else {
        json += ",\"finishedAtEpochMs\":" + jsonLong(session->finishedAtEpochMs);
    }
    if (session->hasExitCode) {
        json += ",\"exitCode\":" + std::to_string(session->exitCode);
    } else {
        json += ",\"exitCode\":null";
    }
    json += ",\"stdout\":" + jsonString(session->stdoutText);
    json += ",\"stderr\":" + jsonString(session->stderrText);
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

    std::lock_guard<std::mutex> lock(gSessionMutex);
    if (gSession != nullptr) {
        return JNI_FALSE;
    }
    Session* session = new Session();
    session->sessionId = id;
    session->home = homePath;
    session->script = scriptText;
    session->generation = static_cast<std::int64_t>(generation);
    session->startedAtEpochMs = nowEpochMillis();
    session->state = SessionState::STARTING;
    gSession = session;
    std::thread(runSession, session).detach();
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_siftalpha_studio_siftalphax_EmbeddedPythonBridge_nativeRequestStop(
    JNIEnv*,
    jclass) {
    std::lock_guard<std::mutex> lock(gSessionMutex);
    if (gSession == nullptr) {
        return JNI_FALSE;
    }
    Session* session = gSession;
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
    return requestPythonStop(session) == 0 ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_siftalpha_studio_siftalphax_EmbeddedPythonBridge_nativeSnapshot(
    JNIEnv* env,
    jclass) {
    std::lock_guard<std::mutex> lock(gSessionMutex);
    if (gSession == nullptr) {
        return env->NewStringUTF(
            "{\"sessionId\":\"\",\"generation\":0,\"state\":\"IDLE\","
            "\"startedAtEpochMs\":null,\"finishedAtEpochMs\":null,\"exitCode\":null,"
            "\"stdout\":\"\",\"stderr\":\"\"}");
    }
    const std::string json = snapshotJson(gSession);
    return env->NewStringUTF(json.c_str());
}
