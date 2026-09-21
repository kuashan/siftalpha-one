package com.siftalpha.studio

import android.animation.ValueAnimator
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.siftalpha.studio.presentation.NormalProjectPrimaryActionPolicy
import com.siftalpha.studio.project.ProjectStore
import com.siftalpha.studio.runtime.ExternalProviderReadiness
import com.siftalpha.studio.runtime.ProjectRuntimeSelection
import com.siftalpha.studio.runtime.RuntimeState
import com.siftalpha.studio.ui.theme.StudioTheme
import com.siftalpha.studio.ui.theme.StudioThemeTokens
import kotlinx.coroutines.delay

private val Ink = Color(0xFF050A16)
private val Panel = Color(0xFF0C1730)
private val Raised = Color(0xFF112140)
private val Blue = Color(0xFF4C6FFF)
private val Cyan = Color(0xFF34D6FF)
private val Violet = Color(0xFF9B5CFF)
private val Green = Color(0xFF39D98A)
private val Red = Color(0xFFFF5F73)
private val Muted = Color(0xFFA7B5D2)
private val Border = Color(0xFF263A60)

private val NormalColors = darkColorScheme(
    background = Ink,
    onBackground = Color(0xFFF5F8FF),
    surface = Panel,
    onSurface = Color(0xFFF5F8FF),
    surfaceVariant = Raised,
    onSurfaceVariant = Muted,
    primary = Blue,
    onPrimary = Color.White,
    secondary = Cyan,
    onSecondary = Ink,
    tertiary = Violet,
    onTertiary = Color.White,
    error = Red,
    onError = Color.White,
)

@Composable
fun SiftAlphaNormalTheme(content: @Composable () -> Unit) {
    StudioTheme(darkTheme = true) {
        MaterialTheme(
            colorScheme = NormalColors,
            typography = MaterialTheme.typography,
            shapes = MaterialTheme.shapes,
            content = content,
        )
    }
}

@Composable
fun SiftAlphaBrandTransition(content: @Composable () -> Unit) {
    val motion = remember { ValueAnimator.areAnimatorsEnabled() }
    var visible by rememberSaveable { mutableStateOf(true) }
    val trace = remember { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (motion) {
            trace.animateTo(1f, tween(620))
            delay(250)
        } else delay(140)
        visible = false
    }
    Box(Modifier.fillMaxSize()) {
        content()
        AnimatedVisibility(visible, exit = fadeOut(tween(if (motion) 220 else 70))) {
            Box(Modifier.fillMaxSize().background(Ink)) {
                TechBackdrop(Modifier.fillMaxSize(), animate = false)
                Column(
                    Modifier.align(Alignment.Center).padding(horizontal = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    SiftMark(Modifier.size(116.dp), trace.value)
                    Spacer(Modifier.height(18.dp))
                    Text(stringResource(R.string.app_name), fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(14.dp))
                    Text(stringResource(R.string.brand_tagline_primary), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(7.dp))
                    Text(stringResource(R.string.brand_tagline_secondary), color = Cyan, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun SiftMark(modifier: Modifier, progress: Float = 1f) {
    Canvas(modifier) {
        val sx = size.width / 100f
        val sy = size.height / 100f
        val full = Path().apply {
            moveTo(79f*sx, 16f*sy)
            cubicTo(62f*sx, 8f*sy, 35f*sx, 12f*sy, 25f*sx, 27f*sy)
            cubicTo(14f*sx, 43f*sy, 30f*sx, 50f*sy, 50f*sx, 54f*sy)
            cubicTo(71f*sx, 58f*sy, 84f*sx, 67f*sy, 76f*sx, 80f*sy)
            cubicTo(66f*sx, 96f*sy, 39f*sx, 96f*sy, 20f*sx, 83f*sy)
        }
        val measure = PathMeasure().apply { setPath(full, false) }
        val segment = Path()
        measure.getSegment(0f, measure.length * progress.coerceIn(0f, 1f), segment, true)
        drawPath(
            segment,
            brush = Brush.linearGradient(listOf(Cyan, Blue, Violet), Offset(size.width, 0f), Offset(0f, size.height)),
            style = Stroke(size.minDimension * .22f, cap = StrokeCap.Round),
        )
    }
}

@Composable
private fun TechBackdrop(modifier: Modifier, animate: Boolean = true) {
    val motion = remember { ValueAnimator.areAnimatorsEnabled() }
    val phase = if (animate && motion) {
        val t = rememberInfiniteTransition(label = "sift-tech")
        val v by t.animateFloat(-.05f, .07f, infiniteRepeatable(tween(5200), RepeatMode.Reverse), label = "phase")
        v
    } else 0f
    Canvas(modifier.background(Ink)) {
        drawRect(Brush.radialGradient(listOf(Color(0xFF173675).copy(.55f), Color.Transparent), Offset(size.width*(.82f+phase), size.height*.12f), size.maxDimension*.72f))
        drawRect(Brush.radialGradient(listOf(Color(0xFF4A176E).copy(.30f), Color.Transparent), Offset(size.width*.08f, size.height*(.82f-phase)), size.maxDimension*.58f))
        val wave = Path().apply {
            moveTo(-40f, size.height*.73f)
            cubicTo(size.width*.22f, size.height*(.58f+phase), size.width*.62f, size.height*(.86f-phase), size.width+50f, size.height*.66f)
        }
        drawPath(wave, brush = Brush.linearGradient(listOf(Color.Transparent, Blue.copy(.5f), Violet.copy(.4f))), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
private fun TechPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = Panel.copy(.92f), border = BorderStroke(1.dp, Border)) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun PrimaryButton(label: String, onClick: () -> Unit, enabled: Boolean = true, danger: Boolean = false) {
    Button(
        onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = if (danger) Red else Blue, disabledContainerColor = Raised),
    ) { Text(label, fontWeight = FontWeight.SemiBold) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiftAlphaNormalHomeScreen(
    state: HomeState,
    versionName: String,
    onImport: () -> Unit,
    onNewProject: () -> Unit,
    onProjectLocation: () -> Unit,
    onOpenProject: (ProjectStore.ProjectSummary) -> Unit,
    onMore: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        TechBackdrop(Modifier.fillMaxSize())
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SiftMark(Modifier.size(31.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(stringResource(R.string.app_name), fontWeight = FontWeight.SemiBold)
                        }
                    },
                    actions = { TextButton(onMore) { Text(stringResource(R.string.home_more_title), color = Cyan) } },
                )
            },
        ) { pad ->
            LazyColumn(
                Modifier.fillMaxSize().padding(pad),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                item {
                    Text(stringResource(R.string.brand_normal_intro), fontSize = 28.sp, lineHeight = 35.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(9.dp))
                    Text(stringResource(R.string.brand_tagline_secondary), color = Cyan, style = MaterialTheme.typography.bodyMedium)
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        HomeAction(stringResource(R.string.home_import_title), stringResource(R.string.home_import_supported), true, Modifier.weight(1f), onImport)
                        HomeAction(stringResource(R.string.brand_new_project_title), stringResource(R.string.brand_new_project_hint), false, Modifier.weight(1f), onNewProject)
                    }
                }
                item {
                    TechPanel(Modifier.clickable(role = Role.Button, onClick = onProjectLocation)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.home_project_location_title), style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    if (state.rootSelected && state.projectError == null) state.rootName ?: stringResource(R.string.home_none) else stringResource(R.string.home_project_location_action),
                                    color = Muted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text("›", fontSize = 28.sp, color = Cyan)
                        }
                    }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.home_all_projects, state.projects.size), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text(versionName, color = Muted.copy(.7f), style = MaterialTheme.typography.bodySmall)
                    }
                }
                when {
                    !state.rootSelected -> item { EmptyPanel(stringResource(R.string.home_project_location_title), stringResource(R.string.home_project_location_help), stringResource(R.string.home_project_location_action), onProjectLocation) }
                    state.projectError != null -> item { EmptyPanel(stringResource(R.string.home_root_access_failed), stringResource(R.string.home_root_read_failed, state.projectError), stringResource(R.string.home_project_location_action), onProjectLocation, true) }
                    state.projects.isEmpty() -> item { EmptyPanel(stringResource(R.string.brand_no_projects_title), stringResource(R.string.brand_empty_project_hint), stringResource(R.string.home_import_title), onImport) }
                    else -> items(state.projects, key = { it.documentId }) { p -> ProjectCardNormal(p) { onOpenProject(p) } }
                }
            }
        }
    }
}

@Composable
private fun HomeAction(title: String, subtitle: String, prominent: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.heightIn(min = 126.dp).clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = if (prominent) Color(0xFF183B86) else Color(0xFF101F4B),
        border = BorderStroke(1.dp, if (prominent) Cyan.copy(.42f) else Border),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(if (prominent) "↑" else "+", fontSize = 25.sp, color = Cyan)
            Column {
                Text(title, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(5.dp))
                Text(subtitle, color = Muted, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ProjectCardNormal(project: ProjectStore.ProjectSummary, onOpen: () -> Unit) {
    TechPanel(Modifier.clickable(role = Role.Button, onClick = onOpen)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).background(Brush.linearGradient(listOf(Blue, Violet)), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                Text("S", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(project.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (project.description.isNotBlank()) Text(project.description, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(if (project.run.isBlank()) R.string.brand_project_needs_setup else R.string.brand_project_ready),
                    color = if (project.run.isBlank()) Color(0xFFFFC15C) else Green,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text("›", fontSize = 28.sp, color = Cyan)
        }
    }
}

@Composable
private fun EmptyPanel(title: String, detail: String, action: String, onAction: () -> Unit, error: Boolean = false) {
    TechPanel {
        SiftMark(Modifier.size(52.dp))
        Spacer(Modifier.height(10.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(5.dp))
        Text(detail, color = if (error) Red else Muted, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(14.dp))
        PrimaryButton(action, onAction)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiftAlphaNormalWorkspaceScreen(
    state: NormalProjectWorkspaceActivity.ScreenState,
    onBack: () -> Unit,
    onPrepare: () -> Unit,
    onConfigure: () -> Unit,
    onRun: () -> Unit,
    onStop: () -> Unit,
    onRefresh: () -> Unit,
    onOpen: () -> Unit,
    onSelectRuntime: (ProjectRuntimeSelection) -> Unit,
    onOpenDeveloper: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenTermux: () -> Unit,
    onRecheckExternal: () -> Unit,
) {
    val resultReady = state.openEnabled && state.runtimeState == RuntimeState.EXITED_SUCCESS
    val accent = when (state.runtimeState) {
        RuntimeState.RUNNING -> Cyan
        RuntimeState.EXITED_SUCCESS -> Green
        RuntimeState.EXITED_ERROR, RuntimeState.ENVIRONMENT_ERROR -> Red
        RuntimeState.PREPARING, RuntimeState.STARTING -> Violet
        RuntimeState.STOPPED_BY_USER -> Muted
        RuntimeState.UNKNOWN -> Blue
    }
    Box(Modifier.fillMaxSize()) {
        TechBackdrop(Modifier.fillMaxSize())
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    title = { Text(state.projectName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = { TextButton(onBack) { Text(stringResource(R.string.common_back), color = Cyan) } },
                )
            },
        ) { pad ->
            LazyColumn(
                Modifier.fillMaxSize().padding(pad),
                contentPadding = PaddingValues(20.dp, 10.dp, 20.dp, 36.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = Panel.copy(.95f), border = BorderStroke(1.dp, accent.copy(.5f))) {
                        Column(Modifier.padding(20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(44.dp).background(accent.copy(.16f), CircleShape), contentAlignment = Alignment.Center) {
                                    if (state.activityIndicatorVisible) CircularProgressIndicator(Modifier.size(24.dp), color = accent, strokeWidth = 2.5.dp)
                                    else Box(Modifier.size(12.dp).background(accent, CircleShape))
                                }
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(stringResource(R.string.normal_project_status_title), color = Muted, style = MaterialTheme.typography.bodySmall)
                                    Text(state.statusLabel, fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            if (state.activityIndicatorVisible) {
                                Spacer(Modifier.height(15.dp))
                                LinearProgressIndicator(Modifier.fillMaxWidth(), color = accent, trackColor = Raised)
                            }
                            state.message?.takeIf { it.isNotBlank() && !it.contains("SIFTALPHA_") }?.let {
                                Spacer(Modifier.height(10.dp)); Text(it, color = Muted, style = MaterialTheme.typography.bodyMedium)
                            }
                            Spacer(Modifier.height(18.dp))
                            if (resultReady) PrimaryButton(stringResource(R.string.normal_project_open_action), onOpen)
                            else when (state.primaryAction) {
                                NormalProjectPrimaryActionPolicy.Action.PREPARE_PROJECT -> PrimaryButton(stringResource(R.string.normal_project_prepare_action), onPrepare, !state.busy)
                                NormalProjectPrimaryActionPolicy.Action.CONFIGURE -> PrimaryButton(stringResource(R.string.normal_project_configure_action), onConfigure, !state.busy)
                                NormalProjectPrimaryActionPolicy.Action.RUN -> PrimaryButton(stringResource(R.string.runtime_button_run), onRun, !state.busy)
                                NormalProjectPrimaryActionPolicy.Action.STOP -> PrimaryButton(stringResource(R.string.runtime_button_stop), onStop, !state.busy || state.runtimeState == RuntimeState.PREPARING, true)
                                NormalProjectPrimaryActionPolicy.Action.NONE -> Text(stringResource(R.string.normal_project_waiting_action), color = Muted)
                            }
                        }
                    }
                }
                state.preparePhaseTitle?.let { phase -> item {
                    TechPanel {
                        Text(stringResource(R.string.normal_prepare_card_title), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(phase, color = Cyan)
                        state.preparePhaseDetail?.let { Spacer(Modifier.height(5.dp)); Text(it, color = Muted, style = MaterialTheme.typography.bodyMedium) }
                    }
                } }
                if (state.runtimeSelection == ProjectRuntimeSelection.TERMUX && state.externalReadiness != null && state.externalReadiness != ExternalProviderReadiness.READY) item {
                    ExternalPanel(state.externalReadiness, onRequestPermission, onOpenTermux, onRecheckExternal)
                }
                if (resultReady) item {
                    TechPanel {
                        Text(stringResource(R.string.brand_result_ready_title), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(5.dp)); Text(stringResource(R.string.brand_result_ready_detail), color = Muted)
                        Spacer(Modifier.height(12.dp)); OutlinedButton(onRun, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.runtime_button_run)) }
                    }
                }
                item {
                    TechPanel {
                        Text(stringResource(R.string.normal_runtime_title), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(5.dp))
                        Text(stringResource(R.string.normal_runtime_current, stringResource(if (state.runtimeSelection == ProjectRuntimeSelection.EMBEDDED_R) R.string.normal_runtime_internal else R.string.normal_runtime_external)), color = Muted)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton({ onSelectRuntime(ProjectRuntimeSelection.EMBEDDED_R) }, Modifier.weight(1f).heightIn(min = 48.dp), enabled = state.runtimeSelectionCanChange && state.runtimeSelection != ProjectRuntimeSelection.EMBEDDED_R && !state.busy) { Text(stringResource(R.string.normal_runtime_internal_short)) }
                            OutlinedButton({ onSelectRuntime(ProjectRuntimeSelection.TERMUX) }, Modifier.weight(1f).heightIn(min = 48.dp), enabled = state.runtimeSelectionCanChange && state.runtimeSelection != ProjectRuntimeSelection.TERMUX && !state.busy) { Text(stringResource(R.string.normal_runtime_external_short)) }
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!resultReady) OutlinedButton(onOpen, Modifier.weight(1f).heightIn(min = 48.dp), enabled = state.openEnabled && !state.busy) { Text(stringResource(R.string.normal_project_open_action)) }
                        OutlinedButton(onRefresh, Modifier.weight(1f).heightIn(min = 48.dp), enabled = !state.busy) { Text(stringResource(R.string.normal_project_refresh_action)) }
                    }
                }
                if (state.developerModeEnabled) item {
                    TechPanel {
                        Text(stringResource(R.string.normal_project_developer_title), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp)); Text(stringResource(R.string.normal_project_developer_summary), color = Muted)
                        Spacer(Modifier.height(10.dp)); OutlinedButton(onOpenDeveloper, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.normal_project_open_developer)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExternalPanel(readiness: ExternalProviderReadiness, onPermission: () -> Unit, onOpenTermux: () -> Unit, onRecheck: () -> Unit) {
    TechPanel {
        Text(stringResource(R.string.normal_external_provider_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        when (readiness) {
            ExternalProviderReadiness.RUN_COMMAND_PERMISSION_REQUIRED -> { Text(stringResource(R.string.normal_external_provider_permission_required), color = Muted); Spacer(Modifier.height(10.dp)); PrimaryButton(stringResource(R.string.normal_external_provider_allow), onPermission) }
            ExternalProviderReadiness.TERMUX_NOT_INSTALLED, ExternalProviderReadiness.EXTERNAL_APPS_CONFIGURATION_REQUIRED -> { Text(stringResource(R.string.normal_external_provider_open_termux), color = Muted); Spacer(Modifier.height(10.dp)); OutlinedButton(onOpenTermux, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.normal_external_provider_open_termux_action)) } }
            ExternalProviderReadiness.BRIDGE_CHECK_REQUIRED -> Text(stringResource(R.string.normal_external_provider_check_required), color = Muted)
            ExternalProviderReadiness.BRIDGE_CHECKING -> { LinearProgressIndicator(Modifier.fillMaxWidth(), color = Cyan, trackColor = Raised); Spacer(Modifier.height(6.dp)); Text(stringResource(R.string.normal_external_provider_checking), color = Muted) }
            ExternalProviderReadiness.BRIDGE_UNRESPONSIVE -> { Text(stringResource(R.string.normal_external_provider_no_response), color = Muted); Spacer(Modifier.height(10.dp)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton(onOpenTermux, Modifier.weight(1f).heightIn(min = 48.dp)) { Text(stringResource(R.string.normal_external_provider_open_termux_action)) }; OutlinedButton(onRecheck, Modifier.weight(1f).heightIn(min = 48.dp)) { Text(stringResource(R.string.normal_external_provider_recheck)) } } }
            ExternalProviderReadiness.UNAVAILABLE -> Text(stringResource(R.string.normal_external_provider_unavailable), color = Red)
            ExternalProviderReadiness.READY -> Unit
        }
    }
}
