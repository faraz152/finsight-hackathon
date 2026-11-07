package com.finsight.ui

import com.finsight.ai.CloudLlm
import com.finsight.coach.*
import com.finsight.data.Repository
import com.finsight.domain.Channel
import com.finsight.domain.RawInput
import com.finsight.data.KeyValueStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop

/** Manual DI for the hackathon (Koin is roadmap). */
class AppState(
    val cloud: CloudLlm,
    val isOnline: () -> Boolean,
    val now: () -> Long,
    val openNotificationSettings: () -> Unit,
    val hasNotificationAccess: () -> Boolean,
    val loadDemo: () -> List<RawInput>,
    val store: KeyValueStore? = null,
    /** Test Lab (Privacy screen): ask for POST_NOTIFICATIONS (Android 13+). No-op off-device / in tests. */
    val requestPostNotifications: () -> Unit = {},
    /** Test Lab: post one real bank-style notification for a [com.finsight.data.TestSamples] id. */
    val sendTestNotification: (String) -> Unit = {},
    /** Test Lab: post every sample, 1.5 s apart. */
    val sendTestBurst: () -> Unit = {},
) {
    val repo = Repository(store)
    val tools = Tools({ repo.transactions.value }, now, { repo.accounts.value })
    val log = CoachLog(store)
    val cloudEnabled = MutableStateFlow(store?.read("cloud_enabled")?.toBooleanStrictOrNull() ?: true)
    val profile = MutableStateFlow(store?.read("profile")?.let { p -> Profile.entries.firstOrNull { it.name == p } } ?: Profile.SALARIED)
    /** First-launch onboarding with the prominent disclosure (plan 6.4) has been completed. */
    val onboarded = MutableStateFlow(store?.read("onboarded")?.toBooleanStrictOrNull() ?: false)
    init { if (store != null) CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
        launch { cloudEnabled.drop(1).collect { store.write("cloud_enabled", it.toString()) } }
        launch { profile.drop(1).collect { store.write("profile", it.name) } }
        launch { onboarded.drop(1).collect { store.write("onboarded", it.toString()) } }
    } }
    /** Text to pre-fill (or auto-send) in Coach, set by the centre quick actions. */
    val coachDraft = MutableStateFlow<String?>(null)
    val coach = CoachAgent(repo, tools, cloud, log, isOnline, { cloudEnabled.value }, { profile.value }, now)

    fun quickAdd(line: String) = repo.ingest(RawInput(Channel.MANUAL, "You", null, line, now()))
}
