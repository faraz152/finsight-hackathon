package com.finsight.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.finsight.data.TestSample
import com.finsight.data.TestSamples
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicInteger

/**
 * Test Lab: posts REAL bank-style notifications on this phone (channel [CHANNEL_ID]).
 * NotificationCaptureService accepts our own package only on this channel, so they travel the same path as a real alert:
 * NotificationManager → listener → Repository.ingest → pipeline.
 */
class TestNotifier(private val context: Context) {
    private val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val counter = AtomicInteger(0)

    init {
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Test bank alerts", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Fake bank / wallet alerts fired by FinSight Test Lab" },
        )
    }

    /** Android 13+ needs POST_NOTIFICATIONS; older versions only need notifications enabled for the app. */
    fun canPost(): Boolean {
        val granted = Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return granted && nm.areNotificationsEnabled()
    }

    /** Posts [sample] [TestSample.copies] times with one fresh TID (copies share it, so a duplicate pair merges). */
    fun send(sample: TestSample, tid: String = TestSamples.randomTid()) {
        val text = sample.render(tid)
        repeat(sample.copies) { post(sample.sender, text) }
    }

    suspend fun sendBurst(samples: List<TestSample> = TestSamples.all, delayMs: Long = 1500) {
        samples.forEachIndexed { i, s ->
            if (i > 0) delay(delayMs)
            send(s)
        }
    }

    private fun post(sender: String, text: String) {
        // Rolling ids: distinct notifications, but never more than SLOTS of ours on screen (Android caps an app at 50).
        val id = NOTIFICATION_ID_BASE + counter.getAndIncrement() % SLOTS
        val n = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(sender)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(id, n) }.onFailure { Log.w("FinSight", "Test Lab post failed", it) }
    }

    companion object {
        const val CHANNEL_ID = "finsight_test_bank"
        const val POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS"
        private const val NOTIFICATION_ID_BASE = 7_000
        private const val SLOTS = 40
    }
}
