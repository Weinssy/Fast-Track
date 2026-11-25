package com.wein.fasttrack.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wein.fasttrack.data.SplitBillSessionFull
import com.wein.fasttrack.data.TagEntity
import com.wein.fasttrack.repository.ExpenseRepository
import com.wein.fasttrack.repository.SplitBillRepository
import com.wein.fasttrack.splitbill.PersonBalanceDetail
import com.wein.fasttrack.splitbill.SettlementTransfer
import com.wein.fasttrack.splitbill.SplitBillCalculator
import com.wein.fasttrack.splitbill.SplitBillShareCodec
import com.wein.fasttrack.splitbill.SplitExpense
import com.wein.fasttrack.splitbill.SplitPerson
import com.wein.fasttrack.splitbill.SplitSharePayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

data class SplitBillSessionSummary(
    val id: Long,
    val title: String,
    val displayTitle: String,
    val participantCount: Int,
    val itemCount: Int,
    val totalAmount: Long,
    val myShare: Long,
    val syncToExpenses: Boolean,
    val expenseTag: String,
    val updatedAt: Long
)

data class SplitBillUiState(
    val isLoading: Boolean = true,
    val sessions: List<SplitBillSessionSummary> = emptyList(),
    val activeSessionId: Long? = null,
    val title: String = "",
    val syncToExpenses: Boolean = true,
    val expenseTag: String = "Makan",
    val availableTags: List<TagEntity> = emptyList(),
    val people: List<SplitPerson> = emptyList(),
    val expenses: List<SplitExpense> = emptyList(),
    val totalAmount: Long = 0L,
    val details: Map<Long, PersonBalanceDetail> = emptyMap(),
    val settlements: List<SettlementTransfer> = emptyList(),
    val settledKeys: Set<String> = emptySet(),
    val selfPerson: SplitPerson? = null,
    val selfShare: Long = 0L,
    val whatsAppSummary: String = "",
    val personError: String? = null,
    val statusMessage: String? = null,
    val isStatusError: Boolean = false
)

private data class SplitBillFeedbackState(
    val personError: String? = null,
    val statusMessage: String? = null,
    val isStatusError: Boolean = false
)

class SplitBillViewModel(
    application: Application,
    private val splitBillRepository: SplitBillRepository,
    private val expenseRepository: ExpenseRepository
) : AndroidViewModel(application) {

    private val _selectedSessionId = MutableStateFlow<Long?>(null)
    private val _draftTitle = MutableStateFlow<Pair<Long, String>?>(null)
    private val _feedback = MutableStateFlow(SplitBillFeedbackState())

    private var titleSaveJob: Job? = null

    val uiState: StateFlow<SplitBillUiState> = combine(
        splitBillRepository.observeSessions(),
        expenseRepository.getAllTags(),
        _selectedSessionId,
        _draftTitle,
        _feedback
    ) { sessions, tags, selectedId, draftTitle, feedback ->
        buildUiState(
            sessions = sessions,
            tags = tags,
            selectedId = selectedId,
            draftTitle = draftTitle,
            feedback = feedback
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SplitBillUiState()
    )

    fun selectSession(sessionId: Long) {
        if (uiState.value.activeSessionId == sessionId) return
        viewModelScope.launch {
            flushTitleUpdate()
            _feedback.update { it.copy(personError = null) }
            _selectedSessionId.value = sessionId
        }
    }

    fun createSession(
        title: String = "",
        includeSelf: Boolean = true,
        selfName: String = "Saya",
        expenseTag: String = "Makan"
    ) {
        viewModelScope.launch {
            flushTitleUpdate()
            val newId = splitBillRepository.createSession(
                title = title,
                includeSelf = includeSelf,
                selfName = selfName,
                expenseTag = expenseTag
            )
            _selectedSessionId.value = newId
            _feedback.update {
                it.copy(
                    personError = null,
                    statusMessage = "Nota baru dibuat.",
                    isStatusError = false
                )
            }
        }
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            if (_draftTitle.value?.first == sessionId) {
                titleSaveJob?.cancel()
                titleSaveJob = null
                _draftTitle.value = null
            }
            val nextId = uiState.value.sessions.firstOrNull { it.id != sessionId }?.id
            splitBillRepository.deleteSession(sessionId)
            if (_selectedSessionId.value == sessionId) {
                _selectedSessionId.value = nextId
            }
            _feedback.update {
                it.copy(
                    personError = null,
                    statusMessage = "Nota dihapus.",
                    isStatusError = false
                )
            }
        }
    }

    fun onTitleChanged(newTitle: String) {
        val sessionId = uiState.value.activeSessionId ?: return
        _draftTitle.value = sessionId to newTitle
        titleSaveJob?.cancel()
        titleSaveJob = viewModelScope.launch {
            delay(300L)
            persistTitle(sessionId, newTitle)
        }
    }

    fun setSyncToExpenses(enabled: Boolean) {
        val sessionId = uiState.value.activeSessionId ?: return
        viewModelScope.launch {
            flushTitleUpdate()
            splitBillRepository.updateSessionMeta(
                sessionId = sessionId,
                syncToExpenses = enabled
            )
        }
    }

    fun setExpenseTag(tag: String) {
        val sessionId = uiState.value.activeSessionId ?: return
        val trimmed = tag.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            flushTitleUpdate()
            splitBillRepository.updateSessionMeta(
                sessionId = sessionId,
                expenseTag = trimmed
            )
        }
    }

    fun clearPersonError() {
        if (_feedback.value.personError != null) {
            _feedback.update { it.copy(personError = null) }
        }
    }

    fun addParticipant(rawName: String, onAdded: () -> Unit = {}) {
        val state = uiState.value
        val sessionId = state.activeSessionId ?: return
        val trimmed = rawName.trim()
        if (trimmed.isEmpty()) {
            _feedback.update { it.copy(personError = "Isi dulu nama peserta.") }
            return
        }
        val duplicate = state.people.any { it.name.trim().equals(trimmed, ignoreCase = true) }
        if (duplicate) {
            _feedback.update {
                it.copy(personError = "Nama $trimmed sudah ada di daftar peserta. Pakai nama yang berbeda.")
            }
            return
        }
        _feedback.update { it.copy(personError = null) }
        viewModelScope.launch {
            splitBillRepository.addParticipant(
                sessionId = sessionId,
                name = trimmed,
                isSelf = false
            )
            onAdded()
        }
    }

    fun setSelfParticipant(participantId: Long) {
        val sessionId = uiState.value.activeSessionId ?: return
        viewModelScope.launch {
            splitBillRepository.setSelfParticipant(sessionId, participantId)
        }
    }

    fun removeParticipant(participantId: Long) {
        val sessionId = uiState.value.activeSessionId ?: return
        viewModelScope.launch {
            splitBillRepository.removeParticipant(sessionId, participantId)
            postStatus("Peserta dihapus.")
        }
    }

    fun saveExpense(
        itemId: Long = 0L,
        description: String,
        subtotal: Long,
        taxPercent: Double,
        discount: Long,
        payerParticipantId: Long,
        splitIds: List<Long>,
        weights: Map<Long, Double>
    ) {
        val sessionId = uiState.value.activeSessionId ?: return
        val netAmount = SplitBillCalculator.computeNetAmount(subtotal, taxPercent, discount)
        if (description.isBlank() || subtotal <= 0L || netAmount <= 0L || splitIds.isEmpty()) return

        viewModelScope.launch {
            splitBillRepository.upsertItem(
                sessionId = sessionId,
                itemId = itemId,
                description = description,
                amount = netAmount,
                payerParticipantId = payerParticipantId,
                splitIds = splitIds,
                weights = weights,
                subtotal = subtotal,
                taxPercent = taxPercent,
                discount = discount
            )
            postStatus(if (itemId == 0L) "Pengeluaran ditambahkan." else "Pengeluaran diperbarui.")
        }
    }

    fun deleteExpense(itemId: Long) {
        val sessionId = uiState.value.activeSessionId ?: return
        viewModelScope.launch {
            splitBillRepository.deleteItem(sessionId, itemId)
            postStatus("Pengeluaran dihapus.")
        }
    }

    fun toggleSettled(settlementKey: String) {
        val sessionId = uiState.value.activeSessionId ?: return
        viewModelScope.launch {
            splitBillRepository.toggleSettled(sessionId, settlementKey)
        }
    }

    fun buildCurrentShareUrl(): String? {
        val state = uiState.value
        if (state.activeSessionId == null || (state.people.isEmpty() && state.expenses.isEmpty())) {
            return null
        }
        viewModelScope.launch { flushTitleUpdate() }
        return SplitBillShareCodec.buildShareUrl(toSharePayload(state))
    }

    fun importFromShareInput(rawInput: String) {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) {
            postStatus("Masukkan tautan nota atau teks JSON terlebih dahulu.", isError = true)
            return
        }
        viewModelScope.launch {
            try {
                flushTitleUpdate()
                val payload = parseImportInput(trimmed)
                val defaultTag = uiState.value.expenseTag.ifBlank { "Makan" }
                val newSessionId = splitBillRepository.importSharePayload(
                    payload = payload,
                    syncToExpenses = true,
                    expenseTag = defaultTag
                )
                _selectedSessionId.value = newSessionId
                _feedback.update { it.copy(personError = null) }
                postStatus(
                    "Nota diimpor: ${payload.people.size} peserta, ${payload.expenses.size} pengeluaran."
                )
            } catch (e: Exception) {
                postStatus(
                    e.localizedMessage ?: "Tautan nota atau berkas JSON tidak valid.",
                    isError = true
                )
            }
        }
    }

    fun importJsonFromUri(uri: Uri) {
        viewModelScope.launch {
            try {
                val jsonText = withContext(Dispatchers.IO) {
                    val stream = getApplication<Application>().contentResolver.openInputStream(uri)
                        ?: throw IOException("Tidak dapat membuka berkas JSON.")
                    stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                }
                val payload = SplitBillShareCodec.importJson(jsonText)
                val defaultTag = uiState.value.expenseTag.ifBlank { "Makan" }
                val newSessionId = splitBillRepository.importSharePayload(
                    payload = payload,
                    syncToExpenses = true,
                    expenseTag = defaultTag
                )
                _selectedSessionId.value = newSessionId
                _feedback.update { it.copy(personError = null) }
                postStatus(
                    "Nota dipulihkan: ${payload.people.size} peserta dan ${payload.expenses.size} pengeluaran."
                )
            } catch (e: Exception) {
                postStatus(
                    "Gagal mengimpor JSON: ${e.localizedMessage ?: "format tidak dikenali"}",
                    isError = true
                )
            }
        }
    }

    fun exportJsonToUri(uri: Uri) {
        viewModelScope.launch {
            try {
                flushTitleUpdate()
                val state = uiState.value
                val json = SplitBillShareCodec.exportJson(toSharePayload(state))
                withContext(Dispatchers.IO) {
                    val stream = getApplication<Application>().contentResolver.openOutputStream(uri)
                        ?: throw IOException("Tidak dapat menulis ke berkas tujuan.")
                    stream.bufferedWriter(Charsets.UTF_8).use { writer ->
                        writer.write(json)
                    }
                }
                postStatus(
                    "Cadangan JSON diekspor (${state.people.size} peserta, ${state.expenses.size} pengeluaran)."
                )
            } catch (e: Exception) {
                postStatus(
                    "Gagal mengekspor JSON: ${e.localizedMessage ?: "kesalahan penyimpanan"}",
                    isError = true
                )
            }
        }
    }

    fun postStatus(message: String, isError: Boolean = false) {
        _feedback.update {
            it.copy(statusMessage = message, isStatusError = isError)
        }
    }

    fun clearStatusMessage() {
        if (_feedback.value.statusMessage != null) {
            _feedback.update { it.copy(statusMessage = null, isStatusError = false) }
        }
    }

    private suspend fun flushTitleUpdate() {
        titleSaveJob?.cancel()
        titleSaveJob = null
        val draft = _draftTitle.value ?: return
        persistTitle(draft.first, draft.second)
        _draftTitle.value = null
    }

    private suspend fun persistTitle(sessionId: Long, newTitle: String) {
        splitBillRepository.updateSessionMeta(sessionId = sessionId, title = newTitle)
        val currentDraft = _draftTitle.value
        if (currentDraft?.first == sessionId &&
            currentDraft.second == newTitle &&
            newTitle == newTitle.trim()
        ) {
            _draftTitle.value = null
        }
    }

    private fun buildUiState(
        sessions: List<SplitBillSessionFull>,
        tags: List<TagEntity>,
        selectedId: Long?,
        draftTitle: Pair<Long, String>?,
        feedback: SplitBillFeedbackState
    ): SplitBillUiState {
        val summaries = sessions.map { full ->
            val people = splitBillRepository.toSplitPeople(full)
            val expenses = splitBillRepository.toSplitExpenses(full)
            val details = SplitBillCalculator.computeDetails(people, expenses)
            val self = people.firstOrNull { it.isSelf }
            val effectiveTitle = if (draftTitle?.first == full.session.id) {
                draftTitle.second
            } else {
                full.session.title
            }
            SplitBillSessionSummary(
                id = full.session.id,
                title = effectiveTitle,
                displayTitle = effectiveTitle.trim().ifEmpty { "Nota #${full.session.id}" },
                participantCount = people.size,
                itemCount = expenses.size,
                totalAmount = expenses.sumOf { it.amount },
                myShare = self?.let { details[it.id]?.share ?: 0L } ?: 0L,
                syncToExpenses = full.session.syncToExpenses,
                expenseTag = full.session.expenseTag,
                updatedAt = full.session.updatedAt
            )
        }

        val activeFull = selectedId?.let { id -> sessions.firstOrNull { it.session.id == id } }
            ?: sessions.firstOrNull()

        if (activeFull == null) {
            return SplitBillUiState(
                isLoading = false,
                sessions = summaries,
                availableTags = tags,
                personError = feedback.personError,
                statusMessage = feedback.statusMessage,
                isStatusError = feedback.isStatusError
            )
        }

        val activeTitle = if (draftTitle?.first == activeFull.session.id) {
            draftTitle.second
        } else {
            activeFull.session.title
        }
        val people = splitBillRepository.toSplitPeople(activeFull)
        val expenses = splitBillRepository.toSplitExpenses(activeFull)
        val totalAmount = expenses.sumOf { it.amount }
        val balances = SplitBillCalculator.computeBalances(people, expenses)
        val details = SplitBillCalculator.computeDetails(people, expenses)
        val settlements = SplitBillCalculator.computeSettlements(people, balances)
        val validKeys = settlements.mapTo(HashSet(settlements.size)) { it.key() }
        val settledKeys = activeFull.settledKeys
            .map { it.settlementKey }
            .filterTo(HashSet()) { it in validKeys }
        val selfPerson = people.firstOrNull { it.isSelf }
        val selfShare = selfPerson?.let { details[it.id]?.share ?: 0L } ?: 0L
        val summaryText = SplitBillCalculator.buildWhatsAppSummary(
            title = activeTitle,
            people = people,
            expenses = expenses,
            details = details,
            settlements = settlements,
            settledKeys = settledKeys
        )

        return SplitBillUiState(
            isLoading = false,
            sessions = summaries,
            activeSessionId = activeFull.session.id,
            title = activeTitle,
            syncToExpenses = activeFull.session.syncToExpenses,
            expenseTag = activeFull.session.expenseTag,
            availableTags = tags,
            people = people,
            expenses = expenses,
            totalAmount = totalAmount,
            details = details,
            settlements = settlements,
            settledKeys = settledKeys,
            selfPerson = selfPerson,
            selfShare = selfShare,
            whatsAppSummary = summaryText,
            personError = feedback.personError,
            statusMessage = feedback.statusMessage,
            isStatusError = feedback.isStatusError
        )
    }

    private fun toSharePayload(state: SplitBillUiState): SplitSharePayload {
        return SplitSharePayload(
            title = state.title.trim(),
            people = state.people,
            expenses = state.expenses,
            settled = state.settledKeys.toList()
        )
    }

    internal fun parseImportInput(rawInput: String): SplitSharePayload {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) {
            throw IllegalArgumentException("Masukkan tautan nota atau teks JSON terlebih dahulu.")
        }
        if (trimmed.startsWith("{")) {
            return SplitBillShareCodec.importJson(trimmed)
        }
        val hashIndex = trimmed.indexOf("#data=")
        if (hashIndex >= 0) {
            val afterHash = trimmed.substring(hashIndex + "#data=".length)
            val token = afterHash.takeWhile { !it.isWhitespace() }
            return SplitBillShareCodec.decodeSharePayload("#data=$token")
        }
        return SplitBillShareCodec.decodeSharePayload(trimmed)
    }
}

class SplitBillViewModelFactory(
    private val application: Application,
    private val splitBillRepository: SplitBillRepository,
    private val expenseRepository: ExpenseRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SplitBillViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SplitBillViewModel(application, splitBillRepository, expenseRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
