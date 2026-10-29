package app.unitconverter.core.catalog

import app.unitconverter.R
import app.unitconverter.core.model.CategoryGroup
import app.unitconverter.core.model.CategoryId
import app.unitconverter.core.model.ConversionDefinition
import app.unitconverter.core.model.DomainRule
import app.unitconverter.core.model.FuelRole
import app.unitconverter.core.model.UnitCategory
import app.unitconverter.core.model.UnitDefinition
import app.unitconverter.core.model.UnitId
import java.math.BigDecimal
import java.math.MathContext

/**
 * Catalog assembled through Phase C5.
 *
 * Length (linear), Temperature (affine), and Fuel economy (reciprocal)
 * were introduced first so that every conversion shape was exercised.
 * Phase C3 appended seven everyday/computing linear categories, and
 * Phase C5 appends seven science/engineering linear categories. The
 * existing category order, unit IDs, factors, and defaults are never
 * changed by an append-only catalog phase, keeping saved favorites and
 * history identifiers stable.
 *
 * Factors are exact decimal definitions wherever the definition is a
 * terminating decimal. Genuinely recurring or irrational ratios (for
 * example km/h to m/s, psi to pascal, and degrees to radians) are
 * materialized once at 50 significant digits, well beyond the
 * conversion engine's 34-digit working precision. No factor originates
 * from a `Double` approximation.
 */
object RepresentativeCatalog {

    val catalog: UnitCatalog = UnitCatalog(
        listOf(
            lengthCategory(),
            temperatureCategory(),
            fuelEconomyCategory(),
            massCategory(),
            areaCategory(),
            volumeCategory(),
            speedCategory(),
            timeCategory(),
            dataStorageCategory(),
            dataRateCategory(),
            pressureCategory(),
            energyCategory(),
            powerCategory(),
            forceCategory(),
            frequencyCategory(),
            angleCategory(),
            torqueCategory()
        )
    )

    //region Length — canonical base: meter.

    private fun lengthCategory(): UnitCategory {
        val categoryId = CategoryId("length")

        fun length(
            id: String,
            nameRes: Int,
            symbol: String,
            factorToMeter: String,
            aliases: Set<String> = emptySet()
        ) = UnitDefinition(
            id = UnitId(id),
            categoryId = categoryId,
            nameRes = nameRes,
            symbol = symbol,
            aliases = aliases,
            conversion = ConversionDefinition.Linear(BigDecimal(factorToMeter))
        )

        return UnitCategory(
            id = categoryId,
            nameRes = R.string.category_length,
            group = CategoryGroup.EVERYDAY,
            units = listOf(
                length("nanometer", R.string.unit_length_nanometer, "nm", "0.000000001",
                    setOf("nanometre")),
                length("micrometer", R.string.unit_length_micrometer, "µm", "0.000001",
                    setOf("micrometre", "micron")),
                length("millimeter", R.string.unit_length_millimeter, "mm", "0.001",
                    setOf("millimetre")),
                length("centimeter", R.string.unit_length_centimeter, "cm", "0.01",
                    setOf("centimetre")),
                length("meter", R.string.unit_length_meter, "m", "1",
                    setOf("metre")),
                length("kilometer", R.string.unit_length_kilometer, "km", "1000",
                    setOf("kilometre")),
                // International inch: exactly 0.0254 m (1959 agreement),
                // from which foot/yard/mile below are exact multiples.
                length("inch", R.string.unit_length_inch, "in", "0.0254",
                    setOf("inches")),
                length("foot", R.string.unit_length_foot, "ft", "0.3048",
                    setOf("feet")),
                length("yard", R.string.unit_length_yard, "yd", "0.9144"),
                length("mile", R.string.unit_length_mile, "mi", "1609.344"),
                // International nautical mile: exactly 1852 m.
                length("nautical_mile", R.string.unit_length_nautical_mile, "nmi", "1852"),
                // IAU 2012: 1 au is exactly 149 597 870 700 m.
                length(
                    "astronomical_unit", R.string.unit_length_astronomical_unit,
                    "AU", "149597870700", setOf("au")
                ),
                // Exact speed of light x one Julian year (365.25 days):
                // 299 792 458 m/s x 31 557 600 s.
                length(
                    "light_year", R.string.unit_length_light_year,
                    "ly", "9460730472580800", setOf("light year")
                )
            ),
            defaultFromId = UnitId("kilometer"),
            defaultToId = UnitId("mile")
        )
    }

    //endregion

    //region Temperature — canonical base: Celsius.
    // base = (value + shift) * scale; each unit's domain floor is
    // absolute zero expressed in that unit.

    private fun temperatureCategory(): UnitCategory {
        val categoryId = CategoryId("temperature")
        val fiveNinths = BigDecimal(5).divide(BigDecimal(9), MathContext(50))

        fun temperature(
            id: String,
            nameRes: Int,
            symbol: String,
            scale: BigDecimal,
            shift: String,
            absoluteZero: String,
            aliases: Set<String> = emptySet()
        ) = UnitDefinition(
            id = UnitId(id),
            categoryId = categoryId,
            nameRes = nameRes,
            symbol = symbol,
            aliases = aliases,
            conversion = ConversionDefinition.Affine(scale, BigDecimal(shift)),
            domain = DomainRule.MinimumInclusive(BigDecimal(absoluteZero))
        )

        return UnitCategory(
            id = categoryId,
            nameRes = R.string.category_temperature,
            group = CategoryGroup.EVERYDAY,
            units = listOf(
                temperature(
                    "celsius", R.string.unit_temperature_celsius, "°C",
                    BigDecimal.ONE, "0", "-273.15"
                ),
                // (F - 32) * 5/9; absolute zero is exactly -459.67 F.
                temperature(
                    "fahrenheit", R.string.unit_temperature_fahrenheit, "°F",
                    fiveNinths, "-32", "-459.67"
                ),
                temperature(
                    "kelvin", R.string.unit_temperature_kelvin, "K",
                    BigDecimal.ONE, "-273.15", "0"
                ),
                // (R - 491.67) * 5/9, since 0 R = 0 K and degrees match F.
                temperature(
                    "rankine", R.string.unit_temperature_rankine, "°R",
                    fiveNinths, "-491.67", "0"
                )
            ),
            defaultFromId = UnitId("celsius"),
            defaultToId = UnitId("fahrenheit")
        )
    }

    //endregion

    //region Fuel economy — canonical base: liters per 100 km.
    // All inputs are strictly positive: zero has no reciprocal, and a
    // negative consumption is meaningless, so both are domain errors.

    private fun fuelEconomyCategory(): UnitCategory {
        val categoryId = CategoryId("fuel_economy")

        fun fuel(id: String, nameRes: Int, symbol: String, role: FuelRole) = UnitDefinition(
            id = UnitId(id),
            categoryId = categoryId,
            nameRes = nameRes,
            symbol = symbol,
            conversion = ConversionDefinition.Fuel(role),
            domain = DomainRule.Positive
        )

        return UnitCategory(
            id = categoryId,
            nameRes = R.string.category_fuel_economy,
            group = CategoryGroup.EVERYDAY,
            units = listOf(
                fuel(
                    "liters_per_100_kilometers",
                    R.string.unit_fuel_liters_per_100_kilometers,
                    "L/100 km",
                    FuelRole.LITERS_PER_100_KM
                ),
                fuel(
                    "kilometers_per_liter",
                    R.string.unit_fuel_kilometers_per_liter,
                    "km/L",
                    FuelRole.KILOMETERS_PER_LITER
                ),
                fuel(
                    "miles_per_us_gallon",
                    R.string.unit_fuel_miles_per_us_gallon,
                    "mpg (US)",
                    FuelRole.MILES_PER_US_GALLON
                ),
                fuel(
                    "miles_per_imperial_gallon",
                    R.string.unit_fuel_miles_per_imperial_gallon,
                    "mpg (Imp)",
                    FuelRole.MILES_PER_IMPERIAL_GALLON
                )
            ),
            defaultFromId = UnitId("liters_per_100_kilometers"),
            defaultToId = UnitId("kilometers_per_liter")
        )
    }

    //endregion

    //region Mass — canonical base: kilogram.
    // The inch-pound definitions are the international avoirdupois
    // definitions: 1 lb is exactly 0.45359237 kg and 1 oz is lb / 16.

    private fun massCategory(): UnitCategory {
        val categoryId = CategoryId("mass")

        fun mass(
            id: String,
            nameRes: Int,
            symbol: String,
            factorToKilogram: String,
            aliases: Set<String> = emptySet()
        ) = UnitDefinition(
            id = UnitId(id),
            categoryId = categoryId,
            nameRes = nameRes,
            symbol = symbol,
            aliases = aliases,
            conversion = ConversionDefinition.Linear(BigDecimal(factorToKilogram))
        )

        return UnitCategory(
            id = categoryId,
            nameRes = R.string.category_mass,
            group = CategoryGroup.EVERYDAY,
            units = listOf(
                mass("microgram", R.string.unit_mass_microgram, "µg", "0.000000001",
                    setOf("microgramme", "micrograms", "microgrammes", "mcg", "ug")),
                mass("milligram", R.string.unit_mass_milligram, "mg", "0.000001",
                    setOf("milligramme", "milligrams", "milligrammes")),
                mass("gram", R.string.unit_mass_gram, "g", "0.001",
                    setOf("gramme", "grams", "grammes")),
                mass("kilogram", R.string.unit_mass_kilogram, "kg", "1",
                    setOf("kilogramme", "kilograms", "kilogrammes", "kilo", "kilos")),
                mass("metric_tonne", R.string.unit_mass_metric_tonne, "t", "1000",
                    setOf("tonne métrique", "metric ton", "metric tonnes", "tonne", "tonnes")),
                mass("ounce", R.string.unit_mass_ounce, "oz", "0.028349523125",
                    setOf("once", "ounces", "onces", "avoirdupois ounce")),
                mass("pound", R.string.unit_mass_pound, "lb", "0.45359237",
                    setOf("livre", "pounds", "livres", "lbs", "avoirdupois pound"))
            ),
            defaultFromId = UnitId("kilogram"),
            defaultToId = UnitId("pound")
        )
    }

    //endregion

    //region Area — canonical base: square meter.
    // Square foot follows from the exact international foot. An acre is
    // exactly 43,560 square feet, hence 4,046.8564224 m² exactly.

    private fun areaCategory(): UnitCategory {
        val categoryId = CategoryId("area")

        fun area(
            id: String,
            nameRes: Int,
            symbol: String,
            factorToSquareMeter: String,
            aliases: Set<String> = emptySet()
        ) = UnitDefinition(
            id = UnitId(id),
            categoryId = categoryId,
            nameRes = nameRes,
            symbol = symbol,
            aliases = aliases,
            conversion = ConversionDefinition.Linear(BigDecimal(factorToSquareMeter))
        )

        return UnitCategory(
            id = categoryId,
            nameRes = R.string.category_area,
            group = CategoryGroup.EVERYDAY,
            units = listOf(
                area("square_millimeter", R.string.unit_area_square_millimeter, "mm²", "0.000001",
                    setOf("millimètre carré", "square millimetre", "mm2", "sq mm")),
                area("square_centimeter", R.string.unit_area_square_centimeter, "cm²", "0.0001",
                    setOf("centimètre carré", "square centimetre", "cm2", "sq cm")),
                area("square_meter", R.string.unit_area_square_meter, "m²", "1",
                    setOf("mètre carré", "square metre", "m2", "sq m")),
                area("hectare", R.string.unit_area_hectare, "ha", "10000",
                    setOf("hectares")),
                area("square_kilometer", R.string.unit_area_square_kilometer, "km²", "1000000",
                    setOf("kilomètre carré", "square kilometre", "km2", "sq km")),
                area("square_foot", R.string.unit_area_square_foot, "ft²", "0.09290304",
                    setOf("pied carré", "square feet", "pieds carrés", "ft2", "sq ft")),
                area("acre", R.string.unit_area_acre, "ac", "4046.8564224",
                    setOf("acres"))
            ),
            defaultFromId = UnitId("square_meter"),
            defaultToId = UnitId("square_foot")
        )
    }

    //endregion

    //region Volume — canonical base: liter.
    // Culinary convention: every spoon/cup below is US customary, not
    // metric and not the 240 mL US nutrition-label cup. Definitions are
    // chained exactly from 1 US gallon = 3.785411784 L:
    // cup = gallon / 16, tablespoon = gallon / 256,
    // teaspoon = tablespoon / 3. The imperial gallon is exactly
    // 4.54609 L. See README for the same convention in user-facing docs.

    private fun volumeCategory(): UnitCategory {
        val categoryId = CategoryId("volume")

        fun volume(
            id: String,
            nameRes: Int,
            symbol: String,
            factorToLiter: String,
            aliases: Set<String> = emptySet()
        ) = UnitDefinition(
            id = UnitId(id),
            categoryId = categoryId,
            nameRes = nameRes,
            symbol = symbol,
            aliases = aliases,
            conversion = ConversionDefinition.Linear(BigDecimal(factorToLiter))
        )

        return UnitCategory(
            id = categoryId,
            nameRes = R.string.category_volume,
            group = CategoryGroup.EVERYDAY,
            units = listOf(
                volume("milliliter", R.string.unit_volume_milliliter, "mL", "0.001",
                    setOf("millilitre", "milliliters", "millilitres", "ml")),
                volume("centiliter", R.string.unit_volume_centiliter, "cL", "0.01",
                    setOf("centilitre", "centiliters", "centilitres", "cl")),
                volume("liter", R.string.unit_volume_liter, "L", "1",
                    setOf("litre", "liters", "litres", "l")),
                volume("cubic_meter", R.string.unit_volume_cubic_meter, "m³", "1000",
                    setOf("mètre cube", "cubic metre", "m3")),
                volume("us_teaspoon", R.string.unit_volume_us_teaspoon, "tsp (US)", "0.00492892159375",
                    setOf("cuillère à café américaine", "us customary teaspoon", "teaspoon", "tsp")),
                volume("us_tablespoon", R.string.unit_volume_us_tablespoon, "tbsp (US)", "0.01478676478125",
                    setOf("cuillère à soupe américaine", "us customary tablespoon", "tablespoon", "tbsp")),
                volume("us_cup", R.string.unit_volume_us_cup, "cup (US)", "0.2365882365",
                    setOf("tasse américaine", "us customary cup", "cup", "cups", "tasses")),
                volume("us_gallon", R.string.unit_volume_us_gallon, "gal (US)", "3.785411784",
                    setOf("gallon américain", "us gallons", "gallons américains", "gal")),
                volume("imperial_gallon", R.string.unit_volume_imperial_gallon, "gal (Imp)", "4.54609",
                    setOf("gallon impérial", "imperial gallons", "gallons impériaux"))
            ),
            defaultFromId = UnitId("liter"),
            defaultToId = UnitId("us_gallon")
        )
    }

    //endregion

    //region Speed — canonical base: meter per second.
    // One mile per hour is exactly 0.44704 m/s. The km/h and knot ratios
    // do not terminate in decimal, so they are evaluated once below at
    // 50 significant digits (1 nmi = 1,852 m exactly; 1 h = 3,600 s).

    private fun speedCategory(): UnitCategory {
        val categoryId = CategoryId("speed")
        val secondsPerHour = BigDecimal("3600")
        val highPrecision = MathContext(50)

        fun speed(
            id: String,
            nameRes: Int,
            symbol: String,
            factorToMeterPerSecond: BigDecimal,
            aliases: Set<String> = emptySet()
        ) = UnitDefinition(
            id = UnitId(id),
            categoryId = categoryId,
            nameRes = nameRes,
            symbol = symbol,
            aliases = aliases,
            conversion = ConversionDefinition.Linear(factorToMeterPerSecond)
        )

        return UnitCategory(
            id = categoryId,
            nameRes = R.string.category_speed,
            group = CategoryGroup.EVERYDAY,
            units = listOf(
                speed("meter_per_second", R.string.unit_speed_meter_per_second, "m/s", BigDecimal.ONE,
                    setOf("mètre par seconde", "metre per second", "meters per second")),
                speed(
                    "kilometer_per_hour", R.string.unit_speed_kilometer_per_hour, "km/h",
                    BigDecimal("1000").divide(secondsPerHour, highPrecision),
                    setOf("kilomètre par heure", "kilometre per hour", "kilometers per hour", "kph")
                ),
                speed("mile_per_hour", R.string.unit_speed_mile_per_hour, "mph", BigDecimal("0.44704"),
                    setOf("mile par heure", "miles per hour", "miles par heure")),
                speed("foot_per_second", R.string.unit_speed_foot_per_second, "ft/s", BigDecimal("0.3048"),
                    setOf("pied par seconde", "feet per second", "pieds par seconde", "fps")),
                speed(
                    "knot", R.string.unit_speed_knot, "kn",
                    BigDecimal("1852").divide(secondsPerHour, highPrecision),
                    setOf("nœud", "noeud", "nœuds", "noeuds", "knots", "kt")
                )
            ),
            defaultFromId = UnitId("kilometer_per_hour"),
            defaultToId = UnitId("mile_per_hour")
        )
    }

    //endregion

    //region Time — canonical base: second.
    // Months and years are intentionally absent: their durations vary
    // by calendar convention. A day here is exactly 24 hours and a
    // week is exactly 7 days.

    private fun timeCategory(): UnitCategory {
        val categoryId = CategoryId("time")

        fun time(
            id: String,
            nameRes: Int,
            symbol: String,
            factorToSecond: String,
            aliases: Set<String> = emptySet()
        ) = UnitDefinition(
            id = UnitId(id),
            categoryId = categoryId,
            nameRes = nameRes,
            symbol = symbol,
            aliases = aliases,
            conversion = ConversionDefinition.Linear(BigDecimal(factorToSecond))
        )

        return UnitCategory(
            id = categoryId,
            nameRes = R.string.category_time,
            group = CategoryGroup.EVERYDAY,
            units = listOf(
                time("millisecond", R.string.unit_time_millisecond, "ms", "0.001",
                    setOf("milliseconde", "milliseconds", "millisecondes")),
                time("second", R.string.unit_time_second, "s", "1",
                    setOf("seconde", "seconds", "sec", "secondes")),
                time("minute", R.string.unit_time_minute, "min", "60",
                    setOf("minutes")),
                time("hour", R.string.unit_time_hour, "h", "3600",
                    setOf("heure", "hours", "heures", "hr")),
                time("day", R.string.unit_time_day, "d", "86400",
                    setOf("jour", "days", "jours")),
                time("week", R.string.unit_time_week, "wk", "604800",
                    setOf("semaine", "weeks", "semaines", "week"))
            ),
            defaultFromId = UnitId("hour"),
            defaultToId = UnitId("minute")
        )
    }

    //endregion

    //region Digital storage — canonical base: byte.
    // SI decimal prefixes are powers of 1,000 (kB, MB, GB, TB); IEC
    // binary prefixes are powers of 1,024 (KiB, MiB, GiB, TiB). A bit
    // is exactly one eighth of a byte. The two prefix families are kept
    // adjacent but distinctly named/symboled so they are never mixed.

    private fun dataStorageCategory(): UnitCategory {
        val categoryId = CategoryId("data_storage")

        fun storage(
            id: String,
            nameRes: Int,
            symbol: String,
            factorToByte: String,
            aliases: Set<String> = emptySet()
        ) = UnitDefinition(
            id = UnitId(id),
            categoryId = categoryId,
            nameRes = nameRes,
            symbol = symbol,
            aliases = aliases,
            conversion = ConversionDefinition.Linear(BigDecimal(factorToByte))
        )

        return UnitCategory(
            id = categoryId,
            nameRes = R.string.category_data_storage,
            group = CategoryGroup.COMPUTING,
            units = listOf(
                storage("bit", R.string.unit_data_storage_bit, "bit", "0.125",
                    setOf("bits")),
                storage("byte", R.string.unit_data_storage_byte, "B", "1",
                    setOf("octet", "bytes", "octets", "o")),
                storage("kilobyte", R.string.unit_data_storage_kilobyte, "kB", "1000",
                    setOf("kilooctet", "kilobytes", "kilooctets", "ko", "KB")),
                storage("megabyte", R.string.unit_data_storage_megabyte, "MB", "1000000",
                    setOf("mégaoctet", "megabytes", "mégaoctets", "mo")),
                storage("gigabyte", R.string.unit_data_storage_gigabyte, "GB", "1000000000",
                    setOf("gigaoctet", "gigabytes", "gigaoctets", "go")),
                storage("terabyte", R.string.unit_data_storage_terabyte, "TB", "1000000000000",
                    setOf("téraoctet", "terabytes", "téraoctets", "to")),
                storage("kibibyte", R.string.unit_data_storage_kibibyte, "KiB", "1024",
                    setOf("kibioctet", "kibibytes", "kibioctets", "kio")),
                storage("mebibyte", R.string.unit_data_storage_mebibyte, "MiB", "1048576",
                    setOf("mébioctet", "mebibytes", "mébioctets", "mio")),
                storage("gibibyte", R.string.unit_data_storage_gibibyte, "GiB", "1073741824",
                    setOf("gibioctet", "gibibytes", "gibioctets", "gio")),
                storage("tebibyte", R.string.unit_data_storage_tebibyte, "TiB", "1099511627776",
                    setOf("tébioctet", "tebibytes", "tébioctets", "tio"))
            ),
            defaultFromId = UnitId("megabyte"),
            defaultToId = UnitId("mebibyte")
        )
    }

    //endregion

    //region Data rate — canonical base: bit per second.
    // SI decimal prefixes only: kbit/s, Mbit/s and Gbit/s are powers of
    // 1,000 bits per second. Byte rates use uppercase B and are exactly
    // eight times the corresponding bit rates.

    private fun dataRateCategory(): UnitCategory {
        val categoryId = CategoryId("data_rate")

        fun rate(
            id: String,
            nameRes: Int,
            symbol: String,
            factorToBitPerSecond: String,
            aliases: Set<String> = emptySet()
        ) = UnitDefinition(
            id = UnitId(id),
            categoryId = categoryId,
            nameRes = nameRes,
            symbol = symbol,
            aliases = aliases,
            conversion = ConversionDefinition.Linear(BigDecimal(factorToBitPerSecond))
        )

        return UnitCategory(
            id = categoryId,
            nameRes = R.string.category_data_rate,
            group = CategoryGroup.COMPUTING,
            units = listOf(
                rate("bit_per_second", R.string.unit_data_rate_bit_per_second, "bit/s", "1",
                    setOf("bit par seconde", "bits per second", "bits par seconde", "bps")),
                rate("kilobit_per_second", R.string.unit_data_rate_kilobit_per_second, "kbit/s", "1000",
                    setOf("kilobit par seconde", "kilobits per second", "kbps")),
                rate("megabit_per_second", R.string.unit_data_rate_megabit_per_second, "Mbit/s", "1000000",
                    setOf("mégabit par seconde", "megabits per second", "mbps")),
                rate("gigabit_per_second", R.string.unit_data_rate_gigabit_per_second, "Gbit/s", "1000000000",
                    setOf("gigabit par seconde", "gigabits per second", "gbps")),
                rate("byte_per_second", R.string.unit_data_rate_byte_per_second, "B/s", "8",
                    setOf("octet par seconde", "bytes per second", "octets par seconde", "o/s")),
                rate("kilobyte_per_second", R.string.unit_data_rate_kilobyte_per_second, "kB/s", "8000",
                    setOf("kilooctet par seconde", "kilobytes per second", "kilooctets par seconde", "ko/s")),
                rate("megabyte_per_second", R.string.unit_data_rate_megabyte_per_second, "MB/s", "8000000",
                    setOf("mégaoctet par seconde", "megabytes per second", "mégaoctets par seconde", "mo/s")),
                rate("gigabyte_per_second", R.string.unit_data_rate_gigabyte_per_second, "GB/s", "8000000000",
                    setOf("gigaoctet par seconde", "gigabytes per second", "gigaoctets par seconde", "go/s"))
            ),
            defaultFromId = UnitId("megabit_per_second"),
            defaultToId = UnitId("megabyte_per_second")
        )
    }

    //endregion

    //region Pressure — canonical base: pascal.
    // 1 bar is exactly 100,000 Pa and one standard atmosphere is
    // exactly 101,325 Pa. One psi is the exact pound-force below
    // divided by one exact square inch (0.00064516 m²); that quotient
    // does not have a short decimal form, so it is materialized at 50
    // significant digits. The conventional millimeter of mercury is
    // 13,595.1 kg/m³ × 9.80665 m/s² × 0.001 m = 133.322387415 Pa; this
    // is intentionally not silently identified with the torr
    // (101,325/760 Pa), which differs by about 1.4e-7 relative.

    private fun pressureCategory(): UnitCategory {
        val categoryId = CategoryId("pressure")
        val highPrecision = MathContext(50)
        val poundForceInNewtons = BigDecimal("4.4482216152605")
        val squareInchInSquareMeters = BigDecimal("0.00064516")

        fun pressure(
            id: String,
            nameRes: Int,
            symbol: String,
            factorToPascal: BigDecimal,
            aliases: Set<String> = emptySet()
        ) = UnitDefinition(
            id = UnitId(id),
            categoryId = categoryId,
            nameRes = nameRes,
            symbol = symbol,
            aliases = aliases,
            conversion = ConversionDefinition.Linear(factorToPascal)
        )

        return UnitCategory(
            id = categoryId,
            nameRes = R.string.category_pressure,
            group = CategoryGroup.SCIENCE_ENGINEERING,
            units = listOf(
                pressure("pascal", R.string.unit_pressure_pascal, "Pa", BigDecimal.ONE,
                    setOf("pascals")),
                pressure("kilopascal", R.string.unit_pressure_kilopascal, "kPa", BigDecimal("1000"),
                    setOf("kilopascals")),
                pressure("megapascal", R.string.unit_pressure_megapascal, "MPa", BigDecimal("1000000"),
                    setOf("mégapascal", "megapascals", "mégapascals", "N/mm²", "N/mm2")),
                pressure("bar", R.string.unit_pressure_bar, "bar", BigDecimal("100000"),
                    setOf("bars")),
                pressure("millibar", R.string.unit_pressure_millibar, "mbar", BigDecimal("100"),
                    setOf("millibars", "hectopascal", "hectopascals", "hPa")),
                pressure(
                    "standard_atmosphere", R.string.unit_pressure_standard_atmosphere, "atm",
                    BigDecimal("101325"),
                    setOf("atmosphère standard", "atmosphere", "atmosphères", "atmospheres")
                ),
                pressure(
                    "pound_per_square_inch", R.string.unit_pressure_pound_per_square_inch, "psi",
                    poundForceInNewtons.divide(squareInchInSquareMeters, highPrecision),
                    setOf(
                        "livre-force par pouce carré", "pound-force per square inch",
                        "livre par pouce carré", "pounds per square inch",
                        "livres-force par pouce carré", "lbf/in²", "lbf/in2"
                    )
                ),
                pressure(
                    "millimeter_of_mercury", R.string.unit_pressure_millimeter_of_mercury, "mmHg",
                    BigDecimal("133.322387415"),
                    setOf(
                        "millimètre de mercure", "millimeters of mercury",
                        "millimètres de mercure", "mm Hg"
                    )
                )
            ),
            defaultFromId = UnitId("kilopascal"),
            defaultToId = UnitId("pound_per_square_inch")
        )
    }

    //endregion

    //region Energy — canonical base: joule.
    // The calorie used here is the thermochemical calorie, exactly
    // 4.184 J (not the International Table calorie of 4.1868 J); the
    // kilocalorie is therefore exactly 4,184 J. One watt-hour is one
    // watt for 3,600 seconds. One electronvolt is exactly the SI fixed
    // elementary charge expressed in joules, 1.602176634e-19 J.

    private fun energyCategory(): UnitCategory {
        val categoryId = CategoryId("energy")

        fun energy(
            id: String,
            nameRes: Int,
            symbol: String,
            factorToJoule: String,
            aliases: Set<String> = emptySet()
        ) = UnitDefinition(
            id = UnitId(id),
            categoryId = categoryId,
            nameRes = nameRes,
            symbol = symbol,
            aliases = aliases,
            conversion = ConversionDefinition.Linear(BigDecimal(factorToJoule))
        )

        return UnitCategory(
            id = categoryId,
            nameRes = R.string.category_energy,
            group = CategoryGroup.SCIENCE_ENGINEERING,
            units = listOf(
                energy("joule", R.string.unit_energy_joule, "J", "1",
                    setOf("joules")),
                energy("kilojoule", R.string.unit_energy_kilojoule, "kJ", "1000",
                    setOf("kilojoules")),
                energy("calorie", R.string.unit_energy_calorie, "cal", "4.184",
                    setOf("calories", "thermochemical calorie", "calorie thermochimique")),
                energy("kilocalorie", R.string.unit_energy_kilocalorie, "kcal", "4184",
                    setOf("kilocalories", "grande calorie", "Calorie")),
                energy("watt_hour", R.string.unit_energy_watt_hour, "Wh", "3600",
                    setOf("watt-heure", "watt hours", "watt-heures", "W·h", "W h")),
                energy("kilowatt_hour", R.string.unit_energy_kilowatt_hour, "kWh", "3600000",
                    setOf("kilowatt-heure", "kilowatt hours", "kilowatt-heures", "kW·h", "kW h")),
                energy(
                    "electronvolt", R.string.unit_energy_electronvolt, "eV",
                    "0.0000000000000000001602176634",
                    setOf("électronvolt", "electron volt", "électron-volts", "electronvolts")
                )
            ),
            defaultFromId = UnitId("kilojoule"),
            defaultToId = UnitId("kilocalorie")
        )
    }

    //endregion

    //region Power — canonical base: watt.
    // Two horsepower conventions are intentionally separate units:
    // mechanical/imperial horsepower is exactly 550 ft·lbf/s =
    // 745.69987158227022 W using the exact international foot and
    // pound-force; metric horsepower is exactly 75 kgf·m/s =
    // 735.49875 W under standard gravity. Their names and symbols
    // (hp versus PS) are never treated as interchangeable.

    private fun powerCategory(): UnitCategory {
        val categoryId = CategoryId("power")
        val mechanicalHorsepower = BigDecimal("550")
            .multiply(BigDecimal("0.3048"))
            .multiply(BigDecimal("4.4482216152605"))

        fun power(
            id: String,
            nameRes: Int,
            symbol: String,
            factorToWatt: BigDecimal,
            aliases: Set<String> = emptySet()
        ) = UnitDefinition(
            id = UnitId(id),
            categoryId = categoryId,
            nameRes = nameRes,
            symbol = symbol,
            aliases = aliases,
            conversion = ConversionDefinition.Linear(factorToWatt)
        )

        return UnitCategory(
            id = categoryId,
            nameRes = R.string.category_power,
            group = CategoryGroup.SCIENCE_ENGINEERING,
            units = listOf(
                power("watt", R.string.unit_power_watt, "W", BigDecimal.ONE,
                    setOf("watts", "joule per second", "joule par seconde", "J/s")),
                power("kilowatt", R.string.unit_power_kilowatt, "kW", BigDecimal("1000"),
                    setOf("kilowatts")),
                power("megawatt", R.string.unit_power_megawatt, "MW", BigDecimal("1000000"),
                    setOf("mégawatt", "megawatts", "mégawatts")),
                power(
                    "mechanical_horsepower", R.string.unit_power_mechanical_horsepower, "hp",
                    mechanicalHorsepower,
                    setOf(
                        "cheval-vapeur mécanique", "imperial horsepower",
                        "horsepower", "chevaux-vapeur mécaniques"
                    )
                ),
                power(
                    "metric_horsepower", R.string.unit_power_metric_horsepower, "PS",
                    BigDecimal("735.49875"),
                    setOf(
                        "cheval-vapeur métrique", "metric horsepower", "cheval-vapeur",
                        "chevaux-vapeur métriques", "ch", "CV"
                    )
                )
            ),
            defaultFromId = UnitId("kilowatt"),
            defaultToId = UnitId("mechanical_horsepower")
        )
    }

    //endregion

    //region Force — canonical base: Newton.
    // One pound-force is one international avoirdupois pound times
    // standard gravity: 0.45359237 kg × 9.80665 m/s² =
    // 4.4482216152605 N exactly. One dyne is exactly 1e-5 N. Force is
    // allowed to be negative because a signed component along an axis
    // is meaningful even though its magnitude is not.

    private fun forceCategory(): UnitCategory {
        val categoryId = CategoryId("force")

        fun force(
            id: String,
            nameRes: Int,
            symbol: String,
            factorToNewton: String,
            aliases: Set<String> = emptySet()
        ) = UnitDefinition(
            id = UnitId(id),
            categoryId = categoryId,
            nameRes = nameRes,
            symbol = symbol,
            aliases = aliases,
            conversion = ConversionDefinition.Linear(BigDecimal(factorToNewton)),
            domain = DomainRule.AnyReal
        )

        return UnitCategory(
            id = categoryId,
            nameRes = R.string.category_force,
            group = CategoryGroup.SCIENCE_ENGINEERING,
            units = listOf(
                force("newton", R.string.unit_force_newton, "N", "1",
                    setOf("Newtons")),
                force("kilonewton", R.string.unit_force_kilonewton, "kN", "1000",
                    setOf("kilonewtons")),
                force("pound_force", R.string.unit_force_pound_force, "lbf", "4.4482216152605",
                    setOf("livre-force", "pounds-force", "livres-force", "lb-force")),
                force("dyne", R.string.unit_force_dyne, "dyn", "0.00001",
                    setOf("dynes"))
            ),
            defaultFromId = UnitId("newton"),
            defaultToId = UnitId("pound_force")
        )
    }

    //endregion

    //region Frequency — canonical base: hertz.
    // Hertz is cycles/occurrences per second. Revolutions per minute
    // is included strictly as a rotational frequency (one complete
    // revolution per cycle), so 1 rpm is exactly 1/60 Hz and 60 rpm is
    // exactly 1 Hz. It is not an angular velocity in radians/second;
    // 60 rpm would be 2π rad/s, a different quantity that would need
    // a separate category/unit rather than a silent 2π factor here.

    private fun frequencyCategory(): UnitCategory {
        val categoryId = CategoryId("frequency")
        val highPrecision = MathContext(50)

        fun frequency(
            id: String,
            nameRes: Int,
            symbol: String,
            factorToHertz: BigDecimal,
            aliases: Set<String> = emptySet()
        ) = UnitDefinition(
            id = UnitId(id),
            categoryId = categoryId,
            nameRes = nameRes,
            symbol = symbol,
            aliases = aliases,
            conversion = ConversionDefinition.Linear(factorToHertz)
        )

        return UnitCategory(
            id = categoryId,
            nameRes = R.string.category_frequency,
            group = CategoryGroup.SCIENCE_ENGINEERING,
            units = listOf(
                frequency("hertz", R.string.unit_frequency_hertz, "Hz", BigDecimal.ONE,
                    setOf("cycle par seconde", "cycles per second", "cycles par seconde", "1/s")),
                frequency("kilohertz", R.string.unit_frequency_kilohertz, "kHz", BigDecimal("1000"),
                    setOf("kilohertz")),
                frequency("megahertz", R.string.unit_frequency_megahertz, "MHz", BigDecimal("1000000"),
                    setOf("mégahertz", "megahertz")),
                frequency("gigahertz", R.string.unit_frequency_gigahertz, "GHz", BigDecimal("1000000000"),
                    setOf("gigahertz")),
                frequency(
                    "revolution_per_minute", R.string.unit_frequency_revolution_per_minute, "rpm",
                    BigDecimal.ONE.divide(BigDecimal("60"), highPrecision),
                    setOf(
                        "tour par minute", "revolutions per minute", "tours par minute",
                        "tr/min", "rev/min", "r/min"
                    )
                )
            ),
            defaultFromId = UnitId("hertz"),
            defaultToId = UnitId("revolution_per_minute")
        )
    }

    //endregion

    //region Angle — canonical base: degree.
    // The degree is used as the internal canonical unit so that the
    // exact civil relations stay exact: 1 turn = 360° and
    // 1 gon = 0.9° (400 gon per turn). The radian necessarily involves
    // π: its factor is 180/π, materialized at 50 significant digits.
    // Angles accept negative values because orientation is signed.

    private fun angleCategory(): UnitCategory {
        val categoryId = CategoryId("angle")
        val highPrecision = MathContext(50)
        val pi = BigDecimal(
            "3.14159265358979323846264338327950288419716939937510"
        )

        fun angle(
            id: String,
            nameRes: Int,
            symbol: String,
            factorToDegree: BigDecimal,
            aliases: Set<String> = emptySet()
        ) = UnitDefinition(
            id = UnitId(id),
            categoryId = categoryId,
            nameRes = nameRes,
            symbol = symbol,
            aliases = aliases,
            conversion = ConversionDefinition.Linear(factorToDegree),
            domain = DomainRule.AnyReal
        )

        return UnitCategory(
            id = categoryId,
            nameRes = R.string.category_angle,
            group = CategoryGroup.SCIENCE_ENGINEERING,
            units = listOf(
                angle("degree", R.string.unit_angle_degree, "°", BigDecimal.ONE,
                    setOf("degré", "degrees", "degrés", "deg")),
                angle(
                    "radian", R.string.unit_angle_radian, "rad",
                    BigDecimal("180").divide(pi, highPrecision),
                    setOf("radians")
                ),
                angle("gradian", R.string.unit_angle_gradian, "gon", BigDecimal("0.9"),
                    setOf("grade", "gradians", "grades", "grad")),
                angle("turn", R.string.unit_angle_turn, "rev", BigDecimal("360"),
                    setOf("tour", "revolution", "tours", "revolutions", "tr"))
            ),
            defaultFromId = UnitId("degree"),
            defaultToId = UnitId("radian")
        )
    }

    //endregion

    //region Torque — canonical base: Newton meter.
    // Torque is deliberately a category separate from Energy even
    // though N·m and J are dimensionally equivalent: they do not
    // measure the same physical quantity, so the catalog never offers
    // a silent joule ↔ Newton-meter conversion. One lbf·ft uses the
    // exact pound-force and international foot; lbf·in is one twelfth
    // of it. Torque accepts negative values for signed direction.

    private fun torqueCategory(): UnitCategory {
        val categoryId = CategoryId("torque")
        val poundForceFoot = BigDecimal("4.4482216152605")
            .multiply(BigDecimal("0.3048"))
        val poundForceInch = poundForceFoot.divide(BigDecimal("12"))

        fun torque(
            id: String,
            nameRes: Int,
            symbol: String,
            factorToNewtonMeter: BigDecimal,
            aliases: Set<String> = emptySet()
        ) = UnitDefinition(
            id = UnitId(id),
            categoryId = categoryId,
            nameRes = nameRes,
            symbol = symbol,
            aliases = aliases,
            conversion = ConversionDefinition.Linear(factorToNewtonMeter),
            domain = DomainRule.AnyReal
        )

        return UnitCategory(
            id = categoryId,
            nameRes = R.string.category_torque,
            group = CategoryGroup.SCIENCE_ENGINEERING,
            units = listOf(
                torque("newton_meter", R.string.unit_torque_newton_meter, "N·m", BigDecimal.ONE,
                    setOf("newton-mètre", "Newton metre", "newton-mètres", "Nm")),
                torque(
                    "kilonewton_meter", R.string.unit_torque_kilonewton_meter, "kN·m",
                    BigDecimal("1000"),
                    setOf("kilonewton-mètre", "kilonewton metre", "kilonewton-mètres", "kNm")
                ),
                torque(
                    "pound_force_foot", R.string.unit_torque_pound_force_foot, "lbf·ft",
                    poundForceFoot,
                    setOf(
                        "livre-force pied", "pound-force feet", "livres-force pied",
                        "lb-ft", "ft-lb", "ft·lbf"
                    )
                ),
                torque(
                    "pound_force_inch", R.string.unit_torque_pound_force_inch, "lbf·in",
                    poundForceInch,
                    setOf(
                        "livre-force pouce", "pound-force inches", "livres-force pouce",
                        "lb-in", "in-lb", "in·lbf"
                    )
                )
            ),
            defaultFromId = UnitId("newton_meter"),
            defaultToId = UnitId("pound_force_foot")
        )
    }

    //endregion
}
