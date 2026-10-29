package app.unitconverter.feature.convert

import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.unitconverter.R
import app.unitconverter.core.ui.AppIcons
import app.unitconverter.core.ui.theme.DynamicColorPolicy
import app.unitconverter.core.ui.theme.LocalExtendedColors
import app.unitconverter.feature.update.UpdateManager
import app.unitconverter.feature.update.UpdateSettingsCard

@StringRes
private fun themeModeLabelRes(mode: AppThemeMode): Int = when (mode) {
    AppThemeMode.SYSTEM -> R.string.theme_system
    AppThemeMode.LIGHT -> R.string.theme_light
    AppThemeMode.DARK -> R.string.theme_dark
}

@StringRes
private fun languageLabelRes(language: AppLanguage): Int = when (language) {
    AppLanguage.SYSTEM -> R.string.language_system
    AppLanguage.FRENCH -> R.string.language_name_french
    AppLanguage.ENGLISH -> R.string.language_name_english
    AppLanguage.SPANISH -> R.string.language_name_spanish
    AppLanguage.PORTUGUESE -> R.string.language_name_portuguese
    AppLanguage.GERMAN -> R.string.language_name_german
    AppLanguage.ITALIAN -> R.string.language_name_italian
    AppLanguage.CHINESE_SIMPLIFIED -> R.string.language_name_chinese_simplified
    AppLanguage.JAPANESE -> R.string.language_name_japanese
    AppLanguage.KOREAN -> R.string.language_name_korean
    AppLanguage.ARABIC -> R.string.language_name_arabic
}

/**
 * Settings destination.
 *
 * Display precision remains in the validated conversion-library payload.
 * Theme and language arrive through [AppPreferences], whose separate
 * persisted key is loaded by the activity before composition so the
 * selected theme/locale applies to the whole window without a flash.
 */
@Composable
fun SettingsScreen(
    settings: DisplaySettings,
    appPreferences: AppPreferences,
    actionsEnabled: Boolean,
    onBack: () -> Unit,
    onPrecisionClick: () -> Unit,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    updateManager: UpdateManager,
    modifier: Modifier = Modifier
) {
    val extended = LocalExtendedColors.current
    val context = LocalContext.current
    val dynamicColorSupported = DynamicColorPolicy.isSupported(Build.VERSION.SDK_INT)
    val dynamicColorChecked = dynamicColorSupported && appPreferences.dynamicColorEnabled
    val chevronRotation = if (LocalLayoutDirection.current == LayoutDirection.Rtl) {
        90f
    } else {
        -90f
    }
    var languageSheetOpen by rememberSaveable { mutableStateOf(false) }
    val versionName = remember(context) {
        runCatching {
            context.packageManager
                .getPackageInfo(context.packageName, 0)
                .versionName
        }.getOrNull()
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
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(48.dp)
            ) {
                SymbolIcon(
                    iconRes = AppIcons.ArrowBack,
                    contentDescription = stringResource(R.string.settings_back),
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            SettingsSection(title = stringResource(R.string.settings_appearance_title)) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = extended.panel,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    border = BorderStroke(1.dp, extended.panelBorder)
                ) {
                    Column(modifier = Modifier.selectableGroup()) {
                        AppThemeMode.entries.forEachIndexed { index, mode ->
                            if (index > 0) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 16.dp),
                                    color = extended.panelBorder
                                )
                            }
                            SettingsOptionRow(
                                title = stringResource(themeModeLabelRes(mode)),
                                selected = appPreferences.themeMode == mode,
                                onClick = { onThemeModeChange(mode) }
                            )
                        }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = extended.panel,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    border = BorderStroke(1.dp, extended.panelBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 72.dp)
                            .toggleable(
                                value = dynamicColorChecked,
                                enabled = dynamicColorSupported,
                                role = Role.Switch,
                                onValueChange = onDynamicColorChange
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.material_you_title),
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = stringResource(
                                    if (dynamicColorSupported) {
                                        R.string.material_you_summary
                                    } else {
                                        R.string.material_you_unavailable
                                    }
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = dynamicColorChecked,
                            onCheckedChange = null,
                            enabled = dynamicColorSupported
                        )
                    }
                }
            }

            SettingsSection(title = stringResource(R.string.language_title)) {
                Surface(
                    onClick = { languageSheetOpen = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = extended.panel,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    border = BorderStroke(1.dp, extended.panelBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .heightIn(min = 72.dp)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.language_title),
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = stringResource(
                                    languageLabelRes(appPreferences.language)
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        SymbolIcon(
                            iconRes = AppIcons.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier
                                .size(24.dp)
                                .rotate(chevronRotation),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            SettingsSection(title = stringResource(R.string.precision_title)) {
                Surface(
                    onClick = onPrecisionClick,
                    enabled = actionsEnabled,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = extended.panel,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    border = BorderStroke(1.dp, extended.panelBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .heightIn(min = 72.dp)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(44.dp),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                SymbolIcon(
                                    iconRes = AppIcons.Tune,
                                    contentDescription = null,
                                    modifier = Modifier.size(23.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.precision_title),
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = precisionSummary(settings),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        SymbolIcon(
                            iconRes = AppIcons.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier
                                .size(24.dp)
                                .rotate(chevronRotation),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            SettingsSection(title = stringResource(R.string.update_section_title)) {
                UpdateSettingsCard(manager = updateManager)
            }

            SettingsSection(title = stringResource(R.string.settings_about_title)) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = extended.panel,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    border = BorderStroke(1.dp, extended.panelBorder)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        AboutRow(
                            title = stringResource(R.string.app_name),
                            summary = stringResource(R.string.converter_subtitle)
                        )
                        if (!versionName.isNullOrBlank()) {
                            HorizontalDivider(color = extended.panelBorder)
                            AboutRow(
                                title = stringResource(R.string.app_name),
                                summary = stringResource(
                                    R.string.settings_version_format,
                                    versionName
                                )
                            )
                        }
                    }
                }
            }

            Text(
                text = stringResource(R.string.created_by_blood),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 8.dp)
            )
        }
    }

    if (languageSheetOpen) {
        LanguagePickerSheet(
            selected = appPreferences.language,
            onDismiss = { languageSheetOpen = false },
            onSelect = { language ->
                languageSheetOpen = false
                if (language != appPreferences.language) onLanguageChange(language)
            }
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        content()
    }
}

@Composable
private fun SettingsOptionRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val extended = LocalExtendedColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (selected) {
            Surface(
                modifier = Modifier.size(24.dp),
                shape = CircleShape,
                color = extended.ink,
                contentColor = extended.onInk
            ) {
                Box(contentAlignment = Alignment.Center) {
                    SymbolIcon(
                        iconRes = AppIcons.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguagePickerSheet(
    selected: AppLanguage,
    onDismiss: () -> Unit,
    onSelect: (AppLanguage) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.language_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
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

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .selectableGroup()
            ) {
                items(AppLanguage.entries, key = { it.code }) { language ->
                    SettingsOptionRow(
                        title = stringResource(languageLabelRes(language)),
                        selected = language == selected,
                        onClick = { onSelect(language) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutRow(
    title: String,
    summary: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(vertical = 10.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = summary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
