package com.floral.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "nurseries")
data class Nursery(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String? = null,
    val phone: String? = null,
    val address: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val photoUri: String? = null,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "nursery_plants",
    foreignKeys = [ForeignKey(
        entity = Nursery::class,
        parentColumns = ["id"],
        childColumns = ["nurseryId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("nurseryId")]
)
data class NurseryPlant(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nurseryId: Long,
    val name: String? = null,
    val price: Double? = null,
    val stock: Int = 0,
    val photoUri: String? = null,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
