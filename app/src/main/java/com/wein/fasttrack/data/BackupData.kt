package com.wein.fasttrack.data

data class BackupPayload(
    val version: Int = 1,
    val app: String = "FastTrack",
    val exportedAt: Long,
    val dailyBudgetCap: Long,
    val tags: List<TagEntity>,
    val expenses: List<Expense>
)
