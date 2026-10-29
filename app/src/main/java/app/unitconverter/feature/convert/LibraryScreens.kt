package app.unitconverter.feature.convert

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.unitconverter.R
import app.unitconverter.core.catalog.UnitCatalog
import app.unitconverter.core.model.UnitCategory
import app.unitconverter.core.model.UnitDefinition
import app.unitconverter.core.ui.AppIcons
import app.unitconverter.core.ui.theme.LocalExtendedColors
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private data class ResolvedFavorite(
    val favorite: FavoriteConversion,
    val category: UnitCategory,
    val from: UnitDefinition,
    val to: UnitDefinition
)

private data class ResolvedHistoryEntry(
    val entry: HistoryEntry,
    val category: UnitCategory,
    val from: UnitDefinition,
    val to: UnitDefinition
)

private enum class HistoryBucket { TODAY, YESTERDAY, EARLIER }

private fun resolveFavorites(
    favorites: List<FavoriteConversion>,
    catalog: UnitCatalog
): List<ResolvedFavorite> = favorites.mapNotNull { favorite ->
    val category = catalog.category(favorite.categoryId) ?: return@mapNotNull null
    val from = category.unit(favorite.fromId) ?: return@mapNotNull null
    val to = category.unit(favorite.toId) ?: return@mapNotNull null
    ResolvedFavorite(favorite, category, from, to)
}

private fun resolveHistory(
    history: List<HistoryEntry>,
    catalog: UnitCatalog
): List<ResolvedHistoryEntry> = history.mapNotNull { entry ->
    val category = catalog.category(entry.categoryId) ?: return@mapNotNull null
    val from = category.unit(entry.fromId) ?: return@mapNotNull null
    val to = category.unit(entry.toId) ?: return@mapNotNull null
    ResolvedHistoryEntry(entry, category, from, to)
}

private fun FavoriteConversion.libraryKey(): String =
    listOf(categoryId.value, fromId.value, toId.value).joinToString("|")

private fun HistoryEntry.asFavorite(): FavoriteConversion = FavoriteConversion(
    categoryId = categoryId,
    fromId = fromId,
    toId = toId
)

/**
 * Dedicated Favorites destination. Restoring a row changes only the
 * category and directed pair, matching the established favorite model;
 * it does not overwrite the converter's current input.
 */
@Composable
fun FavoritesScreen(
    favorites: List<FavoriteConversion>,
    pinnedFavorites: List<FavoriteConversion>,
    catalog: UnitCatalog,
    actionsEnabled: Boolean,
    listState: LazyListState,
    onSelect: (FavoriteConversion) -> Unit,
    onRemove: (FavoriteConversion) -> Unit,
    onTogglePinned: (FavoriteConversion) -> Unit,
    modifier: Modifier = Modifier
) {
    val resolvedFavorites = remember(favorites, catalog) {
        resolveFavorites(favorites, catalog)
    }
    val extended = LocalExtendedColors.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        DestinationHeader(title = stringResource(R.string.favorites_title))
        if (resolvedFavorites.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
                EmptyState(
                    iconRes = AppIcons.Favorite.filled,
                    title = stringResource(R.string.favorites_empty_title),
                    message = stringResource(R.string.favorites_empty),
                    iconTint = extended.favoriteRed,
                    iconContainerColor = Color.Transparent,
                    secondaryMessage = stringResource(R.string.favorites_empty_pin_hint)
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(
                    items = resolvedFavorites,
                    key = { resolved -> resolved.favorite.libraryKey() }
                ) { resolved ->
                    FavoriteRow(
                        resolved = resolved,
                        isPinned = resolved.favorite in pinnedFavorites,
                        actionsEnabled = actionsEnabled,
                        onSelect = { onSelect(resolved.favorite) },
                        onRemove = { onRemove(resolved.favorite) },
                        onTogglePinned = { onTogglePinned(resolved.favorite) }
                    )
                }
            }
        }
    }
}

/**
 * Dedicated History destination. Entries stay newest-first and are only
 * grouped for presentation; grouping never changes the persisted order.
 * Restoring a row also restores its saved input.
 */
@Composable
fun HistoryScreen(
    history: List<HistoryEntry>,
    favorites: List<FavoriteConversion>,
    pinnedFavorites: List<FavoriteConversion>,
    catalog: UnitCatalog,
    evaluator: ConverterEvaluator,
    format: (BigDecimal) -> String,
    actionsEnabled: Boolean,
    listState: LazyListState,
    onSelect: (HistoryEntry) -> Unit,
    onToggleFavorite: (FavoriteConversion) -> Unit,
    onTogglePinned: (FavoriteConversion) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showClearConfirmation by remember { mutableStateOf(false) }
    val resolvedHistory = remember(history, catalog) { resolveHistory(history, catalog) }
    val groupedHistory = remember(resolvedHistory) {
        groupHistory(resolvedHistory)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        DestinationHeader(
            title = stringResource(R.string.history_title),
            trailing = {
                if (resolvedHistory.isNotEmpty()) {
                    IconButton(
                        onClick = { showClearConfirmation = true },
                        enabled = actionsEnabled,
                        modifier = Modifier.size(48.dp)
                    ) {
                        SymbolIcon(
                            iconRes = AppIcons.DeleteSweep,
                            contentDescription = stringResource(R.string.clear_history),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        )

        if (resolvedHistory.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
                EmptyState(
                    iconRes = AppIcons.History.outlined,
                    title = stringResource(R.string.history_empty_title),
                    message = stringResource(R.string.history_empty)
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                groupedHistory.forEach { (bucket, entries) ->
                    item(key = "history-${bucket.name}") {
                        Text(
                            text = stringResource(bucket.titleRes()),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 12.dp, bottom = 2.dp)
                        )
                    }
                    items(
                        items = entries,
                        key = { resolved ->
                            listOf(
                                resolved.entry.categoryId.value,
                                resolved.entry.fromId.value,
                                resolved.entry.toId.value,
                                resolved.entry.inputText,
                                resolved.entry.recordedAtEpochMillis.toString()
                            ).joinToString("|")
                        }
                    ) { resolved ->
                        val pair = resolved.entry.asFavorite()
                        HistoryRow(
                            resolved = resolved,
                            evaluator = evaluator,
                            format = format,
                            isFavorite = pair in favorites,
                            isPinned = pair in pinnedFavorites,
                            actionsEnabled = actionsEnabled,
                            onSelect = { onSelect(resolved.entry) },
                            onToggleFavorite = { onToggleFavorite(pair) },
                            onTogglePinned = { onTogglePinned(pair) }
                        )
                    }
                }
            }
        }
    }

    if (showClearConfirmation) {
        ClearHistoryDialog(
            onDismiss = { showClearConfirmation = false },
            onConfirm = {
                onClearHistory()
                showClearConfirmation = false
            }
        )
    }
}

@Composable
private fun DestinationHeader(
    title: String,
    trailing: @Composable () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        trailing()
    }
}

private fun HistoryBucket.titleRes(): Int = when (this) {
    HistoryBucket.TODAY -> R.string.history_today
    HistoryBucket.YESTERDAY -> R.string.history_yesterday
    HistoryBucket.EARLIER -> R.string.history_earlier
}

private fun groupHistory(
    entries: List<ResolvedHistoryEntry>
): List<Pair<HistoryBucket, List<ResolvedHistoryEntry>>> {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val yesterday = today.minusDays(1)
    val grouped = linkedMapOf<HistoryBucket, MutableList<ResolvedHistoryEntry>>()
    entries.forEach { resolved ->
        val date = Instant.ofEpochMilli(resolved.entry.recordedAtEpochMillis)
            .atZone(zone)
            .toLocalDate()
        val bucket = when (date) {
            today -> HistoryBucket.TODAY
            yesterday -> HistoryBucket.YESTERDAY
            else -> HistoryBucket.EARLIER
        }
        grouped.getOrPut(bucket) { mutableListOf() }.add(resolved)
    }
    return grouped.entries.map { it.key to it.value }
}

private fun historyTimeText(entry: HistoryEntry, locale: Locale): String {
    val zone = ZoneId.systemDefault()
    val dateTime: ZonedDateTime =
        Instant.ofEpochMilli(entry.recordedAtEpochMillis).atZone(zone)
    val isRecent = !dateTime.toLocalDate().isBefore(LocalDate.now(zone).minusDays(1))
    val formatter = if (isRecent) {
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
    } else {
        DateTimeFormatter.ofLocalizedDateTime(
            FormatStyle.SHORT,
            FormatStyle.SHORT
        ).withLocale(locale)
    }
    return formatter.format(dateTime)
}

@Composable
private fun FavoriteRow(
    resolved: ResolvedFavorite,
    isPinned: Boolean,
    actionsEnabled: Boolean,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
    onTogglePinned: () -> Unit
) {
    val extended = LocalExtendedColors.current
    Surface(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = extended.panel,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, extended.panelBorder)
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 88.dp)
                .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CategoryIconBadge(category = resolved.category)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        R.string.conversion_pair_symbols_format,
                        resolved.from.symbol,
                        resolved.to.symbol
                    ),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 18.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(
                        R.string.conversion_pair_format,
                        stringResource(resolved.from.nameRes),
                        stringResource(resolved.to.nameRes)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp)
                )
                Text(
                    text = stringResource(resolved.category.nameRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            FavoriteHeartToggle(
                checked = true,
                onCheckedChange = { checked -> if (!checked) onRemove() },
                enabled = actionsEnabled
            )
            PinToggle(
                checked = isPinned,
                onCheckedChange = { onTogglePinned() },
                enabled = actionsEnabled
            )
        }
    }
}

@Composable
private fun HistoryRow(
    resolved: ResolvedHistoryEntry,
    evaluator: ConverterEvaluator,
    format: (BigDecimal) -> String,
    isFavorite: Boolean,
    isPinned: Boolean,
    actionsEnabled: Boolean,
    onSelect: () -> Unit,
    onToggleFavorite: () -> Unit,
    onTogglePinned: () -> Unit
) {
    val extended = LocalExtendedColors.current
    val entry = resolved.entry
    val resultValue = remember(entry, evaluator) {
        (
            evaluator.evaluate(
                categoryId = entry.categoryId,
                fromId = entry.fromId,
                toId = entry.toId,
                inputText = entry.inputText
            ).result as? ResultState.Value
            )?.value
    }
    val resultText = resultValue?.let(format)
        ?: stringResource(R.string.result_placeholder)
    val timeText = remember(entry.recordedAtEpochMillis) {
        historyTimeText(entry, Locale.getDefault())
    }

    Surface(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = extended.panel,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, extended.panelBorder)
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 96.dp)
                .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CategoryIconBadge(category = resolved.category)
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = stringResource(
                        R.string.history_entry_format,
                        entry.inputText,
                        resolved.from.symbol,
                        resultText,
                        resolved.to.symbol
                    ),
                    style = numericTextStyle(
                        MaterialTheme.typography.titleMedium,
                        fontSizeSp = 16f
                    ),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(
                        R.string.conversion_pair_format,
                        stringResource(resolved.from.nameRes),
                        stringResource(resolved.to.nameRes)
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(
                        R.string.library_entry_meta_format,
                        stringResource(resolved.category.nameRes),
                        timeText
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            FavoriteHeartToggle(
                checked = isFavorite,
                onCheckedChange = { onToggleFavorite() },
                enabled = actionsEnabled
            )
            PinToggle(
                checked = isPinned,
                onCheckedChange = { onTogglePinned() },
                enabled = actionsEnabled
            )
        }
    }
}

@Composable
private fun CategoryIconBadge(category: UnitCategory) {
    Surface(
        modifier = Modifier.size(44.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Box(contentAlignment = Alignment.Center) {
            SymbolIcon(
                iconRes = AppIcons.forCategory(category.id).outlined,
                contentDescription = null,
                modifier = Modifier.size(23.dp)
            )
        }
    }
}

@Composable
private fun ClearHistoryDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val extended = LocalExtendedColors.current
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    modifier = Modifier.size(56.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        SymbolIcon(
                            iconRes = AppIcons.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.clear_history_confirmation_title),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                )
                Text(
                    text = stringResource(R.string.clear_history_confirmation_message),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
                Button(
                    onClick = onConfirm,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp)
                        .heightIn(min = 52.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = extended.ink,
                        contentColor = extended.onInk
                    )
                ) {
                    Text(text = stringResource(R.string.clear_history))
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .heightIn(min = 48.dp)
                ) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        }
    }
}
