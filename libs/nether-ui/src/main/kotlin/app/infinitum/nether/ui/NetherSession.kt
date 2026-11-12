package app.infinitum.nether.ui

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import app.infinitum.nether.core.VaultDocument

object NetherSession {
    @Volatile var key: ByteArray? = null
    @Volatile var document: VaultDocument? = null
    @Volatile var decoy: Boolean = false
    var generation by mutableIntStateOf(0)
        private set

    fun unlock(document: VaultDocument, key: ByteArray, decoy: Boolean) {
        this.key?.fill(0)
        this.document = document
        this.key = key
        this.decoy = decoy
        generation++
    }

    fun lock() {
        if (key == null && document == null) return
        key?.fill(0)
        key = null
        document = null
        decoy = false
        generation++
    }

    val unlocked: Boolean get() = key != null && document != null
}

object NetherClipboard {
    private val handler = Handler(Looper.getMainLooper())
    private var clear: Runnable? = null

    fun copy(context: Context, text: String) {
        val manager = context.getSystemService(ClipboardManager::class.java) ?: return
        val clip = ClipData.newPlainText("Nether", text)
        if (Build.VERSION.SDK_INT >= 33) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        manager.setPrimaryClip(clip)
        clear?.let(handler::removeCallbacks)
        val job = Runnable {
            if (Build.VERSION.SDK_INT >= 28) {
                manager.clearPrimaryClip()
            } else {
                manager.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        }
        clear = job
        handler.postDelayed(job, 30_000)
    }
}

/** Locks the vault as soon as the screen turns off. Registered once per process. */
private object ScreenOffLock {
    @Volatile private var registered = false

    fun register(context: Context) {
        if (registered) return
        synchronized(this) {
            if (registered) return
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (intent.action == Intent.ACTION_SCREEN_OFF) NetherSession.lock()
                }
            }
            ContextCompat.registerReceiver(
                context.applicationContext,
                receiver,
                IntentFilter(Intent.ACTION_SCREEN_OFF),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            registered = true
        }
    }
}

/** Locks 60 seconds after Nether leaves the screen, and at once when the screen turns off. */
fun FragmentActivity.bindNetherLock(timeoutMs: Long = 60_000L) {
    ScreenOffLock.register(this)
    val handler = Handler(Looper.getMainLooper())
    val lock = Runnable { NetherSession.lock() }
    lifecycle.addObserver(object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            handler.removeCallbacks(lock)
        }

        override fun onStop(owner: LifecycleOwner) {
            handler.postDelayed(lock, timeoutMs)
        }
    })
}
