package com.wein.fasttrack.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "split_bill_sessions")
data class SplitBillSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val syncToExpenses: Boolean = true,
    val expenseTag: String = "Makan",
    val syncedExpenseId: Long? = null
)

@Entity(
    tableName = "split_bill_participants",
    foreignKeys = [
        ForeignKey(
            entity = SplitBillSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["sessionId"])]
)
data class SplitBillParticipantEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val name: String,
    val isSelf: Boolean = false,
    val sortOrder: Int = 0
)

@Entity(
    tableName = "split_bill_items",
    foreignKeys = [
        ForeignKey(
            entity = SplitBillSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["sessionId"])]
)
data class SplitBillItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val description: String,
    val amount: Long,
    val subtotal: Long = amount,
    val taxPercent: Double = 0.0,
    val discount: Long = 0L,
    val payerParticipantId: Long,
    val splitIdsSerialized: String = "",
    val weightsJson: String = "{}"
)

@Entity(
    tableName = "split_bill_settled",
    primaryKeys = ["sessionId", "settlementKey"],
    foreignKeys = [
        ForeignKey(
            entity = SplitBillSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["sessionId"])]
)
data class SplitBillSettledEntity(
    val sessionId: Long,
    val settlementKey: String
)

data class SplitBillSessionFull(
    @Embedded val session: SplitBillSessionEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "sessionId"
    )
    val participants: List<SplitBillParticipantEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "sessionId"
    )
    val items: List<SplitBillItemEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "sessionId"
    )
    val settledKeys: List<SplitBillSettledEntity>
)
