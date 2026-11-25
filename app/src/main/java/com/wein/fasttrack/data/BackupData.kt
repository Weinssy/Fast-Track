package com.wein.fasttrack.data

data class BackupPayload(
    val version: Int = 2,
    val app: String = "FastTrack",
    val exportedAt: Long,
    val dailyBudgetCap: Long,
    val tags: List<TagEntity>,
    val expenses: List<Expense>,
    val monthlyBudgetCap: Long = 0L,
    val themeMode: String = "DARK",
    val recurringExpenses: List<RecurringExpenseEntity> = emptyList(),
    val splitBillSessions: List<SplitBillSessionFull> = emptyList()
)
