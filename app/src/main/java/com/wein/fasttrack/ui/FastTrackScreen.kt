package com.wein.fasttrack.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wein.fasttrack.R
import com.wein.fasttrack.data.Expense
import com.wein.fasttrack.data.TagEntity
import com.wein.fasttrack.utils.CsvExporter
import com.wein.fasttrack.viewmodel.ExpenseViewModel
import com.wein.fasttrack.viewmodel.FastTrackUiState
import com.wein.fasttrack.viewmodel.SpendingStatus
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

import androidx.fragment.app.FragmentActivity
import com.wein.fasttrack.utils.BiometricAuthManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FastTrackScreen(
    viewModel: ExpenseViewModel,
    onNavigateToAnalytics: () -> Unit,
    activity: FragmentActivity,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showBudgetDialog by remember { mutableStateOf(false) }
    var showRestoreWarning by remember { mutableStateOf(false) }

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

    LaunchedEffect(uiState.undoExpenseEvent) {
        uiState.undoExpenseEvent?.let { expense ->
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
                title = { Text("Fast Track") },
                actions = {
                    IconButton(onClick = { showBudgetDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Pengaturan Batas Harian")
                    }
                    IconButton(onClick = onNavigateToAnalytics) {
                        Icon(Icons.Default.List, contentDescription = "View Analytics")
                    }
                    IconButton(onClick = { viewModel.onExportTriggered() }, enabled = !uiState.isExporting) {
                        if (uiState.isExporting) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Share, contentDescription = stringResource(R.string.export_csv))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
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
                modifier = Modifier.weight(1f)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            HistoryList(
                expenses = uiState.todayExpenses,
                onDelete = { viewModel.deleteExpense(it) },
                modifier = Modifier.weight(1f)
            )
            
            Spacer(modifier = Modifier.height(16.dp))

            TagSelector(
                tags = uiState.availableTags,
                selectedTag = uiState.selectedTag,
                onTagSelected = { viewModel.onTagSelected(it.name) },
                onAddTagClicked = { viewModel.setShowAddTagDialog(true) },
                onTagLongClicked = { viewModel.setTagToDelete(it) },
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            CustomKeypad(
                onDigit = { viewModel.onDigitPressed(it) },
                onBackspace = { viewModel.onBackspacePressed() },
                onSave = { viewModel.onSavePressed() },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
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

        if (showBudgetDialog) {
            BudgetDialog(
                currentCap = uiState.budgetCap,
                isBackupRestoring = uiState.isBackupRestoring,
                isBiometricEnabled = uiState.isBiometricEnabled,
                activity = activity,
                onDismiss = { showBudgetDialog = false },
                onConfirm = { 
                    viewModel.setDailyBudgetCap(it)
                    showBudgetDialog = false 
                },
                onBiometricToggle = { enabled ->
                    viewModel.setBiometricEnabled(enabled)
                },
                onBackupClick = {
                    showBudgetDialog = false
                    val timestamp = System.currentTimeMillis()
                    createDocumentLauncher.launch("fast_track_backup_$timestamp.json")
                },
                onRestoreClick = {
                    showBudgetDialog = false
                    showRestoreWarning = true
                }
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
                    TextButton(onClick = { showRestoreWarning = false }) { Text("Batal") }
                }
            )
        }
    }
}

@Composable
fun HeroDisplay(
    uiState: FastTrackUiState,
    modifier: Modifier = Modifier
) {
    val currencyFormat = NumberFormat.getCurrencyInstance(Locale("id", "ID")).apply {
        maximumFractionDigits = 0
    }
    
    val displayAmount = if (uiState.currentInput.isEmpty()) {
        "0"
    } else {
        currencyFormat.format(uiState.currentInput.toLong()).replace(currencyFormat.currency?.symbol ?: "", "").trim()
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "${stringResource(R.string.today_total)}: ${currencyFormat.format(uiState.todayTotal)}",
            color = MaterialTheme.colorScheme.secondary,
            fontSize = 16.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        
        val amountColor = when (uiState.spendingStatus) {
            SpendingStatus.NORMAL -> MaterialTheme.colorScheme.onBackground
            SpendingStatus.WARNING -> Color(0xFFF6AD55)
            SpendingStatus.EXCEEDED -> Color(0xFFE53E3E)
        }
        
        Text(
            text = displayAmount,
            color = amountColor,
            fontSize = 56.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryList(
    expenses: List<Expense>,
    onDelete: (Expense) -> Unit,
    modifier: Modifier = Modifier
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale("id", "ID")) }
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("id", "ID")).apply { maximumFractionDigits = 0 } }

    LazyColumn(modifier = modifier.fillMaxWidth()) {
        items(expenses, key = { it.id }) { expense ->
            val dismissState = rememberSwipeToDismissBoxState(
                confirmValueChange = {
                    if (it == SwipeToDismissBoxValue.EndToStart) {
                        onDelete(expense)
                        true
                    } else false
                }
            )

            SwipeToDismissBox(
                state = dismissState,
                backgroundContent = {
                    val color by animateColorAsState(
                        if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) Color.Red else Color.Transparent,
                        label = "dismissColor"
                    )
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(vertical = 4.dp)
                            .background(color, RoundedCornerShape(12.dp))
                            .padding(end = 16.dp),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White)
                        }
                    }
                },
                enableDismissFromStartToEnd = false,
                content = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = expense.tag ?: stringResource(R.string.tag_umum), color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
                            Text(text = timeFormat.format(Date(expense.timestamp)), color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp)
                        }
                        Text(
                            text = currencyFormat.format(expense.amount),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            )
        }
    }
}

@Composable
fun CustomKeypad(
    onDigit: (Int) -> Unit,
    onBackspace: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    val saveText = stringResource(R.string.save)
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("⌫", "0", saveText)
    )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { key ->
                    KeypadButton(
                        text = key,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            when (key) {
                                "⌫" -> onBackspace()
                                saveText -> onSave()
                                else -> onDigit(key.toInt())
                            }
                        },
                        isAction = key == saveText
                    )
                }
            }
        }
    }
}

@Composable
fun KeypadButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isAction: Boolean = false
) {
    val haptic = LocalHapticFeedback.current
    
    Box(
        modifier = modifier
            .aspectRatio(1.5f)
            .clip(RoundedCornerShape(16.dp))
            .background(if (isAction) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
            .clickable(onClick = {
                if (isAction) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                } else {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                onClick()
            }),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isAction) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            fontSize = if (isAction) 20.sp else 24.sp,
            fontWeight = FontWeight.Medium
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
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        items(tags) { tag ->
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
            .background(Color(0xFF242629))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Add, contentDescription = "Tambah Tag", tint = Color(0xFF94A1B2), modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Tambah",
                color = Color(0xFF94A1B2),
                fontSize = 14.sp,
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
    val backgroundColor = if (isSelected) Color(0xFF2CB67D) else Color(0xFF242629)
    val textColor = if (isSelected) Color.White else Color(0xFF94A1B2)
    
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .combinedClickable(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
                onLongClick = if (!tag.isPreset) onLongClick else null
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = tag.name,
            color = textColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
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
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                )
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = text.trim().isNotEmpty()
            ) {
                Text("Simpan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
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
            Text(text = "Hapus kategori \"${tag.name}\"? Catatan transaksi lama yang menggunakan kategori ini tidak akan terhapus.")
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Hapus")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        },
        containerColor = Color(0xFF242629),
        titleContentColor = Color(0xFFFFFFFE),
        textContentColor = Color(0xFF94A1B2)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetDialog(
    currentCap: Long,
    isBackupRestoring: Boolean,
    isBiometricEnabled: Boolean,
    activity: FragmentActivity,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
    onBiometricToggle: (Boolean) -> Unit,
    onBackupClick: () -> Unit,
    onRestoreClick: () -> Unit
) {
    var text by remember { mutableStateOf(if (currentCap > 0) currentCap.toString() else "") }
    var biometricChecked by remember { mutableStateOf(isBiometricEnabled) }
    val context = LocalContext.current
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Pengaturan") },
        text = {
            Column {
                Text(text = "Tentukan batas pengeluaran harian. Kosongkan atau isi 0 untuk menonaktifkan.", color = Color(0xFF94A1B2))
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Gray,
                        focusedTextColor = Color(0xFFFFFFFE),
                        unfocusedTextColor = Color(0xFFFFFFFE)
                    )
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(color = Color.DarkGray)
                Spacer(modifier = Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "Kunci Biometrik", color = Color(0xFFFFFFFE), modifier = Modifier.weight(1f))
                    Switch(
                        checked = biometricChecked,
                        onCheckedChange = { isChecked ->
                            if (isChecked) {
                                if (!BiometricAuthManager.canAuthenticate(context)) {
                                    android.widget.Toast.makeText(context, "Perangkat tidak memiliki biometrik atau PIN aktif.", android.widget.Toast.LENGTH_SHORT).show()
                                    biometricChecked = false
                                } else {
                                    BiometricAuthManager.authenticate(
                                        activity = activity,
                                        onSuccess = {
                                            biometricChecked = true
                                            onBiometricToggle(true)
                                        },
                                        onError = { err ->
                                            android.widget.Toast.makeText(context, err, android.widget.Toast.LENGTH_SHORT).show()
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
                
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color.DarkGray)
                Spacer(modifier = Modifier.height(16.dp))
                
                if (isBackupRestoring) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                } else {
                    OutlinedButton(onClick = onBackupClick, modifier = Modifier.fillMaxWidth()) {
                        Text("Cadangkan Data (JSON)")
                    }
                    OutlinedButton(
                        onClick = onRestoreClick, 
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Pulihkan Data (JSON)")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.toLongOrNull() ?: 0L) }) {
                Text("Simpan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        },
        containerColor = Color(0xFF242629),
        titleContentColor = Color(0xFFFFFFFE)
    )
}
