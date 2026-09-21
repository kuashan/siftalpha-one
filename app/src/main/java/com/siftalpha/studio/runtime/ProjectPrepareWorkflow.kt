package com.siftalpha.studio.runtime

import com.siftalpha.studio.project.V04ProjectGateway

/**
 * Shared PREPARE（准备） workflow kernel used by both Normal Mode（普通模式） and
 * Developer Workspace（开发者工作区）.
 *
 * Presentation surfaces remain free to render different detail levels, but they do not own a
 * second provider-specific preparation implementation. Environment routing and the actual Internal /
 * External prepare command come from the same accepted ProjectRuntimeController contracts here.
 */
class ProjectPrepareWorkflow(
    private val runtime: ProjectRuntimeController,
) {
    data class Decision(
        val plan: ProjectEnvironmentPlan,
        val route: RuntimeControlDecision,
    )

    fun resolve(
        project: V04ProjectGateway.RuntimeProject,
        request: RuntimeControlRequest,
    ): Decision = Decision(
        plan = runtime.environmentPlan(project),
        route = runtime.resolveControlPath(
            project = project,
            action = ProjectRuntimeController.Action.PREPARE,
            request = request,
        ),
    )

    fun prepareInternal(
        project: V04ProjectGateway.RuntimeProject,
        progress: ((String) -> Unit)? = null,
    ): InternalEnvironmentPreparationResult =
        runtime.prepareEmbeddedPythonEnvironment(project, progress)

    fun externalCommand(
        project: V04ProjectGateway.RuntimeProject,
    ): RuntimeCommand = runtime.wrapCancelableExternalActivity(
        project = project,
        action = ProjectRuntimeController.Action.PREPARE,
        command = runtime.prepare(project),
    )
}
