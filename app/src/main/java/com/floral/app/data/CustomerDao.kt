package com.floral.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Customer>>

    @Query("SELECT * FROM customers WHERE id = :id")
    suspend fun get(id: Long): Customer?

    @Insert
    suspend fun insert(customer: Customer): Long

    @Update
    suspend fun update(customer: Customer)

    @Query("DELETE FROM customers WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM customers WHERE phone = :phone LIMIT 1")
    suspend fun findByPhone(phone: String): Customer?

    @Query("SELECT * FROM customers WHERE lower(name) = lower(:name) LIMIT 1")
    suspend fun findByName(name: String): Customer?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(customer: Customer): Long
}
