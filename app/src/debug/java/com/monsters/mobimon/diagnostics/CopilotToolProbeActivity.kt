package com.monsters.mobimon.diagnostics

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.monsters.mobimon.MainActivity
import com.monsters.mobimon.core.auth.PersistentGitHubAuthentication
import com.monsters.mobimon.core.auth.probeToolCalling
import com.monsters.mobimon.core.auth.probeToolFoundation
import com.monsters.mobimon.core.domain.GitHubAuthentication
import com.monsters.mobimon.core.domain.GitHubSession
import com.monsters.mobimon.core.ui.MobiMonButton
import com.monsters.mobimon.core.ui.MobiMonTheme
import com.monsters.mobimon.manual.ManualEvaluation
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Explicit developer entry. There is no background probe, auto-retry or access to chat storage. */
@AndroidEntryPoint
class CopilotToolProbeActivity : ComponentActivity() {
    @Inject lateinit var authentication: GitHubAuthentication

    private val manualStart get() = intent.getIntExtra("manual_start", 1).coerceIn(1, 30)
    private val manualCount get() = intent.getIntExtra("manual_count", 31 - manualStart).coerceIn(1, 31 - manualStart)

    private var job: Job? = null
    private var running by mutableStateOf(false)
    private var report by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val session by authentication.session.collectAsStateWithLifecycle()
            MobiMonTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(32.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        Text("Copilot 도구 호출 진단 · Debug")
                        Text(
                            if (intent.getBooleanExtra("manual_rag", false)) {
                                "gpt-4o 고정 · 매뉴얼 합성 질문 $manualStart 번부터 $manualCount 회 · 실제 사용량 발생"
                            } else if (intent.getBooleanExtra("tool_foundation", false)) {
                                "gpt-4o 고정 · 공용 도구 실행 기반 검증 · 합성 질문 2회 · 실제 사용량 발생"
                            } else {
                                "gpt-4o 고정 · 합성 질문 · 실제 사용량 발생 · 도구 자동 선택 2회와 강제 지정 1회"
                            },
                        )
                        Text(
                            if (session is GitHubSession.Authenticated) {
                                "GitHub 로그인 확인됨"
                            } else {
                                "앱에서 GitHub 로그인을 완료한 후 돌아오세요."
                            },
                        )
                        MobiMonButton(
                            onClick = ::runProbe,
                            enabled =
                                !running &&
                                    session is GitHubSession.Authenticated &&
                                    authentication is PersistentGitHubAuthentication,
                        ) {
                            Text(if (running) "진단 중" else "진단 실행")
                        }
                        MobiMonButton(onClick = { job?.cancel() }, enabled = running) { Text("진단 취소") }
                        MobiMonButton(
                            onClick = {
                                startActivity(
                                    Intent(this@CopilotToolProbeActivity, MainActivity::class.java),
                                )
                            },
                        ) {
                            Text("앱 열기 / 로그인")
                        }
                        Text(report)
                    }
                }
            }
        }
    }

    private fun runProbe() {
        if (job?.isActive == true || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        // Test applications intentionally replace the real authentication binding.
        val realAuthentication = authentication as? PersistentGitHubAuthentication ?: return
        report = ""
        job =
            lifecycleScope.launch {
                running = true
                try {
                    val result =
                        if (intent.getBooleanExtra("manual_rag", false)) {
                            ManualEvaluation.run(
                                realAuthentication,
                                assets,
                                cacheDir,
                                ::append,
                                manualStart,
                                manualCount,
                            )
                        } else if (intent.getBooleanExtra("tool_foundation", false)) {
                            realAuthentication.probeToolFoundation { usage -> append(usage.toString()) }
                        } else {
                            realAuthentication.probeToolCalling { event -> append(event.toString()) }
                        }
                    append(result.toString())
                } catch (cancelled: CancellationException) {
                    append("CANCELLED")
                    throw cancelled
                } finally {
                    running = false
                }
            }
    }

    private fun append(metadata: String) {
        report += "$metadata\n"
        Log.i("MobiMonToolProbe", metadata)
    }

    override fun onPause() {
        job?.cancel()
        super.onPause()
    }
}
