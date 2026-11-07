package com.finsight.android

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.finsight.BuildConfig
import com.finsight.domain.Channel
import com.finsight.domain.RawInput

/**
 * Automatic capture. Only allowlisted packages are read; everything else is ignored in memory and never stored.
 * No READ_SMS permission: we only see notifications the user already receives.
 */
class NotificationCaptureService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName
        val allowed = pkg in SMS_PACKAGES || pkg in FINANCE_PACKAGES || (BuildConfig.DEBUG && pkg == "com.android.shell")
        if (!allowed || pkg == packageName) return
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString() ?: return
        val r = FinSightApp.instance.state.repo.ingest(RawInput(Channel.NOTIFICATION, pkg, title, text, sbn.postTime))
        Log.d("FinSight", "captured from $pkg → $r")
    }

    companion object {
        // From the old FinSight BankNotificationService.kt (SMS apps by OEM).
        val SMS_PACKAGES = setOf(
            "com.google.android.apps.messaging", "com.samsung.android.messaging", "com.android.messaging", "com.android.mms",
            "com.oneplus.mms", "com.miui.smsextra", "com.coloros.sms", "com.huawei.message", "com.sonyericsson.conversations",
            "com.lenovo.mms", "com.motorola.messaging", "com.asus.message", "com.vivo.mms", "com.transsion.message",
            "com.android.messaging.transsion",
        )
        // Verify on the phone: adb shell pm list packages | grep -i -E 'jazz|easypaisa|hbl|meezan|ubl|mcb'
        val FINANCE_PACKAGES = setOf(
            "com.techlogix.mobilinkcustomer", "pk.com.telenor.phoenix", "com.hbl.android.hblmobilebanking",
            "invo8.meezan.mb", "app.com.brd", "com.ubl.digital", "com.mcb.mcblive",
        )
    }
}
