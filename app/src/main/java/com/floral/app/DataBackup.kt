package com.floral.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import com.floral.app.data.FloralDatabase
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object DataBackup {
    private val photoTables = listOf("orders", "order_plants", "nurseries", "nursery_plants")

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

                val photoManifest = mutableListOf<String>()
                val sql = db.openHelper.readableDatabase
                photoTables.forEach { table ->
                    sql.query("SELECT id, photoUri FROM $table WHERE photoUri IS NOT NULL AND photoUri != ''").use { cursor ->
                        while (cursor.moveToNext()) {
                            val id = cursor.getLong(0)
                            val uriText = cursor.getString(1)
                            val entryName = "photo_refs/$table/$id.bin"
                            val stream = context.contentResolver.openInputStream(Uri.parse(uriText))
                                ?: error("Could not read a saved photo in $table (record $id). Please reopen that photo and try again.")
                            zip.putNextEntry(ZipEntry(entryName))
                            stream.use { it.copyTo(zip) }
                            zip.closeEntry()
                            photoManifest += "$table|$id|$entryName"
                        }
                    }
                }
                zip.putNextEntry(ZipEntry("photo_refs/manifest.txt"))
                zip.write(photoManifest.joinToString("\n").toByteArray(Charsets.UTF_8))
                zip.closeEntry()
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
        var photoManifest = ""
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
                                File(staging, "floral.db").outputStream().use { zip.copyTo(it) }
                                hasDatabase = true
                            }
                            name == "settings/from_address.txt" -> senderAddress = zip.readBytes().toString(Charsets.UTF_8)
                            name == "photo_refs/manifest.txt" -> photoManifest = zip.readBytes().toString(Charsets.UTF_8)
                            name.startsWith("external/") || name.startsWith("photo_refs/") -> {
                                val target = File(staging, name)
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

            val mappings = photoManifest.lineSequence().filter { it.isNotBlank() }.map { line ->
                val parts = line.split('|')
                require(parts.size == 3 && parts[0] in photoTables) { "The backup photo index is invalid." }
                Triple(parts[0], parts[1].toLong(), parts[2])
            }.toList()
            mappings.forEach { (_, _, entry) ->
                require(File(staging, entry).exists()) { "A photo is missing from this backup." }
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

            if (mappings.isNotEmpty()) {
                val photoDir = File(context.getExternalFilesDir(null), "Pictures/restored_media").apply {
                    deleteRecursively()
                    mkdirs()
                }
                val restoredDb = SQLiteDatabase.openDatabase(targetDb.path, null, SQLiteDatabase.OPEN_READWRITE)
                try {
                    mappings.forEach { (table, id, entry) ->
                        val source = File(staging, entry)
                        val target = File(photoDir, "${table}_${id}.img")
                        source.copyTo(target, overwrite = true)
                        val values = ContentValues().apply { put("photoUri", Uri.fromFile(target).toString()) }
                        restoredDb.update(table, values, "id = ?", arrayOf(id.toString()))
                    }
                } finally {
                    restoredDb.close()
                }
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
