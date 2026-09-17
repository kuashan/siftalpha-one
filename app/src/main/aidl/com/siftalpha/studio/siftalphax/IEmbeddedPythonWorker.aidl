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
}
