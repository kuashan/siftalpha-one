package com.siftalpha.studio

/**
 * W2 single-project entry point.
 *
 * The workspace intentionally reuses the proven Runtime Center controller and its project-scoped
 * action policy. It receives only the stable SAF documentId, so a project is never selected by a
 * display name or list position.
 */
class ProjectWorkspaceActivity : V04Activity()
