package com.wein.fasttrack.splitbill

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitBillCalculatorTest {

    @Test
    fun `splitAmount membagi rata dengan sisa 1 rupiah di awal`() {
        assertEquals(listOf(3334L, 3333L, 3333L), SplitBillCalculator.splitAmount(10000L, 3))
        assertEquals(listOf(50000L, 50000L, 50000L), SplitBillCalculator.splitAmount(150000L, 3))
        assertEquals(emptyList<Long>(), SplitBillCalculator.splitAmount(10000L, 0))
    }

    @Test
    fun `computeNetAmount menghitung pajak persen dan diskon dengan benar`() {
        assertEquals(111000L, SplitBillCalculator.computeNetAmount(100000L, 11.0, 0L))
        assertEquals(95000L, SplitBillCalculator.computeNetAmount(100000L, 10.0, 15000L))
        assertEquals(0L, SplitBillCalculator.computeNetAmount(10000L, 0.0, 25000L))
        assertEquals(17L, SplitBillCalculator.computeNetAmount(15L, 10.0, 0L))
    }

    @Test
    fun `splitWeighted membagi porsi tidak rata dan menjaga total pas`() {
        val shares = SplitBillCalculator.splitWeighted(
            100000L,
            listOf(
                1L to 2.0,
                2L to 1.0,
                3L to 0.5
            )
        )
        assertEquals(100000L, shares.sum())
        assertEquals(listOf(57143L, 28571L, 14286L), shares)
    }

    @Test
    fun `splitWeighted menangani bobot nol atau kosong dengan aman`() {
        assertEquals(emptyList<Long>(), SplitBillCalculator.splitWeighted(10000L, emptyList()))
        assertEquals(
            listOf(0L, 0L),
            SplitBillCalculator.splitWeighted(10000L, listOf(1L to 0.0, 2L to -1.0))
        )
    }

    @Test
    fun `computeBalances, computeDetails, dan computeSettlements konsisten dan minimal`() {
        val people = listOf(
            SplitPerson(1L, "Andi"),
            SplitPerson(2L, "Budi"),
            SplitPerson(3L, "Cici")
        )
        val expenses = listOf(
            SplitExpense(10L, "Makan", 150000L, payerId = 1L, splitIds = listOf(1L, 2L, 3L)),
            SplitExpense(11L, "Parkir", 10000L, payerId = 3L, splitIds = listOf(1L, 2L, 3L))
        )

        val balances = SplitBillCalculator.computeBalances(people, expenses)
        assertEquals(0L, balances.values.sum())

        val details = SplitBillCalculator.computeDetails(people, expenses)
        assertEquals(150000L, details[1L]?.paid)
        assertEquals(10000L, details[3L]?.paid)
        assertEquals(53334L, details[1L]?.share)
        assertEquals(96666L, details[1L]?.balance)

        val settlements = SplitBillCalculator.computeSettlements(people, balances)
        assertEquals(2, settlements.size)
        assertEquals(
            listOf(
                SettlementTransfer(2L, 1L, "Budi", "Andi", 53333L),
                SettlementTransfer(3L, 1L, "Cici", "Andi", 43333L)
            ),
            settlements
        )
        assertEquals("2->1:53333", settlements[0].key())
    }

    @Test
    fun `describeSplit dan expenseWeight mendukung data lama tanpa weights`() {
        val legacy = SplitExpense(1L, "Kopi", 30000L, payerId = 1L, splitIds = listOf(1L, 2L))
        assertEquals(1.0, SplitBillCalculator.expenseWeight(legacy, 1L), 0.0)
        assertEquals(
            "Andi, Budi",
            SplitBillCalculator.describeSplit(legacy) { id -> if (id == 1L) "Andi" else "Budi" }
        )

        val weighted = SplitExpense(
            id = 2L,
            desc = "Ramen",
            amount = 90000L,
            payerId = 1L,
            splitIds = listOf(1L, 2L),
            weights = mapOf(1L to 2.0, 2L to 0.5)
        )
        assertEquals(
            "Andi (porsi 2), Budi (porsi 0,5)",
            SplitBillCalculator.describeSplit(weighted) { id -> if (id == 1L) "Andi" else "Budi" }
        )
    }

    @Test
    fun `formatRupiah, groupThousands, dan parseAmountInput memformat dan mem-parse angka Rupiah`() {
        assertEquals("Rp150.000", SplitBillCalculator.formatRupiah(150000L))
        assertEquals("Rp1.500.000", SplitBillCalculator.formatRupiah(1500000L))
        assertEquals("Rp0", SplitBillCalculator.formatRupiah(0L))
        assertEquals("-Rp5.000", SplitBillCalculator.formatRupiah(-5000L))
        assertEquals("1.500.000", SplitBillCalculator.groupThousands(1500000L))
        assertEquals(150000L, SplitBillCalculator.parseAmountInput("150.000"))
        assertEquals(0L, SplitBillCalculator.parseAmountInput(""))
    }

    @Test
    fun `buildWhatsAppSummary menghasilkan ringkasan lengkap sesuai format web`() {
        val people = listOf(
            SplitPerson(1L, "Andi", isSelf = true),
            SplitPerson(2L, "Budi"),
            SplitPerson(3L, "Cici")
        )
        val expenses = listOf(
            SplitExpense(10L, "Makan", 150000L, payerId = 1L, splitIds = listOf(1L, 2L, 3L)),
            SplitExpense(
                id = 11L,
                desc = "Kopi",
                amount = 45000L,
                payerId = 2L,
                splitIds = listOf(1L, 2L),
                weights = mapOf(1L to 2.0, 2L to 1.0)
            )
        )
        val balances = SplitBillCalculator.computeBalances(people, expenses)
        val details = SplitBillCalculator.computeDetails(people, expenses)
        val settlements = SplitBillCalculator.computeSettlements(people, balances)
        val settledKeys = setOf(settlements.first().key())

        val summary = SplitBillCalculator.buildWhatsAppSummary(
            title = "Bukber Tim",
            people = people,
            expenses = expenses,
            details = details,
            settlements = settlements,
            settledKeys = settledKeys
        )

        val expected = buildString {
            appendLine("Split Bill Kelompok: Bukber Tim")
            appendLine("Total: Rp195.000")
            appendLine()
            appendLine("Pengeluaran:")
            appendLine("\u2022 Makan: Rp150.000 (dibayar Andi)")
            appendLine("\u2022 Kopi: Rp45.000 (dibayar Budi, dibagi ke Andi (porsi 2), Budi (porsi 1))")
            appendLine()
            appendLine("Rincian per orang:")
            appendLine("\u2022 Andi: bayar Rp150.000, jatah Rp80.000, kelebihan Rp70.000")
            appendLine("\u2022 Budi: bayar Rp45.000, jatah Rp65.000, kurang Rp20.000")
            appendLine("\u2022 Cici: bayar Rp0, jatah Rp50.000, kurang Rp50.000")
            appendLine()
            appendLine("Siapa transfer ke siapa:")
            appendLine("\u2022 Cici \u2192 Andi: Rp50.000 [Lunas]")
            append("\u2022 Budi \u2192 Andi: Rp20.000")
        }
        assertEquals(expected, summary)

        assertEquals(
            "",
            SplitBillCalculator.buildWhatsAppSummary(
                title = "Kosong",
                people = people,
                expenses = emptyList(),
                details = emptyMap(),
                settlements = emptyList(),
                settledKeys = emptySet()
            )
        )
    }

    @Test
    fun `encodeSharePayload dan decodeSharePayload menjaga karakter UTF-8 dan menerima format URL`() {
        val original = SplitSharePayload(
            title = "Makan Malam: Tim (Café & Kopi) \uD83C\uDF5C",
            people = listOf(
                SplitPerson(1L, "Andi", isSelf = true),
                SplitPerson(2L, "Budi")
            ),
            expenses = listOf(
                SplitExpense(
                    id = 10L,
                    desc = "Sate & Es Teh",
                    amount = 66000L,
                    payerId = 1L,
                    splitIds = listOf(1L, 2L),
                    weights = mapOf(1L to 1.0, 2L to 1.0),
                    subtotal = 60000L,
                    taxPercent = 10.0,
                    discount = 0L
                )
            ),
            settled = listOf("2->1:33000")
        )

        val encoded = SplitBillShareCodec.encodeSharePayload(original)
        val fullUrl = SplitBillShareCodec.buildShareUrl(original)
        assertTrue(fullUrl.startsWith("https://weinssy.github.io/split-bill-kelompok/#data="))
        assertEquals("https://weinssy.github.io/split-bill-kelompok/#data=$encoded", fullUrl)

        for (input in listOf(encoded, "#data=$encoded", fullUrl)) {
            val decoded = SplitBillShareCodec.decodeSharePayload(input)
            assertEquals("Makan Malam: Tim (Café & Kopi) \uD83C\uDF5C", decoded.title)
            assertEquals(2, decoded.people.size)
            assertTrue(decoded.people[0].isSelf)
            assertEquals(10.0, decoded.expenses[0].taxPercent, 0.0)
            assertEquals(60000L, decoded.expenses[0].subtotal)
            assertEquals(listOf("2->1:33000"), decoded.settled)
        }
    }

    @Test
    fun `decodeSharePayload menolak payload invalid atau rusak`() {
        val invalidInputs = listOf(
            "",
            "   ",
            "#data=",
            "bukan-base64-valid!!!",
            java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("""{"app":"wrong-app","people":[],"expenses":[]}""".toByteArray()),
            java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("""{"app":"split-bill-kelompok","people":"bad","expenses":[]}""".toByteArray())
        )
        for (bad in invalidInputs) {
            var threw = false
            try {
                SplitBillShareCodec.decodeSharePayload(bad)
            } catch (_: IllegalArgumentException) {
                threw = true
            }
            assertTrue("Expected decodeSharePayload to fail for: $bad", threw)
        }
    }

    @Test
    fun `exportJson dan importJson melakukan roundtrip schemaVersion 1`() {
        val original = SplitSharePayload(
            title = "Trip Bandung",
            people = listOf(
                SplitPerson(1L, "Andi", isSelf = true),
                SplitPerson(2L, "Budi")
            ),
            expenses = listOf(
                SplitExpense(
                    id = 101L,
                    desc = "Villa \"Lembang\"",
                    amount = 290000L,
                    payerId = 1L,
                    splitIds = listOf(1L, 2L),
                    weights = mapOf(1L to 1.5, 2L to 1.0),
                    subtotal = 300000L,
                    taxPercent = 10.0,
                    discount = 40000L
                )
            ),
            settled = listOf("2->1:116000")
        )

        val exported = SplitBillShareCodec.exportJson(original)
        assertTrue(exported.contains("\"app\": \"split-bill-kelompok\""))
        assertTrue(exported.contains("\"schemaVersion\": 1"))
        assertTrue(exported.contains("\"exportedAt\":"))

        val imported = SplitBillShareCodec.importJson(exported)
        assertEquals(original, imported)
    }

    @Test
    fun `importJson juga menerima berkas cadangan versi web tanpa isSelf`() {
        val webBackup = """
            {
              "app": "split-bill-kelompok",
              "schemaVersion": 1,
              "exportedAt": "2026-11-25T00:00:00.000Z",
              "data": {
                "title": "  Patungan Kost  ",
                "people": [
                  { "id": 1, "name": " Andi " },
                  { "id": 1, "name": "Duplikat" },
                  { "id": "abc", "name": "Salah" },
                  { "id": 2, "name": "Budi" }
                ],
                "expenses": [
                  { "id": 10, "desc": "Galon", "amount": 20000, "payerId": 1, "splitIds": [1, 2] },
                  { "id": 11, "desc": "Nol", "amount": 0, "payerId": 1, "splitIds": [1] }
                ],
                "settled": ["2->1:10000", "invalid-key"]
              }
            }
        """.trimIndent()

        val imported = SplitBillShareCodec.importJson(webBackup)
        assertEquals("Patungan Kost", imported.title)
        assertEquals(
            listOf(
                SplitPerson(1L, "Andi", isSelf = false),
                SplitPerson(2L, "Budi", isSelf = false)
            ),
            imported.people
        )
        assertEquals(1, imported.expenses.size)
        assertEquals(20000L, imported.expenses[0].subtotal)
        assertEquals(emptyMap<Long, Double>(), imported.expenses[0].weights)
        assertEquals(listOf("2->1:10000"), imported.settled)
    }

    @Test
    fun `sanitizePeopleList dan sanitizeExpensesList menyaring entri rusak`() {
        assertNull(SplitBillShareCodec.sanitizePeopleList("invalid"))
        assertEquals(
            listOf(SplitPerson(1L, "Andi")),
            SplitBillShareCodec.sanitizePeopleList(
                listOf(
                    mapOf("id" to 1, "name" to " Andi "),
                    mapOf("id" to 1, "name" to "Duplikat ID"),
                    mapOf("id" to "abc", "name" to "Salah")
                )
            )
        )

        assertNull(SplitBillShareCodec.sanitizeExpensesList(null))
        assertEquals(
            listOf(SplitExpense(2L, "Valid", 5000L, 1L, listOf(1L))),
            SplitBillShareCodec.sanitizeExpensesList(
                listOf(
                    mapOf(
                        "id" to 2,
                        "desc" to "Valid",
                        "amount" to 5000,
                        "payerId" to 1,
                        "splitIds" to listOf(1)
                    ),
                    mapOf(
                        "id" to 3,
                        "desc" to "Nol",
                        "amount" to 0,
                        "payerId" to 1,
                        "splitIds" to listOf(1)
                    )
                )
            )
        )
    }
}
