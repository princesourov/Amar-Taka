package com.hisab.app.data.local.entity

import com.hisab.app.domain.report.LedgerRow

/** Room row -> the Android-free row the report/budget calculators work on. */
fun TransactionEntity.toLedgerRow(): LedgerRow = LedgerRow(
    id = id,
    type = type,
    amountMinor = amountMinor,
    categoryId = categoryId,
    sourceAccountId = sourceAccountId,
    destinationAccountId = destinationAccountId,
    note = note,
    dateMillis = transactionDateMillis
)
