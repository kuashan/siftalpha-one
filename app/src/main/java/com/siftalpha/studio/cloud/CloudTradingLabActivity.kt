package com.siftalpha.studio.cloud

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.siftalpha.cloud.agent.SiftAlphaCloudAgentClient
import com.siftalpha.cloud.agent.transport.UrlConnectionCloudHttpTransport
import com.siftalpha.studio.ResultWebActivity
import com.siftalpha.studio.StudioComposeActivity
import com.siftalpha.studio.ui.theme.StudioTheme
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CloudTradingLabActivity : StudioComposeActivity() {
    private lateinit var executor: ExecutorService
    private lateinit var controller: CloudTradingLabController
    private var screenState by mutableStateOf(CloudTradingLabUiState(loading = true))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val serverId = intent.getStringExtra(EXTRA_SERVER_ID) ?: DEFAULT_SERVER_ID
        val client = SiftAlphaCloudAgentClient(
            serverId = serverId,
            credentialStore = AndroidCloudCredentialStore(this),
            transport = UrlConnectionCloudHttpTransport(),
        )
        controller = CloudTradingLabController(CloudTradingLabClientAdapter(client))
        executor = Executors.newSingleThreadExecutor()

        setContent {
            StudioTheme {
                CloudTradingLabScreen(
                    state = screenState,
                    onBack = ::finish,
                    onRefresh = { submit { controller.refresh() } },
                    onPrepare = { submit { controller.prepare(screenState) } },
                    onStart = { submit { controller.start(screenState) } },
                    onStop = { submit { controller.stop(screenState) } },
                    onLogs = { submit { controller.loadLogs(screenState) } },
                    onOpenWeb = ::openWeb,
                )
            }
        }
        submit { controller.refresh() }
    }

    private fun submit(work: () -> CloudTradingLabUiState) {
        if (!::executor.isInitialized) return
        screenState = screenState.copy(loading = true)
        executor.execute {
            val next = runCatching { work() }.getOrElse { screenState }
            runOnUiThread { screenState = next.copy(loading = false) }
        }
    }

    private fun openWeb() {
        val endpoint = screenState.project?.web ?: return
        val url = CloudTradingLabPolicy.remoteWebUrl(endpoint)
        startActivity(
            Intent(this, ResultWebActivity::class.java).putExtra(
                ResultWebActivity.EXTRA_REMOTE_WEB_URL,
                url,
            ),
        )
    }

    override fun onDestroy() {
        if (::executor.isInitialized) executor.shutdownNow()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_SERVER_ID = "cloud_server_id"
        const val DEFAULT_SERVER_ID = "oracle"
    }
}
