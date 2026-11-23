package com.floral.app.data
import androidx.room.Entity
import androidx.room.PrimaryKey
@Entity(tableName = "orders")
data class Order(
 @PrimaryKey(autoGenerate = true) val id: Long = 0,
 val customerName: String? = null, val phone: String? = null, val address: String? = null,
 val plantName: String? = null, val photoUri: String? = null,
 val plantCost: Double? = null, val sellingPrice: Double? = null, val finalPrice: Double? = null,
 val courierCharge: Double? = null, val expectedDispatchAt: Long? = null, val dispatchedAt: Long? = null,
 val isReceived: Boolean = false, val notes: String = "",
 val createdAt: Long = System.currentTimeMillis(), val updatedAt: Long = System.currentTimeMillis()
) {
 val missingFields: List<String> get() = buildList {
  if (customerName.isNullOrBlank()) add("Customer name")
  if (phone.isNullOrBlank()) add("Phone number")
  if (address.isNullOrBlank()) add("Address")
  if (plantName.isNullOrBlank()) add("Plant name")
  if (photoUri.isNullOrBlank()) add("Plant photo")
  if (plantCost == null) add("Plant cost")
  if (sellingPrice == null) add("Selling price")
  if (finalPrice == null) add("Final price")
  if (expectedDispatchAt == null) add("Expected dispatch date")
  if (dispatchedAt == null) add("Actual dispatch date")
  if (courierCharge == null) add("Courier charges")
 }
 val totalValue: Double? get() = if (sellingPrice != null && courierCharge != null) sellingPrice + courierCharge else null
 // Courier is an expense to the business; profit is only shown once all cost inputs are known.
 val profit: Double? get() = if (plantCost != null && sellingPrice != null && courierCharge != null) sellingPrice - plantCost - courierCharge else null
}