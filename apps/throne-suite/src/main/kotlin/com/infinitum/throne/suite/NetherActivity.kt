package com.infinitum.throne.suite

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.infinitum.throne.ui.ThroneButton
import com.infinitum.throne.ui.ThroneInk
import com.infinitum.throne.ui.ThroneMuted
import com.infinitum.throne.ui.ThronePaper
import com.infinitum.throne.ui.ThroneTheme

/**
 * Suite's Nether section. Suite has INTERNET for its web shelves, so it never decrypts
 * the vault itself: it hands off to the standalone Nether app, which has no network access.
 */
class NetherActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (openNether()) {
            finish()
            return
        }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt()),
            navigationBarStyle = SystemBarStyle.light(0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt()),
        )
        setContent {
            ThroneTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ThronePaper)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Nether", style = MaterialTheme.typography.headlineSmall, color = ThroneInk)
                    Text(
                        "Install the Nether app to open your vault. Nether keeps passwords on this phone and never connects to the internet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ThroneMuted,
                    )
                    ThroneButton(text = "Back", onClick = { finish() })
                }
            }
        }
    }

    private fun openNether(): Boolean {
        val launch = packageManager.getLaunchIntentForPackage(NETHER_PACKAGE) ?: return false
        return try {
            startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: Exception) {
            false
        }
    }

    private companion object {
        const val NETHER_PACKAGE = "app.infinitum.nether"
    }
}
