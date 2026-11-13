package app.infinitum.nether

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.view.autofill.AutofillManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.fragment.app.FragmentActivity
import app.infinitum.nether.ui.NetherRoot
import app.infinitum.nether.ui.NetherSession
import app.infinitum.nether.ui.SharedVault
import app.infinitum.nether.ui.bindNetherLock
import com.infinitum.throne.ui.ThroneTheme

/**
 * Unlock screen for autofill. Not exported, so only Nether's own PendingIntent
 * (handed to the system autofill framework) can open it and receive its result.
 */
class AutofillUnlockActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt()),
            navigationBarStyle = SystemBarStyle.light(0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt()),
        )
        bindNetherLock()
        val store = SharedVault.forNether(this)
        setContent {
            ThroneTheme {
                NetherRoot(store)
                LaunchedEffect(NetherSession.generation) {
                    if (NetherSession.unlocked) deliver()
                }
            }
        }
    }

    private fun deliver() {
        val structure = NetherAutofillService.pending
        NetherAutofillService.pending = null
        val response = structure?.let { NetherAutofillService.responseFor(this, it) }
        if (response == null) {
            setResult(RESULT_CANCELED)
        } else {
            setResult(RESULT_OK, Intent().putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, response))
        }
        finish()
    }
}
