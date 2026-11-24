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

    @Query("""INSERT INTO customers (name, phone, address, notes, createdAt, updatedAt)
        SELECT :name, :phone, :address, '', :now, :now
        WHERE NOT EXISTS (
            SELECT 1 FROM customers
            WHERE (:phone != '' AND phone = :phone)
               OR (:phone = '' AND lower(name) = lower(:name))
        )""")
    suspend fun insertFromOrder(name: String, phone: String, address: String, now: Long = System.currentTimeMillis())
}
