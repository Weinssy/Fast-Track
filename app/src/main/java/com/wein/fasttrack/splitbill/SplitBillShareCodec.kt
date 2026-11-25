package com.wein.fasttrack.splitbill

import java.math.BigDecimal
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.time.Instant
import java.util.Base64
import kotlin.math.abs
import kotlin.math.floor

object SplitBillShareCodec {
    private const val APP_ID = "split-bill-kelompok"
    private const val SHARE_VERSION = 1
    private const val SCHEMA_VERSION = 1
    private const val SHARE_BASE_URL = "https://weinssy.github.io/split-bill-kelompok/"

    fun encodeSharePayload(payload: SplitSharePayload): String {
        val root = linkedMapOf<String, Any?>(
            "app" to APP_ID,
            "v" to SHARE_VERSION,
            "title" to payload.title.trim(),
            "people" to serializePeople(payload.people),
            "expenses" to serializeExpenses(payload.expenses),
            "settled" to payload.settled
        )
        val json = JsonCodec.stringify(root, pretty = false)
        val bytes = json.toByteArray(Charsets.UTF_8)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun decodeSharePayload(encodedOrUrl: String): SplitSharePayload {
        val trimmed = encodedOrUrl.trim()
        if (trimmed.isEmpty()) {
            throw IllegalArgumentException("Tautan nota kosong.")
        }

        val token = when {
            trimmed.contains("#data=") -> trimmed.substringAfter("#data=").trim()
            trimmed.startsWith("data=") -> trimmed.removePrefix("data=").trim()
            else -> trimmed
        }
        if (token.isEmpty()) {
            throw IllegalArgumentException("Tautan nota kosong.")
        }

        val bytes = try {
            Base64.getUrlDecoder().decode(token)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Tautan nota bukan base64url yang valid.", e)
        }

        val text = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (e: Exception) {
            throw IllegalArgumentException("Tautan nota mengandung UTF-8 tidak valid.", e)
        }

        val parsed = try {
            JsonCodec.parse(text) as? Map<*, *>
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("JSON tautan nota tidak valid.", e)
        } ?: throw IllegalArgumentException("Format tautan nota tidak dikenali.")

        if (parsed["app"] != APP_ID) {
            throw IllegalArgumentException("Format tautan nota tidak dikenali.")
        }

        val people = sanitizePeopleList(parsed["people"])
        val expenses = sanitizeExpensesList(parsed["expenses"])
        if (people == null || expenses == null) {
            throw IllegalArgumentException("Isi peserta atau pengeluaran pada tautan tidak valid.")
        }

        val title = (parsed["title"] as? String)?.trim() ?: ""
        val settled = sanitizeSettledKeys(parsed["settled"])
        return SplitSharePayload(
            title = title,
            people = people,
            expenses = expenses,
            settled = settled
        )
    }

    fun buildShareUrl(payload: SplitSharePayload): String {
        val encoded = encodeSharePayload(payload)
        return "$SHARE_BASE_URL#data=$encoded"
    }

    fun exportJson(payload: SplitSharePayload): String {
        val root = linkedMapOf<String, Any?>(
            "app" to APP_ID,
            "schemaVersion" to SCHEMA_VERSION,
            "exportedAt" to Instant.now().toString(),
            "data" to linkedMapOf<String, Any?>(
                "title" to payload.title.trim(),
                "people" to serializePeople(payload.people),
                "expenses" to serializeExpenses(payload.expenses),
                "settled" to payload.settled
            )
        )
        return JsonCodec.stringify(root, pretty = true)
    }

    fun importJson(jsonText: String): SplitSharePayload {
        val trimmed = jsonText.trim()
        if (trimmed.isEmpty()) {
            throw IllegalArgumentException("Berkas JSON kosong.")
        }

        val parsed = try {
            JsonCodec.parse(trimmed) as? Map<*, *>
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Berkas bukan JSON yang valid.", e)
        }

        val data = parsed?.get("data") as? Map<*, *>
        if (parsed == null || parsed["app"] != APP_ID || data == null) {
            throw IllegalArgumentException("Format berkas bukan cadangan Split Bill Kelompok.")
        }

        val people = sanitizePeopleList(data["people"])
        val expenses = sanitizeExpensesList(data["expenses"])
        if (people == null || expenses == null) {
            throw IllegalArgumentException("Data peserta atau pengeluaran di dalam berkas tidak valid.")
        }

        val title = (data["title"] as? String)?.trim() ?: ""
        val settled = sanitizeSettledKeys(data["settled"])
        return SplitSharePayload(
            title = title,
            people = people,
            expenses = expenses,
            settled = settled
        )
    }

    internal fun sanitizePeopleList(raw: Any?): List<SplitPerson>? {
        val list = raw as? List<*> ?: return null
        val seenIds = HashSet<Long>()
        val clean = mutableListOf<SplitPerson>()
        for (item in list) {
            val obj = item as? Map<*, *> ?: continue
            val id = parseFiniteLong(obj["id"]) ?: continue
            val name = (obj["name"] as? String)?.trim().orEmpty()
            if (name.isEmpty() || !seenIds.add(id)) continue
            val isSelf = obj["isSelf"] as? Boolean ?: false
            clean.add(SplitPerson(id = id, name = name, isSelf = isSelf))
        }
        return clean
    }

    internal fun sanitizeExpensesList(raw: Any?): List<SplitExpense>? {
        val list = raw as? List<*> ?: return null
        val clean = mutableListOf<SplitExpense>()
        for (item in list) {
            val obj = item as? Map<*, *> ?: continue
            val id = parseFiniteLong(obj["id"]) ?: continue
            val desc = (obj["desc"] as? String)?.trim().orEmpty()
            val amountRaw = parseFiniteDouble(obj["amount"]) ?: continue
            val payerId = parseFiniteLong(obj["payerId"]) ?: continue
            val splitList = obj["splitIds"] as? List<*> ?: continue

            if (desc.isEmpty() || amountRaw <= 0.0) continue
            val amount = floor(amountRaw + 0.5).toLong()
            if (amount <= 0L) continue

            val splitIds = splitList.mapNotNull { parseFiniteLong(it) }
            if (splitIds.isEmpty()) continue

            val weightsMap = linkedMapOf<Long, Double>()
            val rawWeights = obj["weights"] as? Map<*, *>
            if (rawWeights != null) {
                for ((k, v) in rawWeights) {
                    val personId = k?.toString()?.toLongOrNull() ?: continue
                    val w = parseFiniteDouble(v) ?: continue
                    if (w >= 0.0) {
                        weightsMap[personId] = w
                    }
                }
            }

            val subtotalRaw = parseFiniteDouble(obj["subtotal"])
            val subtotal = if (subtotalRaw != null && subtotalRaw > 0.0) {
                floor(subtotalRaw + 0.5).toLong()
            } else {
                amount
            }

            val taxRaw = parseFiniteDouble(obj["taxPercent"])
            val taxPercent = if (taxRaw != null && taxRaw > 0.0) taxRaw else 0.0

            val discountRaw = parseFiniteDouble(obj["discount"])
            val discount = if (discountRaw != null && discountRaw > 0.0) {
                floor(discountRaw + 0.5).toLong()
            } else {
                0L
            }

            clean.add(
                SplitExpense(
                    id = id,
                    desc = desc,
                    amount = amount,
                    payerId = payerId,
                    splitIds = splitIds,
                    weights = weightsMap.toMap(),
                    subtotal = subtotal,
                    taxPercent = taxPercent,
                    discount = discount
                )
            )
        }
        return clean
    }

    internal fun sanitizeSettledKeys(raw: Any?): List<String> {
        val list = raw as? List<*> ?: return emptyList()
        return list.mapNotNull { item ->
            val key = item as? String ?: return@mapNotNull null
            if (key.contains("->")) key else null
        }
    }

    private fun serializePeople(people: List<SplitPerson>): List<Map<String, Any?>> {
        return people.map { person ->
            val obj = linkedMapOf<String, Any?>(
                "id" to person.id,
                "name" to person.name.trim()
            )
            if (person.isSelf) {
                obj["isSelf"] = true
            }
            obj
        }
    }

    private fun serializeExpenses(expenses: List<SplitExpense>): List<Map<String, Any?>> {
        return expenses.map { expense ->
            val obj = linkedMapOf<String, Any?>(
                "id" to expense.id,
                "desc" to expense.desc.trim(),
                "amount" to expense.amount,
                "payerId" to expense.payerId,
                "splitIds" to expense.splitIds
            )
            if (expense.weights.isNotEmpty()) {
                val weightsObj = linkedMapOf<String, Any?>()
                for ((personId, w) in expense.weights) {
                    if (w.isFinite() && w >= 0.0) {
                        weightsObj[personId.toString()] = w
                    }
                }
                if (weightsObj.isNotEmpty()) {
                    obj["weights"] = weightsObj
                }
            }
            if (expense.taxPercent > 0.0 || expense.discount > 0L ||
                (expense.subtotal > 0L && expense.subtotal != expense.amount)
            ) {
                if (expense.subtotal > 0L) {
                    obj["subtotal"] = expense.subtotal
                }
                if (expense.taxPercent > 0.0) {
                    obj["taxPercent"] = expense.taxPercent
                }
                if (expense.discount > 0L) {
                    obj["discount"] = expense.discount
                }
            }
            obj
        }
    }

    private fun parseFiniteDouble(value: Any?): Double? {
        return when (value) {
            is Number -> value.toDouble().takeIf { it.isFinite() }
            is String -> {
                val trimmed = value.trim()
                if (trimmed.isEmpty()) null else trimmed.toDoubleOrNull()?.takeIf { it.isFinite() }
            }
            else -> null
        }
    }

    private fun parseFiniteLong(value: Any?): Long? {
        return when (value) {
            is Long -> value
            is Int -> value.toLong()
            is Number -> {
                val d = value.toDouble()
                if (d.isFinite() && d >= Long.MIN_VALUE.toDouble() && d <= Long.MAX_VALUE.toDouble()) {
                    d.toLong()
                } else {
                    null
                }
            }
            is String -> {
                val trimmed = value.trim()
                if (trimmed.isEmpty()) null else trimmed.toLongOrNull()
            }
            else -> null
        }
    }

    /**
     * Self-contained RFC-8259 JSON parser and serializer so SplitBillShareCodec runs cleanly
     * in both JVM unit tests (where Android's org.json stub is not mocked) and Android runtime.
     */
    private object JsonCodec {

        fun stringify(value: Any?, pretty: Boolean): String {
            val sb = StringBuilder()
            writeValue(sb, value, pretty, depth = 0)
            return sb.toString()
        }

        private fun writeValue(sb: StringBuilder, value: Any?, pretty: Boolean, depth: Int) {
            when (value) {
                null -> sb.append("null")
                is Boolean -> sb.append(if (value) "true" else "false")
                is Int, is Long -> sb.append(value.toString())
                is Double -> appendDouble(sb, value)
                is Float -> appendDouble(sb, value.toDouble())
                is Number -> sb.append(value.toString())
                is String -> appendString(sb, value)
                is Map<*, *> -> {
                    if (value.isEmpty()) {
                        sb.append("{}")
                        return
                    }
                    sb.append('{')
                    var first = true
                    for ((k, v) in value) {
                        if (!first) sb.append(',')
                        first = false
                        if (pretty) {
                            sb.append('\n')
                            appendIndent(sb, depth + 1)
                        }
                        appendString(sb, k.toString())
                        sb.append(if (pretty) ": " else ":")
                        writeValue(sb, v, pretty, depth + 1)
                    }
                    if (pretty) {
                        sb.append('\n')
                        appendIndent(sb, depth)
                    }
                    sb.append('}')
                }
                is Iterable<*> -> {
                    val items = value.toList()
                    if (items.isEmpty()) {
                        sb.append("[]")
                        return
                    }
                    sb.append('[')
                    for (i in items.indices) {
                        if (i > 0) sb.append(',')
                        if (pretty) {
                            sb.append('\n')
                            appendIndent(sb, depth + 1)
                        }
                        writeValue(sb, items[i], pretty, depth + 1)
                    }
                    if (pretty) {
                        sb.append('\n')
                        appendIndent(sb, depth)
                    }
                    sb.append(']')
                }
                else -> appendString(sb, value.toString())
            }
        }

        private fun appendIndent(sb: StringBuilder, depth: Int) {
            repeat(depth * 2) { sb.append(' ') }
        }

        private fun appendDouble(sb: StringBuilder, value: Double) {
            if (!value.isFinite()) {
                sb.append("0")
                return
            }
            if (value == 0.0) {
                sb.append("0")
                return
            }
            if (value == floor(value) && abs(value) < Long.MAX_VALUE.toDouble()) {
                sb.append(value.toLong().toString())
                return
            }
            sb.append(BigDecimal.valueOf(value).stripTrailingZeros().toPlainString())
        }

        private fun appendString(sb: StringBuilder, value: String) {
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
                            sb.append(String.format("\\u%04x", ch.code))
                        } else {
                            sb.append(ch)
                        }
                    }
                }
            }
            sb.append('"')
        }

        fun parse(text: String): Any? {
            val parser = Parser(text)
            val result = parser.readValue()
            parser.skipWhitespace()
            if (!parser.isAtEnd()) {
                throw IllegalArgumentException("Unexpected trailing JSON content at index ${parser.index}")
            }
            return result
        }

        private class Parser(private val src: String) {
            var index = 0
                private set

            fun isAtEnd(): Boolean = index >= src.length

            fun skipWhitespace() {
                while (index < src.length && src[index] in charArrayOf(' ', '\t', '\n', '\r')) {
                    index++
                }
            }

            fun readValue(): Any? {
                skipWhitespace()
                if (isAtEnd()) throw IllegalArgumentException("Unexpected end of JSON")
                return when (val ch = src[index]) {
                    '{' -> readObject()
                    '[' -> readArray()
                    '"' -> readString()
                    't' -> readLiteral("true", true)
                    'f' -> readLiteral("false", false)
                    'n' -> readLiteral("null", null)
                    '-', in '0'..'9' -> readNumber()
                    else -> throw IllegalArgumentException("Unexpected character '$ch' at index $index")
                }
            }

            private fun readObject(): Map<String, Any?> {
                expect('{')
                skipWhitespace()
                val map = LinkedHashMap<String, Any?>()
                if (peek('}')) {
                    index++
                    return map
                }
                while (true) {
                    skipWhitespace()
                    if (!peek('"')) {
                        throw IllegalArgumentException("Expected object key string at index $index")
                    }
                    val key = readString()
                    skipWhitespace()
                    expect(':')
                    val value = readValue()
                    map[key] = value
                    skipWhitespace()
                    if (peek('}')) {
                        index++
                        return map
                    }
                    expect(',')
                }
            }

            private fun readArray(): List<Any?> {
                expect('[')
                skipWhitespace()
                val list = mutableListOf<Any?>()
                if (peek(']')) {
                    index++
                    return list
                }
                while (true) {
                    list.add(readValue())
                    skipWhitespace()
                    if (peek(']')) {
                        index++
                        return list
                    }
                    expect(',')
                }
            }

            private fun readString(): String {
                expect('"')
                val sb = StringBuilder()
                while (index < src.length) {
                    val ch = src[index++]
                    when (ch) {
                        '"' -> return sb.toString()
                        '\\' -> {
                            if (index >= src.length) {
                                throw IllegalArgumentException("Unterminated escape sequence")
                            }
                            when (val esc = src[index++]) {
                                '"', '\\', '/' -> sb.append(esc)
                                'b' -> sb.append('\b')
                                'f' -> sb.append('\u000C')
                                'n' -> sb.append('\n')
                                'r' -> sb.append('\r')
                                't' -> sb.append('\t')
                                'u' -> {
                                    if (index + 4 > src.length) {
                                        throw IllegalArgumentException("Invalid unicode escape at index $index")
                                    }
                                    val hex = src.substring(index, index + 4)
                                    val code = hex.toIntOrNull(16)
                                        ?: throw IllegalArgumentException("Invalid hex '$hex' at index $index")
                                    sb.append(code.toChar())
                                    index += 4
                                }
                                else -> throw IllegalArgumentException("Invalid escape '\\$esc' at index $index")
                            }
                        }
                        else -> {
                            if (ch.code < 0x20) {
                                throw IllegalArgumentException("Unescaped control character in string at index $index")
                            }
                            sb.append(ch)
                        }
                    }
                }
                throw IllegalArgumentException("Unterminated string")
            }

            private fun readNumber(): Number {
                val start = index
                if (peek('-')) index++
                if (index >= src.length) throw IllegalArgumentException("Invalid number at index $start")
                if (src[index] == '0') {
                    index++
                } else if (src[index] in '1'..'9') {
                    while (index < src.length && src[index] in '0'..'9') index++
                } else {
                    throw IllegalArgumentException("Invalid number at index $start")
                }

                var isFloat = false
                if (index < src.length && src[index] == '.') {
                    isFloat = true
                    index++
                    if (index >= src.length || src[index] !in '0'..'9') {
                        throw IllegalArgumentException("Invalid fraction in number at index $start")
                    }
                    while (index < src.length && src[index] in '0'..'9') index++
                }
                if (index < src.length && (src[index] == 'e' || src[index] == 'E')) {
                    isFloat = true
                    index++
                    if (index < src.length && (src[index] == '+' || src[index] == '-')) index++
                    if (index >= src.length || src[index] !in '0'..'9') {
                        throw IllegalArgumentException("Invalid exponent in number at index $start")
                    }
                    while (index < src.length && src[index] in '0'..'9') index++
                }

                val token = src.substring(start, index)
                if (!isFloat) {
                    token.toLongOrNull()?.let { return it }
                }
                val parsed = token.toDoubleOrNull()
                if (parsed == null || !parsed.isFinite()) {
                    throw IllegalArgumentException("Number out of range '$token'")
                }
                return parsed
            }

            private fun <T> readLiteral(expected: String, result: T): T {
                if (!src.startsWith(expected, startIndex = index)) {
                    throw IllegalArgumentException("Unexpected token at index $index")
                }
                index += expected.length
                return result
            }

            private fun peek(ch: Char): Boolean = index < src.length && src[index] == ch

            private fun expect(ch: Char) {
                if (index >= src.length || src[index] != ch) {
                    throw IllegalArgumentException("Expected '$ch' at index $index")
                }
                index++
            }
        }
    }
}
