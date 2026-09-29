package com.wein.fasttrack.utils

import android.content.Context
import android.net.Uri
import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import com.wein.fasttrack.data.BackupPayload
import com.wein.fasttrack.data.Expense
import com.wein.fasttrack.data.TagEntity
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class RestoreSummary(val expensesRestored: Int, val tagsRestored: Int)

object BackupManager {
    suspend fun createBackup(context: Context, uri: Uri, payload: BackupPayload): Result<Int> = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                val writer = JsonWriter(OutputStreamWriter(outputStream, "UTF-8"))
                writer.setIndent("  ")
                writer.beginObject()
                writer.name("version").value(payload.version)
                writer.name("app").value(payload.app)
                writer.name("exportedAt").value(payload.exportedAt)
                writer.name("dailyBudgetCap").value(payload.dailyBudgetCap)
                
                writer.name("tags").beginArray()
                for (tag in payload.tags) {
                    writer.beginObject()
                    writer.name("id").value(tag.id)
                    writer.name("name").value(tag.name)
                    writer.name("isPreset").value(tag.isPreset)
                    writer.endObject()
                }
                writer.endArray()
                
                writer.name("expenses").beginArray()
                for (expense in payload.expenses) {
                    writer.beginObject()
                    writer.name("id").value(expense.id)
                    writer.name("amount").value(expense.amount)
                    writer.name("timestamp").value(expense.timestamp)
                    writer.name("tag").value(expense.tag)
                    writer.name("note").value(expense.note)
                    writer.endObject()
                }
                writer.endArray()
                
                writer.endObject()
                writer.close()
            }
            Result.success(payload.expenses.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun restoreBackup(context: Context, uri: Uri): Result<BackupPayload> = withContext(Dispatchers.IO) {
        try {
            var version = 0
            var app = ""
            var exportedAt = 0L
            var dailyBudgetCap = 0L
            val tags = mutableListOf<TagEntity>()
            val expenses = mutableListOf<Expense>()
            
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val reader = JsonReader(InputStreamReader(inputStream, "UTF-8"))
                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "version" -> version = reader.nextInt()
                        "app" -> app = reader.nextString()
                        "exportedAt" -> exportedAt = reader.nextLong()
                        "dailyBudgetCap" -> dailyBudgetCap = reader.nextLong()
                        "tags" -> {
                            reader.beginArray()
                            while (reader.hasNext()) {
                                reader.beginObject()
                                var tId = 0L
                                var tName = ""
                                var tPreset = false
                                while (reader.hasNext()) {
                                    when (reader.nextName()) {
                                        "id" -> tId = reader.nextLong()
                                        "name" -> tName = reader.nextString()
                                        "isPreset" -> tPreset = reader.nextBoolean()
                                        else -> reader.skipValue()
                                    }
                                }
                                tags.add(TagEntity(id = tId, name = tName, isPreset = tPreset))
                                reader.endObject()
                            }
                            reader.endArray()
                        }
                        "expenses" -> {
                            reader.beginArray()
                            while (reader.hasNext()) {
                                reader.beginObject()
                                var eId = 0L
                                var eAmount = 0L
                                var eTimestamp = 0L
                                var eTag: String? = null
                                var eNote: String? = null
                                while (reader.hasNext()) {
                                    when (reader.nextName()) {
                                        "id" -> eId = reader.nextLong()
                                        "amount" -> eAmount = reader.nextLong()
                                        "timestamp" -> eTimestamp = reader.nextLong()
                                        "tag" -> if (reader.peek() == JsonToken.NULL) { reader.nextNull() } else { eTag = reader.nextString() }
                                        "note" -> if (reader.peek() == JsonToken.NULL) { reader.nextNull() } else { eNote = reader.nextString() }
                                        else -> reader.skipValue()
                                    }
                                }
                                expenses.add(Expense(id = eId, amount = eAmount, timestamp = eTimestamp, tag = eTag, note = eNote))
                                reader.endObject()
                            }
                            reader.endArray()
                        }
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
                reader.close()
            }
            
            if (app != "FastTrack") {
                return@withContext Result.failure(Exception("Berkas bukan format FastTrack yang valid."))
            }
            
            Result.success(BackupPayload(version, app, exportedAt, dailyBudgetCap, tags, expenses))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
