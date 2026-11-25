package com.wein.fasttrack.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wein.fasttrack.R
import com.wein.fasttrack.splitbill.PersonBalanceDetail
import com.wein.fasttrack.splitbill.SettlementTransfer
import com.wein.fasttrack.splitbill.SplitBillCalculator
import com.wein.fasttrack.splitbill.SplitExpense
import com.wein.fasttrack.splitbill.SplitPerson
import com.wein.fasttrack.viewmodel.SplitBillSessionSummary
import com.wein.fasttrack.viewmodel.SplitBillUiState
import com.wein.fasttrack.viewmodel.SplitBillViewModel
import java.math.BigDecimal
import kotlin.math.abs
import kotlin.math.floor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitBillScreen(
    viewModel: SplitBillViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var showNewSessionDialog by remember { mutableStateOf(false) }
    var sessionToDelete by remember { mutableStateOf<SplitBillSessionSummary?>(null) }
    var participantToDelete by remember { mutableStateOf<SplitPerson?>(null) }
    var showExpenseEditor by remember { mutableStateOf(false) }
    var editingExpense by remember { mutableStateOf<SplitExpense?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }

    val createJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { viewModel.exportJsonToUri(it) }
    }

    val openJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importJsonFromUri(it) }
    }

    LaunchedEffect(uiState.statusMessage) {
        val message = uiState.statusMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            message = message,
            duration = SnackbarDuration.Short
        )
        viewModel.clearStatusMessage()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            SplitBillHeader(
                sessionCount = uiState.sessions.size,
                onImportClick = { showImportDialog = true },
                onNewSessionClick = { showNewSessionDialog = true }
            )

            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                uiState.sessions.isEmpty() -> {
                    SplitBillEmptyState(
                        onCreateFirstSession = {
                            viewModel.createSession(title = "", includeSelf = true)
                        },
                        onImportClick = { showImportDialog = true }
                    )
                }

                else -> {
                    SplitBillActiveContent(
                        uiState = uiState,
                        onSelectSession = { viewModel.selectSession(it) },
                        onDeleteSessionRequest = { sessionToDelete = it },
                        onTitleChanged = { viewModel.onTitleChanged(it) },
                        onSyncToggle = { viewModel.setSyncToExpenses(it) },
                        onTagSelected = { viewModel.setExpenseTag(it) },
                        onParticipantErrorClear = { viewModel.clearPersonError() },
                        onAddParticipant = { name, onAdded ->
                            viewModel.addParticipant(name, onAdded)
                        },
                        onSetSelf = { viewModel.setSelfParticipant(it) },
                        onDeleteParticipantRequest = { participantToDelete = it },
                        onAddExpenseClick = {
                            editingExpense = null
                            showExpenseEditor = true
                        },
                        onEditExpenseClick = { expense ->
                            editingExpense = expense
                            showExpenseEditor = true
                        },
                        onDeleteExpenseClick = { viewModel.deleteExpense(it.id) },
                        onToggleSettled = { viewModel.toggleSettled(it) },
                        onCopyWhatsApp = {
                            if (uiState.whatsAppSummary.isBlank()) {
                                viewModel.postStatus("Belum ada hitungan untuk disalin.", isError = true)
                            } else {
                                copyToClipboard(context, "Ringkasan Split Bill", uiState.whatsAppSummary)
                                viewModel.postStatus("Ringkasan tersalin. Tinggal tempel ke WhatsApp.")
                            }
                        },
                        onShareWhatsAppText = {
                            if (uiState.whatsAppSummary.isBlank()) {
                                viewModel.postStatus("Belum ada hitungan untuk dibagikan.", isError = true)
                            } else {
                                sharePlainText(context, "Bagikan Ringkasan Split Bill", uiState.whatsAppSummary)
                            }
                        },
                        onCopyShareLink = {
                            val url = viewModel.buildCurrentShareUrl()
                            if (url == null) {
                                viewModel.postStatus("Belum ada data nota untuk dibagikan.", isError = true)
                            } else {
                                copyToClipboard(context, "Link Nota Split Bill", url)
                                viewModel.postStatus("Link nota tersalin. Siap dibagikan ke teman.")
                            }
                        },
                        onShareLink = {
                            val url = viewModel.buildCurrentShareUrl()
                            if (url == null) {
                                viewModel.postStatus("Belum ada data nota untuk dibagikan.", isError = true)
                            } else {
                                sharePlainText(context, "Bagikan Link Nota", url)
                            }
                        },
                        onExportJson = {
                            createJsonLauncher.launch(buildExportFilename(uiState.title))
                        },
                        onImportClick = { showImportDialog = true }
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }

    if (showNewSessionDialog) {
        NewSessionDialog(
            availableTags = resolveTagOptions(uiState),
            defaultTag = uiState.expenseTag.ifBlank { "Makan" },
            onDismiss = { showNewSessionDialog = false },
            onCreate = { title, includeSelf, tag ->
                showNewSessionDialog = false
                viewModel.createSession(
                    title = title,
                    includeSelf = includeSelf,
                    expenseTag = tag
                )
            }
        )
    }

    sessionToDelete?.let { session ->
        DeleteSessionDialog(
            session = session,
            onDismiss = { sessionToDelete = null },
            onConfirm = {
                sessionToDelete = null
                viewModel.deleteSession(session.id)
            }
        )
    }

    participantToDelete?.let { person ->
        val affectedCount = uiState.expenses.count {
            it.payerId == person.id || person.id in it.splitIds
        }
        DeleteParticipantDialog(
            person = person,
            affectedExpenseCount = affectedCount,
            onDismiss = { participantToDelete = null },
            onConfirm = {
                participantToDelete = null
                viewModel.removeParticipant(person.id)
            }
        )
    }

    if (showImportDialog) {
        ImportSplitBillDialog(
            onDismiss = { showImportDialog = false },
            onImportText = { raw ->
                showImportDialog = false
                viewModel.importFromShareInput(raw)
            },
            onPickJsonFile = {
                showImportDialog = false
                openJsonLauncher.launch(arrayOf("application/json", "*/*"))
            }
        )
    }

    if (showExpenseEditor) {
        ExpenseEditorBottomSheet(
            people = uiState.people,
            defaultPayerId = uiState.selfPerson?.id ?: uiState.people.firstOrNull()?.id,
            editingExpense = editingExpense,
            onDismiss = {
                showExpenseEditor = false
                editingExpense = null
            },
            onSave = { desc, subtotal, taxPercent, discount, payerId, splitIds, weights ->
                val itemId = editingExpense?.id ?: 0L
                showExpenseEditor = false
                editingExpense = null
                viewModel.saveExpense(
                    itemId = itemId,
                    description = desc,
                    subtotal = subtotal,
                    taxPercent = taxPercent,
                    discount = discount,
                    payerParticipantId = payerId,
                    splitIds = splitIds,
                    weights = weights
                )
            }
        )
    }
}

@Composable
private fun SplitBillHeader(
    sessionCount: Int,
    onImportClick: () -> Unit,
    onNewSessionClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.split_bill),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = if (sessionCount > 0) "$sessionCount nota tersimpan" else "Bagi tagihan & sinkron otomatis",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onImportClick,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text("Impor")
            }
            Button(
                onClick = onNewSessionClick,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Nota Baru")
            }
        }
    }
}

@Composable
private fun SplitBillEmptyState(
    onCreateFirstSession: () -> Unit,
    onImportClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.split_bill_empty),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Catat siapa bayar apa, atur porsi tidak rata, hitung transfer minimal, dan sinkronkan otomatis jatahmu ke Fast Track.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = onCreateFirstSession,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Buat Nota Baru")
                }
                OutlinedButton(
                    onClick = onImportClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.split_bill_import))
                }
            }
        }
    }
}

@Composable
private fun SplitBillActiveContent(
    uiState: SplitBillUiState,
    onSelectSession: (Long) -> Unit,
    onDeleteSessionRequest: (SplitBillSessionSummary) -> Unit,
    onTitleChanged: (String) -> Unit,
    onSyncToggle: (Boolean) -> Unit,
    onTagSelected: (String) -> Unit,
    onParticipantErrorClear: () -> Unit,
    onAddParticipant: (String, () -> Unit) -> Unit,
    onSetSelf: (Long) -> Unit,
    onDeleteParticipantRequest: (SplitPerson) -> Unit,
    onAddExpenseClick: () -> Unit,
    onEditExpenseClick: (SplitExpense) -> Unit,
    onDeleteExpenseClick: (SplitExpense) -> Unit,
    onToggleSettled: (String) -> Unit,
    onCopyWhatsApp: () -> Unit,
    onShareWhatsAppText: () -> Unit,
    onCopyShareLink: () -> Unit,
    onShareLink: () -> Unit,
    onExportJson: () -> Unit,
    onImportClick: () -> Unit
) {
    val activeSummary = uiState.sessions.firstOrNull { it.id == uiState.activeSessionId }
    val tagOptions = remember(uiState.availableTags, uiState.expenseTag) {
        resolveTagOptions(uiState)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "session_selector") {
            SessionSelectorStrip(
                sessions = uiState.sessions,
                activeSessionId = uiState.activeSessionId,
                onSelectSession = onSelectSession
            )
        }

        item(key = "meta_and_sync") {
            SessionMetaAndSyncCard(
                title = uiState.title,
                syncToExpenses = uiState.syncToExpenses,
                expenseTag = uiState.expenseTag,
                tagOptions = tagOptions,
                selfPerson = uiState.selfPerson,
                selfShare = uiState.selfShare,
                canDeleteSession = activeSummary != null,
                onTitleChanged = onTitleChanged,
                onDeleteSessionClick = { activeSummary?.let(onDeleteSessionRequest) },
                onSyncToggle = onSyncToggle,
                onTagSelected = onTagSelected
            )
        }

        item(key = "participants") {
            ParticipantsCard(
                people = uiState.people,
                personError = uiState.personError,
                onErrorClear = onParticipantErrorClear,
                onAddParticipant = onAddParticipant,
                onSetSelf = onSetSelf,
                onDeleteRequest = onDeleteParticipantRequest
            )
        }

        item(key = "expenses") {
            ExpensesCard(
                people = uiState.people,
                expenses = uiState.expenses,
                onAddExpenseClick = onAddExpenseClick,
                onEditExpenseClick = onEditExpenseClick,
                onDeleteExpenseClick = onDeleteExpenseClick
            )
        }

        item(key = "results") {
            ResultsAndSettlementCard(
                people = uiState.people,
                expenses = uiState.expenses,
                totalAmount = uiState.totalAmount,
                details = uiState.details,
                settlements = uiState.settlements,
                settledKeys = uiState.settledKeys,
                onToggleSettled = onToggleSettled
            )
        }

        item(key = "share_and_backup") {
            ShareAndBackupCard(
                hasPeopleOrExpenses = uiState.people.isNotEmpty() || uiState.expenses.isNotEmpty(),
                hasExpenses = uiState.expenses.isNotEmpty(),
                onCopyWhatsApp = onCopyWhatsApp,
                onShareWhatsAppText = onShareWhatsAppText,
                onCopyShareLink = onCopyShareLink,
                onShareLink = onShareLink,
                onExportJson = onExportJson,
                onImportClick = onImportClick
            )
        }
    }
}

@Composable
private fun SessionSelectorStrip(
    sessions: List<SplitBillSessionSummary>,
    activeSessionId: Long?,
    onSelectSession: (Long) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(sessions, key = { it.id }) { session ->
            val selected = session.id == activeSessionId
            FilterChip(
                selected = selected,
                onClick = { onSelectSession(session.id) },
                label = {
                    Text(
                        text = "${session.displayTitle} · ${SplitBillCalculator.formatRupiah(session.totalAmount)}",
                        style = tabularTextStyle(MaterialTheme.typography.labelLarge),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    }
}

@Composable
private fun SessionMetaAndSyncCard(
    title: String,
    syncToExpenses: Boolean,
    expenseTag: String,
    tagOptions: List<String>,
    selfPerson: SplitPerson?,
    selfShare: Long,
    canDeleteSession: Boolean,
    onTitleChanged: (String) -> Unit,
    onDeleteSessionClick: () -> Unit,
    onSyncToggle: (Boolean) -> Unit,
    onTagSelected: (String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = onTitleChanged,
                    label = { Text("Judul nota (opsional)") },
                    placeholder = { Text("Contoh: Makan Malam Kampus") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier.weight(1f)
                )
                if (canDeleteSession) {
                    IconButton(onClick = onDeleteSessionClick) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Hapus Nota",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Sinkron Otomatis ke Fast Track",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    when {
                        !syncToExpenses -> {
                            Text(
                                text = "Nonaktif · Jatah Anda tidak dicatat ke pengeluaran pribadi.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        selfPerson == null -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Belum ada peserta bertanda \"Saya\". Ketuk bintang pada peserta.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }

                        selfShare > 0L -> {
                            Text(
                                text = "Jatah Saya (${selfPerson.name}): ${SplitBillCalculator.formatRupiah(selfShare)} · Tercatat otomatis di pengeluaran",
                                style = tabularTextStyle(MaterialTheme.typography.bodySmall),
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        else -> {
                            Text(
                                text = "Jatah Saya (${selfPerson.name}): Rp0 · Tambah pengeluaran untuk sinkron otomatis",
                                style = tabularTextStyle(MaterialTheme.typography.bodySmall),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Switch(
                    checked = syncToExpenses,
                    onCheckedChange = onSyncToggle
                )
            }

            if (syncToExpenses) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Kategori catatan Fast Track:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(tagOptions, key = { it }) { tagName ->
                            FilterChip(
                                selected = tagName == expenseTag,
                                onClick = { onTagSelected(tagName) },
                                label = { Text(tagName) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ParticipantsCard(
    people: List<SplitPerson>,
    personError: String?,
    onErrorClear: () -> Unit,
    onAddParticipant: (String, () -> Unit) -> Unit,
    onSetSelf: (Long) -> Unit,
    onDeleteRequest: (SplitPerson) -> Unit
) {
    var inputName by remember { mutableStateOf("") }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Peserta (${people.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Ketuk ★ untuk tandai Saya",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputName,
                    onValueChange = {
                        inputName = it
                        onErrorClear()
                    },
                    placeholder = { Text("Nama peserta, mis. Andi") },
                    singleLine = true,
                    isError = personError != null,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            onAddParticipant(inputName) { inputName = "" }
                        }
                    ),
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        onAddParticipant(inputName) { inputName = "" }
                    }
                ) {
                    Text("Tambah")
                }
            }

            if (personError != null) {
                Text(
                    text = personError,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (people.isEmpty()) {
                Text(
                    text = "Belum ada peserta. Tambahkan minimal 2 orang.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    people.forEach { person ->
                        ParticipantChip(
                            person = person,
                            onSelectSelf = { onSetSelf(person.id) },
                            onDeleteClick = { onDeleteRequest(person) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ParticipantChip(
    person: SplitPerson,
    onSelectSelf: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val containerColor = if (person.isSelf) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val borderColor = if (person.isSelf) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    val textColor = if (person.isSelf) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Surface(
        shape = RoundedCornerShape(50),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onSelectSelf)
                .padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = if (person.isSelf) "Saya" else "Tandai sebagai Saya",
                tint = if (person.isSelf) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                },
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = person.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = textColor
            )
            if (person.isSelf) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Text(
                        text = "Saya",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onDeleteClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Hapus ${person.name}",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun ExpensesCard(
    people: List<SplitPerson>,
    expenses: List<SplitExpense>,
    onAddExpenseClick: () -> Unit,
    onEditExpenseClick: (SplitExpense) -> Unit,
    onDeleteExpenseClick: (SplitExpense) -> Unit
) {
    val nameMap = remember(people) { people.associate { it.id to it.name } }
    val nameOf: (Long) -> String = { id -> nameMap[id] ?: "?" }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Pengeluaran (${expenses.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Button(
                    onClick = onAddExpenseClick,
                    enabled = people.isNotEmpty(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Tambah")
                }
            }

            when {
                people.isEmpty() -> {
                    Text(
                        text = "Tambahkan peserta terlebih dahulu sebelum mencatat pengeluaran.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                expenses.isEmpty() -> {
                    Text(
                        text = "Belum ada pengeluaran.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                else -> {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        expenses.asReversed().forEach { expense ->
                            ExpenseItemCard(
                                expense = expense,
                                nameOf = nameOf,
                                onEditClick = { onEditExpenseClick(expense) },
                                onDeleteClick = { onDeleteExpenseClick(expense) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpenseItemCard(
    expense: SplitExpense,
    nameOf: (Long) -> String,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = expense.desc,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                )
                Text(
                    text = SplitBillCalculator.formatRupiah(expense.amount),
                    style = tabularTextStyle(MaterialTheme.typography.titleSmall),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Text(
                text = "Dibayar ${nameOf(expense.payerId)} · Dibagi ke ${SplitBillCalculator.describeSplit(expense, nameOf)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (expense.taxPercent > 0.0 || expense.discount > 0L) {
                val adjustmentParts = buildList {
                    add("Subtotal ${SplitBillCalculator.formatRupiah(expense.subtotal)}")
                    if (expense.taxPercent > 0.0) {
                        add("pajak ${formatDecimal(expense.taxPercent)}%")
                    }
                    if (expense.discount > 0L) {
                        add("diskon ${SplitBillCalculator.formatRupiah(expense.discount)}")
                    }
                }
                Text(
                    text = adjustmentParts.joinToString(" · "),
                    style = tabularTextStyle(MaterialTheme.typography.labelSmall),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onEditClick,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Edit")
                }
                TextButton(
                    onClick = onDeleteClick,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Hapus")
                }
            }
        }
    }
}

@Composable
private fun ResultsAndSettlementCard(
    people: List<SplitPerson>,
    expenses: List<SplitExpense>,
    totalAmount: Long,
    details: Map<Long, PersonBalanceDetail>,
    settlements: List<SettlementTransfer>,
    settledKeys: Set<String>,
    onToggleSettled: (String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Siapa Transfer ke Siapa",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = SplitBillCalculator.formatRupiah(totalAmount),
                    style = tabularTextStyle(MaterialTheme.typography.titleMedium),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (people.isEmpty() || expenses.isEmpty()) {
                Text(
                    text = "Belum ada hitungan. Tambah peserta dan pengeluaran dulu.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "Rincian per Orang",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    people.forEach { person ->
                        val detail = details[person.id]
                            ?: PersonBalanceDetail(person.id, person.name, 0L, 0L, 0L)
                        PersonBalanceCard(person = person, detail = detail)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Text(
                    text = "Daftar Transfer Minimal",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (settlements.isEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "Semua sudah lunas, tidak ada transfer yang perlu dilakukan.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        settlements.forEach { transfer ->
                            val key = transfer.key()
                            val isSettled = key in settledKeys
                            SettlementTransferRow(
                                transfer = transfer,
                                isSettled = isSettled,
                                onToggle = { onToggleSettled(key) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PersonBalanceCard(
    person: SplitPerson,
    detail: PersonBalanceDetail
) {
    val (statusText, statusBg, statusFg) = when {
        detail.balance > 0L -> Triple(
            "kelebihan ${SplitBillCalculator.formatRupiah(detail.balance)}",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )

        detail.balance < 0L -> Triple(
            "kurang ${SplitBillCalculator.formatRupiah(-detail.balance)}",
            MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
            MaterialTheme.colorScheme.error
        )

        else -> Triple(
            "lunas",
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.primary
        )
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = person.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (person.isSelf) {
                        Text(
                            text = "(Saya)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Text(
                    text = "Sudah bayar ${SplitBillCalculator.formatRupiah(detail.paid)} · Jatah ${SplitBillCalculator.formatRupiah(detail.share)}",
                    style = tabularTextStyle(MaterialTheme.typography.bodySmall),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                shape = RoundedCornerShape(50),
                color = statusBg
            ) {
                Text(
                    text = statusText,
                    style = tabularTextStyle(MaterialTheme.typography.labelMedium),
                    fontWeight = FontWeight.Bold,
                    color = statusFg,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun SettlementTransferRow(
    transfer: SettlementTransfer,
    isSettled: Boolean,
    onToggle: () -> Unit
) {
    val bgColor = if (isSettled) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    }
    val borderColor = if (isSettled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    val textColor = if (isSettled) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val decoration = if (isSettled) TextDecoration.LineThrough else TextDecoration.None

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Text(
                    text = "${transfer.from} \u2192 ${transfer.to}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = decoration,
                    color = textColor
                )
                Text(
                    text = SplitBillCalculator.formatRupiah(transfer.amount),
                    style = tabularTextStyle(MaterialTheme.typography.titleSmall),
                    fontWeight = FontWeight.Bold,
                    textDecoration = decoration,
                    color = if (isSettled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                )
            }

            FilterChip(
                selected = isSettled,
                onClick = onToggle,
                label = {
                    Text(if (isSettled) "Lunas" else "Tandai Lunas")
                },
                leadingIcon = if (isSettled) {
                    {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else {
                    null
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    }
}

@Composable
private fun ShareAndBackupCard(
    hasPeopleOrExpenses: Boolean,
    hasExpenses: Boolean,
    onCopyWhatsApp: () -> Unit,
    onShareWhatsAppText: () -> Unit,
    onCopyShareLink: () -> Unit,
    onShareLink: () -> Unit,
    onExportJson: () -> Unit,
    onImportClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Bagikan & Cadangkan",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Button(
                onClick = onCopyWhatsApp,
                enabled = hasExpenses,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Salin Ringkasan WhatsApp")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onShareWhatsAppText,
                    enabled = hasExpenses,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Bagikan Teks")
                }
                OutlinedButton(
                    onClick = onCopyShareLink,
                    enabled = hasPeopleOrExpenses,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.split_bill_share_link))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onExportJson,
                    enabled = hasPeopleOrExpenses,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Ekspor JSON")
                }
                OutlinedButton(
                    onClick = onImportClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.split_bill_import))
                }
            }

            if (hasPeopleOrExpenses) {
                TextButton(
                    onClick = onShareLink,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Bagikan Link Nota ke Aplikasi Lain")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ExpenseEditorBottomSheet(
    people: List<SplitPerson>,
    defaultPayerId: Long?,
    editingExpense: SplitExpense?,
    onDismiss: () -> Unit,
    onSave: (
        description: String,
        subtotal: Long,
        taxPercent: Double,
        discount: Long,
        payerId: Long,
        splitIds: List<Long>,
        weights: Map<Long, Double>
    ) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var desc by remember(editingExpense) {
        mutableStateOf(editingExpense?.desc ?: "")
    }
    var subtotalDigits by remember(editingExpense) {
        val initialSub = editingExpense?.let {
            if (it.subtotal > 0L) it.subtotal else it.amount
        } ?: 0L
        mutableStateOf(if (initialSub > 0L) initialSub.toString() else "")
    }
    var payerId by remember(editingExpense, people) {
        mutableStateOf(
            editingExpense?.payerId?.takeIf { id -> people.any { it.id == id } }
                ?: defaultPayerId
                ?: people.firstOrNull()?.id
                ?: 0L
        )
    }
    var showExtras by remember(editingExpense) {
        mutableStateOf(
            (editingExpense?.taxPercent ?: 0.0) > 0.0 || (editingExpense?.discount ?: 0L) > 0L
        )
    }
    var taxText by remember(editingExpense) {
        val initialTax = editingExpense?.taxPercent ?: 0.0
        mutableStateOf(if (initialTax > 0.0) formatDecimal(initialTax, dotSeparator = true) else "")
    }
    var discountDigits by remember(editingExpense) {
        val initialDisc = editingExpense?.discount ?: 0L
        mutableStateOf(if (initialDisc > 0L) initialDisc.toString() else "")
    }

    val checkedMap = remember(editingExpense, people) {
        mutableStateMapOf<Long, Boolean>().apply {
            val initialSet = editingExpense?.splitIds?.toSet()
            people.forEach { person ->
                put(person.id, initialSet?.contains(person.id) ?: true)
            }
        }
    }
    val weightTexts = remember(editingExpense, people) {
        mutableStateMapOf<Long, String>().apply {
            people.forEach { person ->
                val weight = if (editingExpense != null) {
                    SplitBillCalculator.expenseWeight(editingExpense, person.id)
                } else {
                    1.0
                }
                put(person.id, formatDecimal(weight, dotSeparator = true))
            }
        }
    }

    var validationError by remember { mutableStateOf<String?>(null) }

    val subtotalValue = subtotalDigits.toLongOrNull() ?: 0L
    val taxValue = parseDecimalInput(taxText)
    val discountValue = discountDigits.toLongOrNull() ?: 0L
    val netPreview = SplitBillCalculator.computeNetAmount(subtotalValue, taxValue, discountValue)

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
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (editingExpense == null) "Tambah Pengeluaran" else "Edit Pengeluaran",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            OutlinedTextField(
                value = desc,
                onValueChange = {
                    desc = it
                    validationError = null
                },
                label = { Text("Untuk apa") },
                placeholder = { Text("mis. Makan siang") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = subtotalDigits,
                onValueChange = { raw ->
                    subtotalDigits = raw.filter { it.isDigit() }.take(12)
                    validationError = null
                },
                label = { Text("Jumlah subtotal (Rp)") },
                placeholder = { Text("mis. 150.000") },
                prefix = { Text("Rp ") },
                singleLine = true,
                visualTransformation = ThousandSeparatorVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next
                ),
                textStyle = tabularTextStyle(MaterialTheme.typography.bodyLarge),
                modifier = Modifier.fillMaxWidth()
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Dibayar oleh",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    people.forEach { person ->
                        FilterChip(
                            selected = person.id == payerId,
                            onClick = {
                                payerId = person.id
                                validationError = null
                            },
                            label = {
                                Text(if (person.isSelf) "${person.name} (Saya)" else person.name)
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showExtras = !showExtras },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Pajak, biaya tambahan, atau diskon (opsional)",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = if (showExtras) {
                                Icons.Default.KeyboardArrowUp
                            } else {
                                Icons.Default.KeyboardArrowDown
                            },
                            contentDescription = if (showExtras) "Tutup" else "Buka",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (showExtras) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = taxText,
                                onValueChange = { raw ->
                                    taxText = sanitizeDecimalInput(raw)
                                    validationError = null
                                },
                                label = { Text("Pajak / service (%)") },
                                placeholder = { Text("0") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                textStyle = tabularTextStyle(MaterialTheme.typography.bodyMedium),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = discountDigits,
                                onValueChange = { raw ->
                                    discountDigits = raw.filter { it.isDigit() }.take(12)
                                    validationError = null
                                },
                                label = { Text("Diskon (Rp)") },
                                placeholder = { Text("0") },
                                prefix = { Text("Rp ") },
                                singleLine = true,
                                visualTransformation = ThousandSeparatorVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                textStyle = tabularTextStyle(MaterialTheme.typography.bodyMedium),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (subtotalValue > 0L && (taxValue > 0.0 || discountValue > 0L)) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Total akhir setelah penyesuaian: ${SplitBillCalculator.formatRupiah(netPreview)}",
                                style = tabularTextStyle(MaterialTheme.typography.bodySmall),
                                fontWeight = FontWeight.SemiBold,
                                color = if (netPreview > 0L) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.error
                                }
                            )
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Dibagi untuk (atur porsi bila tidak rata)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                people.forEach { checkedMap[it.id] = true }
                                validationError = null
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("Pilih semua", style = MaterialTheme.typography.labelMedium)
                        }
                        OutlinedButton(
                            onClick = {
                                people.forEach { checkedMap[it.id] = false }
                                validationError = null
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("Kosongkan", style = MaterialTheme.typography.labelMedium)
                        }
                        OutlinedButton(
                            onClick = {
                                people.forEach { weightTexts[it.id] = "1" }
                                validationError = null
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("Samakan porsi (1)", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    people.forEach { person ->
                        val isChecked = checkedMap[person.id] ?: false
                        val weightInput = weightTexts[person.id] ?: "1"
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    checkedMap[person.id] = checked
                                    validationError = null
                                }
                            )
                            Text(
                                text = if (person.isSelf) "${person.name} (Saya)" else person.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        checkedMap[person.id] = !isChecked
                                        validationError = null
                                    }
                            )
                            OutlinedTextField(
                                value = weightInput,
                                onValueChange = { raw ->
                                    weightTexts[person.id] = sanitizeDecimalInput(raw)
                                    validationError = null
                                },
                                enabled = isChecked,
                                label = { Text("Porsi") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                textStyle = tabularTextStyle(MaterialTheme.typography.bodyMedium),
                                modifier = Modifier.width(88.dp)
                            )
                        }
                    }
                }
            }

            if (validationError != null) {
                Text(
                    text = validationError!!,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.cancel))
                }
                Button(
                    onClick = {
                        val trimmedDesc = desc.trim()
                        val selectedIds = people
                            .filter { checkedMap[it.id] == true }
                            .map { it.id }

                        if (trimmedDesc.isEmpty()) {
                            validationError = "Isi dulu nama pengeluaran."
                            return@Button
                        }
                        if (subtotalValue <= 0L) {
                            validationError = "Isi jumlah pengeluaran dengan nominal lebih dari 0."
                            return@Button
                        }
                        if (payerId == 0L || people.none { it.id == payerId }) {
                            validationError = "Tambahkan peserta dulu dan pilih pembayarnya."
                            return@Button
                        }
                        if (netPreview <= 0L) {
                            validationError = "Total akhir setelah diskon tidak boleh 0 atau kurang."
                            return@Button
                        }
                        if (selectedIds.isEmpty()) {
                            validationError = "Centang minimal satu orang untuk dibagi."
                            return@Button
                        }

                        val weights = LinkedHashMap<Long, Double>(selectedIds.size)
                        var totalWeight = 0.0
                        for (id in selectedIds) {
                            val parsed = parseDecimalInput(weightTexts[id] ?: "1")
                            val safeWeight = if (parsed > 0.0) parsed else 0.0
                            weights[id] = safeWeight
                            totalWeight += safeWeight
                        }
                        if (totalWeight <= 0.0) {
                            validationError = "Total porsi orang yang dicentang masih 0. Isi porsi minimal satu orang."
                            return@Button
                        }

                        onSave(
                            trimmedDesc,
                            subtotalValue,
                            taxValue,
                            discountValue,
                            payerId,
                            selectedIds,
                            weights
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (editingExpense == null) "Tambah Pengeluaran" else "Simpan Perubahan")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewSessionDialog(
    availableTags: List<String>,
    defaultTag: String,
    onDismiss: () -> Unit,
    onCreate: (title: String, includeSelf: Boolean, expenseTag: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var includeSelf by remember { mutableStateOf(true) }
    var selectedTag by remember { mutableStateOf(defaultTag) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        title = { Text("Nota Baru") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Judul nota (opsional)") },
                    placeholder = { Text("mis. Makan Malam Kampus") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Sertakan peserta \"Saya\"",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Dibutuhkan untuk sinkron otomatis ke Fast Track",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = includeSelf,
                        onCheckedChange = { includeSelf = it }
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Kategori sinkron:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        availableTags.forEach { tag ->
                            FilterChip(
                                selected = tag == selectedTag,
                                onClick = { selectedTag = tag },
                                label = { Text(tag) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onCreate(title, includeSelf, selectedTag) }) {
                Text("Buat Nota")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun DeleteSessionDialog(
    session: SplitBillSessionSummary,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val syncNote = if (session.syncToExpenses && session.myShare > 0L) {
        " Catatan pengeluaran yang tersinkron di Fast Track juga akan dihapus."
    } else {
        ""
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = { Text("Hapus nota \"${session.displayTitle}\"?") },
        text = {
            Text(
                "Nota beserta ${session.participantCount} peserta dan ${session.itemCount} pengeluaran akan dihapus permanen.$syncNote"
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Hapus Nota")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun DeleteParticipantDialog(
    person: SplitPerson,
    affectedExpenseCount: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val detail = if (affectedExpenseCount > 0) {
        " $affectedExpenseCount pengeluaran yang menyangkut dia juga akan terhapus."
    } else {
        ""
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = { Text("Hapus peserta ${person.name}?") },
        text = { Text("Peserta akan dihapus dari daftar.$detail") },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Hapus Peserta")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun ImportSplitBillDialog(
    onDismiss: () -> Unit,
    onImportText: (String) -> Unit,
    onPickJsonFile: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        title = { Text(stringResource(R.string.split_bill_import)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Tempel link nota (https://weinssy.github.io/split-bill-kelompok/#data=...) atau isi JSON cadangan, atau pilih file .json.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    label = { Text("Link nota / teks JSON") },
                    placeholder = { Text("https://weinssy.github.io/split-bill-kelompok/#data=...") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = onPickJsonFile,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Pilih Berkas JSON dari Perangkat")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onImportText(inputText) },
                enabled = inputText.trim().isNotEmpty()
            ) {
                Text("Impor")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

private class ThousandSeparatorVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        if (raw.isEmpty()) {
            return TransformedText(AnnotatedString(""), OffsetMapping.Identity)
        }
        val formatted = SplitBillCalculator.groupThousands(raw)
        val origToTrans = IntArray(raw.length + 1)
        val transToOrig = IntArray(formatted.length + 1)
        var origIndex = 0
        for (i in formatted.indices) {
            transToOrig[i] = origIndex
            if (formatted[i] != '.') {
                if (origIndex < raw.length) {
                    origToTrans[origIndex] = i
                    origIndex += 1
                }
            }
        }
        while (origIndex <= raw.length) {
            origToTrans[origIndex] = formatted.length
            origIndex += 1
        }
        transToOrig[formatted.length] = raw.length

        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                return origToTrans[offset.coerceIn(0, raw.length)]
            }

            override fun transformedToOriginal(offset: Int): Int {
                return transToOrig[offset.coerceIn(0, formatted.length)]
            }
        }
        return TransformedText(AnnotatedString(formatted), mapping)
    }
}

private fun tabularTextStyle(base: TextStyle): TextStyle {
    return base.copy(
        fontFeatureSettings = "tnum",
        fontFamily = FontFamily.SansSerif
    )
}

private fun resolveTagOptions(uiState: SplitBillUiState): List<String> {
    val dbTags = uiState.availableTags.map { it.name }.filter { it.isNotBlank() }
    val base = if (dbTags.isNotEmpty()) {
        dbTags
    } else {
        listOf("Makan", "Umum", "Transport", "Belanja", "Tagihan", "Jajan")
    }
    val current = uiState.expenseTag.trim()
    return if (current.isNotEmpty() && current !in base) {
        listOf(current) + base
    } else {
        base
    }
}

private fun sanitizeDecimalInput(raw: String): String {
    val sb = StringBuilder()
    var hasSeparator = false
    for (ch in raw) {
        if (ch.isDigit()) {
            sb.append(ch)
        } else if ((ch == '.' || ch == ',') && !hasSeparator) {
            if (sb.isEmpty()) sb.append('0')
            sb.append(ch)
            hasSeparator = true
        }
    }
    return sb.toString().take(8)
}

private fun parseDecimalInput(raw: String): Double {
    val parsed = raw.trim().replace(',', '.').toDoubleOrNull() ?: 0.0
    return if (parsed.isFinite() && parsed >= 0.0) parsed else 0.0
}

private fun formatDecimal(value: Double, dotSeparator: Boolean = false): String {
    if (!value.isFinite() || value == 0.0) return "0"
    val raw = if (value == floor(value) && abs(value) < Long.MAX_VALUE.toDouble()) {
        value.toLong().toString()
    } else {
        BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
    }
    return if (dotSeparator) raw else raw.replace('.', ',')
}

private fun buildExportFilename(title: String): String {
    val slug = title.trim()
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
    return if (slug.isNotEmpty()) "split-bill-$slug.json" else "split-bill-backup.json"
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}

private fun sharePlainText(context: Context, chooserTitle: String, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    val chooser = Intent.createChooser(intent, chooserTitle).apply {
        if (context !is Activity) {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
    context.startActivity(chooser)
}
