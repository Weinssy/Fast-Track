package com.wein.fasttrack.repository

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.room.withTransaction
import com.wein.fasttrack.data.AppDatabase
import com.wein.fasttrack.data.Expense
import com.wein.fasttrack.data.ExpenseDao
import com.wein.fasttrack.data.SplitBillDao
import com.wein.fasttrack.data.SplitBillItemEntity
import com.wein.fasttrack.data.SplitBillParticipantEntity
import com.wein.fasttrack.data.SplitBillSessionEntity
import com.wein.fasttrack.data.SplitBillSessionFull
import com.wein.fasttrack.data.SplitBillSettledEntity
import com.wein.fasttrack.splitbill.SplitBillCalculator
import com.wein.fasttrack.splitbill.SplitExpense
import com.wein.fasttrack.splitbill.SplitPerson
import com.wein.fasttrack.splitbill.SplitSharePayload
import com.wein.fasttrack.widget.FastTrackWidget
import kotlinx.coroutines.flow.Flow

class SplitBillRepository(
    context: Context,
    private val database: AppDatabase,
    private val splitBillDao: SplitBillDao = database.splitBillDao(),
    private val expenseDao: ExpenseDao = database.expenseDao()
) {
    private val appContext: Context = context.applicationContext

    fun observeSessions(): Flow<List<SplitBillSessionFull>> = splitBillDao.observeAllSessions()

    fun observeSession(sessionId: Long): Flow<SplitBillSessionFull?> =
        splitBillDao.observeSession(sessionId)

    suspend fun getSession(sessionId: Long): SplitBillSessionFull? =
        splitBillDao.getSessionFullSync(sessionId)

    suspend fun getAllSessionsSync(): List<SplitBillSessionFull> =
        splitBillDao.getAllSessionsSync()

    suspend fun createSession(
        title: String,
        includeSelf: Boolean = true,
        selfName: String = "Saya",
        expenseTag: String = "Makan"
    ): Long = database.withTransaction {
        val now = System.currentTimeMillis()
        val sessionId = splitBillDao.insertSession(
            SplitBillSessionEntity(
                title = title.trim(),
                createdAt = now,
                updatedAt = now,
                syncToExpenses = true,
                expenseTag = expenseTag.trim().ifEmpty { "Makan" },
                syncedExpenseId = null
            )
        )
        if (includeSelf) {
            splitBillDao.insertParticipant(
                SplitBillParticipantEntity(
                    sessionId = sessionId,
                    name = selfName.trim().ifEmpty { "Saya" },
                    isSelf = true,
                    sortOrder = 0
                )
            )
        }
        sessionId
    }

    suspend fun updateSessionMeta(
        sessionId: Long,
        title: String? = null,
        syncToExpenses: Boolean? = null,
        expenseTag: String? = null
    ) {
        val expenseChanged = database.withTransaction {
            val existing = splitBillDao.getSessionById(sessionId) ?: return@withTransaction false
            val updated = existing.copy(
                title = title?.trim() ?: existing.title,
                syncToExpenses = syncToExpenses ?: existing.syncToExpenses,
                expenseTag = expenseTag?.trim()?.ifEmpty { existing.expenseTag } ?: existing.expenseTag,
                updatedAt = System.currentTimeMillis()
            )
            splitBillDao.updateSession(updated)
            syncSessionInTransaction(sessionId)
        }
        if (expenseChanged) {
            refreshWidget()
        }
    }

    suspend fun deleteSession(sessionId: Long) {
        val hadLinkedExpense = database.withTransaction {
            val existingExpense = expenseDao.getExpenseBySplitBillSessionId(sessionId)
            if (existingExpense != null) {
                expenseDao.deleteBySplitBillSessionId(sessionId)
            }
            splitBillDao.deleteSessionById(sessionId)
            existingExpense != null
        }
        if (hadLinkedExpense) {
            refreshWidget()
        }
    }

    suspend fun addParticipant(
        sessionId: Long,
        name: String,
        isSelf: Boolean = false
    ): Long {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return 0L
        var insertedId = 0L
        val expenseChanged = database.withTransaction {
            val existing = splitBillDao.getParticipantsForSession(sessionId)
            if (isSelf) {
                splitBillDao.setSelfParticipant(sessionId, -1L)
            }
            val nextOrder = (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1
            insertedId = splitBillDao.insertParticipant(
                SplitBillParticipantEntity(
                    sessionId = sessionId,
                    name = trimmed,
                    isSelf = isSelf,
                    sortOrder = nextOrder
                )
            )
            splitBillDao.touchSession(sessionId)
            syncSessionInTransaction(sessionId)
        }
        if (expenseChanged) {
            refreshWidget()
        }
        return insertedId
    }

    suspend fun removeParticipant(sessionId: Long, participantId: Long) {
        val expenseChanged = database.withTransaction {
            val items = splitBillDao.getItemsForSession(sessionId)
            for (item in items) {
                val splitIds = parseSplitIds(item.splitIdsSerialized)
                if (item.payerParticipantId == participantId || participantId in splitIds) {
                    splitBillDao.deleteItemById(item.id)
                }
            }
            splitBillDao.deleteParticipantById(participantId)
            val settled = splitBillDao.getSettledForSession(sessionId)
            for (entry in settled) {
                if (settledKeyInvolves(entry.settlementKey, participantId)) {
                    splitBillDao.deleteSettled(sessionId, entry.settlementKey)
                }
            }
            splitBillDao.touchSession(sessionId)
            syncSessionInTransaction(sessionId)
        }
        if (expenseChanged) {
            refreshWidget()
        }
    }

    suspend fun setSelfParticipant(sessionId: Long, participantId: Long) {
        val expenseChanged = database.withTransaction {
            splitBillDao.setSelfParticipant(sessionId, participantId)
            splitBillDao.touchSession(sessionId)
            syncSessionInTransaction(sessionId)
        }
        if (expenseChanged) {
            refreshWidget()
        }
    }

    suspend fun upsertItem(
        sessionId: Long,
        itemId: Long = 0L,
        description: String,
        amount: Long,
        payerParticipantId: Long,
        splitIds: List<Long>,
        weights: Map<Long, Double> = emptyMap(),
        subtotal: Long = amount,
        taxPercent: Double = 0.0,
        discount: Long = 0L
    ): Long {
        val entity = SplitBillItemEntity(
            id = itemId,
            sessionId = sessionId,
            description = description.trim(),
            amount = amount,
            subtotal = subtotal,
            taxPercent = taxPercent,
            discount = discount,
            payerParticipantId = payerParticipantId,
            splitIdsSerialized = splitIds.distinct().joinToString(","),
            weightsJson = serializeWeights(weights)
        )
        return upsertItem(entity)
    }

    suspend fun upsertItem(item: SplitBillItemEntity): Long {
        var savedId = item.id
        val expenseChanged = database.withTransaction {
            savedId = if (item.id == 0L) {
                splitBillDao.insertItem(item)
            } else {
                splitBillDao.updateItem(item)
                item.id
            }
            splitBillDao.touchSession(item.sessionId)
            syncSessionInTransaction(item.sessionId)
        }
        if (expenseChanged) {
            refreshWidget()
        }
        return savedId
    }

    suspend fun deleteItem(sessionId: Long, itemId: Long) {
        val expenseChanged = database.withTransaction {
            splitBillDao.deleteItemById(itemId)
            splitBillDao.touchSession(sessionId)
            syncSessionInTransaction(sessionId)
        }
        if (expenseChanged) {
            refreshWidget()
        }
    }

    suspend fun toggleSettled(sessionId: Long, settlementKey: String) {
        database.withTransaction {
            val current = splitBillDao.getSettledForSession(sessionId)
            if (current.any { it.settlementKey == settlementKey }) {
                splitBillDao.deleteSettled(sessionId, settlementKey)
            } else {
                splitBillDao.insertSettled(SplitBillSettledEntity(sessionId, settlementKey))
            }
            splitBillDao.touchSession(sessionId)
        }
    }

    suspend fun importSharePayload(
        payload: SplitSharePayload,
        syncToExpenses: Boolean = true,
        expenseTag: String = "Makan"
    ): Long {
        var createdSessionId = 0L
        val expenseChanged = database.withTransaction {
            val now = System.currentTimeMillis()
            val sessionId = splitBillDao.insertSession(
                SplitBillSessionEntity(
                    title = payload.title.trim(),
                    createdAt = now,
                    updatedAt = now,
                    syncToExpenses = syncToExpenses,
                    expenseTag = expenseTag.trim().ifEmpty { "Makan" },
                    syncedExpenseId = null
                )
            )
            createdSessionId = sessionId

            val idMap = mutableMapOf<Long, Long>()
            var assignedSelf = false
            payload.people.forEachIndexed { index, person ->
                val markSelf = person.isSelf && !assignedSelf
                if (markSelf) assignedSelf = true
                val newId = splitBillDao.insertParticipant(
                    SplitBillParticipantEntity(
                        sessionId = sessionId,
                        name = person.name.trim().ifEmpty { "Peserta ${index + 1}" },
                        isSelf = markSelf,
                        sortOrder = index
                    )
                )
                idMap[person.id] = newId
            }

            for (expense in payload.expenses) {
                val mappedPayerId = idMap[expense.payerId] ?: continue
                val mappedSplitIds = expense.splitIds.mapNotNull { idMap[it] }.distinct()
                if (mappedSplitIds.isEmpty()) continue
                val mappedWeights = buildMap {
                    expense.weights.forEach { (oldId, weight) ->
                        val newId = idMap[oldId]
                        if (newId != null && weight.isFinite() && weight >= 0.0) {
                            put(newId, weight)
                        }
                    }
                }
                splitBillDao.insertItem(
                    SplitBillItemEntity(
                        sessionId = sessionId,
                        description = expense.desc.trim(),
                        amount = expense.amount,
                        subtotal = expense.subtotal,
                        taxPercent = expense.taxPercent,
                        discount = expense.discount,
                        payerParticipantId = mappedPayerId,
                        splitIdsSerialized = mappedSplitIds.joinToString(","),
                        weightsJson = serializeWeights(mappedWeights)
                    )
                )
            }

            for (key in payload.settled) {
                val remapped = remapSettlementKey(key, idMap) ?: continue
                splitBillDao.insertSettled(SplitBillSettledEntity(sessionId, remapped))
            }

            syncSessionInTransaction(sessionId)
        }
        if (expenseChanged) {
            refreshWidget()
        }
        return createdSessionId
    }

    suspend fun syncSessionToPersonalExpenses(sessionId: Long) {
        val changed = database.withTransaction {
            syncSessionInTransaction(sessionId)
        }
        if (changed) {
            refreshWidget()
        }
    }

    fun toSplitPeople(full: SplitBillSessionFull): List<SplitPerson> {
        return full.participants
            .sortedWith(compareBy({ it.sortOrder }, { it.id }))
            .map { SplitPerson(id = it.id, name = it.name, isSelf = it.isSelf) }
    }

    fun toSplitExpenses(full: SplitBillSessionFull): List<SplitExpense> {
        return full.items
            .sortedBy { it.id }
            .map { item ->
                SplitExpense(
                    id = item.id,
                    desc = item.description,
                    amount = item.amount,
                    payerId = item.payerParticipantId,
                    splitIds = parseSplitIds(item.splitIdsSerialized),
                    weights = parseWeights(item.weightsJson),
                    subtotal = item.subtotal,
                    taxPercent = item.taxPercent,
                    discount = item.discount
                )
            }
    }

    fun toSharePayload(full: SplitBillSessionFull): SplitSharePayload {
        return SplitSharePayload(
            title = full.session.title,
            people = toSplitPeople(full),
            expenses = toSplitExpenses(full),
            settled = full.settledKeys.map { it.settlementKey }
        )
    }

    private suspend fun syncSessionInTransaction(sessionId: Long): Boolean {
        val full = splitBillDao.getSessionFullSync(sessionId) ?: return false
        val session = full.session
        val self = full.participants.firstOrNull { it.isSelf }
        val people = toSplitPeople(full)
        val expenses = toSplitExpenses(full)
        val myShare = if (self != null) {
            SplitBillCalculator.computeDetails(people, expenses)[self.id]?.share ?: 0L
        } else {
            0L
        }

        val existingExpense = expenseDao.getExpenseBySplitBillSessionId(sessionId)
        val targetNote = "Patungan: ${session.title.ifBlank { "Split Bill" }}"
        val targetTag = session.expenseTag.ifBlank { "Makan" }

        return if (session.syncToExpenses && self != null && myShare > 0L) {
            if (existingExpense != null) {
                val changed = existingExpense.amount != myShare ||
                    existingExpense.tag != targetTag ||
                    existingExpense.note != targetNote
                if (changed) {
                    expenseDao.update(
                        existingExpense.copy(
                            amount = myShare,
                            tag = targetTag,
                            note = targetNote,
                            splitBillSessionId = sessionId
                        )
                    )
                }
                if (session.syncedExpenseId != existingExpense.id) {
                    splitBillDao.updateSyncedExpenseId(sessionId, existingExpense.id)
                }
                changed
            } else {
                val insertedId = expenseDao.insert(
                    Expense(
                        amount = myShare,
                        tag = targetTag,
                        note = targetNote,
                        splitBillSessionId = sessionId
                    )
                )
                splitBillDao.updateSyncedExpenseId(sessionId, insertedId)
                true
            }
        } else {
            val hadExpense = existingExpense != null
            if (hadExpense) {
                expenseDao.deleteBySplitBillSessionId(sessionId)
            }
            if (session.syncedExpenseId != null) {
                splitBillDao.updateSyncedExpenseId(sessionId, null)
            }
            hadExpense
        }
    }

    private suspend fun refreshWidget() {
        try {
            FastTrackWidget().updateAll(appContext)
        } catch (_: Exception) {
            // Ignore widget update failures when running in test hosts
        }
    }

    private fun parseSplitIds(serialized: String): List<Long> {
        if (serialized.isBlank()) return emptyList()
        return serialized.split(",")
            .mapNotNull { it.trim().toLongOrNull() }
    }

    private fun serializeWeights(weights: Map<Long, Double>): String {
        val valid = weights.entries.filter { it.value.isFinite() && it.value >= 0.0 }
        if (valid.isEmpty()) return "{}"
        return valid.joinToString(prefix = "{", postfix = "}", separator = ",") { (id, weight) ->
            "\"$id\":$weight"
        }
    }

    private fun parseWeights(json: String): Map<Long, Double> {
        if (json.isBlank() || json.trim() == "{}") return emptyMap()
        val result = mutableMapOf<Long, Double>()
        for (match in WEIGHT_ENTRY_REGEX.findAll(json)) {
            val id = match.groupValues[1].toLongOrNull() ?: continue
            val value = match.groupValues[2].toDoubleOrNull() ?: continue
            if (value.isFinite() && value >= 0.0) {
                result[id] = value
            }
        }
        return result
    }

    private companion object {
        val WEIGHT_ENTRY_REGEX = Regex("\"(-?\\d+)\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)")
    }

    private fun settledKeyInvolves(key: String, participantId: Long): Boolean {
        val arrowIdx = key.indexOf("->")
        val colonIdx = key.indexOf(':', startIndex = if (arrowIdx >= 0) arrowIdx + 2 else 0)
        if (arrowIdx <= 0 || colonIdx <= arrowIdx + 2) return false
        val fromId = key.substring(0, arrowIdx).toLongOrNull()
        val toId = key.substring(arrowIdx + 2, colonIdx).toLongOrNull()
        return fromId == participantId || toId == participantId
    }

    private fun remapSettlementKey(key: String, idMap: Map<Long, Long>): String? {
        val arrowIdx = key.indexOf("->")
        val colonIdx = key.indexOf(':', startIndex = if (arrowIdx >= 0) arrowIdx + 2 else 0)
        if (arrowIdx <= 0 || colonIdx <= arrowIdx + 2) return null
        val oldFrom = key.substring(0, arrowIdx).toLongOrNull() ?: return null
        val oldTo = key.substring(arrowIdx + 2, colonIdx).toLongOrNull() ?: return null
        val newFrom = idMap[oldFrom] ?: return null
        val newTo = idMap[oldTo] ?: return null
        val amountPart = key.substring(colonIdx + 1)
        return "$newFrom->$newTo:$amountPart"
    }
}
