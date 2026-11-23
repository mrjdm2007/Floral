package com.floral.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Order::class, Nursery::class, NurseryPlant::class],
    version = 2,
    exportSchema = true
)
abstract class FloralDatabase : RoomDatabase() {
    abstract fun orders(): OrderDao
    abstract fun nurseries(): NurseryDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS nurseries (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT,
                        phone TEXT,
                        address TEXT,
                        latitude REAL,
                        longitude REAL,
                        photoUri TEXT,
                        notes TEXT NOT NULL DEFAULT '',
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS nursery_plants (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        nurseryId INTEGER NOT NULL,
                        name TEXT,
                        price REAL,
                        photoUri TEXT,
                        notes TEXT NOT NULL DEFAULT '',
                        createdAt INTEGER NOT NULL,
                        FOREIGN KEY(nurseryId) REFERENCES nurseries(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_nursery_plants_nurseryId ON nursery_plants(nurseryId)")
            }
        }

        @Volatile private var instance: FloralDatabase? = null

        fun get(context: Context): FloralDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                FloralDatabase::class.java,
                "floral.db"
            ).addMigrations(MIGRATION_1_2).build().also { instance = it }
        }
    }
}
