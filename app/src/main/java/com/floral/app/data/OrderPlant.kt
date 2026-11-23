package com.floral.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "order_plants",
    foreignKeys = [ForeignKey(
        entity = Order::class,
        parentColumns = ["id"],
        childColumns = ["orderId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("orderId")]
)
data class OrderPlant(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderId: Long,
    val name: String = "",
    val photoUri: String? = null,
    val plantCost: Double? = null,
    val sellingPrice: Double? = null,
    val finalPrice: Double? = null,
    val isPaid: Boolean = false
)
