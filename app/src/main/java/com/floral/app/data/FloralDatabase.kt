package com.floral.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Order::class, Nursery::class, NurseryPlant::class, OrderPlant::class, Customer::class],
    version = 6,
    exportSchema = true
)
abstract class FloralDatabase : RoomDatabase() {
    abstract fun orders(): OrderDao
    abstract fun nurseries(): NurseryDao
    abstract fun orderPlants(): OrderPlantDao
    abstract fun customers(): CustomerDao

    companion object {
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE orders ADD COLUMN includeFromAddress INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS order_plants (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    orderId INTEGER NOT NULL,
                    name TEXT NOT NULL,
                    photoUri TEXT,
                    plantCost REAL,
                    sellingPrice REAL,
                    finalPrice REAL,
                    isPaid INTEGER NOT NULL,
                    FOREIGN KEY(orderId) REFERENCES orders(id) ON UPDATE NO ACTION ON DELETE CASCADE
                )""".trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_order_plants_orderId ON order_plants(orderId)")
                db.execSQL("""INSERT INTO order_plants (orderId, name, photoUri, plantCost, sellingPrice, finalPrice, isPaid)
                    SELECT id, COALESCE(plantName, ''), photoUri, plantCost, sellingPrice, finalPrice, 0 FROM orders""".trimIndent())
            }
        }

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

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE nursery_plants ADD COLUMN stock INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS customers (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    phone TEXT NOT NULL DEFAULT '',
                    address TEXT NOT NULL DEFAULT '',
                    notes TEXT NOT NULL DEFAULT '',
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )""".trimIndent())
                db.execSQL("""INSERT INTO customers (name, phone, address, notes, createdAt, updatedAt)
                    SELECT customerName, COALESCE(phone, ''), COALESCE(address, ''), '',
                           MIN(createdAt), MAX(updatedAt)
                    FROM orders
                    WHERE customerName IS NOT NULL AND trim(customerName) != ''
                    GROUP BY CASE
                        WHEN phone IS NOT NULL AND trim(phone) != '' THEN phone
                        ELSE lower(trim(customerName))
                    END""".trimIndent())
            }
        }

        @Volatile private var instance: FloralDatabase? = null

        fun closeForRestore() {
            synchronized(this) {
                instance?.close()
                instance = null
            }
        }

        fun get(context: Context): FloralDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                FloralDatabase::class.java,
                "floral.db"
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6).build().also { instance = it }
        }
    }
}
