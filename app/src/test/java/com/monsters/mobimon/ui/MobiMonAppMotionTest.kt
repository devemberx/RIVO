package com.monsters.mobimon.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.monsters.mobimon.core.domain.AppUseState
import com.monsters.mobimon.core.domain.Clock
import com.monsters.mobimon.core.domain.CompanionSettings
import com.monsters.mobimon.core.domain.ConversationProvider
import com.monsters.mobimon.core.domain.ConversationResult
import com.monsters.mobimon.core.domain.ConversationTurn
import com.monsters.mobimon.core.domain.CosmeticInventory
import com.monsters.mobimon.core.domain.CosmeticItem
import com.monsters.mobimon.core.domain.DrivingState
import com.monsters.mobimon.core.domain.EquipResult
import com.monsters.mobimon.core.domain.GitHubAuthentication
import com.monsters.mobimon.core.domain.GitHubSession
import com.monsters.mobimon.core.domain.GitHubSignIn
import com.monsters.mobimon.core.domain.PointAwardResult
import com.monsters.mobimon.core.domain.PointEconomy
import com.monsters.mobimon.core.domain.PointWallet
import com.monsters.mobimon.core.domain.ProgressionIdentity
import com.monsters.mobimon.core.domain.PurchaseResult
import com.monsters.mobimon.core.domain.SettingsRepository
import com.monsters.mobimon.core.domain.SignalQuality
import com.monsters.mobimon.core.domain.SignalSource
import com.monsters.mobimon.core.domain.VehicleFreshnessPolicy
import com.monsters.mobimon.core.domain.VehicleRepository
import com.monsters.mobimon.core.domain.VehicleSnapshot
import com.monsters.mobimon.core.domain.WriteResult
import com.monsters.mobimon.core.navigation.AppRoute
import com.monsters.mobimon.core.navigation.FeatureEntry
import com.monsters.mobimon.core.navigation.FeatureNavigator
import com.monsters.mobimon.core.presentation.CompanionAppearancePresentation
import com.monsters.mobimon.core.presentation.VehiclePresentation
import com.monsters.mobimon.core.ui.LocalMobiMonMotionEnabled
import com.monsters.mobimon.runtime.AppUseStateSource
import com.monsters.mobimon.testing.ComposeTestApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = ComposeTestApplication::class, qualifiers = "ko-rKR-w2560dp-h1184dp-mdpi")
class MobiMonAppMotionTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun floatingCompanionMotionPreferenceDoesNotDisableAppScreenMotion() {
        val settings =
            object : SettingsRepository {
                override val settings = flowOf(CompanionSettings(reducedMotion = true))

                override suspend fun setReducedMotion(enabled: Boolean) = WriteResult.Success

                override suspend fun setLauncherCharacterEnabled(enabled: Boolean) = WriteResult.Success

                override suspend fun setDebugModeEnabled(enabled: Boolean) = WriteResult.Success
            }
        val appUse =
            object : AppUseStateSource {
                override val states = MutableStateFlow(AppUseState.ALLOWED)

                override fun state() = states.value

                override fun start() = Unit

                override fun stop() = Unit
            }
        val points =
            object : PointEconomy {
                override val wallet = flowOf(PointWallet(0))
                override val inventory = flowOf(CosmeticInventory(emptySet(), emptyMap()))
                override val catalog = flowOf(emptyList<CosmeticItem>())

                override suspend fun purchase(
                    itemId: String,
                    expectedPrice: Long,
                ): PurchaseResult = error("Not used")

                override suspend fun equip(itemId: String): EquipResult = error("Not used")

                override suspend fun awardQuest(
                    questId: String,
                    displayedSnapshot: VehicleSnapshot,
                ): PointAwardResult = error("Not used")
            }
        val authentication =
            object : GitHubAuthentication {
                override val session = MutableStateFlow<GitHubSession>(GitHubSession.SignedOut)
                override val configured = false

                override suspend fun restore() = Unit

                override fun signIn() = flowOf<GitHubSignIn>(GitHubSignIn.Requesting)

                override suspend fun disconnect() = Unit
            }
        val conversation =
            object : ConversationProvider {
                override suspend fun connect(accountId: Long): ConversationResult<String> = error("Not used")

                override suspend fun reply(
                    accountId: Long,
                    conversationId: String,
                    friendId: String,
                    messages: List<ConversationTurn>,
                ): ConversationResult<String> = error("Not used")
            }
        val vehicle =
            object : VehicleRepository {
                override val snapshots =
                    MutableStateFlow(
                        VehicleSnapshot(
                            "unavailable",
                            "motion-test",
                            0,
                            0,
                            SignalSource.REAL,
                            DrivingState.UNKNOWN,
                            SignalQuality.UNAVAILABLE,
                        ),
                    )

                override fun start() = Unit

                override fun stop() = Unit
            }
        val vehiclePresentation =
            VehiclePresentation(
                vehicle,
                ProgressionIdentity("motion-test", SignalSource.REAL),
                Clock { 0L },
                VehicleFreshnessPolicy(15_000),
            )
        val home =
            object : FeatureEntry {
                override val routes = AppRoute.entries.toSet()

                @Composable
                override fun Content(
                    route: AppRoute,
                    navigator: FeatureNavigator,
                    modifier: Modifier,
                ) {
                    Text(if (LocalMobiMonMotionEnabled.current) "app motion enabled" else "app motion disabled")
                }
            }

        compose.setContent {
            MobiMonApp(
                setOf(home),
                appUse,
                CompanionAppearancePresentation(points),
                settings,
                authentication,
                conversation,
                vehiclePresentation,
            )
        }

        compose.onNodeWithText("app motion enabled").assertExists()
    }
}
