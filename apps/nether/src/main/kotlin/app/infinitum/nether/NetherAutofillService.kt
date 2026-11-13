package app.infinitum.nether

import android.app.PendingIntent
import android.app.assist.AssistStructure
import android.content.Intent
import android.os.Build
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveInfo
import android.service.autofill.SaveRequest
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import app.infinitum.nether.core.AutofillMatch
import app.infinitum.nether.core.AutofillSave
import app.infinitum.nether.core.NetherEntry
import app.infinitum.nether.core.NetherVault
import app.infinitum.nether.ui.NetherSession
import app.infinitum.nether.ui.SharedVault
import app.infinitum.nether.ui.VaultFiles

class NetherAutofillService : AutofillService() {
    override fun onFillRequest(request: FillRequest, cancellationSignal: CancellationSignal, callback: FillCallback) {
        val structure = request.fillContexts.lastOrNull()?.structure
        if (structure == null || structure.activityComponent?.packageName == packageName) {
            callback.onSuccess(null)
            return
        }
        val fields = collect(structure)
        if (fields.isEmpty()) {
            callback.onSuccess(null)
            return
        }
        if (!NetherSession.unlocked) {
            pending = structure
            val ids = fields.map { it.id }.toTypedArray()
            val flags = if (Build.VERSION.SDK_INT >= 31) {
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            // AutofillUnlockActivity is not exported: only this PendingIntent can open it.
            val pendingIntent = PendingIntent.getActivity(
                this,
                1,
                Intent(this, AutofillUnlockActivity::class.java),
                flags,
            )
            val response = FillResponse.Builder()
                .setAuthentication(ids, pendingIntent.intentSender, chip(this, getString(R.string.autofill_unlock)))
                .build()
            callback.onSuccess(response)
            return
        }
        callback.onSuccess(responseFor(this, structure))
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        val structure = request.fillContexts.lastOrNull()?.structure
        val key = NetherSession.key
        val document = NetherSession.document
        if (structure == null || key == null || document == null) {
            callback.onFailure(getString(R.string.autofill_save_locked))
            return
        }
        val fields = collect(structure)
        val password = fields.firstOrNull { it.kind == Kind.PASSWORD && !it.value.isNullOrEmpty() }?.value.orEmpty()
        val username = fields.firstOrNull { it.kind == Kind.USERNAME && !it.value.isNullOrEmpty() }?.value.orEmpty()
        val domain = fields.firstNotNullOfOrNull { it.webDomain }
        val app = structure.activityComponent?.packageName
        val updated = AutofillSave.apply(document, domain, app, username, password)
        if (updated == null) {
            callback.onSuccess()
            return
        }
        val store = SharedVault.forNether(this)
        val name = if (NetherSession.decoy) VaultFiles.DECOY else VaultFiles.PRIMARY
        val blob = store.read(name)
        if (blob == null) {
            callback.onFailure(getString(R.string.autofill_save_failed))
            return
        }
        try {
            store.write(name, NetherVault.save(key, blob, updated))
            NetherSession.document = updated
            callback.onSuccess()
        } catch (_: Exception) {
            callback.onFailure(getString(R.string.autofill_save_failed))
        }
    }

    companion object {
        @Volatile var pending: AssistStructure? = null

        fun responseFor(service: android.content.Context, structure: AssistStructure): FillResponse? {
            val fields = collect(structure)
            val document = NetherSession.document ?: return null
            if (fields.isEmpty()) return null
            val domain = fields.firstNotNullOfOrNull { it.webDomain }
            val app = structure.activityComponent?.packageName
            // Only logins saved for this exact site or app. Never the whole vault.
            val matches = document.entries.filter { entry ->
                entry.secret.isNotEmpty() && AutofillMatch.matches(entry.url, domain, app)
            }.take(8)
            val builder = FillResponse.Builder()
            var added = 0
            for (entry in matches) {
                dataset(service, entry, fields)?.let {
                    builder.addDataset(it)
                    added++
                }
            }
            val save = saveInfo(fields)
            if (save != null) builder.setSaveInfo(save)
            return if (added == 0 && save == null) null else builder.build()
        }

        /** Asks Android to offer "Save to Nether" when a password field is submitted. */
        private fun saveInfo(fields: List<Field>): SaveInfo? {
            val passwords = fields.filter { it.kind == Kind.PASSWORD }.map { it.id }
            if (passwords.isEmpty()) return null
            val usernames = fields.filter { it.kind == Kind.USERNAME }.map { it.id }
            val type = if (usernames.isEmpty()) {
                SaveInfo.SAVE_DATA_TYPE_PASSWORD
            } else {
                SaveInfo.SAVE_DATA_TYPE_PASSWORD or SaveInfo.SAVE_DATA_TYPE_USERNAME
            }
            val builder = SaveInfo.Builder(type, passwords.toTypedArray())
            if (usernames.isNotEmpty()) builder.setOptionalIds(usernames.toTypedArray())
            return builder.build()
        }

        private fun dataset(context: android.content.Context, entry: NetherEntry, fields: List<Field>): Dataset? {
            val views = chip(context, entry.title.ifBlank { entry.username.ifBlank { "Nether" } })
            val builder = Dataset.Builder(views)
            var filled = 0
            for (field in fields) {
                // A password only ever goes into a password field.
                val value = when (field.kind) {
                    Kind.PASSWORD -> entry.secret
                    Kind.USERNAME -> entry.username
                }
                if (value.isEmpty()) continue
                builder.setValue(field.id, AutofillValue.forText(value))
                filled++
            }
            return if (filled == 0) null else builder.build()
        }

        private fun chip(context: android.content.Context, label: String): RemoteViews {
            return RemoteViews(context.packageName, R.layout.autofill_chip).apply {
                setTextViewText(R.id.label, label)
            }
        }

        private fun collect(structure: AssistStructure): List<Field> {
            val out = ArrayList<Field>()
            for (w in 0 until structure.windowNodeCount) {
                walk(structure.getWindowNodeAt(w).rootViewNode, null, out)
            }
            return out
        }

        private fun walk(node: AssistStructure.ViewNode, parentDomain: String?, out: MutableList<Field>) {
            val domain = node.webDomain ?: parentDomain
            val id = node.autofillId
            val kind = kindOf(node)
            if (id != null && kind != null) {
                val value = node.autofillValue?.takeIf { it.isText }?.textValue?.toString() ?: node.text?.toString()
                out += Field(id, kind, domain, value)
            }
            for (i in 0 until node.childCount) walk(node.getChildAt(i), domain, out)
        }

        private fun kindOf(node: AssistStructure.ViewNode): Kind? {
            val hints = node.autofillHints?.joinToString(" ")?.lowercase().orEmpty()
            val hint = node.hint?.lowercase().orEmpty()
            val entry = node.idEntry?.lowercase().orEmpty()
            val html = node.htmlInfo?.attributes?.joinToString(" ") { "${it.first} ${it.second}" }?.lowercase().orEmpty()
            val hay = "$hints $hint $entry $html"
            return when {
                hay.contains("password") || hay.contains("current-password") -> Kind.PASSWORD
                hay.contains("username") || hay.contains("email") || hay.contains("user") -> Kind.USERNAME
                else -> null
            }
        }
    }
}

private enum class Kind { USERNAME, PASSWORD }

private data class Field(val id: AutofillId, val kind: Kind, val webDomain: String?, val value: String? = null)
