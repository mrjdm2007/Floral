package com.floral.app

import android.content.Context
import android.net.Uri
import com.floral.app.data.FloralDatabase
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object DataBackup {
    fun export(context: Context, db: FloralDatabase, destination: Uri) {
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { }
        val database = context.getDatabasePath("floral.db")
        require(database.exists()) { "Floral database was not found." }
        context.contentResolver.openOutputStream(destination, "w")?.use { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry("floral.db"))
                database.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
                val sender = context.getSharedPreferences("floral_settings", Context.MODE_PRIVATE)
                    .getString("from_address", "").orEmpty()
                zip.putNextEntry(ZipEntry("settings/from_address.txt"))
                zip.write(sender.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                val external = context.getExternalFilesDir(null)
                if (external != null && external.exists()) {
                    external.walkTopDown().filter { it.isFile }.forEach { file ->
                        val relative = file.relativeTo(external).invariantSeparatorsPath
                        zip.putNextEntry(ZipEntry("external/$relative"))
                        file.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            }
        } ?: error("Could not create the backup file.")
    }

    fun restore(context: Context, backup: Uri) {
        val staging = File(context.cacheDir, "floral_restore_${System.currentTimeMillis()}").apply {
            deleteRecursively()
            mkdirs()
        }
        var hasDatabase = false
        var senderAddress: String? = null
        try {
            context.contentResolver.openInputStream(backup)?.use { input ->
                ZipInputStream(input).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        val name = entry.name.replace('\\', '/')
                        require(!name.startsWith("/") && !name.split('/').any { it == ".." }) {
                            "The backup contains an unsafe file path."
                        }
                        when {
                            name == "floral.db" -> {
                                val target = File(staging, "floral.db")
                                target.outputStream().use { zip.copyTo(it) }
                                hasDatabase = true
                            }
                            name == "settings/from_address.txt" -> {
                                senderAddress = zip.readBytes().toString(Charsets.UTF_8)
                            }
                            name.startsWith("external/") -> {
                                val relative = name.removePrefix("external/")
                                val target = File(staging, "external/$relative")
                                target.parentFile?.mkdirs()
                                target.outputStream().use { zip.copyTo(it) }
                            }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            } ?: error("Could not open the selected backup.")
            require(hasDatabase) { "This is not a valid Floral backup." }
            val stagedDb = File(staging, "floral.db")
            val header = stagedDb.inputStream().use { input -> ByteArray(16).also { input.read(it) } }
            require(String(header, Charsets.US_ASCII).startsWith("SQLite format 3")) {
                "The backup database is invalid."
            }
            FloralDatabase.closeForRestore()
            val targetDb = context.getDatabasePath("floral.db")
            targetDb.parentFile?.mkdirs()
            File(targetDb.path + "-wal").delete()
            File(targetDb.path + "-shm").delete()
            stagedDb.copyTo(targetDb, overwrite = true)
            val external = context.getExternalFilesDir(null)
            val stagedExternal = File(staging, "external")
            if (external != null && stagedExternal.exists()) {
                external.deleteRecursively()
                stagedExternal.copyRecursively(external, overwrite = true)
            }
            if (senderAddress != null) {
                context.getSharedPreferences("floral_settings", Context.MODE_PRIVATE)
                    .edit().putString("from_address", senderAddress).commit()
            }
        } finally {
            staging.deleteRecursively()
        }
    }
}
