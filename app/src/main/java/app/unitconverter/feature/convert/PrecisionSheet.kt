package app.unitconverter.feature.convert

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.unitconverter.R
import app.unitconverter.core.ui.AppIcons
import app.unitconverter.core.ui.theme.LocalExtendedColors

@Composable
fun precisionModeLabel(mode: DisplayPrecisionMode): String = stringResource(
    when (mode) {
        DisplayPrecisionMode.SMART -> R.string.precision_smart
        DisplayPrecisionMode.FIXED -> R.string.precision_fixed
        DisplayPrecisionMode.SCIENTIFIC -> R.string.precision_scientific
    }
)

@Composable
fun precisionSummary(settings: DisplaySettings): String {
    val label = precisionModeLabel(settings.mode)
    return if (settings.mode == DisplayPrecisionMode.SMART) {
        label
    } else {
        stringResource(R.string.precision_pill_format, label, settings.decimalPlaces)
    }
}

/**
 * Display-precision editor.
 *
 * The sheet emits the exact same [DisplaySettings] updates as the old
 * inline controls, so persistence and formatter behavior do not change.
 * Smart mode continues to ignore the stored decimal-place value.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrecisionSheet(
    settings: DisplaySettings,
    enabled: Boolean,
    onSettingsChange: (DisplaySettings) -> Unit,
    onDismiss: () -> Unit
) {
    val extended = LocalExtendedColors.current
    val haptics = LocalHapticFeedback.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val modes = listOf(
        DisplayPrecisionMode.SMART,
        DisplayPrecisionMode.FIXED,
        DisplayPrecisionMode.SCIENTIFIC
    )

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
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.precision_title),
                    style = MaterialTheme.typography.titleLarge,
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

            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                modes.forEachIndexed { index, mode ->
                    val selected = settings.mode == mode
                    SegmentedButton(
                        selected = selected,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onSettingsChange(settings.copy(mode = mode))
                        },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = modes.size
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 56.dp),
                        enabled = enabled,
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = extended.ink,
                            activeContentColor = extended.onInk,
                            activeBorderColor = extended.ink,
                            inactiveContainerColor = Color.Transparent,
                            inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            inactiveBorderColor = extended.outlineStrong,
                            disabledActiveContainerColor = extended.ink.copy(alpha = 0.38f),
                            disabledActiveContentColor = extended.onInk.copy(alpha = 0.72f),
                            disabledActiveBorderColor = extended.ink.copy(alpha = 0.38f),
                            disabledInactiveContainerColor = Color.Transparent,
                            disabledInactiveContentColor = extended.disabledContent,
                            disabledInactiveBorderColor = extended.panelBorder
                        ),
                        icon = {
                            if (selected) {
                                SymbolIcon(
                                    iconRes = AppIcons.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        label = {
                            Text(
                                text = precisionModeLabel(mode),
                                maxLines = 2,
                                textAlign = TextAlign.Center,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                }
            }

            AnimatedContent(
                targetState = settings.mode,
                transitionSpec = {
                    (fadeIn(tween(180)) + slideInVertically(tween(180)) { height ->
                        height / 4
                    }) togetherWith (fadeOut(tween(120)) +
                        slideOutVertically(tween(120)) { height -> -height / 4 })
                },
                label = "precisionDescription",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) { mode ->
                Text(
                    text = stringResource(
                        when (mode) {
                            DisplayPrecisionMode.SMART -> R.string.precision_description_smart
                            DisplayPrecisionMode.FIXED -> R.string.precision_description_fixed
                            DisplayPrecisionMode.SCIENTIFIC ->
                                R.string.precision_description_scientific
                        }
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(
                visible = settings.mode != DisplayPrecisionMode.SMART,
                enter = fadeIn(tween(180)) + expandVertically(tween(220)),
                exit = fadeOut(tween(120)) + shrinkVertically(tween(180)),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.decimal_places_label),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onSettingsChange(
                                settings.copy(decimalPlaces = settings.decimalPlaces - 1)
                            )
                        },
                        enabled = enabled && settings.decimalPlaces > 0,
                        modifier = Modifier.size(48.dp)
                    ) {
                        SymbolIcon(
                            iconRes = AppIcons.Remove,
                            contentDescription = stringResource(
                                R.string.decrease_decimal_places
                            ),
                            modifier = Modifier.size(24.dp),
                            tint = if (enabled && settings.decimalPlaces > 0) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                extended.disabledContent
                            }
                        )
                    }
                    Text(
                        text = settings.decimalPlaces.toString(),
                        style = numericTextStyle(
                            MaterialTheme.typography.titleMedium,
                            fontSizeSp = 20f
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(min = 36.dp)
                    )
                    IconButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onSettingsChange(
                                settings.copy(decimalPlaces = settings.decimalPlaces + 1)
                            )
                        },
                        enabled = enabled &&
                            settings.decimalPlaces < DisplaySettings.MAX_DECIMAL_PLACES,
                        modifier = Modifier.size(48.dp)
                    ) {
                        SymbolIcon(
                            iconRes = AppIcons.Add,
                            contentDescription = stringResource(
                                R.string.increase_decimal_places
                            ),
                            modifier = Modifier.size(24.dp),
                            tint = if (
                                enabled &&
                                settings.decimalPlaces < DisplaySettings.MAX_DECIMAL_PLACES
                            ) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                extended.disabledContent
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.size(12.dp))
            Text(
                text = stringResource(R.string.precision_sheet_note),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
