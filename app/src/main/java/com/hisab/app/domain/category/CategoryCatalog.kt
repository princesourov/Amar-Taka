package com.hisab.app.domain.category

/** Which pickers a category appears in. Existing (pre-v2) categories are BOTH so nothing disappears. */
enum class CategoryKind { EXPENSE, INCOME, BOTH }

data class DefaultCategory(
    val name: String,
    val kind: CategoryKind,
    val iconKey: String,
    val colorHex: String,
    /** Names that mean the same thing; if the user already has one of these, the default is not added. */
    val aliases: List<String> = emptyList()
)

/**
 * The default category set and the rule for adding it safely.
 *
 * Seeding only ever INSERTS categories that are missing. It matches against every category the user
 * has (including deactivated ones, so a default the user deleted is not resurrected) using a
 * normalised name plus aliases, so "Food" already covers "Food & Dining" and nothing is duplicated.
 * Existing rows are never modified.
 */
object CategoryCatalog {

    val defaults: List<DefaultCategory> = listOf(
        // ---- Expenses ----
        DefaultCategory("Food & Dining", CategoryKind.EXPENSE, "food", "#E4572E", listOf("Food", "Dining")),
        DefaultCategory("Groceries", CategoryKind.EXPENSE, "groceries", "#76B041"),
        DefaultCategory("Transportation", CategoryKind.EXPENSE, "transport", "#2E86AB", listOf("Transport")),
        DefaultCategory("Fuel", CategoryKind.EXPENSE, "fuel", "#5C6B73"),
        DefaultCategory("Rent & Housing", CategoryKind.EXPENSE, "home", "#8E6C8A", listOf("Home", "Rent", "Housing")),
        DefaultCategory("Utilities & Bills", CategoryKind.EXPENSE, "bills", "#F2A541", listOf("Bills", "Utilities")),
        DefaultCategory("Mobile & Internet", CategoryKind.EXPENSE, "mobile", "#17BEBB"),
        DefaultCategory("Shopping", CategoryKind.EXPENSE, "shopping", "#D1495B"),
        DefaultCategory("Clothing", CategoryKind.EXPENSE, "clothing", "#EDAE49"),
        DefaultCategory("Health & Medical", CategoryKind.EXPENSE, "medical", "#00798C", listOf("Medical", "Health")),
        DefaultCategory("Education", CategoryKind.EXPENSE, "education", "#30638E"),
        DefaultCategory("Entertainment", CategoryKind.EXPENSE, "entertainment", "#9B5DE5"),
        DefaultCategory("Travel", CategoryKind.EXPENSE, "travel", "#3A86FF"),
        DefaultCategory("Personal Care", CategoryKind.EXPENSE, "personal_care", "#F28482"),
        DefaultCategory("Family", CategoryKind.EXPENSE, "family", "#84A59D"),
        DefaultCategory("Gifts & Donations", CategoryKind.EXPENSE, "gift", "#E76F51", listOf("Gift", "Gifts", "Donations")),
        DefaultCategory("Subscriptions", CategoryKind.EXPENSE, "subscriptions", "#6D597A"),
        DefaultCategory("Business Expenses", CategoryKind.EXPENSE, "business", "#355070"),
        DefaultCategory("EMI & Loan Payments", CategoryKind.EXPENSE, "loan", "#B56576", listOf("Loan", "EMI")),
        DefaultCategory("Other Expenses", CategoryKind.EXPENSE, "other", "#8D99AE", listOf("Other")),
        // ---- Income ----
        DefaultCategory("Salary", CategoryKind.INCOME, "salary", "#2A9D8F"),
        DefaultCategory("Freelance", CategoryKind.INCOME, "freelance", "#43AA8B", listOf("Freelancing")),
        DefaultCategory("Business Income", CategoryKind.INCOME, "business_income", "#4D908E"),
        DefaultCategory("Investments", CategoryKind.INCOME, "investments", "#577590", listOf("Investment Return", "Investment")),
        DefaultCategory("Interest", CategoryKind.INCOME, "interest", "#90BE6D"),
        DefaultCategory("Rental Income", CategoryKind.INCOME, "rental", "#F9C74F"),
        DefaultCategory("Gifts Received", CategoryKind.INCOME, "gift_received", "#F8961E", listOf("Gift Received")),
        DefaultCategory("Refunds", CategoryKind.INCOME, "refund", "#F3722C", listOf("Refund")),
        DefaultCategory("Other Income", CategoryKind.INCOME, "other_income", "#A3B18A")
    )

    /**
     * Categories added by the app BEFORE the kind column existed that are unambiguously income-only
     * (they were only ever seeded for the Add Money screen). The v1->v2 migration marks exactly
     * these as INCOME; everything else becomes BOTH so existing pickers keep working unchanged.
     */
    val legacyIncomeOnlyNames: List<String> = listOf(
        "Business Income", "Investment Return", "Gift Received", "Refund", "Loan Received", "Other Income"
    )

    fun normalize(name: String): String =
        name.trim().lowercase().replace("&", "and").replace(Regex("\\s+"), " ")

    /** Defaults that are neither present (by name or alias) in [existingNames] nor duplicated within the catalog. */
    fun missing(existingNames: Collection<String>): List<DefaultCategory> {
        val taken = existingNames.map { normalize(it) }.toMutableSet()
        val result = ArrayList<DefaultCategory>()
        for (candidate in defaults) {
            val keys = (listOf(candidate.name) + candidate.aliases).map { normalize(it) }
            if (keys.any { it in taken }) continue
            result += candidate
            taken += keys
        }
        return result
    }
}
