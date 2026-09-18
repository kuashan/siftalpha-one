package com.siftalpha.studio.siftalphax;

interface IEmbeddedPythonWorker {
    const int BINDING_STATE_UNBOUND = 0;
    const int BINDING_STATE_BOUND = 1;

    const int BOUND_NEW = 0;
    const int BOUND_SAME = 1;
    const int REJECTED_INVALID_REQUEST = 2;
    const int REJECTED_BINDING_CONFLICT = 3;
    const int REJECTED_WORKER_TERMINATING = 4;

    const int WORKER_LIFECYCLE_ACTIVE = 0;
    const int WORKER_LIFECYCLE_TERMINATING = 1;

    const int EXIT_ACCEPTED = 0;
    const int EXIT_ALREADY_REQUESTED = 1;
    const int EXIT_REJECTED_INVALID_REQUEST = 2;
    const int EXIT_REJECTED_IDENTITY_MISMATCH = 3;
    const int EXIT_REJECTED_WRONG_PROCESS = 4;

    const int CPYTHON_SMOKE_NOT_STARTED = 0;
    const int CPYTHON_SMOKE_PREPARING = 1;
    const int CPYTHON_SMOKE_STARTING = 2;
    const int CPYTHON_SMOKE_RUNNING = 3;
    const int CPYTHON_SMOKE_SUCCEEDED = 4;
    const int CPYTHON_SMOKE_FAILED = 5;

    const int CPYTHON_SMOKE_START_ACCEPTED = 0;
    const int CPYTHON_SMOKE_START_ALREADY_STARTED = 1;
    const int CPYTHON_SMOKE_START_REJECTED_INVALID_REQUEST = 2;
    const int CPYTHON_SMOKE_START_REJECTED_IDENTITY_MISMATCH = 3;
    const int CPYTHON_SMOKE_START_REJECTED_NOT_BOUND = 4;
    const int CPYTHON_SMOKE_START_REJECTED_WORKER_TERMINATING = 5;
    const int CPYTHON_SMOKE_START_REJECTED_WRONG_PROCESS = 6;
    const int CPYTHON_SMOKE_START_REJECTED_RUNTIME_BUSY = 7;

    const int PROJECT_EXECUTION_NOT_STARTED = 0;
    const int PROJECT_EXECUTION_PREPARING = 1;
    const int PROJECT_EXECUTION_STARTING = 2;
    const int PROJECT_EXECUTION_RUNNING = 3;
    const int PROJECT_EXECUTION_SUCCEEDED = 4;
    const int PROJECT_EXECUTION_FAILED = 5;
    const int PROJECT_EXECUTION_STOPPED = 6;
    const int PROJECT_EXECUTION_INTERNAL_ERROR = 7;

    const int PROJECT_EXECUTION_START_ACCEPTED = 0;
    const int PROJECT_EXECUTION_START_REJECTED_INVALID_REQUEST = 1;
    const int PROJECT_EXECUTION_START_REJECTED_IDENTITY_MISMATCH = 2;
    const int PROJECT_EXECUTION_START_REJECTED_NOT_BOUND = 3;
    const int PROJECT_EXECUTION_START_REJECTED_WORKER_TERMINATING = 4;
    const int PROJECT_EXECUTION_START_REJECTED_WRONG_PROCESS = 5;
    const int PROJECT_EXECUTION_START_REJECTED_BUSY = 6;
    const int PROJECT_EXECUTION_START_REJECTED_RUNTIME_MODE_CONFLICT = 7;
    const int PROJECT_EXECUTION_START_REJECTED_UNSUPPORTED_DEPENDENCY_LAYER = 8;

    String getWorkerInstanceId();

    int getWorkerPid();

    int getBindingState();

    int getWorkerLifecycleState();

    String getBoundProcessBindingId();

    int bindRuntimeLoadV1(
        String projectIdentity,
        String projectSourceGeneration,
        String runtimeProvenanceDigest,
        String dependencyLayerBinding,
        String expectedProcessBindingId
    );

    int requestWorkerExitForRebind(
        String expectedWorkerInstanceId,
        String expectedProcessBindingId
    );

    int startCpythonSmokeV1(
        String expectedWorkerInstanceId,
        String expectedProcessBindingId
    );

    int getCpythonSmokeState();

    boolean hasCpythonSmokeExitCode();

    int getCpythonSmokeExitCode();

    String getCpythonSmokeStdout();

    String getCpythonSmokeStderr();

    int getCpythonSmokePythonPid();

    String getCpythonSmokeSessionId();

    int startProjectExecutionV1(
        String expectedWorkerInstanceId,
        String expectedProcessBindingId,
        String executionRoot,
        String entrypoint,
        String workingDirectory
    );

    int getProjectExecutionState();

    String getProjectExecutionSessionId();

    long getProjectExecutionGeneration();

    String getProjectExecutionProjectIdentity();

    String getProjectExecutionRoot();

    String getProjectExecutionEntrypoint();

    String getProjectExecutionWorkingDirectory();

    boolean hasProjectExecutionExitCode();

    int getProjectExecutionExitCode();

    String getProjectExecutionStdout();

    String getProjectExecutionStderr();
}
