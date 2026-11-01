package com.hisab.app.domain.model

import com.hisab.app.data.local.entity.BudgetEntity
import com.hisab.app.domain.report.DateSpan

/** A budget together with the period it is being evaluated for and what was spent in it. */
data class BudgetSpent(val budget: BudgetEntity, val period: DateSpan, val spentMinor: Long)
