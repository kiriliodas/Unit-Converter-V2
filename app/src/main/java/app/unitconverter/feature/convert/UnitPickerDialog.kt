package app.unitconverter.feature.convert

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.unitconverter.R
import app.unitconverter.core.model.UnitDefinition
import app.unitconverter.core.model.UnitId
import app.unitconverter.core.ui.AppIcons

/**
 * Searchable unit selector used for one role in the current category.
 *
 * The sheet intentionally does not request search focus on entry: units
 * can be browsed immediately, and the keyboard appears only after the
 * user deliberately touches the search field. Filtering still delegates
 * to the JVM-tested [UnitSearch], and selecting a tile still applies
 * immediately through the caller's unchanged callback.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnitPickerSheet(
    title: String,
    units: List<UnitDefinition>,
    selectedId: UnitId,
    currentBadgeText: String?,
    onDismiss: () -> Unit,
    onSelect: (UnitDefinition) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val localizedNames = units.map { unit -> unit to stringResource(unit.nameRes) }
    val filtered = remember(query, localizedNames) {
        localizedNames
            .map { (unit, name) ->
                Triple(unit, name, UnitSearch.rank(query, name, unit.symbol, unit.aliases))
            }
            .filter { it.third >= 0 }
            .sortedByDescending { it.third }
    }

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
                .fillMaxHeight(0.88f)
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
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

            UnitSearchField(
                query = query,
                onQueryChange = { query = it },
                placeholder = stringResource(R.string.search_units_placeholder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )

            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    EmptyState(
                        iconRes = AppIcons.SearchOff,
                        title = stringResource(R.string.units_no_results_title),
                        message = stringResource(R.string.no_units_found)
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 152.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 8.dp),
                    contentPadding = PaddingValues(bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items = filtered, key = { it.first.id.value }) { item ->
                        val unit = item.first
                        val isCurrent = unit.id == selectedId
                        UnitTile(
                            unit = unit,
                            onClick = { onSelect(unit) },
                            badgeText = if (isCurrent) currentBadgeText else null,
                            isCurrent = isCurrent,
                            compact = true
                        )
                    }
                }
            }
        }
    }
}
