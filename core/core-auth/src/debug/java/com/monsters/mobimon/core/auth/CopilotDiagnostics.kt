package com.monsters.mobimon.core.auth

import android.util.Log
import com.monsters.mobimon.core.domain.ManualReplyRejection

/** Fixed metadata only: never accept credentials, headers, prompts, or provider response bodies. */
internal object CopilotDiagnostics {
    fun replyShape(
        stopped: Boolean,
        toolBatch: Boolean,
        validAssistant: Boolean,
        legacyCall: Boolean,
        stringContent: Boolean,
        objectContent: Boolean,
        arrayContent: Boolean,
        nullContent: Boolean,
        refusalPresent: Boolean,
        emptyContent: Boolean,
        toolCallsAbsent: Boolean,
        emptyToolCalls: Boolean,
    ) {
        Log.i(
            "MobiMonCopilot",
            "replyShape stopped=$stopped toolBatch=$toolBatch validAssistant=$validAssistant legacyCall=$legacyCall " +
                "stringContent=$stringContent emptyContent=$emptyContent " +
                "objectContent=$objectContent arrayContent=$arrayContent " +
                "nullContent=$nullContent refusalPresent=$refusalPresent " +
                "toolCallsAbsent=$toolCallsAbsent emptyToolCalls=$emptyToolCalls",
        )
    }

    fun replyCorrection(reason: ManualReplyRejection? = null) {
        Log.i("MobiMonCopilot", "replyCorrection attempted=true manualReason=${reason ?: "NONE"}")
    }

    fun replyContract(
        versionPresent: Boolean,
        vehicleRefsPresent: Boolean,
        sourceIdsPresent: Boolean,
        evidenceFreeStatus: Boolean,
        knownStatus: Boolean,
    ) {
        Log.i(
            "MobiMonCopilot",
            "replyContract versionPresent=$versionPresent vehicleRefsPresent=$vehicleRefsPresent " +
                "sourceIdsPresent=$sourceIdsPresent evidenceFreeStatus=$evidenceFreeStatus knownStatus=$knownStatus",
        )
    }

    fun http(
        stage: CopilotRequestStage,
        status: Int,
    ) {
        Log.i("MobiMonCopilot", "stage=$stage status=$status")
    }

    fun rejection(reason: CopilotRejection) {
        Log.i("MobiMonCopilot", "rejection=$reason")
    }

    fun models(
        total: Int,
        compatible: Int,
    ) {
        Log.i("MobiMonCopilot", "stage=MODELS total=$total compatible=$compatible")
    }
}
