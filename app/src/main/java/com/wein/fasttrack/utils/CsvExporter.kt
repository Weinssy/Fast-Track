package com.wein.fasttrack.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.wein.fasttrack.data.Expense
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CsvExporter {
    suspend fun exportExpensesToCsv(context: Context, expenses: List<Expense>): Uri? =
        withContext(Dispatchers.IO) {
            if (expenses.isEmpty()) return@withContext null

            val exportsDir = File(context.cacheDir, "exports")
            if (!exportsDir.exists()) {
                exportsDir.mkdirs()
            }

            val idLocale = Locale("id", "ID")
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", idLocale).format(Date())
            val file = File(exportsDir, "fast_track_expenses_$timestamp.csv")

            try {
                val csvContent = buildExpensesCsv(expenses)
                OutputStreamWriter(FileOutputStream(file), Charsets.UTF_8).use { writer ->
                    writer.write(csvContent)
                }

                return@withContext FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            } catch (e: Exception) {
                e.printStackTrace()
                return@withContext null
            }
        }

    fun buildExpensesCsv(expenses: List<Expense>): String {
        val idLocale = Locale("id", "ID")
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", idLocale)
        val timeFormat = SimpleDateFormat("HH:mm:ss", idLocale)

        val sb = StringBuilder()
        sb.append("ID,Tanggal,Waktu,Nominal,Kategori,Catatan,Sumber\n")

        expenses.forEach { expense ->
            val dateObj = Date(expense.timestamp)
            val dateStr = dateFormat.format(dateObj)
            val timeStr = timeFormat.format(dateObj)
            val tag = escapeCsv(expense.tag ?: "")
            val note = escapeCsv(expense.note ?: "")
            val source = if (expense.splitBillSessionId != null) "Patungan" else "Pribadi"
            sb.append("${expense.id},$dateStr,$timeStr,${expense.amount},$tag,$note,$source\n")
        }
        return sb.toString()
    }

    private fun escapeCsv(value: String): String {
        var escaped = value
        if (escaped.contains("\"") || escaped.contains(",") || escaped.contains("\n") || escaped.contains("\r")) {
            escaped = escaped.replace("\"", "\"\"")
            return "\"$escaped\""
        }
        return escaped
    }

    fun shareCsv(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, "Fast Track Expenses Backup")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Export Expenses").apply {
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        context.startActivity(chooser)
    }

    suspend fun cleanupOldExports(context: Context, maxAgeMillis: Long = 24 * 60 * 60 * 1000) =
        withContext(Dispatchers.IO) {
            val exportsDir = File(context.cacheDir, "exports")
            if (!exportsDir.exists()) return@withContext

            val now = System.currentTimeMillis()
            exportsDir.listFiles()?.forEach { file ->
                if (now - file.lastModified() > maxAgeMillis) {
                    file.delete()
                }
            }
        }
}
