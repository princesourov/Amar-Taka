package com.hisab.app.domain.category

/** Validation for creating/renaming a category. Pure, so it is unit-tested off-device. */
object CategoryRules {
    const val MAX_NAME = 40

    sealed interface Validation {
        data class Valid(val name: String) : Validation
        data object Empty : Validation
        data object TooLong : Validation
        /** Another category (active or hidden) already has this name; [existingId] lets the UI offer restoring it. */
        data class Duplicate(val existingId: String) : Validation
    }

    /**
     * @param others every OTHER category of the user (active and hidden) as id to name.
     * Names are compared after [CategoryCatalog.normalize], so "food & dining" equals "Food and Dining".
     */
    fun validate(rawName: String, others: Collection<Pair<String, String>>): Validation {
        val name = rawName.trim().replace(Regex("\\s+"), " ")
        if (name.isEmpty()) return Validation.Empty
        if (name.length > MAX_NAME) return Validation.TooLong
        val key = CategoryCatalog.normalize(name)
        others.firstOrNull { CategoryCatalog.normalize(it.second) == key }?.let { return Validation.Duplicate(it.first) }
        return Validation.Valid(name)
    }

    /** Colours handed out to new custom categories, cycling, so they are distinguishable without the user picking. */
    val customPalette = listOf(
        "#E4572E", "#2E86AB", "#76B041", "#9B5DE5", "#F2A541",
        "#17BEBB", "#D1495B", "#3A86FF", "#8E6C8A", "#5C6B73"
    )

    fun nextColor(customCount: Int): String = customPalette[Math.floorMod(customCount, customPalette.size)]

    /**
     * Should a picker for a [forIncome] (true) / expense (false) transaction offer this category?
     * The transaction's CURRENT category is always offered, even if hidden or of the other kind, so
     * opening and saving an old transaction never silently changes its category.
     */
    fun isOffered(kind: String, isActive: Boolean, forIncome: Boolean, isCurrent: Boolean): Boolean {
        if (isCurrent) return true
        if (!isActive) return false
        return when (kind) {
            CategoryKind.EXPENSE.name -> !forIncome
            CategoryKind.INCOME.name -> forIncome
            else -> true
        }
    }
}
