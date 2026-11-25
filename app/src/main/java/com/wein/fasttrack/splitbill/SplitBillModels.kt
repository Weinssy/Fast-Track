package com.wein.fasttrack.splitbill

data class SplitPerson(
    val id: Long,
    val name: String,
    val isSelf: Boolean = false
)

data class SplitExpense(
    val id: Long,
    val desc: String,
    val amount: Long,
    val payerId: Long,
    val splitIds: List<Long>,
    val weights: Map<Long, Double> = emptyMap(),
    val subtotal: Long = amount,
    val taxPercent: Double = 0.0,
    val discount: Long = 0L
)

data class PersonBalanceDetail(
    val id: Long,
    val name: String,
    val paid: Long,
    val share: Long,
    val balance: Long
)

data class SettlementTransfer(
    val fromId: Long,
    val toId: Long,
    val from: String,
    val to: String,
    val amount: Long
) {
    fun key(): String = "$fromId->$toId:$amount"
}

data class SplitSharePayload(
    val title: String,
    val people: List<SplitPerson>,
    val expenses: List<SplitExpense>,
    val settled: List<String>
)
