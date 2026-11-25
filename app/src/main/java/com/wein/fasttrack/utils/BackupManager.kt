package com.wein.fasttrack.utils

import android.content.Context
import android.net.Uri
import com.wein.fasttrack.data.BackupPayload
import com.wein.fasttrack.data.Expense
import com.wein.fasttrack.data.RecurringExpenseEntity
import com.wein.fasttrack.data.SplitBillItemEntity
import com.wein.fasttrack.data.SplitBillParticipantEntity
import com.wein.fasttrack.data.SplitBillSessionEntity
import com.wein.fasttrack.data.SplitBillSessionFull
import com.wein.fasttrack.data.SplitBillSettledEntity
import com.wein.fasttrack.data.TagEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.util.Locale

data class RestoreSummary(
    val expensesRestored: Int,
    val tagsRestored: Int,
    val recurringRestored: Int = 0,
    val splitBillsRestored: Int = 0
)

object BackupManager {

    suspend fun createBackup(context: Context, uri: Uri, payload: BackupPayload): Result<Int> =
        withContext(Dispatchers.IO) {
            try {
                val outputStream = context.contentResolver.openOutputStream(uri)
                    ?: throw IOException("Tidak dapat membuka berkas tujuan untuk pencadangan.")
                outputStream.use { stream ->
                    writeBackupToStream(stream, payload)
                }
                Result.success(payload.expenses.size)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun restoreBackup(context: Context, uri: Uri): Result<BackupPayload> =
        withContext(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: throw IOException("Tidak dapat membuka berkas cadangan.")
                val payload = inputStream.use { stream ->
                    readBackupFromStream(stream)
                }
                Result.success(payload)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    fun serializeBackup(payload: BackupPayload): String {
        val baos = ByteArrayOutputStream()
        writeBackupToStream(baos, payload)
        return baos.toString(Charsets.UTF_8.name())
    }

    fun deserializeBackup(jsonText: String): BackupPayload {
        return parsePayload(jsonText)
    }

    fun writeBackupToStream(outputStream: OutputStream, payload: BackupPayload) {
        val json = buildJsonString(payload)
        val writer = OutputStreamWriter(outputStream, Charsets.UTF_8)
        writer.write(json)
        writer.flush()
    }

    fun readBackupFromStream(inputStream: InputStream): BackupPayload {
        val text = inputStream.bufferedReader(Charsets.UTF_8).readText()
        return parsePayload(text)
    }

    private fun buildJsonString(payload: BackupPayload): String {
        val sb = StringBuilder(1024)
        val w = JsonTextWriter(sb)
        w.beginObject()
        w.prop("version", payload.version)
        w.prop("app", payload.app)
        w.prop("exportedAt", payload.exportedAt)
        w.prop("dailyBudgetCap", payload.dailyBudgetCap)
        w.prop("monthlyBudgetCap", payload.monthlyBudgetCap)
        w.prop("themeMode", payload.themeMode)

        w.beginArrayProp("tags")
        for (tag in payload.tags) {
            w.beginObject()
            w.prop("id", tag.id)
            w.prop("name", tag.name)
            w.prop("isPreset", tag.isPreset)
            w.endObject()
        }
        w.endArray()

        w.beginArrayProp("expenses")
        for (expense in payload.expenses) {
            w.beginObject()
            w.prop("id", expense.id)
            w.prop("amount", expense.amount)
            w.prop("timestamp", expense.timestamp)
            w.propNullable("tag", expense.tag)
            w.propNullable("note", expense.note)
            w.propNullableLong("splitBillSessionId", expense.splitBillSessionId)
            w.endObject()
        }
        w.endArray()

        w.beginArrayProp("recurringExpenses")
        for (item in payload.recurringExpenses) {
            w.beginObject()
            w.prop("id", item.id)
            w.prop("amount", item.amount)
            w.prop("tag", item.tag)
            w.propNullable("note", item.note)
            w.prop("dayOfMonth", item.dayOfMonth)
            w.prop("isEnabled", item.isEnabled)
            w.propNullable("lastAppliedYearMonth", item.lastAppliedYearMonth)
            w.prop("createdAt", item.createdAt)
            w.endObject()
        }
        w.endArray()

        w.beginArrayProp("splitBillSessions")
        for (full in payload.splitBillSessions) {
            w.beginObject()

            w.beginObjectProp("session")
            w.prop("id", full.session.id)
            w.prop("title", full.session.title)
            w.prop("createdAt", full.session.createdAt)
            w.prop("updatedAt", full.session.updatedAt)
            w.prop("syncToExpenses", full.session.syncToExpenses)
            w.prop("expenseTag", full.session.expenseTag)
            w.propNullableLong("syncedExpenseId", full.session.syncedExpenseId)
            w.endObject()

            w.beginArrayProp("participants")
            for (p in full.participants) {
                w.beginObject()
                w.prop("id", p.id)
                w.prop("sessionId", p.sessionId)
                w.prop("name", p.name)
                w.prop("isSelf", p.isSelf)
                w.prop("sortOrder", p.sortOrder)
                w.endObject()
            }
            w.endArray()

            w.beginArrayProp("items")
            for (item in full.items) {
                w.beginObject()
                w.prop("id", item.id)
                w.prop("sessionId", item.sessionId)
                w.prop("description", item.description)
                w.prop("amount", item.amount)
                w.prop("subtotal", item.subtotal)
                w.prop("taxPercent", item.taxPercent)
                w.prop("discount", item.discount)
                w.prop("payerParticipantId", item.payerParticipantId)
                w.prop("splitIdsSerialized", item.splitIdsSerialized)
                w.prop("weightsJson", item.weightsJson)
                w.endObject()
            }
            w.endArray()

            w.beginArrayProp("settledKeys")
            for (s in full.settledKeys) {
                w.beginObject()
                w.prop("sessionId", s.sessionId)
                w.prop("settlementKey", s.settlementKey)
                w.endObject()
            }
            w.endArray()

            w.endObject()
        }
        w.endArray()

        w.endObject()
        return sb.toString()
    }

    private fun parsePayload(jsonText: String): BackupPayload {
        val rootAny = JsonTextParser(jsonText).parse()
        val root = rootAny as? Map<*, *>
            ?: throw IllegalArgumentException("Format JSON cadangan tidak valid.")

        val app = root.strOrNull("app") ?: ""
        if (app != "FastTrack") {
            throw IllegalArgumentException("Berkas bukan format FastTrack yang valid.")
        }

        val version = root.intOrDefault("version", 1)
        val exportedAt = root.longOrDefault("exportedAt", 0L)
        val dailyBudgetCap = root.longOrDefault("dailyBudgetCap", 0L)
        val monthlyBudgetCap = root.longOrDefault("monthlyBudgetCap", 0L)
        val themeMode = root.strOrNull("themeMode")?.takeIf { it.isNotBlank() } ?: "DARK"

        val tags = root.listOrEmpty("tags").mapNotNull { raw ->
            val obj = raw as? Map<*, *> ?: return@mapNotNull null
            TagEntity(
                id = obj.longOrDefault("id", 0L),
                name = obj.strOrNull("name") ?: return@mapNotNull null,
                isPreset = obj.boolOrDefault("isPreset", false)
            )
        }

        val expenses = root.listOrEmpty("expenses").mapNotNull { raw ->
            val obj = raw as? Map<*, *> ?: return@mapNotNull null
            Expense(
                id = obj.longOrDefault("id", 0L),
                amount = obj.longOrDefault("amount", 0L),
                timestamp = obj.longOrDefault("timestamp", 0L),
                tag = obj.strOrNull("tag"),
                note = obj.strOrNull("note"),
                splitBillSessionId = obj.longOrNull("splitBillSessionId")
            )
        }

        val recurringExpenses = root.listOrEmpty("recurringExpenses").mapNotNull { raw ->
            val obj = raw as? Map<*, *> ?: return@mapNotNull null
            RecurringExpenseEntity(
                id = obj.longOrDefault("id", 0L),
                amount = obj.longOrDefault("amount", 0L),
                tag = obj.strOrNull("tag")?.ifBlank { "Tagihan" } ?: "Tagihan",
                note = obj.strOrNull("note"),
                dayOfMonth = obj.intOrDefault("dayOfMonth", 1),
                isEnabled = obj.boolOrDefault("isEnabled", true),
                lastAppliedYearMonth = obj.strOrNull("lastAppliedYearMonth"),
                createdAt = obj.longOrDefault("createdAt", 0L)
            )
        }

        val splitBillSessions = root.listOrEmpty("splitBillSessions").mapNotNull { raw ->
            val obj = raw as? Map<*, *> ?: return@mapNotNull null
            val sessionObj = obj["session"] as? Map<*, *> ?: return@mapNotNull null
            val sessionId = sessionObj.longOrDefault("id", 0L)
            val session = SplitBillSessionEntity(
                id = sessionId,
                title = sessionObj.strOrNull("title") ?: "",
                createdAt = sessionObj.longOrDefault("createdAt", 0L),
                updatedAt = sessionObj.longOrDefault("updatedAt", 0L),
                syncToExpenses = sessionObj.boolOrDefault("syncToExpenses", true),
                expenseTag = sessionObj.strOrNull("expenseTag")?.ifBlank { "Makan" } ?: "Makan",
                syncedExpenseId = sessionObj.longOrNull("syncedExpenseId")
            )

            val participants = obj.listOrEmpty("participants").mapNotNull { pRaw ->
                val pObj = pRaw as? Map<*, *> ?: return@mapNotNull null
                SplitBillParticipantEntity(
                    id = pObj.longOrDefault("id", 0L),
                    sessionId = pObj.longOrDefault("sessionId", sessionId),
                    name = pObj.strOrNull("name") ?: return@mapNotNull null,
                    isSelf = pObj.boolOrDefault("isSelf", false),
                    sortOrder = pObj.intOrDefault("sortOrder", 0)
                )
            }

            val items = obj.listOrEmpty("items").mapNotNull { iRaw ->
                val iObj = iRaw as? Map<*, *> ?: return@mapNotNull null
                val amount = iObj.longOrDefault("amount", 0L)
                SplitBillItemEntity(
                    id = iObj.longOrDefault("id", 0L),
                    sessionId = iObj.longOrDefault("sessionId", sessionId),
                    description = iObj.strOrNull("description") ?: "",
                    amount = amount,
                    subtotal = iObj.longOrDefault("subtotal", amount),
                    taxPercent = iObj.doubleOrDefault("taxPercent", 0.0),
                    discount = iObj.longOrDefault("discount", 0L),
                    payerParticipantId = iObj.longOrDefault("payerParticipantId", 0L),
                    splitIdsSerialized = iObj.strOrNull("splitIdsSerialized") ?: "",
                    weightsJson = iObj.strOrNull("weightsJson")?.ifBlank { "{}" } ?: "{}"
                )
            }

            val settledKeys = obj.listOrEmpty("settledKeys").mapNotNull { sRaw ->
                val sObj = sRaw as? Map<*, *> ?: return@mapNotNull null
                val key = sObj.strOrNull("settlementKey") ?: return@mapNotNull null
                SplitBillSettledEntity(
                    sessionId = sObj.longOrDefault("sessionId", sessionId),
                    settlementKey = key
                )
            }

            SplitBillSessionFull(
                session = session,
                participants = participants,
                items = items,
                settledKeys = settledKeys
            )
        }

        return BackupPayload(
            version = version,
            app = app,
            exportedAt = exportedAt,
            dailyBudgetCap = dailyBudgetCap,
            tags = tags,
            expenses = expenses,
            monthlyBudgetCap = monthlyBudgetCap,
            themeMode = themeMode,
            recurringExpenses = recurringExpenses,
            splitBillSessions = splitBillSessions
        )
    }

    private class JsonNum(val raw: String)

    private class JsonTextWriter(private val sb: StringBuilder) {
        private var depth = 0
        private val hasChildStack = ArrayDeque<Boolean>()

        fun beginObject() {
            prepareElement()
            sb.append('{')
            depth += 1
            hasChildStack.addLast(false)
        }

        fun endObject() {
            val hadChild = hasChildStack.removeLast()
            depth -= 1
            if (hadChild) {
                sb.append('\n')
                appendIndent()
            }
            sb.append('}')
        }

        fun beginArrayProp(name: String) {
            writeName(name)
            sb.append('[')
            depth += 1
            hasChildStack.addLast(false)
        }

        fun beginObjectProp(name: String) {
            writeName(name)
            sb.append('{')
            depth += 1
            hasChildStack.addLast(false)
        }

        fun endArray() {
            val hadChild = hasChildStack.removeLast()
            depth -= 1
            if (hadChild) {
                sb.append('\n')
                appendIndent()
            }
            sb.append(']')
        }

        fun prop(name: String, value: Int) {
            writeName(name)
            sb.append(value)
        }

        fun prop(name: String, value: Long) {
            writeName(name)
            sb.append(value)
        }

        fun prop(name: String, value: Double) {
            writeName(name)
            if (value.isFinite()) {
                sb.append(value)
            } else {
                sb.append("0.0")
            }
        }

        fun prop(name: String, value: Boolean) {
            writeName(name)
            sb.append(if (value) "true" else "false")
        }

        fun prop(name: String, value: String) {
            writeName(name)
            writeQuoted(value)
        }

        fun propNullable(name: String, value: String?) {
            writeName(name)
            if (value == null) sb.append("null") else writeQuoted(value)
        }

        fun propNullableLong(name: String, value: Long?) {
            writeName(name)
            if (value == null) sb.append("null") else sb.append(value)
        }

        private fun prepareElement() {
            if (hasChildStack.isEmpty()) return
            val hadChild = hasChildStack.last()
            if (hadChild) {
                sb.append(',')
            } else {
                hasChildStack[hasChildStack.lastIndex] = true
            }
            sb.append('\n')
            appendIndent()
        }

        private fun writeName(name: String) {
            prepareElement()
            writeQuoted(name)
            sb.append(": ")
        }

        private fun appendIndent() {
            repeat(depth) { sb.append("  ") }
        }

        private fun writeQuoted(value: String) {
            sb.append('"')
            for (ch in value) {
                when (ch) {
                    '"' -> sb.append("\\\"")
                    '\\' -> sb.append("\\\\")
                    '\b' -> sb.append("\\b")
                    '\u000C' -> sb.append("\\f")
                    '\n' -> sb.append("\\n")
                    '\r' -> sb.append("\\r")
                    '\t' -> sb.append("\\t")
                    else -> {
                        if (ch.code < 0x20) {
                            sb.append(String.format(Locale.US, "\\u%04x", ch.code))
                        } else {
                            sb.append(ch)
                        }
                    }
                }
            }
            sb.append('"')
        }
    }

    private class JsonTextParser(private val text: String) {
        private var pos = if (text.startsWith('\uFEFF')) 1 else 0

        fun parse(): Any? {
            skipWhitespace()
            val value = parseValue()
            skipWhitespace()
            if (pos < text.length) {
                throw IllegalArgumentException("Unexpected trailing character at index $pos")
            }
            return value
        }

        private fun parseValue(): Any? {
            skipWhitespace()
            if (pos >= text.length) {
                throw IllegalArgumentException("Unexpected end of JSON")
            }
            return when (val ch = text[pos]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't', 'f' -> parseBoolean()
                'n' -> parseNull()
                '-', in '0'..'9' -> parseNumber()
                else -> throw IllegalArgumentException("Unexpected character '$ch' at index $pos")
            }
        }

        private fun parseObject(): Map<String, Any?> {
            expect('{')
            val map = linkedMapOf<String, Any?>()
            skipWhitespace()
            if (peek('}')) {
                pos += 1
                return map
            }
            while (true) {
                skipWhitespace()
                val key = parseString()
                skipWhitespace()
                expect(':')
                val value = parseValue()
                map[key] = value
                skipWhitespace()
                if (peek(',')) {
                    pos += 1
                } else if (peek('}')) {
                    pos += 1
                    break
                } else {
                    throw IllegalArgumentException("Expected ',' or '}' at index $pos")
                }
            }
            return map
        }

        private fun parseArray(): List<Any?> {
            expect('[')
            val list = mutableListOf<Any?>()
            skipWhitespace()
            if (peek(']')) {
                pos += 1
                return list
            }
            while (true) {
                list.add(parseValue())
                skipWhitespace()
                if (peek(',')) {
                    pos += 1
                } else if (peek(']')) {
                    pos += 1
                    break
                } else {
                    throw IllegalArgumentException("Expected ',' or ']' at index $pos")
                }
            }
            return list
        }

        private fun parseString(): String {
            expect('"')
            val sb = StringBuilder()
            while (pos < text.length) {
                val ch = text[pos++]
                when (ch) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (pos >= text.length) {
                            throw IllegalArgumentException("Unterminated escape sequence")
                        }
                        when (val esc = text[pos++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (pos + 4 > text.length) {
                                    throw IllegalArgumentException("Invalid unicode escape")
                                }
                                val hex = text.substring(pos, pos + 4)
                                val code = hex.toIntOrNull(16)
                                    ?: throw IllegalArgumentException("Invalid hex unicode escape: $hex")
                                sb.append(code.toChar())
                                pos += 4
                            }
                            else -> throw IllegalArgumentException("Invalid escape '\\$esc'")
                        }
                    }
                    else -> sb.append(ch)
                }
            }
            throw IllegalArgumentException("Unterminated string literal")
        }

        private fun parseBoolean(): Boolean {
            return if (text.startsWith("true", pos)) {
                pos += 4
                true
            } else if (text.startsWith("false", pos)) {
                pos += 5
                false
            } else {
                throw IllegalArgumentException("Invalid boolean at index $pos")
            }
        }

        private fun parseNull(): Any? {
            if (text.startsWith("null", pos)) {
                pos += 4
                return null
            }
            throw IllegalArgumentException("Invalid null literal at index $pos")
        }

        private fun parseNumber(): JsonNum {
            val start = pos
            if (peek('-')) pos += 1
            while (pos < text.length && text[pos].isDigit()) pos += 1
            if (pos < text.length && text[pos] == '.') {
                pos += 1
                if (pos >= text.length || !text[pos].isDigit()) {
                    throw IllegalArgumentException("Invalid decimal number at index $pos")
                }
                while (pos < text.length && text[pos].isDigit()) pos += 1
            }
            if (pos < text.length && (text[pos] == 'e' || text[pos] == 'E')) {
                pos += 1
                if (pos < text.length && (text[pos] == '+' || text[pos] == '-')) pos += 1
                if (pos >= text.length || !text[pos].isDigit()) {
                    throw IllegalArgumentException("Invalid exponent at index $pos")
                }
                while (pos < text.length && text[pos].isDigit()) pos += 1
            }
            val raw = text.substring(start, pos)
            if (raw.isEmpty() || raw == "-") {
                throw IllegalArgumentException("Invalid number at index $start")
            }
            return JsonNum(raw)
        }

        private fun skipWhitespace() {
            while (pos < text.length && text[pos].isWhitespace()) {
                pos += 1
            }
        }

        private fun peek(expected: Char): Boolean = pos < text.length && text[pos] == expected

        private fun expect(expected: Char) {
            if (pos >= text.length || text[pos] != expected) {
                throw IllegalArgumentException("Expected '$expected' at index $pos")
            }
            pos += 1
        }
    }

    private fun Map<*, *>.strOrNull(key: String): String? = this[key] as? String

    private fun Map<*, *>.boolOrDefault(key: String, default: Boolean): Boolean =
        this[key] as? Boolean ?: default

    private fun Map<*, *>.longOrNull(key: String): Long? {
        val v = this[key] ?: return null
        return when (v) {
            is JsonNum -> v.raw.toLongOrNull() ?: v.raw.toDoubleOrNull()?.toLong()
            is Number -> v.toLong()
            else -> null
        }
    }

    private fun Map<*, *>.longOrDefault(key: String, default: Long): Long =
        longOrNull(key) ?: default

    private fun Map<*, *>.intOrDefault(key: String, default: Int): Int {
        val v = this[key] ?: return default
        return when (v) {
            is JsonNum -> v.raw.toIntOrNull() ?: v.raw.toDoubleOrNull()?.toInt() ?: default
            is Number -> v.toInt()
            else -> default
        }
    }

    private fun Map<*, *>.doubleOrDefault(key: String, default: Double): Double {
        val v = this[key] ?: return default
        return when (v) {
            is JsonNum -> v.raw.toDoubleOrNull() ?: default
            is Number -> v.toDouble()
            else -> default
        }
    }

    private fun Map<*, *>.listOrEmpty(key: String): List<*> =
        this[key] as? List<*> ?: emptyList<Any?>()
}
