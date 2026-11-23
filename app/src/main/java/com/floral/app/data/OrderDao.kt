package com.floral.app.data
import androidx.room.*
import kotlinx.coroutines.flow.Flow
@Dao interface OrderDao {
 @Query("SELECT * FROM orders ORDER BY createdAt DESC") fun observeAll(): Flow<List<Order>>
 @Query("SELECT * FROM orders WHERE id = :id") suspend fun get(id: Long): Order?
 @Insert suspend fun insert(order: Order): Long
 @Update suspend fun update(order: Order)
 @Query("UPDATE orders SET isReceived = :received, updatedAt = :now WHERE id = :id") suspend fun setReceived(id: Long, received: Boolean, now: Long = System.currentTimeMillis())
}