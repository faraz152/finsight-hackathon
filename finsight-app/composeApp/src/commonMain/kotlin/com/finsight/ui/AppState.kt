package com.finsight.ui

import com.finsight.ai.CloudLlm
import com.finsight.coach.*
import com.finsight.data.Repository
import com.finsight.domain.Channel
import com.finsight.domain.RawInput
import kotlinx.coroutines.flow.MutableStateFlow

/** Manual DI for the hackathon (Koin is roadmap). */
class AppState(
    val cloud: CloudLlm,
    val isOnline: () -> Boolean,
    val now: () -> Long,
    val openNotificationSettings: () -> Unit,
    val hasNotificationAccess: () -> Boolean,
    val loadDemo: () -> List<RawInput>,
) {
    val repo = Repository()
    val tools = Tools({ repo.transactions.value }, now)
    val log = CoachLog()
    val cloudEnabled = MutableStateFlow(true)
    val profile = MutableStateFlow(Profile.SALARIED)
    val coach = CoachAgent(repo, tools, cloud, log, isOnline, { cloudEnabled.value }, { profile.value }, now)

    fun quickAdd(line: String) = repo.ingest(RawInput(Channel.MANUAL, "You", null, line, now()))
}
