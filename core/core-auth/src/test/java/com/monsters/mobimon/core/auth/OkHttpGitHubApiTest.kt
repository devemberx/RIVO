package com.monsters.mobimon.core.auth

import com.monsters.mobimon.core.domain.AuthenticationProblem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetAddress
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OkHttpGitHubApiTest {
    private val server = MockWebServer()
    private lateinit var api: OkHttpGitHubApi

    @Before fun setup() {
        server.start(InetAddress.getByName("127.0.0.1"), 0)
        // Keep transport retry tests independent of localhost IPv4/IPv6 route selection.
        val base =
            server
                .url("/")
                .newBuilder()
                .host("127.0.0.1")
                .build()
        api =
            OkHttpGitHubApi(
                OkHttpClient
                    .Builder()
                    .followRedirects(
                        false,
                    ).retryOnConnectionFailure(false)
                    .build(),
                "app-id",
                {
                    1000
                },
                base,
                base,
            )
    }

    @After fun close() {
        server.shutdown()
    }

    @Test fun deviceRequestUsesFormAndOnlyPublicClientId() =
        runBlocking {
            enqueue(
                """{"device_code":"private-code","user_code":"ABCD-EFGH","verification_uri":"https://github.com/login/device","expires_in":900,"interval":5}""",
            )
            val code = api.requestCode()
            assertEquals(900, code.expiresInSeconds)
            val request = server.takeRequest()
            assertEquals("/login/device/code", request.path)
            assertEquals("POST", request.method)
            assertEquals("application/json", request.getHeader("Accept"))
            assertEquals("client_id=app-id&scope=read%3Auser", request.body.readUtf8())
            assertFalse(code.toString().contains("private-code"))
        }

    @Test fun pollsMapProviderResponsesAndSendDeviceGrant() =
        runBlocking {
            enqueue("""{"error":"authorization_pending"}""")
            assertEquals(TokenPoll.Pending, api.poll("private-code"))
            assertEquals(
                "client_id=app-id&device_code=private-code&grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Adevice_code",
                server.takeRequest().body.readUtf8(),
            )
            enqueue("""{"error":"slow_down","interval":10}""")
            assertEquals(TokenPoll.SlowDown(10), api.poll("private-code"))
            enqueue("""{"error":"expired_token"}""")
            assertEquals(TokenPoll.Expired, api.poll("private-code"))
            enqueue("""{"error":"access_denied","error_description":"private server message"}""")
            assertProblem(AuthenticationProblem.DENIED) { api.poll("private-code") }
        }

    @Test fun tokenExpiryAndRefreshAreParsedWithoutClientSecret() =
        runBlocking {
            enqueue(
                """{"access_token":"access","token_type":"bearer","expires_in":3600,"refresh_token":"refresh","refresh_token_expires_in":7200}""",
            )
            val tokens = api.refresh("old-refresh")
            assertEquals(3_601_000L, tokens.expiresAtMillis)
            assertEquals(7_201_000L, tokens.refreshExpiresAtMillis)
            assertEquals(
                "client_id=app-id&grant_type=refresh_token&refresh_token=old-refresh",
                server.takeRequest().body.readUtf8(),
            )
            assertEquals("GitHubTokens(REDACTED)", tokens.toString())
        }

    @Test fun accountUsesBearerHeaderAndValidatesIdentity() =
        runBlocking {
            enqueue("""{"id":42,"login":"driver"}""")
            assertEquals(42L, api.account("private-token").id)
            val request = server.takeRequest()
            assertEquals("/user", request.path)
            assertEquals("Bearer private-token", request.getHeader("Authorization"))
            assertFalse(request.path!!.contains("private-token"))
        }

    @Test fun accountRecoversFromTransientServerFailure() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(503))
            enqueue("""{"id":42,"login":"driver"}""")

            assertEquals(42L, api.account("private-token").id)
            assertEquals(2, server.requestCount)
            repeat(2) {
                val request = server.takeRequest()
                assertEquals("GET", request.method)
                assertEquals("/user", request.path)
                assertEquals("Bearer private-token", request.getHeader("Authorization"))
            }
        }

    @Test fun accountRecoversFromInterruptedResponseBody() =
        runBlocking {
            server.enqueue(
                MockResponse().setBody("x".repeat(1024)).setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY),
            )
            enqueue("""{"id":42,"login":"driver"}""")

            assertEquals(42L, api.account("private-token").id)
            assertEquals(2, server.requestCount)
        }

    @Test fun accountStopsAfterThreeTransientFailures() =
        runBlocking {
            repeat(3) { server.enqueue(MockResponse().setResponseCode(503)) }
            enqueue("""{"id":42,"login":"driver"}""")

            assertProblem(AuthenticationProblem.NETWORK) { api.account("private-token") }
            assertEquals(3, server.requestCount)
        }

    @Test fun accountAllowsProviderDirectedZeroWaitGetFollowup() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "0"))
            enqueue("""{"id":42,"login":"driver"}""")

            assertEquals(42L, api.account("private-token").id)
            assertEquals(2, server.requestCount)
        }

    @Test fun accountBoundsWireRequestsIncludingProviderDirectedZeroWaitFollowups() =
        runBlocking {
            repeat(3) {
                server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "0"))
                server.enqueue(MockResponse().setResponseCode(503))
            }
            enqueue("""{"id":42,"login":"driver"}""")

            assertProblem(AuthenticationProblem.NETWORK) { api.account("private-token") }
            assertEquals(6, server.requestCount)
        }

    @Test fun accountRateLimitWithoutRetryAfterIsNotRetried() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(429))
            enqueue("""{"id":42,"login":"driver"}""")

            assertProblem(AuthenticationProblem.NETWORK) { api.account("private-token") }
            assertEquals(1, server.requestCount)
        }

    @Test fun cancellationStopsIdentityRecoveryBeforeAnotherRequest() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(503))
            enqueue("""{"id":42,"login":"driver"}""")
            val pending = async(Dispatchers.Default) { api.account("private-token") }
            assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
            pending.cancelAndJoin()
            delay(400)

            assertTrue(pending.isCancelled)
            assertEquals(1, server.requestCount)
        }

    @Test fun deviceCodeRequestIsNotReplayedAfterServerFailure() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "0"))
            enqueue(
                """{"device_code":"private-code","user_code":"ABCD-EFGH","verification_uri":"https://github.com/login/device","expires_in":900,"interval":5}""",
            )

            assertProblem(AuthenticationProblem.NETWORK) { api.requestCode() }
            assertEquals(1, server.requestCount)
        }

    @Test fun refreshRequestIsNotReplayedAfterServerFailure() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "0"))
            enqueue("""{"access_token":"replacement","token_type":"bearer"}""")

            assertProblem(AuthenticationProblem.NETWORK) { api.refresh("rotating-refresh-token") }
            assertEquals(1, server.requestCount)
        }

    @Test fun invalidOrMaliciousProviderPayloadNeverBecomesAuthentication() =
        runBlocking {
            enqueue(
                """{"device_code":"private","user_code":"ABCD-EFGH","verification_uri":"https://attacker.example","expires_in":900,"interval":5}""",
            )
            assertProblem(AuthenticationProblem.PROVIDER) { api.requestCode() }
            enqueue("""{"access_token":"private","token_type":"mac"}""")
            assertProblem(AuthenticationProblem.PROVIDER) { api.poll("private") }
            enqueue("""{"access_token":"private","token_type":"bearer","expires_in":-1}""")
            assertProblem(AuthenticationProblem.PROVIDER) { api.poll("private") }
            enqueue("not json private-token")
            assertProblem(AuthenticationProblem.PROVIDER) { api.account("private-token") }
        }

    @Test fun statusCodesDistinguishRevocationFromOutageAndForbidden() =
        runBlocking {
            for ((code, problem) in listOf(
                401 to AuthenticationProblem.REAUTHENTICATION,
                403 to AuthenticationProblem.PROVIDER,
                408 to AuthenticationProblem.NETWORK,
                429 to AuthenticationProblem.NETWORK,
                500 to AuthenticationProblem.NETWORK,
                502 to AuthenticationProblem.NETWORK,
                503 to AuthenticationProblem.NETWORK,
                504 to AuthenticationProblem.NETWORK,
            )) {
                server.enqueue(
                    MockResponse().setResponseCode(code).setHeader("Retry-After", "1").setBody("private details"),
                )
                assertProblem(problem) { api.account("private-token") }
            }
            assertEquals(8, server.requestCount)
        }

    @Test fun redirectsAreNotFollowed() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", server.url("/leak")))
            assertProblem(AuthenticationProblem.PROVIDER) { api.account("private-token") }
            assertEquals(1, server.requestCount)
        }

    private fun enqueue(body: String) {
        server.enqueue(MockResponse().setBody(body).setHeader("Content-Type", "application/json"))
    }

    private suspend fun assertProblem(
        expected: AuthenticationProblem,
        block: suspend () -> Unit,
    ) {
        try {
            block()
            fail("Expected authentication failure")
        } catch (error: AuthenticationException) {
            assertEquals(expected, error.problem)
            assertEquals(expected.name, error.message)
        }
    }
}
