package com.siftalpha.studio.presentation

/**
 * Normal Mode（普通模式） maps the existing ProjectActionPolicy（项目动作策略） into the small
 * first-level action vocabulary exposed to ordinary users.
 *
 * This is not a second lifecycle/state machine. It only translates the already-resolved developer
 * policy into fewer user-facing choices. Until Unified Refresh（统一刷新） is implemented, an
 * Environment STATUS（环境状态查询） whose direct secondary action is PREPARE（准备环境） is presented
 * as PREPARE_PROJECT（准备项目） because that workflow performs read-only detection before mutation.
 */
object NormalProjectPrimaryActionPolicy {

    enum class Action {
        PREPARE_PROJECT,
        CONFIGURE,
        RUN,
        STOP,
        NONE,
    }

    fun resolve(policy: ProjectActionPolicy.Result): Action = when (policy.primaryAction) {
        ProjectActionPolicy.Action.PREPARE -> Action.PREPARE_PROJECT
        ProjectActionPolicy.Action.CONFIGURE -> Action.CONFIGURE
        ProjectActionPolicy.Action.START -> Action.RUN
        ProjectActionPolicy.Action.STOP -> Action.STOP
        ProjectActionPolicy.Action.STATUS -> if (
            policy.directSecondaryAction == ProjectActionPolicy.Action.PREPARE &&
            policy.isEnabled(ProjectActionPolicy.Action.PREPARE)
        ) {
            Action.PREPARE_PROJECT
        } else {
            Action.NONE
        }
        null,
        ProjectActionPolicy.Action.EDIT,
        ProjectActionPolicy.Action.LOGS,
        ProjectActionPolicy.Action.OPEN_BROWSER,
        ProjectActionPolicy.Action.CLEAN,
        ProjectActionPolicy.Action.OPEN_SOURCE,
        -> Action.NONE
    }
}
