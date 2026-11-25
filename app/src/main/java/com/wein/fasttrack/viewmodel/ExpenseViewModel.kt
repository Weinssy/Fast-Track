package com.wein.fasttrack.viewmodel

import android.app.Application
import android.net.Uri
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wein.fasttrack.BuildConfig
import com.wein.fasttrack.data.BackupPayload
import com.wein.fasttrack.data.Expense
import com.wein.fasttrack.data.RecurringExpenseEntity
import com.wein.fasttrack.data.TagEntity
import com.wein.fasttrack.repository.ExpenseRepository
import com.wein.fasttrack.repository.SplitBillRepository
import com.wein.fasttrack.repository.ThemeMode
import com.wein.fasttrack.repository.UserPreferencesRepository
import com.wein.fasttrack.utils.AppUpdateManager
import com.wein.fasttrack.utils.BackupManager
import com.wein.fasttrack.utils.GitHubReleaseInfo
import com.wein.fasttrack.utils.UpdateCheckResult
import com.wein.fasttrack.widget.FastTrackWidget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FastTrackUiState(
    val currentInput: String = "",
    val currentNote: String = "",
    val todayTotal: Long = 0L,
    val monthTotal: Long = 0L,
    val todayExpenses: List<Expense> = emptyList(),
    val isExporting: Boolean = false,
    val exportData: List<Expense>? = null,
    val selectedTag: String = "Umum",
    val availableTags: List<TagEntity> = emptyList(),
    val showAddTagDialog: Boolean = false,
    val tagToDelete: TagEntity? = null,
    val budgetCap: Long = 0L,
    val monthlyBudgetCap: Long = 0L,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val spendingStatus: SpendingStatus = SpendingStatus.NORMAL,
    val undoExpenseEvent: Expense? = null,
    val backupRestoreMessage: String? = null,
    val isBackupRestoring: Boolean = false,
    val isBiometricEnabled: Boolean = false,
    val recurringExpenses: List<RecurringExpenseEntity> = emptyList(),
    val isCheckingUpdate: Boolean = false,
    val availableUpdateRelease: GitHubReleaseInfo? = null,
    val snackbarMessage: String? = null
)

enum class SpendingStatus {
    NORMAL, WARNING, EXCEEDED
}

private data class InputSlice(
    val currentInput: String,
    val currentNote: String,
    val selectedTag: String,
    val showAddTagDialog: Boolean,
    val tagToDelete: TagEntity?
)

private data class DataSlice(
    val todayTotal: Long,
    val monthTotal: Long,
    val todayExpenses: List<Expense>,
    val availableTags: List<TagEntity>,
    val recurringExpenses: List<RecurringExpenseEntity>
)

private data class PrefsSlice(
    val dailyCap: Long,
    val monthlyCap: Long,
    val isBiometricEnabled: Boolean,
    val themeMode: ThemeMode
)

private data class ActionSlice(
    val isExporting: Boolean,
    val exportData: List<Expense>?,
    val undoExpenseEvent: Expense?,
    val backupRestoreMessage: String?,
    val isBackupRestoring: Boolean
)

private data class UpdateSlice(
    val isCheckingUpdate: Boolean,
    val availableUpdateRelease: GitHubReleaseInfo?,
    val snackbarMessage: String?
)

class ExpenseViewModel(
    application: Application,
    private val repository: ExpenseRepository,
    private val userPrefs: UserPreferencesRepository,
    private val splitBillRepository: SplitBillRepository? = null
) : AndroidViewModel(application) {

    private val _currentInput = MutableStateFlow("")
    private val _currentNote = MutableStateFlow("")
    private val _isExporting = MutableStateFlow(false)
    private val _exportData = MutableStateFlow<List<Expense>?>(null)
    private val _selectedTag = MutableStateFlow("Umum")
    private val _showAddTagDialog = MutableStateFlow(false)
    private val _tagToDelete = MutableStateFlow<TagEntity?>(null)
    private val _undoExpenseEvent = MutableStateFlow<Expense?>(null)
    private val _backupRestoreMessage = MutableStateFlow<String?>(null)
    private val _isBackupRestoring = MutableStateFlow(false)
    private val _isCheckingUpdate = MutableStateFlow(false)
    private val _availableUpdateRelease = MutableStateFlow<GitHubReleaseInfo?>(null)
    private val _snackbarMessage = MutableStateFlow<String?>(null)
    private var lastInsertedExpense: Expense? = null

    private val inputFlow = combine(
        _currentInput,
        _currentNote,
        _selectedTag,
        _showAddTagDialog,
        _tagToDelete
    ) { input, note, tag, showAddTag, tagToDelete ->
        InputSlice(input, note, tag, showAddTag, tagToDelete)
    }

    private val dataFlow = combine(
        repository.getTodayTotal(),
        repository.getCurrentMonthTotal(),
        repository.getTodayExpenses(),
        repository.getAllTags(),
        repository.getAllRecurringExpenses()
    ) { todayTotal, monthTotal, todayExpenses, tags, recurring ->
        DataSlice(
            todayTotal = todayTotal ?: 0L,
            monthTotal = monthTotal ?: 0L,
            todayExpenses = todayExpenses,
            availableTags = tags,
            recurringExpenses = recurring
        )
    }

    private val prefsFlow = combine(
        userPrefs.dailyBudgetCap,
        userPrefs.monthlyBudgetCap,
        userPrefs.isBiometricEnabled,
        userPrefs.themeMode
    ) { dailyCap, monthlyCap, biometric, themeMode ->
        PrefsSlice(dailyCap, monthlyCap, biometric, themeMode)
    }

    private val actionFlow = combine(
        _isExporting,
        _exportData,
        _undoExpenseEvent,
        _backupRestoreMessage,
        _isBackupRestoring
    ) { isExporting, exportData, undoEvent, backupMsg, isRestoring ->
        ActionSlice(isExporting, exportData, undoEvent, backupMsg, isRestoring)
    }

    private val updateFlow = combine(
        _isCheckingUpdate,
        _availableUpdateRelease,
        _snackbarMessage
    ) { isChecking, release, snackbarMsg ->
        UpdateSlice(isChecking, release, snackbarMsg)
    }

    val uiState: StateFlow<FastTrackUiState> = combine(
        inputFlow,
        dataFlow,
        prefsFlow,
        actionFlow,
        updateFlow
    ) { input, data, prefs, action, update ->
        val status = when {
            prefs.dailyCap == 0L -> SpendingStatus.NORMAL
            data.todayTotal >= prefs.dailyCap -> SpendingStatus.EXCEEDED
            data.todayTotal >= prefs.dailyCap * 0.8 -> SpendingStatus.WARNING
            else -> SpendingStatus.NORMAL
        }

        FastTrackUiState(
            currentInput = input.currentInput,
            currentNote = input.currentNote,
            todayTotal = data.todayTotal,
            monthTotal = data.monthTotal,
            todayExpenses = data.todayExpenses,
            isExporting = action.isExporting,
            exportData = action.exportData,
            selectedTag = input.selectedTag,
            availableTags = data.availableTags,
            showAddTagDialog = input.showAddTagDialog,
            tagToDelete = input.tagToDelete,
            budgetCap = prefs.dailyCap,
            monthlyBudgetCap = prefs.monthlyCap,
            themeMode = prefs.themeMode,
            spendingStatus = status,
            undoExpenseEvent = action.undoExpenseEvent,
            backupRestoreMessage = action.backupRestoreMessage,
            isBackupRestoring = action.isBackupRestoring,
            isBiometricEnabled = prefs.isBiometricEnabled,
            recurringExpenses = data.recurringExpenses,
            isCheckingUpdate = update.isCheckingUpdate,
            availableUpdateRelease = update.availableUpdateRelease,
            snackbarMessage = update.snackbarMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = FastTrackUiState()
    )

    fun onDigitPressed(digit: Int) {
        if (digit !in 0..9) return
        _currentInput.update { current ->
            if (current.length < MAX_INPUT_DIGITS) {
                if (current == "0") digit.toString() else current + digit
            } else {
                current
            }
        }
    }

    fun onTripleZeroPressed() {
        _currentInput.update { current ->
            if (current.isNotEmpty() && current != "0" && current.length + 3 <= MAX_INPUT_DIGITS) {
                current + "000"
            } else {
                current
            }
        }
    }

    fun onBackspacePressed() {
        _currentInput.update { current ->
            if (current.isNotEmpty()) current.dropLast(1) else ""
        }
    }

    fun onClearInput() {
        _currentInput.value = ""
    }

    fun onNoteChanged(note: String) {
        _currentNote.value = note
    }

    fun onSavePressed() {
        val amount = _currentInput.value.toLongOrNull()
        if (amount != null && amount > 0L) {
            viewModelScope.launch {
                val note = _currentNote.value.trim().takeIf { it.isNotEmpty() }
                val expenseToInsert = Expense(
                    amount = amount,
                    tag = _selectedTag.value,
                    note = note
                )
                val id = repository.insertExpense(expenseToInsert)
                val insertedExpense = expenseToInsert.copy(id = id)

                lastInsertedExpense = insertedExpense
                _undoExpenseEvent.value = insertedExpense

                _currentInput.value = ""
                _currentNote.value = ""
                _selectedTag.value = "Umum"
                refreshWidget()
            }
        }
    }

    fun onUndoEventHandled() {
        _undoExpenseEvent.value = null
    }

    fun onUndoLastExpense() {
        lastInsertedExpense?.let { expense ->
            viewModelScope.launch {
                repository.deleteExpense(expense)
                _currentInput.value = expense.amount.toString()
                _selectedTag.value = expense.tag ?: "Umum"
                _currentNote.value = expense.note ?: ""
                lastInsertedExpense = null
                _undoExpenseEvent.value = null
                refreshWidget()
            }
        }
    }

    fun onTagSelected(tag: String) {
        _selectedTag.value = if (_selectedTag.value == tag) "Umum" else tag
    }

    fun updateExpense(expense: Expense, newAmount: Long, newTag: String, newNote: String?) {
        if (newAmount <= 0L) return
        viewModelScope.launch {
            val cleanedTag = newTag.trim().ifBlank { "Umum" }
            val cleanedNote = newNote?.trim()?.takeIf { it.isNotEmpty() }
            repository.updateExpense(
                expense.copy(
                    amount = newAmount,
                    tag = cleanedTag,
                    note = cleanedNote
                )
            )
            refreshWidget()
        }
    }

    fun deleteExpense(expense: Expense) {
        viewModelScope.launch {
            repository.deleteExpense(expense)
            refreshWidget()
        }
    }

    fun setShowAddTagDialog(show: Boolean) {
        _showAddTagDialog.value = show
    }

    fun addNewTag(name: String) {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty()) {
            viewModelScope.launch {
                repository.insertTag(TagEntity(name = trimmed))
                _selectedTag.value = trimmed
                _showAddTagDialog.value = false
            }
        }
    }

    fun setTagToDelete(tag: TagEntity?) {
        _tagToDelete.value = tag
    }

    fun deleteCustomTag(tag: TagEntity) {
        viewModelScope.launch {
            repository.deleteTag(tag)
            if (_selectedTag.value == tag.name) {
                _selectedTag.value = "Umum"
            }
            _tagToDelete.value = null
        }
    }

    fun onExportTriggered() {
        viewModelScope.launch {
            _isExporting.value = true
            val allExpenses = repository.getAllExpenses()
            if (allExpenses.isEmpty()) {
                _snackbarMessage.value = "Belum ada transaksi untuk diekspor."
            } else {
                _exportData.value = allExpenses
            }
            _isExporting.value = false
        }
    }

    fun onExportHandled() {
        _exportData.value = null
    }

    fun setDailyBudgetCap(amount: Long) {
        viewModelScope.launch {
            userPrefs.setDailyBudgetCap(amount.coerceAtLeast(0L))
            refreshWidget()
        }
    }

    fun setMonthlyBudgetCap(amount: Long) {
        viewModelScope.launch {
            userPrefs.setMonthlyBudgetCap(amount.coerceAtLeast(0L))
        }
    }

    fun saveBudgetCaps(dailyCap: Long, monthlyCap: Long) {
        viewModelScope.launch {
            userPrefs.setDailyBudgetCap(dailyCap.coerceAtLeast(0L))
            userPrefs.setMonthlyBudgetCap(monthlyCap.coerceAtLeast(0L))
            refreshWidget()
            _snackbarMessage.value = "Pengaturan anggaran disimpan"
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            userPrefs.setThemeMode(mode)
            refreshWidget()
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPrefs.setBiometricEnabled(enabled)
        }
    }

    fun addRecurringExpense(amount: Long, tag: String, note: String?, dayOfMonth: Int) {
        if (amount <= 0L) return
        viewModelScope.launch {
            repository.insertRecurringExpense(
                RecurringExpenseEntity(
                    amount = amount,
                    tag = tag.trim().ifBlank { "Tagihan" },
                    note = note?.trim()?.takeIf { it.isNotEmpty() },
                    dayOfMonth = dayOfMonth.coerceIn(1, 31),
                    isEnabled = true
                )
            )
        }
    }

    fun updateRecurringExpense(item: RecurringExpenseEntity) {
        if (item.amount <= 0L) return
        viewModelScope.launch {
            repository.updateRecurringExpense(
                item.copy(
                    tag = item.tag.trim().ifBlank { "Tagihan" },
                    note = item.note?.trim()?.takeIf { it.isNotEmpty() },
                    dayOfMonth = item.dayOfMonth.coerceIn(1, 31)
                )
            )
        }
    }

    fun toggleRecurringExpense(item: RecurringExpenseEntity, enabled: Boolean) {
        viewModelScope.launch {
            repository.updateRecurringExpense(item.copy(isEnabled = enabled))
        }
    }

    fun deleteRecurringExpense(item: RecurringExpenseEntity) {
        viewModelScope.launch {
            repository.deleteRecurringExpense(item)
        }
    }

    fun applyDueRecurringNow() {
        viewModelScope.launch {
            val applied = repository.applyDueRecurringExpenses(getApplication())
            _snackbarMessage.value = if (applied > 0) {
                "$applied pengeluaran rutin diterapkan"
            } else {
                "Tidak ada pengeluaran rutin yang jatuh tempo saat ini"
            }
        }
    }

    fun checkForUpdateManually() {
        if (_isCheckingUpdate.value) return
        viewModelScope.launch {
            _isCheckingUpdate.value = true
            val result = AppUpdateManager.checkForUpdate(BuildConfig.VERSION_NAME)
            userPrefs.setLastUpdateCheckAt(System.currentTimeMillis())
            result.onSuccess { checkResult ->
                when (checkResult) {
                    is UpdateCheckResult.UpdateAvailable -> {
                        _availableUpdateRelease.value = checkResult.release
                    }
                    is UpdateCheckResult.UpToDate -> {
                        _snackbarMessage.value = "Sudah menggunakan versi terbaru"
                    }
                }
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Gagal memeriksa pembaruan"
            }
            _isCheckingUpdate.value = false
        }
    }

    fun dismissUpdateDialog() {
        _availableUpdateRelease.value = null
    }

    fun ignoreUpdateVersion(versionName: String) {
        viewModelScope.launch {
            userPrefs.setIgnoredUpdateVersion(versionName)
            _availableUpdateRelease.value = null
        }
    }

    fun onSnackbarMessageHandled() {
        _snackbarMessage.value = null
    }

    fun onBackupRestoreMessageHandled() {
        _backupRestoreMessage.value = null
    }

    fun createBackup(uri: Uri) {
        viewModelScope.launch {
            _isBackupRestoring.value = true
            try {
                val tags = repository.getAllTagsSync()
                val expenses = repository.getAllExpenses()
                val dailyCap = userPrefs.dailyBudgetCap.first()
                val monthlyCap = userPrefs.monthlyBudgetCap.first()
                val themeMode = userPrefs.themeMode.first().name
                val recurringExpenses = repository.getAllRecurringExpensesSync()
                val splitBillSessions = splitBillRepository?.getAllSessionsSync() ?: emptyList()

                val payload = BackupPayload(
                    version = 2,
                    exportedAt = System.currentTimeMillis(),
                    dailyBudgetCap = dailyCap,
                    tags = tags,
                    expenses = expenses,
                    monthlyBudgetCap = monthlyCap,
                    themeMode = themeMode,
                    recurringExpenses = recurringExpenses,
                    splitBillSessions = splitBillSessions
                )
                val result = BackupManager.createBackup(getApplication(), uri, payload)
                result.onSuccess { count ->
                    _backupRestoreMessage.value = "Berhasil mencadangkan $count transaksi."
                }.onFailure {
                    _backupRestoreMessage.value = "Gagal mencadangkan: ${it.localizedMessage}"
                }
            } catch (e: Exception) {
                _backupRestoreMessage.value = "Gagal mencadangkan: ${e.localizedMessage}"
            } finally {
                _isBackupRestoring.value = false
            }
        }
    }

    fun restoreBackup(uri: Uri) {
        viewModelScope.launch {
            _isBackupRestoring.value = true
            val result = BackupManager.restoreBackup(getApplication(), uri)
            result.onSuccess { payload ->
                try {
                    repository.restoreDatabase(
                        tags = payload.tags,
                        expenses = payload.expenses,
                        recurringExpenses = payload.recurringExpenses,
                        splitBillSessions = payload.splitBillSessions
                    )
                    userPrefs.setDailyBudgetCap(payload.dailyBudgetCap)
                    userPrefs.setMonthlyBudgetCap(payload.monthlyBudgetCap)
                    val restoredTheme = runCatching { ThemeMode.valueOf(payload.themeMode) }
                        .getOrDefault(ThemeMode.DARK)
                    userPrefs.setThemeMode(restoredTheme)

                    _backupRestoreMessage.value = "Pemulihan berhasil: ${payload.expenses.size} transaksi dipulihkan"
                    refreshWidget()
                } catch (e: Exception) {
                    _backupRestoreMessage.value = "Gagal menyimpan ke database: ${e.localizedMessage}"
                }
            }.onFailure {
                _backupRestoreMessage.value = "Gagal membaca berkas: ${it.localizedMessage}"
            }
            _isBackupRestoring.value = false
        }
    }

    private suspend fun refreshWidget() {
        runCatching {
            FastTrackWidget().updateAll(getApplication())
        }
    }

    private companion object {
        const val MAX_INPUT_DIGITS = 10
    }
}

class ExpenseViewModelFactory(
    private val application: Application,
    private val repository: ExpenseRepository,
    private val userPrefs: UserPreferencesRepository,
    private val splitBillRepository: SplitBillRepository? = null
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ExpenseViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ExpenseViewModel(application, repository, userPrefs, splitBillRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
