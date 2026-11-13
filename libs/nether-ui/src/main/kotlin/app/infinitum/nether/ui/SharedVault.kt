package app.infinitum.nether.ui

import android.content.Context
import android.net.Uri
import java.io.File

interface NetherStore {
    fun read(name: String): ByteArray?
    fun write(name: String, bytes: ByteArray)
    fun delete(name: String)
}

object VaultFiles {
    const val PRIMARY = "primary"
    const val DECOY = "decoy"
    const val META = "meta"
    const val SUITE_AUTHORITY = "com.infinitum.throne.suite.nether"
    const val PERMISSION = "app.infinitum.nether.ACCESS_VAULT"
}

class FileVaultStore(private val dir: File) : NetherStore {
    override fun read(name: String): ByteArray? = file(name).takeIf { it.exists() }?.readBytes()
    /**
     * Writes to a temporary file, flushes it to disk, then renames it over the old file,
     * so a crash or power loss mid-save leaves either the old vault or the new one, never half.
     */
    override fun write(name: String, bytes: ByteArray) {
        dir.mkdirs()
        val target = file(name)
        val temp = File(dir, "$name.vault.tmp")
        java.io.FileOutputStream(temp).use { out ->
            out.write(bytes)
            out.flush()
            out.fd.sync()
        }
        if (!temp.renameTo(target)) {
            temp.delete()
            throw java.io.IOException("Could not save the vault.")
        }
    }
    override fun delete(name: String) {
        file(name).delete()
    }
    private fun file(name: String) = File(dir, "$name.vault")
}

class RemoteVaultStore(private val context: Context, private val authority: String) : NetherStore {
    override fun read(name: String): ByteArray? = try {
        context.contentResolver.openInputStream(uri(name))?.use { it.readBytes() }
    } catch (_: Exception) {
        null
    }

    override fun write(name: String, bytes: ByteArray) {
        try {
            context.contentResolver.openOutputStream(uri(name), "wt")?.use { it.write(bytes) }
        } catch (_: Exception) {
            // The other app is not installed.
        }
    }

    override fun delete(name: String) {
        try {
            context.contentResolver.delete(uri(name), null, null)
        } catch (_: Exception) {
            // Ignore.
        }
    }

    private fun uri(name: String) = Uri.parse("content://$authority/$name")
}

object SharedVault {
    fun forNether(context: Context): NetherStore {
        val local = FileVaultStore(File(context.filesDir, "nether"))
        if (local.read(VaultFiles.PRIMARY) == null) {
            val remote = RemoteVaultStore(context, VaultFiles.SUITE_AUTHORITY)
            remote.read(VaultFiles.PRIMARY)?.let { local.write(VaultFiles.PRIMARY, it) }
            remote.read(VaultFiles.DECOY)?.let { local.write(VaultFiles.DECOY, it) }
            remote.read(VaultFiles.META)?.let { local.write(VaultFiles.META, it) }
        }
        return local
    }
}
