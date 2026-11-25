package com.wein.fasttrack.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.wein.fasttrack.BuildConfig
import com.wein.fasttrack.R
import com.wein.fasttrack.data.Expense
import com.wein.fasttrack.data.TagEntity
import com.wein.fasttrack.repository.ThemeMode
import com.wein.fasttrack.ui.theme.WarningDark
import com.wein.fasttrack.ui.theme.WarningLight
import com.wein.fasttrack.utils.BiometricAuthManager
import com.wein.fasttrack.utils.CsvExporter
import com.wein.fasttrack.viewmodel.ExpenseViewModel
import com.wein.fasttrack.viewmodel.FastTrackUiState
import com.wein.fasttrack.viewmodel.SpendingStatus
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FastTrackScreen(
    viewModel: ExpenseViewModel,
    activity: FragmentActivity,
    onOpenSplitBillSession: (Long) -> Unit = {},
    onNavigateToAnalytics: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showRecurringSheet by remember { mutableStateOf(false) }
    var showRestoreWarning by remember { mutableStateOf(false) }
    var showNoteDialog by remember { mutableStateOf(false) }
    var editingExpense by remember { mutableStateOf<Expense?>(null) }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { viewModel.createBackup(it) }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.restoreBackup(it) }
    }

    LaunchedEffect(uiState.backupRestoreMessage) {
        uiState.backupRestoreMessage?.let { message ->
            snackbarHostState.showSnackbar(message = message, duration = SnackbarDuration.Short)
            viewModel.onBackupRestoreMessageHandled()
        }
    }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message = message, duration = SnackbarDuration.Short)
            viewModel.onSnackbarMessageHandled()
        }
    }

    LaunchedEffect(uiState.undoExpenseEvent) {
        uiState.undoExpenseEvent?.let {
            val result = snackbarHostState.showSnackbar(
                message = "Catatan tersimpan",
                actionLabel = "Urungkan",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.onUndoLastExpense()
            }
            viewModel.onUndoEventHandled()
        }
    }

    LaunchedEffect(uiState.exportData) {
        uiState.exportData?.let { expenses ->
            CsvExporter.cleanupOldExports(context)
            val uri = CsvExporter.exportExpensesToCsv(context, expenses)
            if (uri != null) {
                CsvExporter.shareCsv(context, uri)
            }
            viewModel.onExportHandled()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
                        fontWeight = FontWeight.SemiBold
                    )
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.onExportTriggered() },
                        enabled = !uiState.isExporting
                    ) {
                        if (uiState.isExporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = stringResource(R.string.export_csv)
                            )
                        }
                    }
                    IconButton(onClick = { showSettingsSheet = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Pengaturan"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            HeroDisplay(
                uiState = uiState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 8.dp)
            )

            HistoryList(
                expenses = uiState.todayExpenses,
                onExpenseClick = { editingExpense = it },
                onDelete = { viewModel.deleteExpense(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NoteChip(
                    note = uiState.currentNote,
                    onClick = { showNoteDialog = true }
                )

                TagSelector(
                    tags = uiState.availableTags,
                    selectedTag = uiState.selectedTag,
                    onTagSelected = { viewModel.onTagSelected(it.name) },
                    onAddTagClicked = { viewModel.setShowAddTagDialog(true) },
                    onTagLongClicked = { viewModel.setTagToDelete(it) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            CustomKeypad(
                onDigit = { viewModel.onDigitPressed(it) },
                onTripleZero = { viewModel.onTripleZeroPressed() },
                onBackspace = { viewModel.onBackspacePressed() },
                onSave = { viewModel.onSavePressed() },
                saveEnabled = (uiState.currentInput.toLongOrNull() ?: 0L) > 0L,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
            )
        }

        if (showNoteDialog) {
            NoteInputDialog(
                initialNote = uiState.currentNote,
                onDismiss = { showNoteDialog = false },
                onConfirm = { note ->
                    viewModel.onNoteChanged(note)
                    showNoteDialog = false
                }
            )
        }

        editingExpense?.let { target ->
            EditExpenseDialog(
                expense = target,
                availableTags = uiState.availableTags,
                onDismiss = { editingExpense = null },
                onSave = { newAmount, newTag, newNote ->
                    viewModel.updateExpense(target, newAmount, newTag, newNote)
                    editingExpense = null
                },
                onOpenSplitBillSession = if (target.splitBillSessionId != null) {
                    {
                        val sid = target.splitBillSessionId
                        editingExpense = null
                        if (sid != null) {
                            onOpenSplitBillSession(sid)
                        }
                    }
                } else {
                    null
                }
            )
        }

        if (uiState.showAddTagDialog) {
            AddTagDialog(
                onDismiss = { viewModel.setShowAddTagDialog(false) },
                onConfirm = { viewModel.addNewTag(it) }
            )
        }

        uiState.tagToDelete?.let { tag ->
            DeleteTagDialog(
                tag = tag,
                onDismiss = { viewModel.setTagToDelete(null) },
                onConfirm = { viewModel.deleteCustomTag(tag) }
            )
        }

        if (showSettingsSheet) {
            SettingsBottomSheet(
                uiState = uiState,
                activity = activity,
                onDismiss = { showSettingsSheet = false },
                onSaveBudgets = { daily, monthly ->
                    viewModel.saveBudgetCaps(daily, monthly)
                },
                onThemeModeSelected = { viewModel.setThemeMode(it) },
                onOpenRecurring = {
                    showSettingsSheet = false
                    showRecurringSheet = true
                },
                onBiometricToggle = { viewModel.setBiometricEnabled(it) },
                onExportCsv = {
                    showSettingsSheet = false
                    viewModel.onExportTriggered()
                },
                onBackupClick = {
                    showSettingsSheet = false
                    val timestamp = System.currentTimeMillis()
                    createDocumentLauncher.launch("fast_track_backup_$timestamp.json")
                },
                onRestoreClick = {
                    showSettingsSheet = false
                    showRestoreWarning = true
                },
                onCheckUpdate = { viewModel.checkForUpdateManually() }
            )
        }

        if (showRecurringSheet) {
            RecurringExpensesSheet(
                recurringExpenses = uiState.recurringExpenses,
                availableTags = uiState.availableTags,
                statusMessage = uiState.snackbarMessage,
                onDismiss = { showRecurringSheet = false },
                onAddRecurring = { amount, tag, note, day ->
                    viewModel.addRecurringExpense(amount, tag, note, day)
                },
                onUpdateRecurring = { viewModel.updateRecurringExpense(it) },
                onToggleRecurring = { item, enabled ->
                    viewModel.toggleRecurringExpense(item, enabled)
                },
                onDeleteRecurring = { viewModel.deleteRecurringExpense(it) },
                onApplyDueNow = { viewModel.applyDueRecurringNow() }
            )
        }

        if (showRestoreWarning) {
            AlertDialog(
                onDismissRequest = { showRestoreWarning = false },
                title = { Text("Peringatan") },
                text = { Text("Data saat ini akan digantikan oleh isi file cadangan. Lanjutkan?") },
                confirmButton = {
                    TextButton(onClick = {
                        showRestoreWarning = false
                        openDocumentLauncher.launch(arrayOf("application/json", "*/*"))
                    }) { Text("Lanjutkan") }
                },
                dismissButton = {
                    TextButton(onClick = { showRestoreWarning = false }) { Text(stringResource(R.string.cancel)) }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun HeroDisplay(
    uiState: FastTrackUiState,
    modifier: Modifier = Modifier
) {
    val idLocale = remember { Locale("id", "ID") }
    val currencyFormat = remember(idLocale) {
        NumberFormat.getCurrencyInstance(idLocale).apply {
            maximumFractionDigits = 0
        }
    }
    val plainNumberFormat = remember(idLocale) {
        NumberFormat.getNumberInstance(idLocale)
    }

    val inputLong = uiState.currentInput.toLongOrNull() ?: 0L
    val displayAmount = if (uiState.currentInput.isEmpty()) {
        "0"
    } else {
        plainNumberFormat.format(inputLong)
    }

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val warningColor = if (isDark) WarningDark else WarningLight

    val amountColor = when (uiState.spendingStatus) {
        SpendingStatus.NORMAL -> MaterialTheme.colorScheme.onBackground
        SpendingStatus.WARNING -> warningColor
        SpendingStatus.EXCEEDED -> MaterialTheme.colorScheme.error
    }

    val dynamicFontSize = when {
        displayAmount.length <= 7 -> 46.sp
        displayAmount.length <= 10 -> 38.sp
        else -> 30.sp
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${stringResource(R.string.today_total)}: ${currencyFormat.format(uiState.todayTotal)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp
            )
            if (uiState.budgetCap > 0L) {
                Text(
                    text = "/ ${currencyFormat.format(uiState.budgetCap)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = displayAmount,
            color = amountColor,
            fontSize = dynamicFontSize,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            softWrap = false
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryList(
    expenses: List<Expense>,
    onExpenseClick: (Expense) -> Unit,
    onDelete: (Expense) -> Unit,
    modifier: Modifier = Modifier
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale("id", "ID")) }
    val currencyFormat = remember {
        NumberFormat.getCurrencyInstance(Locale("id", "ID")).apply {
            maximumFractionDigits = 0
        }
    }

    if (expenses.isEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.empty_state),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp
            )
        }
        return
    }

    LazyColumn(modifier = modifier.fillMaxWidth()) {
        items(expenses, key = { it.id }) { expense ->
            SwipeableExpenseRow(
                expense = expense,
                timeText = timeFormat.format(Date(expense.timestamp)),
                amountText = currencyFormat.format(expense.amount),
                onClick = { onExpenseClick(expense) },
                onDelete = { onDelete(expense) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableExpenseRow(
    expense: Expense,
    timeText: String,
    amountText: String,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        backgroundContent = {
            val bgColor by animateColorAsState(
                targetValue = if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) {
                    MaterialTheme.colorScheme.error
                } else {
                    Color.Transparent
                },
                label = "dismissColor"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 4.dp)
                    .background(bgColor, RoundedCornerShape(14.dp))
                    .padding(end = 16.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.delete),
                        tint = MaterialTheme.colorScheme.onError
                    )
                }
            }
        },
        enableDismissFromStartToEnd = false
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = expense.tag?.ifBlank { stringResource(R.string.tag_umum) }
                                ?: stringResource(R.string.tag_umum),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (expense.splitBillSessionId != null) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "Patungan",
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    if (!expense.note.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = expense.note,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = timeText,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        fontSize = 11.sp
                    )
                }

                Text(
                    text = amountText,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 15.sp
                )
            }
        }
    }
}

@Composable
fun NoteChip(
    note: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasNote = note.isNotBlank()
    val bgColor = if (hasNote) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val textColor = if (hasNote) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val borderColor = if (hasNote) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    } else {
        MaterialTheme.colorScheme.outline
    }

    Row(
        modifier = modifier
            .widthIn(max = 148.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Create,
            contentDescription = stringResource(R.string.note_label),
            tint = textColor,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = if (hasNote) note.trim() else "+ Catatan",
            color = textColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun CustomKeypad(
    onDigit: (Int) -> Unit,
    onTripleZero: () -> Unit,
    onBackspace: () -> Unit,
    onSave: () -> Unit,
    saveEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val saveText = stringResource(R.string.save)
    val digitRows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9")
    )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        digitRows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { key ->
                    KeypadButton(
                        text = key,
                        modifier = Modifier.weight(1f),
                        onClick = { onDigit(key.toInt()) }
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            KeypadButton(
                text = "0",
                modifier = Modifier.weight(1f),
                onClick = { onDigit(0) }
            )
            KeypadButton(
                text = "000",
                modifier = Modifier.weight(1f),
                onClick = onTripleZero
            )
            KeypadButton(
                text = "⌫",
                modifier = Modifier.weight(1f),
                isSecondary = true,
                onClick = onBackspace
            )
            KeypadButton(
                text = saveText,
                modifier = Modifier.weight(1.25f),
                isAction = true,
                enabled = saveEnabled,
                onClick = onSave
            )
        }
    }
}

@Composable
fun KeypadButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isAction: Boolean = false,
    isSecondary: Boolean = false,
    enabled: Boolean = true
) {
    val haptic = LocalHapticFeedback.current
    val bgColor = when {
        isAction && enabled -> MaterialTheme.colorScheme.primary
        isAction -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
        isSecondary -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surface
    }
    val textColor = when {
        isAction -> MaterialTheme.colorScheme.onPrimary
        isSecondary -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }
    val borderModifier = if (!isAction) {
        Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .height(50.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .then(borderModifier)
            .clickable(enabled = enabled) {
                if (isAction) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                } else {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = when {
                isAction -> 17.sp
                text == "000" -> 18.sp
                else -> 22.sp
            },
            fontWeight = if (isAction) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
fun TagSelector(
    tags: List<TagEntity>,
    selectedTag: String,
    onTagSelected: (TagEntity) -> Unit,
    onAddTagClicked: () -> Unit,
    onTagLongClicked: (TagEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 2.dp)
    ) {
        items(tags, key = { it.id }) { tag ->
            TagChip(
                tag = tag,
                isSelected = tag.name == selectedTag,
                onClick = { onTagSelected(tag) },
                onLongClick = { onTagLongClicked(tag) }
            )
        }
        item {
            AddTagButton(onClick = onAddTagClicked)
        }
    }
}

@Composable
fun AddTagButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Tambah Tag",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Tambah",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TagChip(
    tag: TagEntity,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surface
    }
    val textColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val borderModifier = if (!isSelected) {
        Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .then(borderModifier)
            .combinedClickable(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
                onLongClick = if (!tag.isPreset) onLongClick else null
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = tag.name,
            color = textColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun NoteInputDialog(
    initialNote: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember(initialNote) { mutableStateOf(initialNote) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.note_label)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.note_placeholder)) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.trim()) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            Row {
                if (initialNote.isNotBlank()) {
                    TextButton(onClick = { onConfirm("") }) {
                        Text(
                            text = stringResource(R.string.delete),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
fun EditExpenseDialog(
    expense: Expense,
    availableTags: List<TagEntity>,
    onDismiss: () -> Unit,
    onSave: (newAmount: Long, newTag: String, newNote: String?) -> Unit,
    onOpenSplitBillSession: (() -> Unit)? = null
) {
    var amountText by remember(expense) { mutableStateOf(expense.amount.toString()) }
    var selectedTag by remember(expense) {
        mutableStateOf(expense.tag?.ifBlank { "Umum" } ?: "Umum")
    }
    var noteText by remember(expense) { mutableStateOf(expense.note ?: "") }

    val tagNames = remember(availableTags, selectedTag) {
        val base = availableTags.map { it.name }.ifEmpty {
            listOf("Umum", "Makan", "Transport", "Belanja", "Tagihan", "Jajan")
        }
        if (selectedTag in base) base else base + selectedTag
    }
    val parsedAmount = amountText.filter { it.isDigit() }.toLongOrNull() ?: 0L

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_expense)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (onOpenSplitBillSession != null) {
                    OutlinedButton(
                        onClick = onOpenSplitBillSession,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Buka di Split Bill")
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { raw -> amountText = raw.filter { it.isDigit() }.take(10) },
                    label = { Text("Nominal (Rp)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Kategori",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(tagNames) { tag ->
                        FilterChip(
                            selected = selectedTag == tag,
                            onClick = { selectedTag = tag },
                            label = { Text(tag) }
                        )
                    }
                }

                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text(stringResource(R.string.note_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        parsedAmount,
                        selectedTag,
                        noteText.trim().takeIf { it.isNotEmpty() }
                    )
                },
                enabled = parsedAmount > 0L
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
fun AddTagDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Tambah Kategori Baru") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Nama Kategori") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    focusedLabelColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = text.trim().isNotEmpty()
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
fun DeleteTagDialog(
    tag: TagEntity,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Hapus Kategori?") },
        text = {
            Text(
                text = "Hapus kategori \"${tag.name}\"? Catatan transaksi lama yang menggunakan kategori ini tidak akan terhapus."
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(stringResource(R.string.delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBottomSheet(
    uiState: FastTrackUiState,
    activity: FragmentActivity,
    onDismiss: () -> Unit,
    onSaveBudgets: (dailyCap: Long, monthlyCap: Long) -> Unit,
    onThemeModeSelected: (ThemeMode) -> Unit,
    onOpenRecurring: () -> Unit,
    onBiometricToggle: (Boolean) -> Unit,
    onExportCsv: () -> Unit,
    onBackupClick: () -> Unit,
    onRestoreClick: () -> Unit,
    onCheckUpdate: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    var dailyText by remember(uiState.budgetCap) {
        mutableStateOf(if (uiState.budgetCap > 0L) uiState.budgetCap.toString() else "")
    }
    var monthlyText by remember(uiState.monthlyBudgetCap) {
        mutableStateOf(if (uiState.monthlyBudgetCap > 0L) uiState.monthlyBudgetCap.toString() else "")
    }
    var biometricChecked by remember(uiState.isBiometricEnabled) {
        mutableStateOf(uiState.isBiometricEnabled)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Pengaturan",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Budgets section
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Anggaran",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                OutlinedTextField(
                    value = dailyText,
                    onValueChange = { raw -> dailyText = raw.filter { it.isDigit() }.take(10) },
                    label = { Text(stringResource(R.string.daily_budget)) },
                    supportingText = { Text(stringResource(R.string.daily_budget_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = monthlyText,
                    onValueChange = { raw -> monthlyText = raw.filter { it.isDigit() }.take(12) },
                    label = { Text(stringResource(R.string.monthly_budget)) },
                    supportingText = { Text(stringResource(R.string.monthly_budget_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        onSaveBudgets(
                            dailyText.toLongOrNull() ?: 0L,
                            monthlyText.toLongOrNull() ?: 0L
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Simpan Anggaran")
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            // Theme selector
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.theme_label),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val options = listOf(
                        ThemeMode.SYSTEM to "Sistem",
                        ThemeMode.DARK to stringResource(R.string.theme_dark),
                        ThemeMode.LIGHT to stringResource(R.string.theme_light)
                    )
                    options.forEach { (mode, label) ->
                        FilterChip(
                            selected = uiState.themeMode == mode,
                            onClick = { onThemeModeSelected(mode) },
                            label = { Text(label) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            // Recurring expenses entry point
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onOpenRecurring),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.background,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.recurring_expenses),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val activeCount = uiState.recurringExpenses.count { it.isEnabled }
                        Text(
                            text = if (activeCount > 0) "$activeCount jadwal aktif" else "Kelola tagihan & langganan bulanan",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Biometric lock toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Kunci Biometrik",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Minta sidik jari atau PIN saat membuka aplikasi",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = biometricChecked,
                    onCheckedChange = { isChecked ->
                        if (isChecked) {
                            if (!BiometricAuthManager.canAuthenticate(context)) {
                                android.widget.Toast.makeText(
                                    context,
                                    "Perangkat tidak memiliki biometrik atau PIN aktif.",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                                biometricChecked = false
                            } else {
                                BiometricAuthManager.authenticate(
                                    activity = activity,
                                    onSuccess = {
                                        biometricChecked = true
                                        onBiometricToggle(true)
                                    },
                                    onError = { err ->
                                        android.widget.Toast.makeText(
                                            context,
                                            err,
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                        biometricChecked = false
                                    }
                                )
                            }
                        } else {
                            biometricChecked = false
                            onBiometricToggle(false)
                        }
                    }
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            // Data backup & export
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Data & Cadangan",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )

                if (uiState.isBackupRestoring) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                } else {
                    OutlinedButton(
                        onClick = onExportCsv,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.export_csv))
                    }
                    OutlinedButton(
                        onClick = onBackupClick,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cadangkan Data (JSON)")
                    }
                    OutlinedButton(
                        onClick = onRestoreClick,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Pulihkan Data (JSON)")
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            // App version & update check
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.app_update),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.app_version_format, BuildConfig.VERSION_NAME),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedButton(
                    onClick = onCheckUpdate,
                    enabled = !uiState.isCheckingUpdate,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (uiState.isCheckingUpdate) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Memeriksa…")
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Cek Pembaruan")
                    }
                }
            }

            uiState.snackbarMessage?.let { msg ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = msg,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}
