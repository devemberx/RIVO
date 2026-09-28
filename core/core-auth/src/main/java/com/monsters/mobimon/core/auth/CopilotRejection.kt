package com.monsters.mobimon.core.auth

import org.json.JSONObject

/** Provider error text never crosses into logs or UI. */
internal enum class CopilotRejection {
    APP_PERMISSION,
    NOT_SIGNED_UP,
    NO_ACCESS,
    POLICY,
    RATE_LIMIT,
    NO_ELIGIBLE_MODELS,
    UNSUPPORTED_INTEGRATION,
    NO_HEALTHY_UPSTREAM,
    CONTEXT_LIMIT,
    UNKNOWN,
    ;

    companion object {
        fun from(error: JSONObject?): CopilotRejection {
            val id = error?.optJSONObject("error_details")?.optString("notification_id")
            val detail = error?.optJSONObject("error")
            val message =
                listOf(
                    error?.optString("message"),
                    error?.opt("error") as? String,
                    error?.optString("code"),
                    detail?.optString("message"),
                    detail?.optString("code"),
                ).filterNotNull().joinToString(" ").lowercase().replace('_', ' ')
            return when {
                listOf(
                    "context length exceeded",
                    "context window exceeded",
                    "maximum context length",
                    "prompt too long",
                ).any { it in message } -> CONTEXT_LIMIT
                "no healthy upstream" in message -> NO_HEALTHY_UPSTREAM
                listOf("no eligible model", "no available model", "no models available", "no model available")
                    .any { it in message } -> NO_ELIGIBLE_MODELS
                "integration" in message &&
                    listOf("unsupported", "not supported", "not allowed", "unknown", "invalid")
                        .any { it in message } -> UNSUPPORTED_INTEGRATION
                id == "not_signed_up" || error?.optBoolean("can_signup_for_limited") == true -> NOT_SIGNED_UP
                id in
                    listOf(
                        "no_copilot_access",
                        "subscription_ended",
                        "access_revoked",
                        "expired_coupon",
                        "revoked_coupon",
                    ) -> NO_ACCESS
                id in
                    listOf(
                        "feature_flag_blocked",
                        "spammy_user",
                        "billing_locked",
                        "trade_restricted",
                        "trade_restricted_country",
                        "enterprise_managed_user_account",
                        "programmatic_token_generation",
                    ) -> POLICY
                "resource not accessible" in message -> APP_PERMISSION
                "rate limit" in message -> RATE_LIMIT
                else -> UNKNOWN
            }
        }
    }
}
