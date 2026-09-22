package com.siftalpha.studio

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.siftalpha.studio.project.ProjectStore

private val SecondaryInk = Color(0xFF040817)
private val SecondaryPanel = Color(0xFF0B1631)
private val SecondaryRaised = Color(0xFF101D3D)
private val SecondaryBorder = Color(0xFF263A60)
private val SecondaryText = Color(0xFFF7FAFF)
private val SecondaryMuted = Color(0xFF9BAAD0)
private val SecondaryBlue = Color(0xFF387DFF)
private val SecondaryCyan = Color(0xFF39DFFF)
private val SecondaryViolet = Color(0xFFA84CFF)
private val SecondaryRed = Color(0xFFFF4F72)

@Composable
private fun NormalSecondaryDialog(
    title: String,
    subtitle: String? = null,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = SecondaryPanel,
                border = BorderStroke(1.dp, SecondaryBorder),
                shadowElevation = 18.dp,
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.siftalpha_launcher_art),
                            contentDescription = stringResource(R.string.brand_mark_content_description),
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Fit,
                        )
                        Spacer(Modifier.size(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = title,
                                color = SecondaryText,
                                fontSize = 19.sp,
                                lineHeight = 25.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            subtitle?.takeIf { it.isNotBlank() }?.let {
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    text = it,
                                    color = SecondaryMuted,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp,
                                )
                            }
                        }
                    }
                    content()
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.common_cancel),
                            color = SecondaryMuted,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SecondaryActionCard(
    title: String,
    subtitle: String,
    accent: Color = SecondaryBlue,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 70.dp)
            .clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = SecondaryRaised,
        border = BorderStroke(1.dp, SecondaryBorder),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .padding(2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier.size(34.dp),
                    shape = RoundedCornerShape(11.dp),
                    color = accent.copy(alpha = .16f),
                    border = BorderStroke(1.dp, accent.copy(alpha = .42f)),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "›",
                            color = accent,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Spacer(Modifier.size(11.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = SecondaryText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    color = SecondaryMuted,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun SiftAlphaNormalImportDialog(
    onDismiss: () -> Unit,
    onChooseFile: () -> Unit,
    onGitHub: () -> Unit,
    onNewProject: () -> Unit,
) {
    NormalSecondaryDialog(
        title = stringResource(R.string.home_import_title),
        subtitle = stringResource(R.string.home_import_supported),
        onDismiss = onDismiss,
    ) {
        SecondaryActionCard(
            title = stringResource(R.string.home_import_choose_file),
            subtitle = stringResource(R.string.home_import_summary),
            accent = SecondaryCyan,
            onClick = onChooseFile,
        )
        SecondaryActionCard(
            title = stringResource(R.string.home_import_github),
            subtitle = "GitHub · HTTPS / SSH",
            accent = SecondaryViolet,
            onClick = onGitHub,
        )
        SecondaryActionCard(
            title = stringResource(R.string.home_import_new_project),
            subtitle = stringResource(R.string.brand_new_project_hint).replace("\n", " "),
            accent = SecondaryBlue,
            onClick = onNewProject,
        )
    }
}

@Composable
internal fun SiftAlphaNormalProjectLocationDialog(
    state: HomeState,
    onDismiss: () -> Unit,
    onConnectAcode: () -> Unit,
    onChooseRoot: () -> Unit,
) {
    val rootLabel = when {
        !state.rootSelected -> stringResource(R.string.home_project_root_unselected)
        state.projectError != null -> stringResource(R.string.home_root_access_failed)
        else -> stringResource(
            R.string.home_root_selected,
            state.rootName ?: stringResource(R.string.home_none),
        )
    }
    NormalSecondaryDialog(
        title = stringResource(R.string.home_project_location_title),
        subtitle = stringResource(R.string.home_project_location_help),
        onDismiss = onDismiss,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = if (state.projectError == null) SecondaryInk else SecondaryRed.copy(alpha = .10f),
            border = BorderStroke(
                1.dp,
                if (state.projectError == null) SecondaryBorder else SecondaryRed.copy(alpha = .45f),
            ),
        ) {
            Column(Modifier.padding(13.dp)) {
                Text(
                    text = rootLabel,
                    color = SecondaryText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                state.projectError?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = it,
                        color = SecondaryRed,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                    )
                }
            }
        }
        SecondaryActionCard(
            title = stringResource(R.string.home_connect_acode),
            subtitle = stringResource(R.string.home_project_location_help),
            accent = SecondaryCyan,
            onClick = onConnectAcode,
        )
        SecondaryActionCard(
            title = stringResource(R.string.home_choose_other_root),
            subtitle = stringResource(R.string.home_project_location_help),
            accent = SecondaryBlue,
            onClick = onChooseRoot,
        )
    }
}

@Composable
internal fun SiftAlphaNormalMoreDialog(
    onDismiss: () -> Unit,
    onRefreshProjects: () -> Unit,
    onNewProject: () -> Unit,
    onUserStorage: () -> Unit,
    onSettings: () -> Unit,
) {
    NormalSecondaryDialog(
        title = stringResource(R.string.home_more_title),
        subtitle = stringResource(R.string.app_name),
        onDismiss = onDismiss,
    ) {
        SecondaryActionCard(
            title = stringResource(R.string.home_refresh_projects),
            subtitle = stringResource(R.string.home_all_projects, 0),
            accent = SecondaryCyan,
            onClick = onRefreshProjects,
        )
        SecondaryActionCard(
            title = stringResource(R.string.home_new_python_project),
            subtitle = stringResource(R.string.brand_new_project_hint).replace("\n", " "),
            accent = SecondaryViolet,
            onClick = onNewProject,
        )
        SecondaryActionCard(
            title = stringResource(R.string.home_user_storage),
            subtitle = stringResource(R.string.user_storage_summary),
            accent = SecondaryBlue,
            onClick = onUserStorage,
        )
        SecondaryActionCard(
            title = stringResource(R.string.home_action_settings),
            subtitle = stringResource(R.string.home_action_settings),
            accent = SecondaryMuted,
            onClick = onSettings,
        )
    }
}

@Composable
internal fun SiftAlphaNormalCreateProjectDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    NormalSecondaryDialog(
        title = stringResource(R.string.home_create_project_title),
        subtitle = stringResource(R.string.brand_new_project_hint).replace("\n", " "),
        onDismiss = onDismiss,
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.home_project_name_hint)) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
        )
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.home_project_description_hint)) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
        )
        Button(
            onClick = { onCreate(name.trim(), description.trim()) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            shape = RoundedCornerShape(15.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SecondaryBlue),
        ) {
            Text(
                text = stringResource(R.string.home_create),
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
internal fun SiftAlphaNormalGitHubImportDialog(
    onDismiss: () -> Unit,
    onImport: (String, String, String) -> Unit,
) {
    var url by remember { mutableStateOf("") }
    var branch by remember { mutableStateOf("main") }
    var name by remember { mutableStateOf("") }

    NormalSecondaryDialog(
        title = stringResource(R.string.runtime_github_title),
        subtitle = stringResource(R.string.runtime_github_message),
        onDismiss = onDismiss,
    ) {
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("GitHub URL") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
        )
        OutlinedTextField(
            value = branch,
            onValueChange = { branch = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.runtime_github_branch_hint)) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
        )
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.runtime_github_name_hint)) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
        )
        Button(
            onClick = { onImport(url.trim(), branch.trim(), name.trim()) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            shape = RoundedCornerShape(15.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = SecondaryViolet,
                contentColor = Color.White,
            ),
        ) {
            Text(
                text = stringResource(R.string.runtime_github_start),
                fontWeight = FontWeight.Bold,
            )
        }
    }
}


@Composable
internal fun SiftAlphaNormalProjectDetailsDialog(
    project: ProjectStore.ProjectSummary,
    onDismiss: () -> Unit,
) {
    NormalSecondaryDialog(
        title = project.name,
        subtitle = stringResource(R.string.home_details),
        onDismiss = onDismiss,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = SecondaryRaised,
            border = BorderStroke(1.dp, SecondaryBorder),
        ) {
            Text(
                text = stringResource(
                    R.string.home_project_details,
                    project.description.ifBlank { stringResource(R.string.home_none) },
                    project.entry.ifBlank { stringResource(R.string.home_none) },
                    project.run.ifBlank { stringResource(R.string.home_none) },
                    project.source,
                ),
                color = SecondaryText,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(15.dp),
            )
        }
    }
}

@Composable
internal fun SiftAlphaNormalDeleteProjectDialog(
    project: ProjectStore.ProjectSummary,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    NormalSecondaryDialog(
        title = stringResource(R.string.home_confirm_delete_title, project.name),
        subtitle = stringResource(R.string.home_confirm_delete_message),
        onDismiss = onDismiss,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(15.dp),
            color = SecondaryRed.copy(alpha = .10f),
            border = BorderStroke(1.dp, SecondaryRed.copy(alpha = .45f)),
        ) {
            Text(
                text = project.name,
                color = SecondaryText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(14.dp),
            )
        }
        Button(
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            shape = RoundedCornerShape(15.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = SecondaryRed,
                contentColor = Color.White,
            ),
        ) {
            Text(
                text = stringResource(R.string.home_confirm_delete),
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
