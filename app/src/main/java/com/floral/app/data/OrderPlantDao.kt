package com.floral.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.OnConflictStrategy
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderPlantDao {
    @Query("SELECT * FROM order_plants ORDER BY id")
    fun observeAll(): Flow<List<OrderPlant>>

    @Query("SELECT * FROM order_plants WHERE orderId = :orderId ORDER BY id")
    fun observeForOrder(orderId: Long): Flow<List<OrderPlant>>

    @Query("SELECT * FROM order_plants WHERE orderId = :orderId ORDER BY id")
    suspend fun getForOrder(orderId: Long): List<OrderPlant>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(plants: List<OrderPlant>)

    @Query("DELETE FROM order_plants WHERE orderId = :orderId")
    suspend fun deleteForOrder(orderId: Long)

    @Query("UPDATE order_plants SET isPaid = :paid WHERE id = :plantId")
    suspend fun setPaid(plantId: Long, paid: Boolean)
}
