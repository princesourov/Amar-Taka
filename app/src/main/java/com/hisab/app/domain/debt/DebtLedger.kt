package com.hisab.app.domain.debt

/**
 * Debt (lending / borrowing) rules as pure functions - no Android, no database - so they can be
 * unit-tested and so the SQL in TransactionDao has exactly one written-down definition to mirror.
 *
 * Model (all of it comes from the ledger rows with a personId; nothing is stored separately):
 *  - LENDING            -> a RECEIVABLE debt: the person owes the user.
 *  - REPAYMENT_RECEIVED -> reduces that person's receivable.
 *  - BORROWING          -> a PAYABLE debt: the user owes the person.
 *  - REPAYMENT_MADE     -> reduces that person's payable.
 *
 * The two directions are tracked SEPARATELY per person (someone you lent 100 and borrowed 40 from is
 * a 100 receivable and a 40 payable). That keeps every record's own status honest and makes
 * "Net position = receivable - payable" the sum of both. A repayment can never push a direction
 * below zero: anything above what was owed is reported as an unallocated surplus instead of silently
 * turning into a debt in the other direction.
 */
enum class DebtDirection { RECEIVABLE, PAYABLE }

enum class DebtStatus { ACTIVE, PARTIALLY_PAID, SETTLED }

/** The slice of a ledger row the debt rules need (Room-free). */
data class DebtTxn(
    val id: String,
    val type: String,
    val amountMinor: Long,
    val dateMillis: Long,
    val createdAtMillis: Long = dateMillis,
    val note: String? = null,
    val dueDateEpochDay: Long? = null
)

data class DebtPaymentAllocation(val paymentId: String, val amountMinor: Long, val dateMillis: Long)

/** One lent/borrowed amount with what has been repaid against it (oldest repayments go to oldest debts first). */
data class DebtRecord(
    val id: String,
    val direction: DebtDirection,
    val principalMinor: Long,
    val paidMinor: Long,
    val dateMillis: Long,
    val dueDateEpochDay: Long?,
    val note: String?,
    val payments: List<DebtPaymentAllocation>
) {
    val remainingMinor: Long get() = principalMinor - paidMinor
    val status: DebtStatus
        get() = when {
            paidMinor <= 0 -> DebtStatus.ACTIVE
            paidMinor < principalMinor -> DebtStatus.PARTIALLY_PAID
            else -> DebtStatus.SETTLED
        }
}

data class PersonDebt(
    val records: List<DebtRecord>,
    val receivableMinor: Long,
    val payableMinor: Long,
    /** Repayments received above everything lent (only possible from old data or after deleting a loan). */
    val surplusReceivedMinor: Long,
    /** Repayments made above everything borrowed. */
    val surplusMadeMinor: Long
) {
    val netMinor: Long get() = receivableMinor - payableMinor
}

data class DebtTotals(val receivableMinor: Long, val payableMinor: Long) {
    /** Net Position = Total Receivable - Total Payable. */
    val netMinor: Long get() = receivableMinor - payableMinor

    companion object { val ZERO = DebtTotals(0, 0) }
}

object DebtLedger {

    const val LENDING = "LENDING"
    const val BORROWING = "BORROWING"
    const val REPAYMENT_RECEIVED = "REPAYMENT_RECEIVED"
    const val REPAYMENT_MADE = "REPAYMENT_MADE"

    /** What is still owed in one direction: principal - repayments, never below zero. */
    fun outstanding(principalMinor: Long, repaidMinor: Long): Long = (principalMinor - repaidMinor).coerceAtLeast(0)

    /** Totals across people: each person's receivable and payable are clamped at zero before summing. */
    fun totals(rows: Collection<Pair<Long, Long>>): DebtTotals =
        DebtTotals(
            receivableMinor = rows.sumOf { it.first.coerceAtLeast(0) },
            payableMinor = rows.sumOf { it.second.coerceAtLeast(0) }
        )

    /**
     * Builds one person's debt picture from that person's non-deleted ledger rows.
     * Repayments are applied to debts first-in-first-out (by date, then creation time, then id),
     * which is deterministic and needs no stored link between a repayment and a loan.
     */
    fun build(txns: List<DebtTxn>): PersonDebt {
        val order = compareBy<DebtTxn>({ it.dateMillis }, { it.createdAtMillis }, { it.id })
        val received = mutableListOf<DebtRecord>()
        val payable = mutableListOf<DebtRecord>()
        val (recv, recvSurplus) = allocate(
            principals = txns.filter { it.type == LENDING && it.amountMinor > 0 }.sortedWith(order),
            payments = txns.filter { it.type == REPAYMENT_RECEIVED && it.amountMinor > 0 }.sortedWith(order),
            direction = DebtDirection.RECEIVABLE
        )
        val (pay, paySurplus) = allocate(
            principals = txns.filter { it.type == BORROWING && it.amountMinor > 0 }.sortedWith(order),
            payments = txns.filter { it.type == REPAYMENT_MADE && it.amountMinor > 0 }.sortedWith(order),
            direction = DebtDirection.PAYABLE
        )
        received += recv
        payable += pay
        val records = (received + payable).sortedWith(compareByDescending<DebtRecord> { it.dateMillis }.thenBy { it.id })
        return PersonDebt(
            records = records,
            receivableMinor = recv.sumOf { it.remainingMinor },
            payableMinor = pay.sumOf { it.remainingMinor },
            surplusReceivedMinor = recvSurplus,
            surplusMadeMinor = paySurplus
        )
    }

    private fun allocate(
        principals: List<DebtTxn>,
        payments: List<DebtTxn>,
        direction: DebtDirection
    ): Pair<List<DebtRecord>, Long> {
        val remaining = LongArray(principals.size) { principals[it].amountMinor }
        val applied = List(principals.size) { mutableListOf<DebtPaymentAllocation>() }
        var surplus = 0L
        var cursor = 0
        for (p in payments) {
            var left = p.amountMinor
            while (left > 0 && cursor < principals.size) {
                val take = minOf(left, remaining[cursor])
                if (take > 0) {
                    remaining[cursor] -= take
                    left -= take
                    applied[cursor] += DebtPaymentAllocation(p.id, take, p.dateMillis)
                }
                if (remaining[cursor] == 0L) cursor++
            }
            surplus += left
        }
        val records = principals.mapIndexed { i, t ->
            DebtRecord(
                id = t.id,
                direction = direction,
                principalMinor = t.amountMinor,
                paidMinor = t.amountMinor - remaining[i],
                dateMillis = t.dateMillis,
                dueDateEpochDay = t.dueDateEpochDay,
                note = t.note,
                payments = applied[i]
            )
        }
        return records to surplus
    }
}
