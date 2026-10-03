package com.azimulkabir.actua.data.budget.model

enum class ActualAccountType {
    CHECKING,
    SAVINGS,
    CREDIT,
    INVESTMENT,
    MORTGAGE,
    DEBT,
    OTHER;

    companion object {
        fun fromDatabase(value: String?): ActualAccountType = entries.firstOrNull {
            it.name.equals(value, ignoreCase = true)
        } ?: CHECKING
    }
}

data class ActualAccount(
    val id: String,
    val name: String,
    val type: ActualAccountType,
    val offBudget: Boolean,
    val closed: Boolean,
    val sortOrder: Int,
    val balanceCents: Long,
    val clearedCents: Long = 0,
    val unclearedCents: Long = 0,
    val reconciledCents: Long = 0,
)

data class ActualBankSyncAccount(
    val id: String,
    val name: String,
    val externalAccountId: String,
    val provider: String,
    val bankName: String?,
)

data class ActualPayee(
    val id: String,
    val name: String,
    val transferAccountId: String?,
)

data class ActualCategory(
    val id: String,
    val name: String,
    val groupId: String,
    val isIncome: Boolean,
    val hidden: Boolean,
    val sortOrder: Double,
    val cleanupDef: String? = null,
)

/** Actual's `cleanup_groups` table: named pools referenced by category `cleanup_def` rows. */
data class ActualCleanupGroup(
    val id: String,
    val name: String,
    val tombstone: Boolean = false,
)

data class ActualCategoryGroup(
    val id: String,
    val name: String,
    val isIncome: Boolean,
    val hidden: Boolean,
    val sortOrder: Double,
    val categories: List<ActualCategory>,
)

data class ActualTransaction(
    val id: String,
    val accountId: String,
    val date: Int,
    val amountCents: Long,
    val payeeId: String?,
    val payeeName: String?,
    val categoryId: String?,
    val categoryName: String?,
    val notes: String?,
    val cleared: Boolean,
    val reconciled: Boolean,
    val transferId: String?,
    val isParent: Boolean,
    val parentId: String?,
    val tombstone: Boolean,
    val sortOrder: Double?,
    val importedPayee: String?,
    val scheduleId: String?,
    val transferAccountId: String?,
    val startingBalance: Boolean = false,
    val splitPortions: List<SplitPortion> = emptyList(),
    val categoryIsIncome: Boolean? = null,
    val importedId: String? = null,
    val rawSyncedData: String? = null,
) {
    data class SplitPortion(
        val id: String,
        val categoryName: String?,
        val amountCents: Long,
        val notes: String?,
        val payeeName: String?,
        val categoryIsIncome: Boolean? = null,
    )
}
