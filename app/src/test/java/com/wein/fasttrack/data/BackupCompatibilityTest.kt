package com.wein.fasttrack.data

import com.wein.fasttrack.utils.BackupManager
import com.wein.fasttrack.utils.CsvExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class BackupCompatibilityTest {

    @Test
    fun restoreV1Json_parsesWithoutV2FieldsAndAppliesDefaults() {
        val v1Json = """
            {
              "version": 1,
              "app": "FastTrack",
              "exportedAt": 1700000000000,
              "dailyBudgetCap": 150000,
              "tags": [
                { "id": 1, "name": "Umum", "isPreset": true },
                { "id": 7, "name": "Kopi", "isPreset": false }
              ],
              "expenses": [
                { "id": 10, "amount": 25000, "timestamp": 1700000001000, "tag": "Kopi", "note": "Es kopi susu" },
                { "id": 11, "amount": 12000, "timestamp": 1700000002000, "tag": null, "note": null }
              ]
            }
        """.trimIndent()

        val payload = BackupManager.readBackupFromStream(
            ByteArrayInputStream(v1Json.toByteArray(Charsets.UTF_8))
        )

        assertEquals(1, payload.version)
        assertEquals("FastTrack", payload.app)
        assertEquals(1700000000000L, payload.exportedAt)
        assertEquals(150000L, payload.dailyBudgetCap)
        assertEquals(0L, payload.monthlyBudgetCap)
        assertEquals("DARK", payload.themeMode)
        assertEquals(2, payload.tags.size)
        assertEquals("Kopi", payload.tags[1].name)
        assertEquals(2, payload.expenses.size)
        assertEquals(25000L, payload.expenses[0].amount)
        assertEquals("Es kopi susu", payload.expenses[0].note)
        assertNull(payload.expenses[0].splitBillSessionId)
        assertNull(payload.expenses[1].tag)
        assertNull(payload.expenses[1].note)
        assertNull(payload.expenses[1].splitBillSessionId)
        assertTrue(payload.recurringExpenses.isEmpty())
        assertTrue(payload.splitBillSessions.isEmpty())
    }

    @Test
    fun v2RoundTripViaByteStreams_preservesAllFields() {
        val original = BackupPayload(
            version = 2,
            app = "FastTrack",
            exportedAt = 1764000000123L,
            dailyBudgetCap = 200000L,
            tags = listOf(
                TagEntity(id = 1L, name = "Umum", isPreset = true),
                TagEntity(id = 2L, name = "Makan", isPreset = true),
                TagEntity(id = 8L, name = "Olahraga", isPreset = false)
            ),
            expenses = listOf(
                Expense(
                    id = 1L,
                    amount = 45000L,
                    timestamp = 1764000000000L,
                    tag = "Makan",
                    note = "Nasi \"Padang\",\nExtra sambal",
                    splitBillSessionId = null
                ),
                Expense(
                    id = 2L,
                    amount = 75500L,
                    timestamp = 1764000050000L,
                    tag = "Makan",
                    note = "Patungan: Makan Tim",
                    splitBillSessionId = 4L
                )
            ),
            monthlyBudgetCap = 4500000L,
            themeMode = "LIGHT",
            recurringExpenses = listOf(
                RecurringExpenseEntity(
                    id = 3L,
                    amount = 350000L,
                    tag = "Tagihan",
                    note = "Internet Rumah",
                    dayOfMonth = 25,
                    isEnabled = true,
                    lastAppliedYearMonth = "2026-11",
                    createdAt = 1760000000000L
                ),
                RecurringExpenseEntity(
                    id = 4L,
                    amount = 99000L,
                    tag = "Tagihan",
                    note = null,
                    dayOfMonth = 31,
                    isEnabled = false,
                    lastAppliedYearMonth = null,
                    createdAt = 1760000001000L
                )
            ),
            splitBillSessions = listOf(
                SplitBillSessionFull(
                    session = SplitBillSessionEntity(
                        id = 4L,
                        title = "Makan Tim",
                        createdAt = 1764000040000L,
                        updatedAt = 1764000050000L,
                        syncToExpenses = true,
                        expenseTag = "Makan",
                        syncedExpenseId = 2L
                    ),
                    participants = listOf(
                        SplitBillParticipantEntity(id = 10L, sessionId = 4L, name = "Saya", isSelf = true, sortOrder = 0),
                        SplitBillParticipantEntity(id = 11L, sessionId = 4L, name = "Budi", isSelf = false, sortOrder = 1)
                    ),
                    items = listOf(
                        SplitBillItemEntity(
                            id = 20L,
                            sessionId = 4L,
                            description = "Ayam Bakar & Es Teh",
                            amount = 151000L,
                            subtotal = 140000L,
                            taxPercent = 10.0,
                            discount = 3000L,
                            payerParticipantId = 11L,
                            splitIdsSerialized = "10,11",
                            weightsJson = "{\"10\":1.5,\"11\":1.5}"
                        )
                    ),
                    settledKeys = listOf(
                        SplitBillSettledEntity(sessionId = 4L, settlementKey = "10->11:75500")
                    )
                )
            )
        )

        val out = ByteArrayOutputStream()
        BackupManager.writeBackupToStream(out, original)
        val restored = BackupManager.readBackupFromStream(ByteArrayInputStream(out.toByteArray()))

        assertEquals(original, restored)
    }

    @Test
    fun deserializeBackup_ignoresUnknownForwardKeys() {
        val futureJson = """
            {
              "version": 3,
              "app": "FastTrack",
              "exportedAt": 1764000000000,
              "dailyBudgetCap": 100000,
              "monthlyBudgetCap": 3000000,
              "themeMode": "SYSTEM",
              "futureFlag": true,
              "tags": [
                { "id": 1, "name": "Umum", "isPreset": true, "colorHex": "#FFFFFF" }
              ],
              "expenses": [
                { "id": 5, "amount": 50000, "timestamp": 1764000000000, "tag": "Umum", "note": "Tes", "extra": [1, 2] }
              ]
            }
        """.trimIndent()

        val payload = BackupManager.deserializeBackup(futureJson)
        assertEquals(3, payload.version)
        assertEquals(3000000L, payload.monthlyBudgetCap)
        assertEquals("SYSTEM", payload.themeMode)
        assertEquals(1, payload.tags.size)
        assertEquals(1, payload.expenses.size)
        assertNull(payload.expenses[0].splitBillSessionId)
    }

    @Test(expected = IllegalArgumentException::class)
    fun deserializeBackup_rejectsNonFastTrackApp() {
        BackupManager.deserializeBackup("""{ "version": 2, "app": "OtherApp" }""")
    }

    @Test
    fun csvExporter_includesSourceAndEscapesSpecialCharacters() {
        val expenses = listOf(
            Expense(
                id = 1L,
                amount = 25000L,
                timestamp = 1764000000000L,
                tag = "Makan",
                note = "Bakso, \"Jumbo\"",
                splitBillSessionId = null
            ),
            Expense(
                id = 2L,
                amount = 60000L,
                timestamp = 1764000060000L,
                tag = "Makan",
                note = "Patungan: Dinner",
                splitBillSessionId = 9L
            )
        )

        val csv = CsvExporter.buildExpensesCsv(expenses)
        val lines = csv.trimEnd('\n').split("\n")
        assertEquals("ID,Tanggal,Waktu,Nominal,Kategori,Catatan,Sumber", lines[0])
        assertTrue(lines[1].contains("\"Bakso, \"\"Jumbo\"\"\""))
        assertTrue(lines[1].endsWith(",Pribadi"))
        assertTrue(lines[2].endsWith(",Patungan"))
    }
}
