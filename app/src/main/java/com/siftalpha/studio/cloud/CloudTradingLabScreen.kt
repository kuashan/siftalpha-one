package com.siftalpha.studio.cloud

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.siftalpha.cloud.core.CloudEnvironmentState
import com.siftalpha.cloud.core.CloudProjectAction
import com.siftalpha.cloud.core.CloudRuntimeState
import com.siftalpha.studio.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudTradingLabScreen(
    state: CloudTradingLabUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onPrepare: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onLogs: () -> Unit,
    onOpenWeb: () -> Unit,
) {
    var showLogs by remember { mutableStateOf(false) }
    val decision = CloudTradingLabPolicy.action(state)
    val operationLabel = state.operation?.state?.name ?: "IDLE"

    if (showLogs) {
        AlertDialog(
            onDismissRequest = { showLogs = false },
            title = { Text(stringResource(R.string.cloud_trading_lab_logs)) },
            text = {
                Text(
                    state.logs?.lines?.joinToString("\n")
                        ?.ifBlank { stringResource(R.string.cloud_trading_lab_no_logs) }
                        ?: stringResource(R.string.cloud_trading_lab_no_logs),
                )
            },
            confirmButton = {
                TextButton(onClick = { showLogs = false }) {
                    Text(stringResource(R.string.common_close))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cloud_trading_lab)) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.cloud_trading_lab_back)) }
                },
                actions = {
                    TextButton(onClick = onRefresh, enabled = !state.loading) {
                        Text(stringResource(R.string.cloud_trading_lab_refresh))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = stringResource(R.string.cloud_trading_lab),
                                style = MaterialTheme.typography.headlineSmall,
                            )
                            Text(
                                text = state.project?.displayName
                                    ?: stringResource(R.string.cloud_trading_lab_no_project),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            state.error?.let { error ->
                                Text(
                                    text = error.message,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            state.operation?.let {
                                Text(stringResource(R.string.cloud_trading_lab_operation, operationLabel))
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Environment: ${state.status?.environmentState ?: "UNKNOWN"}")
                            Text("Runtime: ${state.status?.runtimeState ?: CloudRuntimeState.UNKNOWN}")
                            Text(
                                text = state.runtimeUnits.singleOrNull()?.let { unit ->
                                    stringResource(
                                        R.string.cloud_trading_lab_resources,
                                        unit.cpuPercent?.let { "%.1f%% CPU".format(it) } ?: "CPU n/a",
                                        unit.memoryUsage ?: "Memory n/a",
                                    )
                                } ?: stringResource(R.string.cloud_trading_lab_no_runtime),
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = onPrepare,
                                enabled = !state.loading && decision?.enabled == true && decision.action == CloudProjectAction.PREPARE,
                                modifier = Modifier.weight(1f),
                            ) { Text(stringResource(R.string.cloud_trading_lab_prepare)) }
                            Button(
                                onClick = onStart,
                                enabled = !state.loading && decision?.enabled == true && decision.action == CloudProjectAction.START,
                                modifier = Modifier.weight(1f),
                            ) { Text(stringResource(R.string.cloud_trading_lab_start)) }
                            Button(
                                onClick = onStop,
                                enabled = !state.loading && decision?.enabled == true && decision.action == CloudProjectAction.STOP,
                                modifier = Modifier.weight(1f),
                            ) { Text(stringResource(R.string.cloud_trading_lab_stop)) }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = {
                                    onLogs()
                                    showLogs = true
                                },
                                enabled = !state.loading,
                                modifier = Modifier.weight(1f),
                            ) { Text(stringResource(R.string.cloud_trading_lab_logs)) }
                            OutlinedButton(
                                onClick = onOpenWeb,
                                enabled = !state.loading && state.project?.web != null,
                                modifier = Modifier.weight(1f),
                            ) { Text(stringResource(R.string.cloud_trading_lab_open_web)) }
                        }
                        Text(
                            text = "Environment detail: ${state.status?.environmentState ?: CloudEnvironmentState.UNKNOWN}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
