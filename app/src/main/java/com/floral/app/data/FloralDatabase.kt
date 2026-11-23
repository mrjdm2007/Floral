package com.floral.app.data
import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
@Database(entities = [Order::class], version = 1, exportSchema = true)
abstract class FloralDatabase : RoomDatabase() {
 abstract fun orders(): OrderDao
 companion object {
  @Volatile private var instance: FloralDatabase? = null
  fun get(context: Context): FloralDatabase = instance ?: synchronized(this) {
   instance ?: Room.databaseBuilder(context.applicationContext, FloralDatabase::class.java, "floral.db").build().also { instance = it }
  }
 }
}