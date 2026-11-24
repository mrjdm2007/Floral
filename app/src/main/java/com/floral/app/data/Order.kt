package com.floral.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "orders")
data class Order(
 @PrimaryKey(autoGenerate = true) val id: Long = 0,
 val customerName: String? = null,
 val phone: String? = null,
 val address: String? = null,
 val plantName: String? = null,
 val photoUri: String? = null,
 val plantCost: Double? = null,
 val sellingPrice: Double? = null,
 val finalPrice: Double? = null,
 val courierCharge: Double? = null,
 val expectedDispatchAt: Long? = null,
 val dispatchedAt: Long? = null,
 val isReceived: Boolean = false,
 val notes: String = "",
 val includeFromAddress: Boolean = false,
 val createdAt: Long = System.currentTimeMillis(),
 val updatedAt: Long = System.currentTimeMillis()
) {
 val missingFields: List<String> get() = buildList {
  if (customerName.isNullOrBlank()) add("Customer name")
  if (phone.isNullOrBlank()) add("Phone number")
  if (address.isNullOrBlank()) add("Delivery address")
  if (plantName.isNullOrBlank()) add("Plant name")
  if (plantCost == null) add("Actual plant cost")
  if (sellingPrice == null && finalPrice == null) add("Selling / final agreed price")
  if (expectedDispatchAt == null) add("Expected dispatch date")
  if (courierCharge == null) add("Courier charges")
 }
 // The final agreed plant amount takes precedence over the initial/listed selling price.
 // Courier is added to the customer total but excluded from plant margin as a pass-through.
 val effectiveSalePrice: Double? get() = finalPrice ?: sellingPrice
 val totalValue: Double? get() = if (effectiveSalePrice != null && courierCharge != null) effectiveSalePrice + courierCharge else null
 val profit: Double? get() = if (plantCost != null && effectiveSalePrice != null) effectiveSalePrice - plantCost else null
 val financialsComplete: Boolean get() = plantCost != null && effectiveSalePrice != null
}
