package app.unitconverter.core.catalog

import app.unitconverter.core.model.CategoryId
import app.unitconverter.core.model.UnitCategory
import app.unitconverter.core.model.UnitDefinition
import app.unitconverter.core.model.UnitId

/**
 * An immutable, validated collection of categories.
 *
 * Construction fails fast on duplicate category IDs or duplicate
 * (categoryId, unitId) pairs, because persistence refers to units by
 * that pair and ambiguity there would corrupt user data later. The same
 * unit ID may legitimately appear in different categories.
 */
class UnitCatalog(categories: List<UnitCategory>) {

    val categories: List<UnitCategory> = categories.toList()

    private val categoriesById: Map<CategoryId, UnitCategory>
    private val unitsByKey: Map<Pair<CategoryId, UnitId>, UnitDefinition>

    init {
        require(this.categories.isNotEmpty()) { "Catalog must contain at least one category" }
        require(this.categories.distinctBy { it.id }.size == this.categories.size) {
            "Duplicate category ids in catalog"
        }
        val keys = this.categories.flatMap { category ->
            category.units.map { unit -> category.id to unit.id }
        }
        require(keys.distinct().size == keys.size) {
            "A unit id may not appear twice within one category"
        }
        categoriesById = this.categories.associateBy { it.id }
        unitsByKey = this.categories
            .flatMap { category -> category.units }
            .associateBy { unit -> unit.categoryId to unit.id }
    }

    fun category(id: CategoryId): UnitCategory? = categoriesById[id]

    fun requireCategory(id: CategoryId): UnitCategory =
        checkNotNull(category(id)) { "Unknown category '${id.value}'" }

    fun unit(categoryId: CategoryId, unitId: UnitId): UnitDefinition? =
        unitsByKey[categoryId to unitId]

    fun requireUnit(categoryId: CategoryId, unitId: UnitId): UnitDefinition =
        checkNotNull(unit(categoryId, unitId)) {
            "Unknown unit '${unitId.value}' in category '${categoryId.value}'"
        }

    val allUnits: List<UnitDefinition> get() = categories.flatMap { it.units }
}
