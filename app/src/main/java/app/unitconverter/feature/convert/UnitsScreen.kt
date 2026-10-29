package app.unitconverter.feature.convert

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.unitconverter.R
import app.unitconverter.core.model.CategoryId
import app.unitconverter.core.model.UnitCategory
import app.unitconverter.core.model.UnitDefinition
import app.unitconverter.core.model.UnitId
import app.unitconverter.core.ui.AppIcons
import app.unitconverter.core.ui.theme.LocalExtendedColors

private enum class UnitsMode { CATEGORIES, UNITS, SEARCH }

private data class RoleChoice(
    val category: UnitCategory,
    val unit: UnitDefinition
)

/**
 * Browsable catalog destination.
 *
 * Every tile, count, and search result is derived from the real catalog.
 * Global search calls the same [UnitSearch] ranker as the converter's
 * picker; no duplicate catalog or matcher is maintained for this screen.
 */
@Composable
fun UnitsScreen(
    categories: List<UnitCategory>,
    activeCategoryId: CategoryId,
    activeFromId: UnitId,
    activeToId: UnitId,
    browseCategoryId: CategoryId?,
    query: String,
    categoryGridState: LazyGridState,
    unitGridState: LazyGridState,
    searchListState: LazyListState,
    onBrowseCategoryChange: (CategoryId?) -> Unit,
    onQueryChange: (String) -> Unit,
    onApplyUnit: (UnitCategory, UnitDefinition, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val browseCategory = browseCategoryId?.let { id ->
        categories.firstOrNull { it.id == id }
    }
    val queryActive = query.isNotBlank()
    val mode = when {
        queryActive -> UnitsMode.SEARCH
        browseCategory != null -> UnitsMode.UNITS
        else -> UnitsMode.CATEGORIES
    }
    var roleChoice by remember { mutableStateOf<RoleChoice?>(null) }

    LaunchedEffect(browseCategoryId) {
        if (browseCategoryId != null) unitGridState.scrollToItem(0)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (browseCategory != null && !queryActive) {
                IconButton(
                    onClick = { onBrowseCategoryChange(null) },
                    modifier = Modifier.size(48.dp)
                ) {
                    SymbolIcon(
                        iconRes = AppIcons.ArrowBack,
                        contentDescription = stringResource(R.string.units_back_to_categories),
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    text = stringResource(browseCategory.nameRes),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Text(
                    text = stringResource(R.string.units_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        UnitSearchField(
            query = query,
            onQueryChange = onQueryChange,
            placeholder = stringResource(R.string.units_search_placeholder),
            modifier = Modifier.fillMaxWidth()
        )

        when {
            queryActive -> {
                Text(
                    text = stringResource(R.string.units_search_results_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 20.dp, bottom = 4.dp)
                )
            }
            browseCategory != null -> {
                val count = browseCategory.units.size
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = pluralStringResource(R.plurals.units_count, count, count),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    if (browseCategory.id == activeCategoryId) {
                        RoleBadge(text = stringResource(R.string.units_active_category))
                    }
                }
            }
            else -> {
                Text(
                    text = stringResource(R.string.units_categories_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 20.dp, bottom = 4.dp)
                )
            }
        }

        AnimatedContent(
            targetState = mode,
            transitionSpec = {
                (fadeIn(animationSpec = androidx.compose.animation.core.tween(220)) +
                    slideInHorizontally(animationSpec = androidx.compose.animation.core.tween(220)) { width ->
                        if (targetState == UnitsMode.UNITS) width / 8 else -width / 16
                    }) togetherWith fadeOut(
                    animationSpec = androidx.compose.animation.core.tween(160)
                )
            },
            label = "unitsContent",
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { animatedMode ->
            when (animatedMode) {
                UnitsMode.CATEGORIES -> CategoryGrid(
                    categories = categories,
                    activeCategoryId = activeCategoryId,
                    state = categoryGridState,
                    onCategoryClick = { onBrowseCategoryChange(it.id) }
                )

                UnitsMode.UNITS -> {
                    val category = browseCategory
                    if (category != null) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            state = unitGridState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(category.units, key = { it.id.value }) { unit ->
                                val badge = roleBadge(
                                    category = category,
                                    unit = unit,
                                    activeCategoryId = activeCategoryId,
                                    activeFromId = activeFromId,
                                    activeToId = activeToId
                                )
                                UnitTile(
                                    unit = unit,
                                    onClick = { roleChoice = RoleChoice(category, unit) },
                                    badgeText = badge,
                                    isCurrent = badge != null
                                )
                            }
                        }
                    }
                }

                UnitsMode.SEARCH -> SearchResults(
                    categories = categories,
                    query = query,
                    activeCategoryId = activeCategoryId,
                    activeFromId = activeFromId,
                    activeToId = activeToId,
                    state = searchListState,
                    onUnitClick = { category, unit -> roleChoice = RoleChoice(category, unit) }
                )
            }
        }
    }

    roleChoice?.let { choice ->
        UnitRoleSheet(
            choice = choice,
            activeCategoryId = activeCategoryId,
            activeFromId = activeFromId,
            activeToId = activeToId,
            onDismiss = { roleChoice = null },
            onApply = { asSource ->
                onApplyUnit(choice.category, choice.unit, asSource)
                roleChoice = null
            }
        )
    }
}

@Composable
private fun roleBadge(
    category: UnitCategory,
    unit: UnitDefinition,
    activeCategoryId: CategoryId,
    activeFromId: UnitId,
    activeToId: UnitId
): String? {
    if (category.id != activeCategoryId) return null
    val roles = listOfNotNull(
        if (unit.id == activeFromId) stringResource(R.string.source_short) else null,
        if (unit.id == activeToId) stringResource(R.string.destination_short) else null
    )
    return roles.takeIf { it.isNotEmpty() }?.joinToString(separator = " · ")
}

@Composable
private fun CategoryGrid(
    categories: List<UnitCategory>,
    activeCategoryId: CategoryId,
    state: LazyGridState,
    onCategoryClick: (UnitCategory) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = state,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(categories, key = { it.id.value }) { category ->
            CategoryTile(
                category = category,
                isActive = category.id == activeCategoryId,
                onClick = { onCategoryClick(category) }
            )
        }
    }
}

@Composable
private fun CategoryTile(
    category: UnitCategory,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val extended = LocalExtendedColors.current
    val name = stringResource(category.nameRes)
    val count = pluralStringResource(
        R.plurals.units_count,
        category.units.size,
        category.units.size
    )
    val activeText = stringResource(R.string.units_active_category)
    val description = if (isActive) "$name, $count, $activeText" else "$name, $count"
    val nameStyle = MaterialTheme.typography.titleMedium
    val countStyle = MaterialTheme.typography.bodySmall
    val nameZoneHeight = reservedTextHeight(nameStyle, lineCount = 2)

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 136.dp)
            .semantics {
                role = Role.Button
                selected = isActive
                contentDescription = description
            },
        shape = RoundedCornerShape(24.dp),
        color = if (isActive) extended.ink else extended.panel,
        contentColor = if (isActive) extended.onInk else MaterialTheme.colorScheme.onSurface,
        border = if (isActive) null else BorderStroke(1.dp, extended.panelBorder)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Top
            ) {
                Box(
                    modifier = Modifier.size(28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    SymbolIcon(
                        iconRes = AppIcons.forCategory(category.id).forSelected(isActive),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(Modifier.size(10.dp))
                Box(modifier = Modifier.heightIn(min = nameZoneHeight)) {
                    Text(
                        text = name,
                        style = nameStyle,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = count,
                    style = countStyle,
                    color = if (isActive) {
                        extended.onInk.copy(alpha = 0.72f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            if (isActive) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(21.dp),
                    shape = CircleShape,
                    color = extended.onInk,
                    contentColor = extended.ink
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        SymbolIcon(
                            iconRes = AppIcons.Check,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

private data class RankedSearchUnit(
    val category: UnitCategory,
    val unit: UnitDefinition,
    val name: String,
    val rank: Int
)

@Composable
private fun SearchResults(
    categories: List<UnitCategory>,
    query: String,
    activeCategoryId: CategoryId,
    activeFromId: UnitId,
    activeToId: UnitId,
    state: LazyListState,
    onUnitClick: (UnitCategory, UnitDefinition) -> Unit
) {
    val localizedUnits = categories.map { category ->
        category.units.map { unit -> unit to stringResource(unit.nameRes) }
    }
    val grouped: List<Pair<UnitCategory, List<RankedSearchUnit>>> =
        remember(categories, query, localizedUnits) {
            categories.mapIndexed { categoryIndex, category ->
                val matches = localizedUnits[categoryIndex].map { (unit, name) ->
                    RankedSearchUnit(
                        category = category,
                        unit = unit,
                        name = name,
                        rank = UnitSearch.rank(query, name, unit.symbol, unit.aliases)
                    )
                }.filter { it.rank >= 0 }.sortedByDescending { it.rank }
                category to matches
            }.filter { it.second.isNotEmpty() }
        }

    if (grouped.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            EmptyState(
                iconRes = AppIcons.SearchOff,
                title = stringResource(R.string.units_no_results_title),
                message = stringResource(R.string.units_no_results_message)
            )
        }
        return
    }

    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        grouped.forEach { (category, matches) ->
            item(key = "search-category-${category.id.value}") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SymbolIcon(
                        iconRes = AppIcons.forCategory(category.id).outlined,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(category.nameRes),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(matches.size, key = { index ->
                "search-${category.id.value}-${matches[index].unit.id.value}"
            }) { index ->
                val match = matches[index]
                val badge = roleBadge(
                    category = category,
                    unit = match.unit,
                    activeCategoryId = activeCategoryId,
                    activeFromId = activeFromId,
                    activeToId = activeToId
                )
                SearchUnitRow(
                    match = match,
                    badgeText = badge,
                    onClick = { onUnitClick(category, match.unit) }
                )
            }
        }
    }
}

@Composable
private fun SearchUnitRow(
    match: RankedSearchUnit,
    badgeText: String?,
    onClick: () -> Unit
) {
    val extended = LocalExtendedColors.current
    val nameStyle = MaterialTheme.typography.bodyLarge
    val nameZoneHeight = reservedTextHeight(nameStyle, lineCount = 2)
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 84.dp)
            .semantics {
                role = Role.Button
                selected = badgeText != null
                contentDescription = listOfNotNull(match.name, match.unit.symbol, badgeText)
                    .joinToString(separator = ", ")
            },
        shape = RoundedCornerShape(20.dp),
        color = extended.panel,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, extended.panelBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier
                    .width(64.dp)
                    .heightIn(min = 64.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = match.unit.symbol,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 18.sp,
                            lineHeight = 22.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Box(modifier = Modifier.heightIn(min = nameZoneHeight)) {
                    Text(
                        text = match.name,
                        style = nameStyle,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = match.unit.symbol,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            if (badgeText != null) {
                Spacer(Modifier.width(8.dp))
                RoleBadge(text = badgeText)
            }
        }
    }
}

@Composable
private fun RoleBadge(text: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 2,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnitRoleSheet(
    choice: RoleChoice,
    activeCategoryId: CategoryId,
    activeFromId: UnitId,
    activeToId: UnitId,
    onDismiss: () -> Unit,
    onApply: (Boolean) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isSource = choice.category.id == activeCategoryId && choice.unit.id == activeFromId
    val isDestination = choice.category.id == activeCategoryId && choice.unit.id == activeToId

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        SymbolIcon(
                            iconRes = AppIcons.forCategory(choice.category.id).outlined,
                            contentDescription = null,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(choice.unit.nameRes),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(
                            R.string.library_entry_meta_format,
                            choice.unit.symbol,
                            stringResource(choice.category.nameRes)
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(48.dp)
                ) {
                    SymbolIcon(
                        iconRes = AppIcons.Close,
                        contentDescription = stringResource(R.string.close),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(Modifier.size(20.dp))
            RoleOption(
                text = stringResource(R.string.units_use_as_source),
                currentText = if (isSource) stringResource(R.string.source_short) else null,
                onClick = { onApply(true) }
            )
            Spacer(Modifier.size(8.dp))
            RoleOption(
                text = stringResource(R.string.units_use_as_destination),
                currentText = if (isDestination) {
                    stringResource(R.string.destination_short)
                } else {
                    null
                },
                onClick = { onApply(false) }
            )
        }
    }
}

@Composable
private fun RoleOption(
    text: String,
    currentText: String?,
    onClick: () -> Unit
) {
    val extended = LocalExtendedColors.current
    val isCurrent = currentText != null
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .semantics {
                role = Role.Button
                selected = isCurrent
            },
        shape = RoundedCornerShape(20.dp),
        color = if (isCurrent) extended.ink else MaterialTheme.colorScheme.surfaceContainer,
        contentColor = if (isCurrent) extended.onInk else MaterialTheme.colorScheme.onSurface
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            if (currentText != null) {
                SymbolIcon(
                    iconRes = AppIcons.Check,
                    contentDescription = currentText,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
