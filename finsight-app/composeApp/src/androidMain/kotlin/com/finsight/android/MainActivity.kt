package com.finsight.android

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.finsight.domain.Channel
import com.finsight.domain.RawInput
import com.finsight.pipeline.PipelineResult
import com.finsight.ui.App

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleShare(intent)
        setContent { App(FinSightApp.instance.state) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    /** Share-in channel: text shared from any app goes through the same pipeline. */
    private fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        val r = FinSightApp.instance.state.repo.ingest(RawInput(Channel.SHARE, "share", null, text, System.currentTimeMillis()))
        val msg = when (r) {
            is PipelineResult.Accepted -> "Added ${r.txn.amount} → ${r.txn.category.label}"
            is PipelineResult.Dropped -> "Not a transaction: ${r.reason}"
            is PipelineResult.ScamWarning -> "Warning: ${r.reason}"
        }
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }
}
