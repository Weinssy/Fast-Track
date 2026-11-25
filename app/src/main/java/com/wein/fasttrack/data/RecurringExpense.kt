package com.wein.fasttrack.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recurring_expenses")
data class RecurringExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Long,
    val tag: String = "Tagihan",
    val note: String? = null,
    val dayOfMonth: Int = 1,
    val isEnabled: Boolean = true,
    val lastAppliedYearMonth: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
