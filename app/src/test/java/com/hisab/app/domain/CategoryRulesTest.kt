package com.hisab.app.domain

import com.hisab.app.domain.category.CategoryRules
import com.hisab.app.domain.category.CategoryRules.Validation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryRulesTest {
    private val others = listOf("1" to "Food & Dining", "2" to "Salary")

    @Test fun trims_and_collapses_spaces() {
        assertEquals(Validation.Valid("Pet Care"), CategoryRules.validate("  Pet    Care ", others))
    }
    @Test fun blank_is_rejected() {
        assertEquals(Validation.Empty, CategoryRules.validate("   ", others))
    }
    @Test fun too_long_is_rejected() {
        assertEquals(Validation.TooLong, CategoryRules.validate("x".repeat(41), others))
        assertTrue(CategoryRules.validate("x".repeat(40), others) is Validation.Valid)
    }
    @Test fun duplicate_ignores_case_and_ampersand() {
        assertEquals(Validation.Duplicate("1"), CategoryRules.validate("food and dining", others))
        assertEquals(Validation.Duplicate("2"), CategoryRules.validate("SALARY", others))
    }
    @Test fun renaming_to_own_name_is_fine_because_self_is_excluded() {
        assertTrue(CategoryRules.validate("Salary", others.filter { it.first != "2" }) is Validation.Valid)
    }
    @Test fun colour_cycles_without_overflow() {
        assertEquals(CategoryRules.customPalette[0], CategoryRules.nextColor(10))
        assertEquals(CategoryRules.customPalette[9], CategoryRules.nextColor(-1))
    }

    @Test fun picker_respects_kind_and_hidden_but_always_keeps_current() {
        assertTrue(CategoryRules.isOffered("EXPENSE", true, forIncome = false, isCurrent = false))
        assertEquals(false, CategoryRules.isOffered("EXPENSE", true, forIncome = true, isCurrent = false))
        assertEquals(false, CategoryRules.isOffered("INCOME", true, forIncome = false, isCurrent = false))
        assertTrue(CategoryRules.isOffered("BOTH", true, forIncome = true, isCurrent = false))
        assertEquals(false, CategoryRules.isOffered("BOTH", false, forIncome = false, isCurrent = false))
        assertTrue(CategoryRules.isOffered("INCOME", false, forIncome = false, isCurrent = true))
    }
}
