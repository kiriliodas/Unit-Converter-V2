package app.unitconverter.feature.convert

import java.text.Normalizer
import java.util.Locale

/**
 * Accent- and case-insensitive matching for the unit picker.
 *
 * Kept Android-free so the matching rules (including French unit names
 * such as "Kilomètre") are unit-testable on the JVM.
 */
object UnitSearch {

    /** Lowercases and strips diacritics: "Kilomètre" -> "kilometre". */
    fun normalize(text: String): String {
        val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
        return decomposed
            .replace(COMBINING_MARKS, "")
            .lowercase(Locale.ROOT)
            .trim()
    }

    /**
     * True when [query] is blank, or matches the unit's localized
     * [name], its [symbol], or any of its search [aliases].
     *
     * Matching is a substring test after [normalize]; ranking prefers
     * an exact symbol match, then a name prefix, so typing "mi" surfaces
     * Mile before units that merely contain those letters.
     */
    fun matches(query: String, name: String, symbol: String, aliases: Set<String>): Boolean =
        rank(query, name, symbol, aliases) >= 0

    /** Higher is a better match; -1 means "does not match". */
    fun rank(query: String, name: String, symbol: String, aliases: Set<String>): Int {
        val trimmedQuery = query.trim()
        val needle = normalize(trimmedQuery)
        if (needle.isEmpty()) return 0

        val normalizedSymbol = normalize(symbol)
        val normalizedName = normalize(name)
        val singleCharacterCaseMismatch =
            trimmedQuery.length == 1 &&
            symbol.isNotEmpty() &&
            symbol.first().equals(trimmedQuery.first(), ignoreCase = true) &&
            symbol.first() != trimmedQuery.first()

        if (singleCharacterCaseMismatch) return 1

        return when {
            normalizedSymbol == needle -> 4
            normalizedName.startsWith(needle) -> 3
            aliases.any { normalize(it).startsWith(needle) } -> 2
            normalizedName.contains(needle) || normalizedSymbol.contains(needle) -> 1
            aliases.any { normalize(it).contains(needle) } -> 1
            else -> -1
        }
    }

    private val COMBINING_MARKS = Regex("\\p{Mn}+")
}
