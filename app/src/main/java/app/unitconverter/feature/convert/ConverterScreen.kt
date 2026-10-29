package app.unitconverter.feature.convert

import android.content.ClipData
import android.content.ClipboardManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.unitconverter.R
import app.unitconverter.core.catalog.RepresentativeCatalog
import app.unitconverter.core.catalog.UnitCatalog
import app.unitconverter.core.conversion.DomainViolation
import app.unitconverter.core.conversion.InvalidReason
import app.unitconverter.core.conversion.NumberFormatOptions
import app.unitconverter.core.conversion.NumberFormatter
import app.unitconverter.core.model.CategoryId
import app.unitconverter.core.model.UnitDefinition
import app.unitconverter.core.model.UnitId
import app.unitconverter.core.ui.AppIcons
import app.unitconverter.core.ui.theme.LocalExtendedColors
import app.unitconverter.feature.update.UpdateManager
import java.math.BigDecimal
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private enum class PickerTarget { FROM, TO }

private data class BottomDestination(
    val destination: MainDestination,
    val labelRes: Int,
    val icons: app.unitconverter.core.ui.SymbolPair
)

private val BottomDestinations = listOf(
    BottomDestination(MainDestination.CONVERT, R.string.nav_convert, AppIcons.Convert),
    BottomDestination(MainDestination.UNITS, R.string.nav_units, AppIcons.Units),
    BottomDestination(MainDestination.FAVORITES, R.string.nav_favorites, AppIcons.Favorite),
    BottomDestination(MainDestination.HISTORY, R.string.nav_history, AppIcons.History)
)

/**
 * Root converter shell.
 *
 * Conversion decisions still come exclusively from [ConverterEvaluator],
 * and saved data still lives behind [ConversionLibraryStore]. Phase C8
 * keeps bottom navigation in the single saveable [MainNavigationState]
 * holder, animates one shared selection pill, and retains the validated
 * presentation around those existing state owners; it does not introduce
 * a second library, catalog, or persistence path.
 */
@Composable
fun ConverterScreen(
    appPreferences: AppPreferences,
    onAppPreferencesChange: (AppPreferences) -> Unit,
    updateManager: UpdateManager
) {
    val catalog = remember { RepresentativeCatalog.catalog }
    val evaluator = remember { ConverterEvaluator(catalog) }
    val formatter = remember { NumberFormatter() }

    val firstCategory = catalog.categories.first()
    var categoryId by rememberSaveable { mutableStateOf(firstCategory.id.value) }
    var fromId by rememberSaveable { mutableStateOf(firstCategory.defaultFromId.value) }
    var toId by rememberSaveable { mutableStateOf(firstCategory.defaultToId.value) }
    var inputText by rememberSaveable { mutableStateOf("") }
    var pickerTarget by remember { mutableStateOf<PickerTarget?>(null) }
    var swapCount by remember { mutableIntStateOf(0) }
    var copyCount by remember { mutableIntStateOf(0) }

    val navigationState = rememberSaveable(saver = MainNavigationState.Saver) {
        MainNavigationState()
    }
    val destination = navigationState.destination
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var precisionSheetOpen by rememberSaveable { mutableStateOf(false) }
    var browseCategoryValue by rememberSaveable { mutableStateOf<String?>(null) }
    val browseCategoryId = browseCategoryValue?.let { value ->
        runCatching { CategoryId(value) }.getOrNull()
    }
    var unitsQuery by rememberSaveable { mutableStateOf("") }

    val AccueilListState = rememberLazyListState()
    val favoritesListState = rememberLazyListState()
    val historyListState = rememberLazyListState()
    val categoryGridState = rememberLazyGridState()
    val unitGridState = rememberLazyGridState()
    val unitsSearchListState = rememberLazyListState()

    val uiState = remember(categoryId, fromId, toId, inputText) {
        evaluator.evaluate(
            categoryId = CategoryId(categoryId),
            fromId = UnitId(fromId),
            toId = UnitId(toId),
            inputText = inputText
        )
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val appName = stringResource(R.string.app_name)
    val copiedMessage = stringResource(R.string.result_copied)
    val resultValue = (uiState.result as? ResultState.Value)?.value

    val libraryStore = remember(context) {
        SerializedConversionLibraryStore(
            SharedPreferencesStringStorage(context.applicationContext)
        )
    }
    var libraryState by remember { mutableStateOf(ConversionLibraryState()) }
    var libraryLoaded by remember { mutableStateOf(false) }
    val librarySaveMutex = remember { Mutex() }

    LaunchedEffect(libraryStore) {
        val loadedState = withContext(Dispatchers.IO) { libraryStore.load() }
        libraryState = loadedState
        libraryLoaded = true
    }

    LaunchedEffect(copyCount) {
        if (copyCount > 0) {
            delay(2_000)
            copyCount = 0
        }
    }

    val updateLibrary: ((ConversionLibraryState) -> ConversionLibraryState) -> Unit =
        { transform ->
            if (libraryLoaded) {
                val updatedState = transform(libraryState)
                if (updatedState != libraryState) {
                    libraryState = updatedState
                    scope.launch {
                        librarySaveMutex.withLock {
                            withContext(Dispatchers.IO) {
                                libraryStore.save(updatedState)
                            }
                        }
                    }
                }
            }
        }

    val currentFavorite = FavoriteConversion(
        categoryId = uiState.category.id,
        fromId = uiState.from.id,
        toId = uiState.to.id
    )
    val isCurrentFavorite = libraryState.isFavorite(currentFavorite)
    val isCurrentPinned = libraryState.isPinned(currentFavorite)
    val formatOptions = remember(libraryState.displaySettings) {
        NumberFormatOptions.forLocale(
            Locale.getDefault(),
            libraryState.displaySettings.toPrecisionMode()
        )
    }
    val formatValue = remember(formatter, formatOptions) {
        { value: BigDecimal -> formatter.format(value, formatOptions) }
    }

    fun selectDestination(next: MainDestination) {
        // The holder reads the live destination at click time. Do not
        // compare against a destination value captured by composition:
        // that was the C6 reselection/navigation desynchronization path.
        if (navigationState.select(next)) return
        when (next) {
            MainDestination.CONVERT -> scope.launch {
                AccueilListState.animateScrollToItem(0)
            }
            MainDestination.UNITS -> {
                if (unitsQuery.isNotBlank()) {
                    unitsQuery = ""
                } else if (browseCategoryValue != null) {
                    browseCategoryValue = null
                } else {
                    scope.launch { categoryGridState.animateScrollToItem(0) }
                }
            }
            MainDestination.FAVORITES -> scope.launch {
                favoritesListState.animateScrollToItem(0)
            }
            MainDestination.HISTORY -> scope.launch {
                historyListState.animateScrollToItem(0)
            }
        }
    }

    BackHandler(enabled = settingsOpen) {
        settingsOpen = false
    }
    BackHandler(
        enabled = !settingsOpen &&
            destination == MainDestination.UNITS &&
            unitsQuery.isNotBlank()
    ) {
        unitsQuery = ""
    }
    BackHandler(
        enabled = !settingsOpen &&
            destination == MainDestination.UNITS &&
            unitsQuery.isBlank() &&
            browseCategoryValue != null
    ) {
        browseCategoryValue = null
    }
    BackHandler(
        enabled = !settingsOpen &&
            destination != MainDestination.CONVERT &&
            !(destination == MainDestination.UNITS &&
                (unitsQuery.isNotBlank() || browseCategoryValue != null))
    ) {
        navigationState.select(MainDestination.CONVERT)
    }

    val visibleDestination: MainDestination? = if (settingsOpen) null else destination

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            ConverterBottomBarHost(
                visible = !settingsOpen,
                selected = destination,
                onSelect = { nextDestination ->
                    selectDestination(nextDestination)
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        contentWindowInsets = WindowInsets.statusBars
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .then(
                    if (settingsOpen) Modifier.navigationBarsPadding() else Modifier
                )
        ) {
            AnimatedContent(
                targetState = visibleDestination,
                contentKey = { destinationKey -> destinationKey?.name ?: "settings" },
                transitionSpec = {
                    (fadeIn(tween(220)) + slideInHorizontally(tween(220)) { width ->
                        width / 16
                    }) togetherWith (fadeOut(tween(140)) +
                        slideOutHorizontally(tween(140)) { width -> -width / 24 })
                },
                label = "mainDestination",
                modifier = Modifier.fillMaxSize()
            ) { animatedRoot ->
                if (animatedRoot == null) {
                    SettingsScreen(
                        settings = libraryState.displaySettings,
                        appPreferences = appPreferences,
                        actionsEnabled = libraryLoaded,
                        onBack = { settingsOpen = false },
                        onPrecisionClick = { precisionSheetOpen = true },
                        onThemeModeChange = { mode ->
                            onAppPreferencesChange(appPreferences.copy(themeMode = mode))
                        },
                        onDynamicColorChange = { enabled ->
                            onAppPreferencesChange(
                                appPreferences.copy(dynamicColorEnabled = enabled)
                            )
                        },
                        onLanguageChange = { language ->
                            onAppPreferencesChange(appPreferences.copy(language = language))
                        },
                        updateManager = updateManager
                    )
                } else {
                    when (animatedRoot) {
                        MainDestination.CONVERT -> HomeContent(
                            uiState = uiState,
                            inputText = inputText,
                            catalog = catalog,
                            AccueilListState = AccueilListState,
                            pinnedFavorites = libraryState.pinnedFavorites,
                            currentFavorite = currentFavorite,
                            isFavorite = isCurrentFavorite,
                            isPinned = isCurrentPinned,
                            actionsEnabled = libraryLoaded,
                            settings = libraryState.displaySettings,
                            swapCount = swapCount,
                            copied = copyCount > 0,
                            format = formatValue,
                            onSettingsClick = { settingsOpen = true },
                            onPinnedSelect = { favorite ->
                                categoryId = favorite.categoryId.value
                                fromId = favorite.fromId.value
                                toId = favorite.toId.value
                            },
                            onInputChange = { inputText = it },
                            onClearInput = { inputText = "" },
                            onToggleSign = {
                                inputText = if (inputText.startsWith("-")) {
                                    inputText.removePrefix("-")
                                } else {
                                    "-$inputText"
                                }
                            },
                            onFromClick = { pickerTarget = PickerTarget.FROM },
                            onToClick = { pickerTarget = PickerTarget.TO },
                            onSwap = {
                                val previousFrom = fromId
                                fromId = toId
                                toId = previousFrom
                                swapCount++
                            },
                            onPrecisionClick = { precisionSheetOpen = true },
                            onToggleFavorite = {
                                updateLibrary { it.toggleFavorite(currentFavorite) }
                            },
                            onTogglePinned = {
                                updateLibrary { it.togglePinnedFavorite(currentFavorite) }
                            },
                            onCopy = {
                                resultValue?.let { value ->
                                    val clipboard = context.getSystemService(
                                        ClipboardManager::class.java
                                    )
                                    clipboard?.let { clipboardManager ->
                                        clipboardManager.setPrimaryClip(
                                            ClipData.newPlainText(
                                                appName,
                                                formatter.format(
                                                    value,
                                                    formatOptions.copy(useGrouping = false)
                                                )
                                            )
                                        )
                                        // Copy remains the one explicit history trigger.
                                        updateLibrary { state ->
                                            state.recordCompletedConversion(
                                                state = uiState,
                                                recordedAtEpochMillis =
                                                    System.currentTimeMillis()
                                            )
                                        }
                                        haptics.performHapticFeedback(
                                            HapticFeedbackType.Confirm
                                        )
                                        copyCount++
                                        scope.launch {
                                            snackbarHostState.showSnackbar(copiedMessage)
                                        }
                                    }
                                }
                            },
                            onUseAsInput = {
                                resultValue?.let { value ->
                                    inputText = formatter.format(
                                        value,
                                        NumberFormatOptions(useGrouping = false)
                                    )
                                }
                            },
                            onRowClick = { unit -> toId = unit.id.value }
                        )

                        MainDestination.UNITS -> UnitsScreen(
                            categories = catalog.categories,
                            activeCategoryId = uiState.category.id,
                            activeFromId = uiState.from.id,
                            activeToId = uiState.to.id,
                            browseCategoryId = browseCategoryId,
                            query = unitsQuery,
                            categoryGridState = categoryGridState,
                            unitGridState = unitGridState,
                            searchListState = unitsSearchListState,
                            onBrowseCategoryChange = { categoryIdValue ->
                                browseCategoryValue = categoryIdValue?.value
                            },
                            onQueryChange = { unitsQuery = it },
                            onApplyUnit = { category, unit, asSource ->
                                if (category.id == uiState.category.id) {
                                    if (asSource) {
                                        fromId = unit.id.value
                                    } else {
                                        toId = unit.id.value
                                    }
                                } else {
                                    categoryId = category.id.value
                                    if (asSource) {
                                        fromId = unit.id.value
                                        toId = if (unit.id == category.defaultToId) {
                                            category.defaultFromId.value
                                        } else {
                                            category.defaultToId.value
                                        }
                                    } else {
                                        fromId = if (unit.id == category.defaultFromId) {
                                            category.defaultToId.value
                                        } else {
                                            category.defaultFromId.value
                                        }
                                        toId = unit.id.value
                                    }
                                }
                                navigationState.select(MainDestination.CONVERT)
                            }
                        )

                        MainDestination.FAVORITES -> FavoritesScreen(
                            favorites = libraryState.favorites,
                            pinnedFavorites = libraryState.pinnedFavorites,
                            catalog = catalog,
                            actionsEnabled = libraryLoaded,
                            listState = favoritesListState,
                            onSelect = { favorite ->
                                categoryId = favorite.categoryId.value
                                fromId = favorite.fromId.value
                                toId = favorite.toId.value
                                navigationState.select(MainDestination.CONVERT)
                            },
                            onRemove = { favorite ->
                                updateLibrary { it.removeFavorite(favorite) }
                            },
                            onTogglePinned = { favorite ->
                                updateLibrary { it.togglePinnedFavorite(favorite) }
                            }
                        )

                        MainDestination.HISTORY -> HistoryScreen(
                            history = libraryState.history,
                            favorites = libraryState.favorites,
                            pinnedFavorites = libraryState.pinnedFavorites,
                            catalog = catalog,
                            evaluator = evaluator,
                            format = formatValue,
                            actionsEnabled = libraryLoaded,
                            listState = historyListState,
                            onSelect = { entry ->
                                categoryId = entry.categoryId.value
                                fromId = entry.fromId.value
                                toId = entry.toId.value
                                inputText = entry.inputText
                                navigationState.select(MainDestination.CONVERT)
                            },
                            onToggleFavorite = { favorite ->
                                updateLibrary { it.toggleFavorite(favorite) }
                            },
                            onTogglePinned = { favorite ->
                                updateLibrary { it.togglePinnedFavorite(favorite) }
                            },
                            onClearHistory = {
                                updateLibrary { it.clearHistory() }
                            }
                        )
                    }
                }
            }
        }
    }

    pickerTarget?.let { target ->
        UnitPickerSheet(
            title = stringResource(
                if (target == PickerTarget.FROM) {
                    R.string.select_source_unit
                } else {
                    R.string.select_destination_unit
                }
            ),
            units = uiState.category.units,
            selectedId = if (target == PickerTarget.FROM) uiState.from.id else uiState.to.id,
            currentBadgeText = stringResource(
                if (target == PickerTarget.FROM) {
                    R.string.source_short
                } else {
                    R.string.destination_short
                }
            ),
            onDismiss = { pickerTarget = null },
            onSelect = { unit ->
                if (target == PickerTarget.FROM) {
                    fromId = unit.id.value
                } else {
                    toId = unit.id.value
                }
                pickerTarget = null
            }
        )
    }

    if (precisionSheetOpen) {
        PrecisionSheet(
            settings = libraryState.displaySettings,
            enabled = libraryLoaded,
            onSettingsChange = { settings ->
                updateLibrary { it.copy(displaySettings = settings) }
            },
            onDismiss = { precisionSheetOpen = false }
        )
    }
}

@Composable
private fun ConverterBottomBarHost(
    visible: Boolean,
    selected: MainDestination,
    onSelect: (MainDestination) -> Unit
) {
    // Reading the IME inset in this small host keeps keyboard animation
    // frames from invalidating the whole converter root. Only the
    // bottom-bar slot needs to react to those intermediate values.
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    AnimatedVisibility(
        visible = visible && !imeVisible,
        enter = slideInVertically(
            animationSpec = tween(220)
        ) { height -> height } + fadeIn(tween(220)),
        exit = slideOutVertically(
            animationSpec = tween(180)
        ) { height -> height } + fadeOut(tween(120))
    ) {
        ConverterBottomBar(selected = selected, onSelect = onSelect)
    }
}

@Composable
private fun ConverterBottomBar(
    selected: MainDestination,
    onSelect: (MainDestination) -> Unit
) {
    val extended = LocalExtendedColors.current

    // A real floating capsule: it does not touch either display edge, and
    // its complete slot (capsule + surrounding breathing room + system
    // navigation inset) is what Scaffold reports as bottom content padding.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = CircleShape,
            color = extended.bottomBar.copy(alpha = 0.94f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            shadowElevation = 10.dp,
            border = BorderStroke(
                width = 1.dp,
                color = extended.panelBorder.copy(alpha = 0.8f)
            )
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(6.dp)
            ) {
                val itemSpacing = 4.dp
                val itemWidth = (maxWidth - itemSpacing * (BottomDestinations.size - 1)) /
                    BottomDestinations.size
                val selectedIndex = BottomDestinations.indexOfFirst {
                    it.destination == selected
                }.coerceAtLeast(0)
                val visualIndex = if (LocalLayoutDirection.current == LayoutDirection.Rtl) {
                    BottomDestinations.lastIndex - selectedIndex
                } else {
                    selectedIndex
                }
                val indicatorOffset by animateDpAsState(
                    targetValue = (itemWidth + itemSpacing) * visualIndex,
                    animationSpec = tween(
                        durationMillis = 220,
                        easing = FastOutSlowInEasing
                    ),
                    label = "floatingNavIndicatorOffset"
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                ) {
                    // Exactly one shared pill slides between equal-width
                    // destinations. Reading its animated value inside the
                    // offset lambda keeps the animation in the layout
                    // phase instead of recomposing the capsule per frame.
                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(indicatorOffset.roundToPx(), 0)
                            }
                            .width(itemWidth)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(extended.ink)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .selectableGroup(),
                        horizontalArrangement = Arrangement.spacedBy(itemSpacing),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BottomDestinations.forEach { item ->
                            FloatingNavigationItem(
                                item = item,
                                isSelected = item.destination == selected,
                                onClick = { onSelect(item.destination) },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FloatingNavigationItem(
    item: BottomDestination,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val extended = LocalExtendedColors.current
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) {
            extended.onInk
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(
            durationMillis = 220,
            easing = FastOutSlowInEasing
        ),
        label = "floatingNavContent"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (isSelected) 1.06f else 1f,
        animationSpec = tween(
            durationMillis = 220,
            easing = FastOutSlowInEasing
        ),
        label = "floatingNavIconScale"
    )

    Box(
        modifier = modifier
            .clip(CircleShape)
            .selectable(
                selected = isSelected,
                role = Role.Tab,
                onClick = onClick
            )
            .heightIn(min = 58.dp)
            .padding(horizontal = 4.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            SymbolIcon(
                iconRes = item.icons.forSelected(isSelected),
                contentDescription = null,
                modifier = Modifier
                    .size(23.dp)
                    .scale(iconScale),
                tint = contentColor
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = stringResource(item.labelRes),
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                maxLines = 2,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun HomeContent(
    uiState: ConverterUiState,
    inputText: String,
    catalog: UnitCatalog,
    AccueilListState: LazyListState,
    pinnedFavorites: List<FavoriteConversion>,
    currentFavorite: FavoriteConversion,
    isFavorite: Boolean,
    isPinned: Boolean,
    actionsEnabled: Boolean,
    settings: DisplaySettings,
    swapCount: Int,
    copied: Boolean,
    format: (BigDecimal) -> String,
    onSettingsClick: () -> Unit,
    onPinnedSelect: (FavoriteConversion) -> Unit,
    onInputChange: (String) -> Unit,
    onClearInput: () -> Unit,
    onToggleSign: () -> Unit,
    onFromClick: () -> Unit,
    onToClick: () -> Unit,
    onSwap: () -> Unit,
    onPrecisionClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onTogglePinned: () -> Unit,
    onCopy: () -> Unit,
    onUseAsInput: () -> Unit,
    onRowClick: (UnitDefinition) -> Unit
) {
    LazyColumn(
        state = AccueilListState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 0.dp, end = 20.dp, bottom = 24.dp)
    ) {
        item(key = "home-header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .heightIn(min = 64.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier.size(48.dp)
                ) {
                    SymbolIcon(
                        iconRes = AppIcons.Settings,
                        contentDescription = stringResource(R.string.settings_title),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        item(key = "home-pins") {
            PinnedConversionsRow(
                pinnedFavorites = pinnedFavorites,
                catalog = catalog,
                currentFavorite = currentFavorite,
                onSelect = onPinnedSelect
            )
        }

        item(key = "home-input") {
            InputPanel(
                uiState = uiState,
                inputText = inputText,
                onInputChange = onInputChange,
                onClearInput = onClearInput,
                onToggleSign = onToggleSign,
                onFromClick = onFromClick
            )
        }

        item(key = "home-seam") {
            SeamRow(
                settings = settings,
                swapCount = swapCount,
                onPrecisionClick = onPrecisionClick,
                onSwap = onSwap
            )
        }

        item(key = "home-result") {
            ResultPanel(
                uiState = uiState,
                isFavorite = isFavorite,
                isPinned = isPinned,
                actionsEnabled = actionsEnabled,
                copied = copied,
                format = format,
                onToClick = onToClick,
                onToggleFavorite = onToggleFavorite,
                onTogglePinned = onTogglePinned,
                onCopy = onCopy,
                onUseAsInput = onUseAsInput
            )
        }

        item(key = "home-all-title") {
            Text(
                text = if (uiState.rowsUseUnitReference) {
                    stringResource(R.string.equivalents_of_one_title, uiState.from.symbol)
                } else {
                    stringResource(R.string.all_conversions_title)
                },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
            )
        }

        items(uiState.rows, key = { row -> row.unit.id.value }) { row ->
            ConversionValueRow(
                row = row,
                isDestination = row.unit.id == uiState.to.id,
                format = format,
                onClick = { onRowClick(row.unit) },
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
    }
}

@Composable
private fun PinnedConversionsRow(
    pinnedFavorites: List<FavoriteConversion>,
    catalog: UnitCatalog,
    currentFavorite: FavoriteConversion,
    onSelect: (FavoriteConversion) -> Unit
) {
    val extended = LocalExtendedColors.current
    val availablePins = remember(pinnedFavorites, catalog) {
        pinnedFavorites.filter { favorite ->
            catalog.category(favorite.categoryId) != null &&
                catalog.unit(favorite.categoryId, favorite.fromId) != null &&
                catalog.unit(favorite.categoryId, favorite.toId) != null
        }
    }

    AnimatedVisibility(
        visible = availablePins.isNotEmpty(),
        enter = fadeIn(tween(240)) + expandVertically(tween(280)),
        exit = fadeOut(tween(160)) + shrinkVertically(tween(220))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.pinned_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(end = 4.dp)
            ) {
                items(
                    availablePins,
                    key = { favorite ->
                        listOf(
                            favorite.categoryId.value,
                            favorite.fromId.value,
                            favorite.toId.value
                        ).joinToString("|")
                    }
                ) { favorite ->
                    val category = catalog.category(favorite.categoryId)
                    val from = catalog.unit(favorite.categoryId, favorite.fromId)
                    val to = catalog.unit(favorite.categoryId, favorite.toId)
                    if (category != null && from != null && to != null) {
                        val isSelected = favorite == currentFavorite
                        val description = stringResource(
                            R.string.library_entry_meta_format,
                            stringResource(category.nameRes),
                            stringResource(
                                R.string.conversion_pair_format,
                                stringResource(from.nameRes),
                                stringResource(to.nameRes)
                            )
                        )
                        Surface(
                            onClick = { onSelect(favorite) },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .semantics(mergeDescendants = true) {
                                    contentDescription = description
                                    selected = isSelected
                                },
                            shape = CircleShape,
                            color = if (isSelected) {
                                extended.ink
                            } else {
                                MaterialTheme.colorScheme.surfaceContainer
                            },
                            contentColor = if (isSelected) {
                                extended.onInk
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(
                                    horizontal = 14.dp,
                                    vertical = 8.dp
                                ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SymbolIcon(
                                    iconRes = AppIcons.Pin.filled,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (isSelected) {
                                        extended.pinBlueOnInk
                                    } else {
                                        extended.pinBlue
                                    }
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = stringResource(
                                        R.string.conversion_pair_symbols_format,
                                        from.symbol,
                                        to.symbol
                                    ),
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InputPanel(
    uiState: ConverterUiState,
    inputText: String,
    onInputChange: (String) -> Unit,
    onClearInput: () -> Unit,
    onToggleSign: () -> Unit,
    onFromClick: () -> Unit
) {
    val extended = LocalExtendedColors.current
    val errorText = errorTextFor(uiState.result)
    var inputFocused by remember { mutableStateOf(false) }
    val amountStyle = numericTextStyle(
        base = MaterialTheme.typography.displayMedium,
        fontSizeSp = amountFontSizeFor(inputText)
    ).copy(color = MaterialTheme.colorScheme.onSurface)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = extended.panel,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(
            width = if (inputFocused) 2.dp else 1.dp,
            color = if (inputFocused) extended.ink else extended.outlineStrong
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.label_from),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                if (uiState.negativeInputAllowed) {
                    val toggleSignDescription = stringResource(R.string.toggle_sign)
                    TextButton(
                        onClick = onToggleSign,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .semantics(mergeDescendants = true) {
                                contentDescription = toggleSignDescription
                            }
                    ) {
                        Text(
                            text = "±",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                if (inputText.isNotEmpty()) {
                    IconButton(
                        onClick = onClearInput,
                        modifier = Modifier.size(48.dp)
                    ) {
                        SymbolIcon(
                            iconRes = AppIcons.Close,
                            contentDescription = stringResource(R.string.clear_input),
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            UnitPill(
                unit = uiState.from,
                selectionDescription = stringResource(R.string.select_source_unit),
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurface,
                secondaryContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = onFromClick
            )

            val inputDescription = stringResource(R.string.input_description)
            BasicTextField(
                value = inputText,
                onValueChange = onInputChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .padding(top = 8.dp)
                    .onFocusChanged { inputFocused = it.isFocused }
                    .semantics { contentDescription = inputDescription },
                textStyle = amountStyle,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done
                ),
                singleLine = true,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (inputText.isEmpty()) {
                            Text(
                                text = stringResource(R.string.input_placeholder),
                                style = amountStyle.copy(color = extended.hint),
                                maxLines = 1
                            )
                        }
                        innerTextField()
                    }
                }
            )

            if (errorText != null) {
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SymbolIcon(
                        iconRes = AppIcons.Error,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = errorText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun SeamRow(
    settings: DisplaySettings,
    swapCount: Int,
    onPrecisionClick: () -> Unit,
    onSwap: () -> Unit
) {
    val extended = LocalExtendedColors.current
    val swapRotation by animateFloatAsState(
        targetValue = swapCount * 180f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = 320f
        ),
        label = "swapRotation"
    )
    val swapDescription = stringResource(R.string.swap_units)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
    ) {
        HorizontalDivider(
            modifier = Modifier.align(Alignment.Center),
            color = extended.panelBorder,
            thickness = 1.dp
        )

        // Independent start-side region. Reserving only the 48 dp swap
        // target plus a 8 dp gap at the end prevents precision labels of
        // any length from participating in the swap button's placement.
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth()
                .padding(end = 56.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Surface(
                onClick = onPrecisionClick,
                modifier = Modifier.heightIn(min = 48.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SymbolIcon(
                        iconRes = AppIcons.Tune,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = precisionSummary(settings),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 3
                    )
                }
            }
        }

        FilledIconButton(
            onClick = onSwap,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(48.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = swapDescription
                },
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = extended.ink,
                contentColor = extended.onInk,
                disabledContainerColor = extended.ink.copy(alpha = 0.38f),
                disabledContentColor = extended.onInk.copy(alpha = 0.72f)
            )
        ) {
            SymbolIcon(
                iconRes = AppIcons.Swap,
                contentDescription = null,
                modifier = Modifier
                    .size(24.dp)
                    .rotate(swapRotation)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResultPanel(
    uiState: ConverterUiState,
    isFavorite: Boolean,
    isPinned: Boolean,
    actionsEnabled: Boolean,
    copied: Boolean,
    format: (BigDecimal) -> String,
    onToClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onTogglePinned: () -> Unit,
    onCopy: () -> Unit,
    onUseAsInput: () -> Unit
) {
    val extended = LocalExtendedColors.current
    val resultValue = uiState.result as? ResultState.Value
    val resultText = resultValue?.let { format(it.value) }
        ?: stringResource(R.string.result_placeholder)
    val resultDescription = if (resultValue != null) {
        stringResource(R.string.result_description, resultText, uiState.to.symbol)
    } else {
        stringResource(R.string.result_label)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = extended.resultSlab,
        contentColor = extended.onResultSlab
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.label_to),
                style = MaterialTheme.typography.labelMedium,
                color = extended.onResultSlabVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                UnitPill(
                    unit = uiState.to,
                    selectionDescription = stringResource(R.string.select_destination_unit),
                    containerColor = extended.onResultSlab.copy(alpha = 0.12f),
                    contentColor = extended.onResultSlab,
                    secondaryContentColor = extended.onResultSlabVariant,
                    onClick = onToClick,
                    modifier = Modifier.weight(1f)
                )
                FavoriteHeartToggle(
                    checked = isFavorite,
                    onCheckedChange = { onToggleFavorite() },
                    enabled = actionsEnabled,
                    inactiveTint = extended.onResultSlabVariant
                )
                PinToggle(
                    checked = isPinned,
                    onCheckedChange = { onTogglePinned() },
                    enabled = actionsEnabled,
                    inactiveTint = extended.onResultSlabVariant,
                    activeTint = extended.pinBlueOnInk
                )
            }

            Text(
                text = stringResource(R.string.result_label),
                style = MaterialTheme.typography.labelMedium,
                color = extended.onResultSlabVariant,
                modifier = Modifier.padding(top = 16.dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(top = 2.dp)
            ) {
                SelectionContainer {
                    Text(
                        text = resultText,
                        style = numericTextStyle(
                            base = MaterialTheme.typography.headlineLarge,
                            fontSizeSp = resultFontSizeFor(resultText)
                        ),
                        color = if (resultValue != null) {
                            extended.onResultSlab
                        } else {
                            extended.onResultSlabVariant
                        },
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.semantics {
                            contentDescription = resultDescription
                        }
                    )
                }
            }

            resultValue?.rate?.let { rate ->
                Text(
                    text = stringResource(
                        R.string.rate_format,
                        uiState.from.symbol,
                        format(rate),
                        uiState.to.symbol
                    ),
                    style = numericTextStyle(
                        MaterialTheme.typography.bodyMedium,
                        fontSizeSp = 14f
                    ),
                    color = extended.onResultSlabVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onCopy,
                    enabled = resultValue != null,
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = extended.onResultSlab,
                        contentColor = extended.resultSlab,
                        disabledContainerColor = extended.onResultSlab.copy(alpha = 0.16f),
                        disabledContentColor = extended.onResultSlabVariant
                    ),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                ) {
                    AnimatedContent(
                        targetState = copied,
                        transitionSpec = {
                            fadeIn(tween(180)) togetherWith fadeOut(tween(120))
                        },
                        label = "copyFeedback"
                    ) { isCopied ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SymbolIcon(
                                iconRes = if (isCopied) AppIcons.Check else AppIcons.Copy,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(
                                    if (isCopied) {
                                        R.string.result_copied_button
                                    } else {
                                        R.string.copy_result
                                    }
                                )
                            )
                        }
                    }
                }
                OutlinedButton(
                    onClick = onUseAsInput,
                    enabled = resultValue != null,
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = extended.onResultSlab,
                        disabledContentColor = extended.onResultSlabVariant
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (resultValue != null) {
                            extended.onResultSlab.copy(alpha = 0.4f)
                        } else {
                            extended.onResultSlab.copy(alpha = 0.18f)
                        }
                    ),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                ) {
                    SymbolIcon(
                        iconRes = AppIcons.ArrowUpward,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(text = stringResource(R.string.use_as_input))
                }
            }
        }
    }
}

@Composable
private fun UnitPill(
    unit: UnitDefinition,
    selectionDescription: String,
    containerColor: Color,
    contentColor: Color,
    secondaryContentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pillDescription = "$selectionDescription: ${stringResource(unit.nameRes)} (${unit.symbol})"
    Surface(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 56.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = pillDescription
            },
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedContent(
                targetState = unit,
                transitionSpec = {
                    (fadeIn(tween(180)) + slideInVertically(tween(180)) { height ->
                        height / 3
                    }) togetherWith (fadeOut(tween(120)) +
                        slideOutVertically(tween(120)) { height -> -height / 3 })
                },
                label = "unitPill",
                modifier = Modifier.weight(1f)
            ) { animatedUnit ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = animatedUnit.symbol,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 20.sp,
                            lineHeight = 24.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(animatedUnit.nameRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = secondaryContentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            SymbolIcon(
                iconRes = AppIcons.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = secondaryContentColor
            )
        }
    }
}

@Composable
private fun ConversionValueRow(
    row: UnitConversionRow,
    isDestination: Boolean,
    format: (BigDecimal) -> String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val extended = LocalExtendedColors.current
    val valueText = row.value?.let(format) ?: stringResource(R.string.result_placeholder)
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .semantics { selected = isDestination },
        shape = RoundedCornerShape(16.dp),
        color = if (isDestination) extended.ink else Color.Transparent,
        contentColor = if (isDestination) {
            extended.onInk
        } else {
            MaterialTheme.colorScheme.onSurface
        }
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 56.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(row.unit.nameRes),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = if (isDestination) {
                            FontWeight.SemiBold
                        } else {
                            FontWeight.Normal
                        }
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = row.unit.symbol,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isDestination) {
                        extended.onInk.copy(alpha = 0.72f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1
                )
            }
            Box(
                modifier = Modifier
                    .weight(1.1f)
                    .horizontalScroll(rememberScrollState()),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text(
                    text = valueText,
                    style = numericTextStyle(
                        MaterialTheme.typography.bodyLarge,
                        fontSizeSp = 16f
                    ),
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.End
                )
            }
            if (isDestination) {
                Spacer(Modifier.width(10.dp))
                Surface(
                    modifier = Modifier.size(20.dp),
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

private fun amountFontSizeFor(text: String): Float = when (text.length) {
    in 0..8 -> 48f
    in 9..12 -> 40f
    in 13..16 -> 32f
    else -> 28f
}

private fun resultFontSizeFor(text: String): Float = when (text.length) {
    in 0..10 -> 44f
    in 11..14 -> 38f
    in 15..19 -> 32f
    else -> 26f
}

@Composable
private fun errorTextFor(result: ResultState): String? {
    val resId: Int? = when (result) {
        is ResultState.Invalid -> when (result.reason) {
            InvalidReason.TOO_MANY_DIGITS,
            InvalidReason.EXPONENT_OUT_OF_RANGE -> R.string.error_number_too_large

            InvalidReason.INVALID_CHARACTER,
            InvalidReason.INVALID_SIGN_PLACEMENT,
            InvalidReason.MULTIPLE_DECIMAL_SEPARATORS,
            InvalidReason.AMBIGUOUS_SEPARATORS,
            InvalidReason.INVALID_GROUPING,
            InvalidReason.INVALID_EXPONENT -> R.string.error_invalid_number
        }

        is ResultState.Domain -> when (result.violation) {
            DomainViolation.MUST_BE_NON_NEGATIVE -> R.string.error_non_negative
            DomainViolation.MUST_BE_POSITIVE -> R.string.error_positive
            DomainViolation.BELOW_MINIMUM -> R.string.error_below_absolute_zero
        }

        ResultState.Overflow -> R.string.error_number_too_large
        ResultState.Empty,
        ResultState.Incomplete,
        is ResultState.Value -> null
    }
    return resId?.let { stringResource(it) }
}
