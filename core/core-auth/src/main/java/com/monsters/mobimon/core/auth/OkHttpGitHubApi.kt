package com.monsters.mobimon.core.auth

import com.monsters.mobimon.core.domain.AuthenticationProblem
import com.monsters.mobimon.core.domain.GitHubAccount
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class OkHttpGitHubApi(
    private val client: OkHttpClient,
    private val clientId: String,
    private val nowMillis: () -> Long,
    private val oauthBase: HttpUrl = "https://github.com/".toHttpUrl(),
    private val apiBase: HttpUrl = "https://api.github.com/".toHttpUrl(),
) : GitHubApi {
    override suspend fun requestCode(): DeviceAuthorization {
        val json = post("login/device/code", "scope" to "read:user")
        rejectError(json)
        val uri = json.requiredString("verification_uri")
        // QR and address help must lead only to GitHub's documented approval page.
        if (uri != "https://github.com/login/device") fail(AuthenticationProblem.PROVIDER)
        return DeviceAuthorization(
            json.requiredString("device_code"),
            json.requiredString("user_code"),
            uri,
            json.positiveInt("expires_in", 3600),
            json.positiveInt("interval", 300),
        )
    }

    override suspend fun poll(deviceCode: String): TokenPoll {
        val json =
            post(
                "login/oauth/access_token",
                "device_code" to deviceCode,
                "grant_type" to "urn:ietf:params:oauth:grant-type:device_code",
            )
        return when (json.optString("error")) {
            "authorization_pending" -> TokenPoll.Pending
            "slow_down" -> TokenPoll.SlowDown(json.optionalPositiveInt("interval", 3600))
            "expired_token", "token_expired" -> TokenPoll.Expired
            else -> {
                rejectError(json)
                TokenPoll.Approved(tokens(json))
            }
        }
    }

    override suspend fun refresh(refreshToken: String): GitHubTokens {
        val json = post("login/oauth/access_token", "grant_type" to "refresh_token", "refresh_token" to refreshToken)
        rejectError(json)
        return tokens(json)
    }

    override suspend fun account(accessToken: String): GitHubAccount {
        val json =
            request(
                Request
                    .Builder()
                    .url(apiBase.resolve("user")!!)
                    .header("Authorization", "Bearer $accessToken")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .get(),
            )
        val id = json.optLong("id", -1)
        if (id <= 0) fail(AuthenticationProblem.PROVIDER)
        return GitHubAccount(id, json.requiredString("login"))
    }

    private fun tokens(json: JSONObject): GitHubTokens {
        if (!json.optString("token_type").equals("bearer", ignoreCase = true)) fail(AuthenticationProblem.PROVIDER)
        val now = nowMillis()
        val expiry = json.optionalPositiveInt("expires_in", Int.MAX_VALUE)?.let { now + it * 1000L }
        val refresh = if (json.has("refresh_token")) json.requiredString("refresh_token") else null
        val refreshExpiry =
            json
                .optionalPositiveInt(
                    "refresh_token_expires_in",
                    Int.MAX_VALUE,
                )?.let { now + it * 1000L }
        return GitHubTokens(json.requiredString("access_token"), expiry, refresh, refreshExpiry)
    }

    private suspend fun post(
        path: String,
        vararg fields: Pair<String, String>,
    ): JSONObject {
        val body = FormBody.Builder().add("client_id", clientId)
        fields.forEach { (name, value) -> body.add(name, value) }
        return request(Request.Builder().url(oauthBase.resolve(path)!!).post(body.build()))
    }

    private suspend fun request(builder: Request.Builder): JSONObject =
        suspendCancellableCoroutine { continuation ->
            val call =
                client.newCall(
                    builder.header("Accept", "application/json").header("User-Agent", "MobiMon").build(),
                )
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(
                        call: Call,
                        e: IOException,
                    ) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(
                                AuthenticationException(AuthenticationProblem.NETWORK),
                            )
                        }
                    }

                    override fun onResponse(
                        call: Call,
                        response: Response,
                    ) {
                        try {
                            val json =
                                response.use {
                                    when (it.code) {
                                        401 -> fail(AuthenticationProblem.REAUTHENTICATION)
                                        408, 429 -> fail(AuthenticationProblem.NETWORK)
                                    }
                                    if (it.code >= 500) fail(AuthenticationProblem.NETWORK)
                                    if (!it.isSuccessful) fail(AuthenticationProblem.PROVIDER)
                                    val source = it.body?.source() ?: fail(AuthenticationProblem.PROVIDER)
                                    if (source.request(65_537)) fail(AuthenticationProblem.PROVIDER)
                                    JSONObject(source.readUtf8())
                                }
                            if (continuation.isActive) continuation.resume(json)
                        } catch (error: Exception) {
                            val safeError =
                                when (error) {
                                    is AuthenticationException -> error
                                    is IOException -> AuthenticationException(AuthenticationProblem.NETWORK)
                                    else -> AuthenticationException(AuthenticationProblem.PROVIDER)
                                }
                            if (continuation.isActive) continuation.resumeWithException(safeError)
                        }
                    }
                },
            )
        }

    private fun rejectError(json: JSONObject) {
        when (json.optString("error")) {
            "" -> Unit
            "access_denied" -> fail(AuthenticationProblem.DENIED)
            "bad_refresh_token", "invalid_grant", "expired_token" -> fail(AuthenticationProblem.REAUTHENTICATION)
            "incorrect_client_credentials", "device_flow_disabled", "unsupported_grant_type" ->
                fail(
                    AuthenticationProblem.CONFIGURATION,
                )
            else -> fail(AuthenticationProblem.PROVIDER)
        }
    }

    private fun JSONObject.requiredString(name: String): String {
        val value = opt(name) as? String ?: fail(AuthenticationProblem.PROVIDER)
        if (value.isBlank() ||
            value.length > 8192 ||
            value.any { it.isISOControl() }
        ) {
            fail(AuthenticationProblem.PROVIDER)
        }
        return value
    }

    private fun JSONObject.positiveInt(
        name: String,
        max: Int,
    ): Int = optionalPositiveInt(name, max) ?: fail(AuthenticationProblem.PROVIDER)

    private fun JSONObject.optionalPositiveInt(
        name: String,
        max: Int,
    ): Int? {
        if (!has(name)) return null
        val value =
            try {
                getLong(name)
            } catch (_: JSONException) {
                fail(AuthenticationProblem.PROVIDER)
            }
        if (value !in 1..max.toLong()) fail(AuthenticationProblem.PROVIDER)
        return value.toInt()
    }

    private fun fail(problem: AuthenticationProblem): Nothing = throw AuthenticationException(problem)
}
