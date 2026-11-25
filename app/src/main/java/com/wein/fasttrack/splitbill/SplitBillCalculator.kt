package com.wein.fasttrack.splitbill

import java.math.BigDecimal
import kotlin.math.abs
import kotlin.math.floor

object SplitBillCalculator {

    fun splitAmount(amount: Long, count: Int): List<Long> {
        if (count <= 0) return emptyList()
        val base = Math.floorDiv(amount, count.toLong())
        val remainder = amount - base * count.toLong()
        return List(count) { i -> base + if (i.toLong() < remainder) 1L else 0L }
    }

    fun computeNetAmount(subtotal: Long, taxPercent: Double = 0.0, discount: Long = 0L): Long {
        val safeSub = maxOf(0L, subtotal)
        val safeTax = if (taxPercent.isFinite() && taxPercent > 0.0) taxPercent else 0.0
        val safeDisc = maxOf(0L, discount)
        val taxed = floor(safeSub.toDouble() * (1.0 + safeTax / 100.0) + 0.5).toLong()
        return maxOf(0L, taxed - safeDisc)
    }

    /**
     * Legacy expenses saved before weighted splits omit entries in [SplitExpense.weights]
     * and default every participant's weight to 1.0.
     */
    fun expenseWeight(expense: SplitExpense, personId: Long): Double {
        if (!expense.weights.containsKey(personId)) return 1.0
        val raw = expense.weights[personId] ?: return 1.0
        return if (raw.isFinite() && raw > 0.0) raw else 0.0
    }

    /**
     * Distributes [amount] proportionally by weight using largest-remainder rounding so shares
     * sum exactly to [amount] whenever total positive weight > 0.
     */
    fun splitWeighted(amount: Long, entries: List<Pair<Long, Double>>): List<Long> {
        if (entries.isEmpty()) return emptyList()
        val weights = DoubleArray(entries.size) { i ->
            val w = entries[i].second
            if (w.isFinite() && w > 0.0) w else 0.0
        }
        val totalWeight = weights.sum()
        if (totalWeight <= 0.0) return List(entries.size) { 0L }

        val exact = DoubleArray(entries.size) { i -> (amount.toDouble() * weights[i]) / totalWeight }
        val shares = LongArray(entries.size) { i -> floor(exact[i]).toLong() }
        var remainder = amount - shares.sum()

        val order = entries.indices.sortedWith { a, b ->
            val fracA = exact[a] - shares[a]
            val fracB = exact[b] - shares[b]
            val cmp = fracB.compareTo(fracA)
            if (cmp != 0) cmp else a.compareTo(b)
        }
        var k = 0
        while (remainder > 0L) {
            shares[order[k % order.size]] += 1L
            remainder -= 1L
            k += 1
        }
        return shares.toList()
    }

    fun describeSplit(expense: SplitExpense, nameOf: (Long) -> String): String {
        val weighted = expense.splitIds.any { id -> expenseWeight(expense, id) != 1.0 }
        return expense.splitIds.joinToString(", ") { id ->
            if (weighted) {
                "${nameOf(id)} (porsi ${formatWeight(expenseWeight(expense, id))})"
            } else {
                nameOf(id)
            }
        }
    }

    fun computeBalances(people: List<SplitPerson>, expenses: List<SplitExpense>): Map<Long, Long> {
        val balances = LinkedHashMap<Long, Long>(people.size)
        for (person in people) {
            balances[person.id] = 0L
        }
        val validIds = people.mapTo(HashSet(people.size)) { it.id }

        for (expense in expenses) {
            if (!balances.containsKey(expense.payerId)) continue
            balances[expense.payerId] = (balances[expense.payerId] ?: 0L) + expense.amount

            val entries = expenseEntries(expense, validIds)
            if (entries.isEmpty()) continue
            val shares = splitWeighted(expense.amount, entries)
            entries.forEachIndexed { index, (personId, _) ->
                balances[personId] = (balances[personId] ?: 0L) - shares[index]
            }
        }
        return balances
    }

    fun computeDetails(
        people: List<SplitPerson>,
        expenses: List<SplitExpense>
    ): Map<Long, PersonBalanceDetail> {
        val paidMap = LinkedHashMap<Long, Long>(people.size)
        val shareMap = LinkedHashMap<Long, Long>(people.size)
        for (person in people) {
            paidMap[person.id] = 0L
            shareMap[person.id] = 0L
        }
        val validIds = people.mapTo(HashSet(people.size)) { it.id }

        for (expense in expenses) {
            if (!paidMap.containsKey(expense.payerId)) continue
            paidMap[expense.payerId] = (paidMap[expense.payerId] ?: 0L) + expense.amount

            val entries = expenseEntries(expense, validIds)
            if (entries.isEmpty()) continue
            val shares = splitWeighted(expense.amount, entries)
            entries.forEachIndexed { index, (personId, _) ->
                shareMap[personId] = (shareMap[personId] ?: 0L) + shares[index]
            }
        }

        val details = LinkedHashMap<Long, PersonBalanceDetail>(people.size)
        for (person in people) {
            val paid = paidMap[person.id] ?: 0L
            val share = shareMap[person.id] ?: 0L
            details[person.id] = PersonBalanceDetail(
                id = person.id,
                name = person.name,
                paid = paid,
                share = share,
                balance = paid - share
            )
        }
        return details
    }

    fun computeSettlements(
        people: List<SplitPerson>,
        balances: Map<Long, Long>
    ): List<SettlementTransfer> {
        val names = people.associate { it.id to it.name }
        val nameOf: (Long) -> String = { id -> names[id] ?: "?" }

        data class MutableParticipantAmount(val id: Long, var amount: Long)

        val debtors = people
            .mapNotNull { person ->
                val bal = balances[person.id] ?: 0L
                if (bal < 0L) MutableParticipantAmount(person.id, -bal) else null
            }
            .sortedByDescending { it.amount }

        val creditors = people
            .mapNotNull { person ->
                val bal = balances[person.id] ?: 0L
                if (bal > 0L) MutableParticipantAmount(person.id, bal) else null
            }
            .sortedByDescending { it.amount }

        val settlements = mutableListOf<SettlementTransfer>()
        var d = 0
        var c = 0
        while (d < debtors.size && c < creditors.size) {
            val pay = minOf(debtors[d].amount, creditors[c].amount)
            if (pay > 0L) {
                settlements.add(
                    SettlementTransfer(
                        fromId = debtors[d].id,
                        toId = creditors[c].id,
                        from = nameOf(debtors[d].id),
                        to = nameOf(creditors[c].id),
                        amount = pay
                    )
                )
            }
            debtors[d].amount -= pay
            creditors[c].amount -= pay
            if (debtors[d].amount == 0L) d += 1
            if (creditors[c].amount == 0L) c += 1
        }
        return settlements
    }

    fun formatRupiah(amount: Long): String {
        return if (amount < 0L) {
            val absDigits = if (amount == Long.MIN_VALUE) {
                "9223372036854775808"
            } else {
                (-amount).toString()
            }
            "-Rp${groupThousands(absDigits)}"
        } else {
            "Rp${groupThousands(amount.toString())}"
        }
    }

    fun buildWhatsAppSummary(
        title: String,
        people: List<SplitPerson>,
        expenses: List<SplitExpense>,
        details: Map<Long, PersonBalanceDetail>,
        settlements: List<SettlementTransfer>,
        settledKeys: Set<String>
    ): String {
        if (expenses.isEmpty()) return ""

        val names = people.associate { it.id to it.name }
        val nameOf: (Long) -> String = { id -> names[id] ?: "?" }
        val total = expenses.sumOf { it.amount }

        val trimmedTitle = title.trim()
        val header = if (trimmedTitle.isNotEmpty()) {
            "Split Bill Kelompok: $trimmedTitle\nTotal: ${formatRupiah(total)}"
        } else {
            "Split Bill Kelompok\nTotal: ${formatRupiah(total)}"
        }

        val expenseLines = expenses.joinToString("\n") { expense ->
            val weighted = expense.splitIds.any { id -> expenseWeight(expense, id) != 1.0 }
            val splitText = if (weighted) ", dibagi ke ${describeSplit(expense, nameOf)}" else ""
            "\u2022 ${expense.desc}: ${formatRupiah(expense.amount)} (dibayar ${nameOf(expense.payerId)}$splitText)"
        }

        val detailLines = people.joinToString("\n") { person ->
            val detail = details[person.id]
                ?: PersonBalanceDetail(person.id, person.name, 0L, 0L, 0L)
            val status = when {
                detail.balance > 0L -> "kelebihan ${formatRupiah(detail.balance)}"
                detail.balance < 0L -> "kurang ${formatRupiah(-detail.balance)}"
                else -> "lunas"
            }
            "\u2022 ${person.name}: bayar ${formatRupiah(detail.paid)}, jatah ${formatRupiah(detail.share)}, $status"
        }

        val settlementSection = if (settlements.isNotEmpty()) {
            val lines = settlements.joinToString("\n") { settlement ->
                val done = if (settledKeys.contains(settlement.key())) " [Lunas]" else ""
                "\u2022 ${settlement.from} \u2192 ${settlement.to}: ${formatRupiah(settlement.amount)}$done"
            }
            "Siapa transfer ke siapa:\n$lines"
        } else {
            "Siapa transfer ke siapa:\nTidak ada, semua sudah lunas."
        }

        return listOf(
            header,
            "Pengeluaran:\n$expenseLines",
            "Rincian per orang:\n$detailLines",
            settlementSection
        ).joinToString("\n\n")
    }

    internal fun groupThousands(value: Long): String {
        val absDigits = if (value < 0L) {
            if (value == Long.MIN_VALUE) "9223372036854775808" else (-value).toString()
        } else {
            value.toString()
        }
        return groupThousands(absDigits)
    }

    internal fun groupThousands(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        if (digits.length <= 3) return digits
        val out = StringBuilder(digits.length + digits.length / 3)
        for (i in digits.indices) {
            if (i > 0 && (digits.length - i) % 3 == 0) {
                out.append('.')
            }
            out.append(digits[i])
        }
        return out.toString()
    }

    internal fun parseAmountInput(text: String): Long {
        val digits = text.filter { it.isDigit() }
        return if (digits.isEmpty()) 0L else digits.toLongOrNull() ?: 0L
    }

    private fun expenseEntries(expense: SplitExpense, validIds: Set<Long>): List<Pair<Long, Double>> {
        return expense.splitIds
            .filter { id -> validIds.contains(id) }
            .map { id -> id to expenseWeight(expense, id) }
    }

    private fun formatWeight(weight: Double): String {
        if (!weight.isFinite() || weight == 0.0) return "0"
        if (weight == floor(weight) && abs(weight) < Long.MAX_VALUE.toDouble()) {
            return weight.toLong().toString()
        }
        return BigDecimal.valueOf(weight).stripTrailingZeros().toPlainString().replace('.', ',')
    }
}
