package com.finsight.android

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings
import com.finsight.BuildConfig
import com.finsight.domain.Channel
import com.finsight.domain.RawInput
import com.finsight.pipeline.GemmaReviewer
import com.finsight.ui.AppState
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject

class FinSightApp : Application() {
    lateinit var state: AppState
        private set
    lateinit var localLlm: LiteRtLlm
        private set
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, t ->
        android.util.Log.w("FinSightApp", "background task failed", t)
    })

    override fun onCreate() {
        super.onCreate()
        instance = this
        state = AppState(
            cloud = GeminiCloudLlm(BuildConfig.GEMINI_API_KEY, BuildConfig.GEMINI_MODEL),
            isOnline = ::isOnline,
            now = { System.currentTimeMillis() },
            openNotificationSettings = {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            },
            hasNotificationAccess = ::hasNotificationAccess,
            loadDemo = ::loadDemo,
            store = FileStore(this),
        )
        startGemma()
    }

    /** On-device Gemma second opinion for uncertain transactions. No model file → nothing happens (rules-only). */
    private fun startGemma() {
        localLlm = LiteRtLlm(this)
        if (!localLlm.modelFile.exists()) return
        appScope.launch {
            if (localLlm.warmUp()) GemmaReviewer(state.repo, localLlm).start(this)
        }
    }

    private fun isOnline(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun hasNotificationAccess(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
        val me = ComponentName(this, NotificationCaptureService::class.java).flattenToString()
        return flat.split(":").any { it == me }
    }

    /** Demo Replay: the 58 labelled templates, spread over the last ~60 days so month comparisons work. */
    private fun loadDemo(): List<RawInput> {
        val json = assets.open("sms_test_templates.json").bufferedReader().use { it.readText() }
        val arr = JSONObject(json).getJSONArray("templates")
        val now = System.currentTimeMillis()
        val day = 86_400_000L
        val out = mutableListOf<RawInput>()
        for (i in 0 until arr.length()) {
            val t = arr.getJSONObject(i)
            out += RawInput(Channel.DEMO, t.optString("bank", "demo"), t.optString("sender"), t.getString("message"), now - (i * 59L / arr.length()) * day - (i % 7) * 3_600_000L)
        }
        // Extra PK records so Health, Pulse and recurring bills have something real to show.
        val extra = listOf(
            2 to "Salary of Rs 185,000.00 credited to your A/C **4821 by ACME TECH PVT LTD. Avail Bal: Rs 212,450.00",
            32 to "Salary of Rs 185,000.00 credited to your A/C **4821 by ACME TECH PVT LTD.",
            5 to "Rs 8,450.00 paid to K-Electric bill via HBL Mobile from A/C **4821. Ref: KE88231190",
            35 to "Rs 7,920.00 paid to K-Electric bill via HBL Mobile from A/C **4821. Ref: KE77120944",
            9 to "Rs 2,999.00 paid to Nayatel internet from A/C **4821. Ref: NY55120931",
            39 to "Rs 2,999.00 paid to Nayatel internet from A/C **4821. Ref: NY44120877",
            1 to "Rs 3,450.00 spent at Foodpanda using card **7710 on 06-Nov-26. Avail Bal: Rs 61,750.00",
            3 to "Rs 2,890.00 spent at KFC Gulberg using card **7710. Avail Bal: Rs 65,200.00",
            6 to "Rs 4,100.00 spent at Cheezious using card **7710. Avail Bal: Rs 68,740.00",
            8 to "Rs 1,850.00 spent at Foodpanda using card **7710. Avail Bal: Rs 71,640.00",
            38 to "Rs 1,600.00 spent at Foodpanda using card **7710. Avail Bal: Rs 88,000.00",
            4 to "Rs 650.00 paid to Careem ride using card **7710. Avail Bal: Rs 68,090.00",
            12 to "Rs 15,000.00 sent to SAJID committee via Raast from A/C **4821. Ref: RA99120034",
            42 to "Rs 15,000.00 sent to SAJID committee via Raast from A/C **4821. Ref: RA99020011",
            7 to "Rs 1,200.00 refund received from Daraz to card **7710. Avail Bal: Rs 72,840.00",
            10 to "Your OTP for transaction is 482913. Do not share it with anyone.",
            11 to "Dear customer your account will be blocked. Click link bit.ly/hbl-kyc to update your KYC",
            13 to "Rs 450.00 SMS charges and excise duty deducted from A/C **4821.",
        )
        extra.forEach { (d, msg) -> out += RawInput(Channel.DEMO, "HBL", "HBL", msg, now - d * day - 5_400_000L) }
        // The same Foodpanda payment arriving twice (SMS + app notification) → integrity merges it.
        out += RawInput(Channel.NOTIFICATION, "Foodpanda", null, "Rs 3,450.00 spent at Foodpanda using card **7710 on 06-Nov-26. Avail Bal: Rs 61,750.00", now - 1 * day - 5_400_000L + 60_000)
        return out
    }

    companion object {
        lateinit var instance: FinSightApp
    }
}
