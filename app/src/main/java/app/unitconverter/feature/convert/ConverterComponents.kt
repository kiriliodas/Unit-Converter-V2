package app.unitconverter.feature.convert

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.unitconverter.R
import app.unitconverter.core.model.UnitDefinition
import app.unitconverter.core.ui.AppIcons
import app.unitconverter.core.ui.theme.LocalExtendedColors

/**
 * Height occupied by [lineCount] lines of [style] at the current font
 * scale. Grid cards reserve this space instead of using a brittle fixed
 * height, so one-line and two-line labels keep the same baseline zones
 * while larger accessibility fonts can still grow the card.
 */
@Composable
internal fun reservedTextHeight(style: TextStyle, lineCount: Int): Dp {
    val lineHeight = if (style.lineHeight.isSp) {
        style.lineHeight
    } else {
        style.fontSize
    }
    return with(LocalDensity.current) { lineHeight.toDp() } * lineCount
}

/** Numeric text always uses tabular figures, including when font scaling. */
@Composable
fun numericTextStyle(base: TextStyle, fontSizeSp: Float = base.fontSize.value): TextStyle =
    base.copy(
        fontSize = fontSizeSp.sp,
        lineHeight = (fontSizeSp * 1.18f).sp,
        fontFeatureSettings = "tnum"
    )

@Composable
fun SymbolIcon(
    @DrawableRes iconRes: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    Icon(
        painter = painterResource(iconRes),
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint
    )
}

/**
 * Favorite heart with no decorative container behind the glyph. Red is
 * applied exclusively when the represented directed conversion is a
 * favorite; the 48 dp toggle target itself remains unchanged.
 */
@Composable
fun FavoriteHeartToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    inactiveTint: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    val extended = LocalExtendedColors.current
    val haptics = LocalHapticFeedback.current
    val scale = remember { Animatable(1f) }
    var initialized by remember { mutableStateOf(false) }

    LaunchedEffect(checked) {
        if (!initialized) {
            initialized = true
        } else if (checked) {
            scale.animateTo(1.25f, tween(durationMillis = 120))
            scale.animateTo(
                1f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 400f)
            )
        } else {
            scale.animateTo(0.85f, tween(durationMillis = 90))
            scale.animateTo(
                1f,
                spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 500f)
            )
        }
    }

    IconToggleButton(
        checked = checked,
        onCheckedChange = { newValue ->
            haptics.performHapticFeedback(
                if (newValue) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff
            )
            onCheckedChange(newValue)
        },
        modifier = modifier.size(48.dp),
        enabled = enabled,
        colors = IconButtonDefaults.iconToggleButtonColors(
            containerColor = Color.Transparent,
            contentColor = inactiveTint,
            checkedContainerColor = Color.Transparent,
            checkedContentColor = extended.favoriteRed,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = extended.disabledContent
        )
    ) {
        SymbolIcon(
            iconRes = AppIcons.Favorite.forSelected(checked),
            contentDescription = stringResource(
                if (checked) R.string.remove_favorite else R.string.add_favorite
            ),
            modifier = Modifier
                .size(24.dp)
                .scale(scale.value),
            tint = when {
                !enabled -> extended.disabledContent
                checked -> extended.favoriteRed
                else -> inactiveTint
            }
        )
    }
}

/**
 * Pin toggle with no decorative circular container. The active glyph
 * uses the theme's restrained blue unless a caller on the dark result
 * slab supplies its high-contrast on-ink variant.
 */
@Composable
fun PinToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    inactiveTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    activeTint: Color? = null
) {
    val extended = LocalExtendedColors.current
    val selectedTint = activeTint ?: extended.pinBlue
    val haptics = LocalHapticFeedback.current
    val scale = remember { Animatable(1f) }
    val rotation = remember { Animatable(0f) }
    var initialized by remember { mutableStateOf(false) }

    LaunchedEffect(checked) {
        if (!initialized) {
            initialized = true
        } else if (checked) {
            rotation.snapTo(-20f)
            scale.animateTo(1.15f, tween(durationMillis = 100))
            rotation.animateTo(
                0f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 400f)
            )
            scale.animateTo(
                1f,
                spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 500f)
            )
        } else {
            scale.animateTo(0.9f, tween(durationMillis = 80))
            scale.animateTo(
                1f,
                spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 500f)
            )
        }
    }

    IconToggleButton(
        checked = checked,
        onCheckedChange = { newValue ->
            haptics.performHapticFeedback(
                if (newValue) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff
            )
            onCheckedChange(newValue)
        },
        modifier = modifier.size(48.dp),
        enabled = enabled,
        colors = IconButtonDefaults.iconToggleButtonColors(
            containerColor = Color.Transparent,
            contentColor = inactiveTint,
            checkedContainerColor = Color.Transparent,
            checkedContentColor = selectedTint,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = extended.disabledContent
        )
    ) {
        SymbolIcon(
            iconRes = AppIcons.Pin.forSelected(checked),
            contentDescription = stringResource(
                if (checked) R.string.unpin_conversion else R.string.pin_conversion
            ),
            modifier = Modifier
                .size(24.dp)
                .scale(scale.value)
                .rotate(rotation.value),
            tint = when {
                !enabled -> extended.disabledContent
                checked -> selectedTint
                else -> inactiveTint
            }
        )
    }
}

/** Full-width searchable field used by both unit-selection surfaces. */
@Composable
fun UnitSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    val extended = LocalExtendedColors.current
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.heightIn(min = 56.dp),
        placeholder = {
            Text(
                text = placeholder,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingIcon = {
            SymbolIcon(
                iconRes = AppIcons.Search,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(48.dp)
                ) {
                    SymbolIcon(
                        iconRes = AppIcons.Close,
                        contentDescription = stringResource(R.string.clear_search),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        },
        singleLine = true,
        shape = CircleShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            disabledTextColor = extended.disabledContent,
            focusedContainerColor = extended.panel,
            unfocusedContainerColor = extended.panel,
            disabledContainerColor = extended.panel,
            cursorColor = MaterialTheme.colorScheme.primary,
            focusedBorderColor = extended.ink,
            unfocusedBorderColor = extended.panelBorder,
            disabledBorderColor = extended.panelBorder,
            focusedLeadingIconColor = MaterialTheme.colorScheme.onSurface,
            unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            focusedTrailingIconColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}

/**
 * Symbol-first unit tile. [badgeText] describes the unit role already
 * active on the converter screen when this tile represents that unit.
 *
 * The regular tile reserves a visible two-line badge zone for the Units
 * browser. [compact] is reserved for the converter's picker sheet: the
 * sheet title already states the source/destination role, so the current
 * tile is marked by its ink surface and check while the badge remains in
 * the accessibility description. Removing that one flow zone cuts the
 * picker tile from 130 dp to about 80 dp without shrinking its text.
 */
@Composable
fun UnitTile(
    unit: UnitDefinition,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badgeText: String? = null,
    isCurrent: Boolean = false,
    compact: Boolean = false
) {
    val extended = LocalExtendedColors.current
    val container by animateColorAsState(
        targetValue = if (isCurrent) {
            extended.ink
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        animationSpec = tween(durationMillis = 180),
        label = "unitTileContainer"
    )
    val content by animateColorAsState(
        targetValue = if (isCurrent) {
            extended.onInk
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        animationSpec = tween(durationMillis = 180),
        label = "unitTileContent"
    )
    val secondary = if (isCurrent) {
        extended.onInk.copy(alpha = 0.72f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val symbolStyle = MaterialTheme.typography.titleMedium.copy(
        fontSize = if (compact) 18.sp else 20.sp,
        lineHeight = if (compact) 22.sp else 24.sp,
        fontWeight = FontWeight.SemiBold
    )
    val nameStyle = MaterialTheme.typography.bodySmall
    val badgeStyle = MaterialTheme.typography.labelSmall
    val symbolZoneHeight = reservedTextHeight(symbolStyle, lineCount = 1)
    val nameZoneHeight = reservedTextHeight(nameStyle, lineCount = 2)
    val badgeZoneHeight = reservedTextHeight(badgeStyle, lineCount = 2) + 8.dp
    val showCurrentCheck = isCurrent && (compact || badgeText == null)
    val unitName = stringResource(unit.nameRes)
    val description = if (badgeText == null) {
        "$unitName, ${unit.symbol}"
    } else {
        "$unitName, ${unit.symbol}, $badgeText"
    }

    Surface(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = if (compact) 80.dp else 130.dp)
            .semantics {
                role = Role.Button
                selected = isCurrent
                contentDescription = description
            },
        shape = RoundedCornerShape(20.dp),
        color = container,
        contentColor = content
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = if (compact) 12.dp else 14.dp,
                    vertical = if (compact) 10.dp else 12.dp
                )
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Top
            ) {
                Box(
                    modifier = Modifier
                        .padding(end = if (showCurrentCheck) 24.dp else 0.dp)
                        .heightIn(min = symbolZoneHeight)
                ) {
                    Text(
                        text = unit.symbol,
                        style = symbolStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .heightIn(min = nameZoneHeight)
                ) {
                    Text(
                        text = unitName,
                        style = nameStyle,
                        color = secondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (!compact) {
                Box(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .heightIn(min = badgeZoneHeight)
                ) {
                    if (badgeText != null) {
                        Surface(
                            shape = CircleShape,
                            color = extended.onInk,
                            contentColor = extended.ink
                        ) {
                            Row(
                                modifier = Modifier.padding(
                                    horizontal = 8.dp,
                                    vertical = 4.dp
                                ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SymbolIcon(
                                    iconRes = AppIcons.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = badgeText,
                                    style = badgeStyle,
                                    maxLines = 2
                                )
                            }
                        }
                    }
                }
                }
            }
            if (showCurrentCheck) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(20.dp),
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

@Composable
fun EmptyState(
    @DrawableRes iconRes: Int,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    iconContainerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    secondaryMessage: String? = null
) {
    Column(
        modifier = modifier.padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(80.dp),
            shape = CircleShape,
            color = iconContainerColor,
            contentColor = iconTint
        ) {
            Box(contentAlignment = Alignment.Center) {
                SymbolIcon(
                    iconRes = iconRes,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = iconTint
                )
            }
        }
        Spacer(Modifier.size(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
        if (secondaryMessage != null) {
            Row(
                modifier = Modifier.padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SymbolIcon(
                    iconRes = AppIcons.Pin.outlined,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = secondaryMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
