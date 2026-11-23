package com.floral.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NurseryDao {
    @Query("SELECT * FROM nurseries ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<Nursery>>

    @Query("SELECT * FROM nurseries WHERE id = :id")
    suspend fun get(id: Long): Nursery?

    @Insert
    suspend fun insert(nursery: Nursery): Long

    @Update
    suspend fun update(nursery: Nursery)

    @Query("DELETE FROM nurseries WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM nursery_plants WHERE nurseryId = :nurseryId ORDER BY createdAt DESC")
    fun observePlants(nurseryId: Long): Flow<List<NurseryPlant>>

    @Insert
    suspend fun insertPlant(plant: NurseryPlant): Long

    @Update
    suspend fun updatePlant(plant: NurseryPlant)

    @Query("DELETE FROM nursery_plants WHERE id = :id")
    suspend fun deletePlant(id: Long)
}
