package com.monsters.mobimon.core.auth

import com.monsters.mobimon.core.domain.ManualReplyRejection

@Suppress("UNUSED_PARAMETER")
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
    ) = Unit

    fun replyCorrection(reason: ManualReplyRejection? = null) = Unit

    fun replyContract(
        versionPresent: Boolean,
        vehicleRefsPresent: Boolean,
        sourceIdsPresent: Boolean,
        evidenceFreeStatus: Boolean,
        knownStatus: Boolean,
    ) = Unit

    fun http(
        stage: CopilotRequestStage,
        status: Int,
    ) = Unit

    fun rejection(reason: CopilotRejection) = Unit

    fun models(
        total: Int,
        compatible: Int,
    ) = Unit
}
