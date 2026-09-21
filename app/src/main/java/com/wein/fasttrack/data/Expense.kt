package com.wein.fasttrack.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val tag: String? = "General",
    val note: String? = null
)
