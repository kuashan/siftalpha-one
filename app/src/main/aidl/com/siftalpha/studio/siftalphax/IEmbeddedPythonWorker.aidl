package com.siftalpha.studio.siftalphax;

interface IEmbeddedPythonWorker {
    const int BINDING_STATE_UNBOUND = 0;
    const int BINDING_STATE_BOUND = 1;

    const int BOUND_NEW = 0;
    const int BOUND_SAME = 1;
    const int REJECTED_INVALID_REQUEST = 2;
    const int REJECTED_BINDING_CONFLICT = 3;

    String getWorkerInstanceId();

    int getWorkerPid();

    int getBindingState();

    String getBoundProcessBindingId();

    int bindRuntimeLoadV1(
        String projectIdentity,
        String projectSourceGeneration,
        String runtimeProvenanceDigest,
        String dependencyLayerBinding,
        String expectedProcessBindingId
    );
}
