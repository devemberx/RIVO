package com.monsters.mobimon.service

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

/** Release has no temporary parking control or simulated vehicle writes. */
@Suppress("UNUSED_PARAMETER")
class TemporaryParkingControl
    @Inject
    constructor() {
        fun attach(
            context: Context,
            scope: CoroutineScope,
        ) = Unit

        fun setVisible(visible: Boolean) = Unit

        fun detach() = Unit
    }
