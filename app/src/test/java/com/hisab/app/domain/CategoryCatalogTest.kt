package com.hisab.app.domain

import com.hisab.app.domain.category.CategoryCatalog
import com.hisab.app.domain.category.CategoryKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryCatalogTest {
    // The categories a v1 install already has (Phase 1 seed + income seed).
    private val v1Names = listOf(
        "Food", "Snacks", "Transport", "University Transport", "University Adda", "Education", "Shopping", "Clothing",
        "Mobile & Internet", "Bills", "Home", "Medical", "Personal Care", "Entertainment", "Relationship", "Gift",
        "Family", "Salary", "Freelancing", "Business", "Development", "Family Support", "Loan", "Savings", "Other",
        "Business Income", "Investment Return", "Gift Received", "Refund", "Loan Received", "Other Income"
    )

    @Test fun catalog_itself_has_no_duplicate_names_or_alias_clashes() {
        val keys = CategoryCatalog.defaults.flatMap { c -> (listOf(c.name) + c.aliases).map { CategoryCatalog.normalize(it) } }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test fun fresh_install_gets_every_default_once() {
        assertEquals(CategoryCatalog.defaults.size, CategoryCatalog.missing(emptyList()).size)
    }

    @Test fun seeding_is_idempotent() {
        val first = CategoryCatalog.missing(v1Names)
        val second = CategoryCatalog.missing(v1Names + first.map { it.name })
        assertTrue(second.isEmpty())
    }

    @Test fun existing_equivalents_are_not_duplicated() {
        val missing = CategoryCatalog.missing(v1Names).map { it.name }
        // already present under their old names
        for (covered in listOf("Food & Dining", "Transportation", "Utilities & Bills", "Health & Medical", "Rent & Housing",
            "Gifts & Donations", "Other Expenses", "Education", "Shopping", "Clothing", "Mobile & Internet", "Personal Care",
            "Entertainment", "Family", "Salary", "Freelance", "Business Income", "Gifts Received", "Refunds", "Other Income",
            "Investments", "EMI & Loan Payments")) {
            assertFalse("$covered should not be re-added", covered in missing)
        }
        // genuinely new ones are added
        for (added in listOf("Groceries", "Fuel", "Travel", "Subscriptions", "Business Expenses", "Interest", "Rental Income")) {
            assertTrue("$added should be added", added in missing)
        }
    }

    @Test fun matching_ignores_case_spacing_and_ampersand_style() {
        assertTrue(CategoryCatalog.missing(listOf("  food   &  dining ")).none { it.name == "Food & Dining" })
        assertTrue(CategoryCatalog.missing(listOf("FOOD AND DINING")).none { it.name == "Food & Dining" })
    }

    @Test fun user_custom_categories_are_respected_and_untouched() {
        val m = CategoryCatalog.missing(listOf("Fuel", "My Custom Thing"))
        assertTrue(m.none { it.name == "Fuel" })
    }

    @Test fun deactivated_categories_still_count_as_present() {
        // The seeder passes ALL names (including inactive), so a default the user deleted is not resurrected.
        assertTrue(CategoryCatalog.missing(CategoryCatalog.defaults.map { it.name }).isEmpty())
    }

    @Test fun kinds_are_sensible() {
        val byName = CategoryCatalog.defaults.associateBy { it.name }
        assertEquals(CategoryKind.INCOME, byName.getValue("Salary").kind)
        assertEquals(CategoryKind.EXPENSE, byName.getValue("Groceries").kind)
        assertTrue(CategoryCatalog.defaults.none { it.kind == CategoryKind.BOTH })
    }

    @Test fun colors_are_valid_hex() {
        assertTrue(CategoryCatalog.defaults.all { Regex("^#[0-9A-Fa-f]{6}$").matches(it.colorHex) })
    }

    @Test fun legacy_income_only_names_are_all_known_v1_names() {
        assertTrue(v1Names.containsAll(CategoryCatalog.legacyIncomeOnlyNames))
    }
}
