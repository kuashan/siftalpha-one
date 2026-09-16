package com.siftalpha.studio.siftalphax

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.siftalpha.studio.R
import com.siftalpha.studio.StudioComposeActivity
import com.siftalpha.studio.ui.theme.StudioTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

class EmbeddedPythonTestActivity : StudioComposeActivity() {
    private lateinit var session: EmbeddedPythonSession

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        session = EmbeddedPythonSession.shared(this)
        setContent {
            StudioTheme {
                EmbeddedPythonScreen(
                    session = session,
                    onBack = { onBackPressedDispatcher.onBackPressed() },
                )
            }
        }
    }
}

@androidx.compose.runtime.Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun EmbeddedPythonScreen(
    session: EmbeddedPythonSession,
    onBack: () -> Unit,
) {
    var snapshot by remember { mutableStateOf(EmbeddedPythonSnapshot()) }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    fun copyAllDiagnostics(value: EmbeddedPythonSnapshot) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(
            ClipData.newPlainText(
                "SiftAlpha X diagnostics",
                EmbeddedPythonDiagnosticText.copyAll(value),
            ),
        )
        Toast.makeText(context, R.string.siftalpha_x_copied, Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            snapshot = runCatching { session.snapshot() }
                .onFailure { error = it.message ?: it.javaClass.simpleName }
                .getOrElse { snapshot }
            delay(200L)
        }
    }

    val canStart = EmbeddedPythonStatePolicy.canStart(snapshot.state)
    val canStop = EmbeddedPythonStatePolicy.canStop(snapshot.state)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.siftalpha_x_title)) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text(text = stringResource(R.string.common_back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.siftalpha_x_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            item {
                Text(
                    text = stringResource(R.string.siftalpha_x_one_session_notice),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    EmbeddedPythonScenario.entries.forEach { scenario ->
                        Button(
                            onClick = {
                                error = null
                                runCatching { session.start(scenario) }
                                    .onFailure { error = it.message ?: it.javaClass.simpleName }
                            },
                            enabled = canStart,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(text = stringResource(scenario.labelResource))
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            error = null
                            if (!session.requestStop()) {
                                error = "SIFTALPHA_X_STOP_REQUEST_NOT_ACCEPTED"
                            }
                        },
                        enabled = canStop,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = stringResource(R.string.siftalpha_x_stop))
                    }
                    OutlinedButton(
                        onClick = {
                            error = null
                            snapshot = runCatching { session.snapshot() }
                                .onFailure { error = it.message ?: it.javaClass.simpleName }
                                .getOrElse { snapshot }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = stringResource(R.string.siftalpha_x_refresh_status))
                    }
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SelectionContainer {
                            Text(
                                text = EmbeddedPythonDiagnosticText.session(snapshot),
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                        OutlinedButton(
                            onClick = { copyAllDiagnostics(snapshot) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(text = stringResource(R.string.siftalpha_x_copy_all))
                        }
                    }
                }
            }
            if (error != null) {
                item {
                    Text(
                        text = error.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
            item {
                Text(
                    text = stringResource(R.string.siftalpha_x_stdout),
                    style = MaterialTheme.typography.titleMedium,
                )
                SelectionContainer {
                    Text(
                        text = snapshot.stdout.ifBlank { stringResource(R.string.siftalpha_x_no_output) },
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
            item {
                Text(
                    text = stringResource(R.string.siftalpha_x_stderr),
                    style = MaterialTheme.typography.titleMedium,
                )
                SelectionContainer {
                    Text(
                        text = snapshot.stderr.ifBlank { stringResource(R.string.siftalpha_x_no_output) },
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}
