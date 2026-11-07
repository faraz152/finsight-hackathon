package com.finsight.android

import android.content.Context
import com.finsight.data.KeyValueStore
import java.io.File

/** One UTF-8 file per key in app-private filesDir (allowBackup=false). Writes are atomic (temp file + rename). */
class FileStore(context: Context) : KeyValueStore {
    private val dir = File(context.filesDir, "finsight_store").apply { mkdirs() }
    private fun file(key: String) = File(dir, key.replace(Regex("[^A-Za-z0-9_.-]"), "_") + ".txt")

    @Synchronized
    override fun read(key: String): String? = file(key).takeIf { it.exists() }?.readText(Charsets.UTF_8)

    @Synchronized
    override fun write(key: String, value: String) {
        val target = file(key)
        val tmp = File(dir, target.name + ".tmp")
        tmp.writeText(value, Charsets.UTF_8)
        if (!tmp.renameTo(target)) { target.delete(); tmp.renameTo(target) }
    }
}
