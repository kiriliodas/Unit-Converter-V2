# Unit Converter V2.0.0

A native Android unit converter built with Kotlin, Jetpack Compose, and
Material 3. This repository contains the source code for version 2.0.0.

- **Application ID:** `com.kirilliodas.unitconverter`
- **Version:** `2.0.0` (version code `1`)
- **Minimum Android version:** Android 8.0 (API 26)
- **Target SDK:** 36
- **Build system:** Gradle 8.13 with the Gradle Wrapper

The app provides offline unit conversion, a searchable unit catalog,
favorites, pinned conversions, history, configurable precision, and
display settings. The catalog currently contains 17 categories and
110 units.

## Application status

Version 2.0.0 has been built and validated on Windows. Unit tests,
Android lint, debug packaging, and release packaging completed
successfully. The release APK was installed and tested on a Galaxy A23 5G.

The repository includes the Gradle Wrapper and its required files.
Release signing uses a developer-owned keystore that must remain outside
version control.
| Category | Units | Canonical base |
|---|---:|---|
| Length | 13 | meter |
| Temperature | 4 | degree Celsius |
| Fuel economy | 4 | liters per 100 km |
| Mass | 7 | kilogram |
| Area | 7 | square meter |
| Volume | 9 | liter |
| Speed | 5 | meter per second |
| Time | 6 | second |
| Digital storage | 10 | byte |
| Data rate | 8 | bit per second |
| Pressure | 8 | pascal |
| Energy | 7 | joule |
| Power | 5 | watt |
| Force | 4 | Newton |
| Frequency | 5 | hertz |
| Angle | 4 | degree |
| Torque | 4 | Newton meter |

### Phase C8 interface

- Four bottom destinations—Convert, Units, Favorites, and History—plus
  a full-screen Settings route opened only from Convert. A saveable
  `MainNavigationState` enum holder is now the only destination source;
  each bottom-bar click calls that stable holder and reads the live
  destination at click time. This replaces C6's saveable string, derived
  enum, and captured reselection comparison. JVM regression tests cover
  every ordered destination pair, repeated current-destination clicks,
  requested route sequences, and rapid cycling through all four tabs.
- The bar itself remains a native Compose floating capsule with
  side/bottom breathing room, system navigation-bar padding, a
  translucent monochrome surface, border, shadow, and tab semantics.
  C8 replaces four separately fading item backgrounds with exactly one
  shared ink pill that slides horizontally (220 ms,
  FastOutSlowIn) between the equal-width destinations, including in
  RTL. Its animated offset is read in the layout phase, so the pill can
  retarget during rapid taps without recomposing the capsule for every
  animation frame. Scaffold still measures the whole floating slot.
- Convert no longer shows the horizontal category-chip strip under its
  header. Header, optional pinned conversions, input panel,
  precision/swap seam, high-contrast result slab, and complete
  conversions list remain. Category/unit selection remains available
  through the destination Unit picker sheets and Units. The
  validated sign toggle, copy-only history rule, Use-as-input
  formatting, swap semantics, and equivalents-of-one list behavior are
  unchanged.
- Units category cards, shared unit tiles, and search rows reserve
  font-scale-aware symbol/title/badge zones, giving consistent card
  heights and aligned baselines without a fixed height that clips
  larger text. The Settings entry uses the filled local Material
  Symbols Rounded gear instead of the thinner outlined gear; no icon
  dependency was added.
- Targeted performance work keeps IME inset reads inside the small
  bottom-bar host instead of invalidating the converter root, disables
  redundant full-screen `AnimatedContent` size transforms, caches
  history-row evaluation and repeated search/pin derivations, reuses
  an already-computed destination conversion inside the evaluator, and
  stabilizes formatter/bottom-destination allocations. The successful
  C7 animations and lazy-list structure remain.
- The converter unit picker no longer requests focus or opens the
  keyboard on entry. Its picker-only compact tiles are about 80 dp
  high instead of 130 dp, in an adaptive grid (two columns on ordinary
  phones), while reserving two name lines and retaining the current
  ink/check state and accessible role description. The Units browser
  keeps its fuller badge tiles.
- The Convert seam uses independent overlays: the precision pill lives
  in the start region and the 48 dp swap button is aligned to the seam
  end itself, so changing Smart/Fixed/Scientific cannot move swap. The
  pill may wrap to three lines at large font sizes instead of truncating.
- Favorite hearts and pin toggles no longer draw circular containers
  behind their glyphs. The active heart remains red; the active pin is
  a restrained blue (darker blue on light panels and a brighter blue
  on dark panels/result slab) with filled/outlined state glyphs and
  the same 48 dp targets/animations.
- Settings now contains persisted **System / Light / Dark** theme
  choices and a language picker. Language defaults to the phone
  language and can be set independently to French, English, Spanish,
  Portuguese, German, Italian, Simplified Chinese, Japanese, Korean,
  or Arabic. Each offered language has a complete offline Android
  `strings.xml` (229 strings plus the units plural); Arabic uses the
  existing RTL-capable layouts and auto-mirrored back arrow. A discreet
  localized “Created by Blood” signature closes Settings only.
- Both manual themes retain the C7 neutral ramp and dynamic color
  stays disabled. Material Symbols Rounded glyphs are bundled as local
  vector drawables (Google, Apache License 2.0), and typography uses
  the system Roboto-family fallback rather than adding a font asset.

### Favorites, pinned conversions, and history

- A favorite is one directed source-to-target unit pair in a category.
  The red-filled heart on the result slab toggles the current pair.
  Favorites are deduplicated and shown newest first; selecting one
  restores its category and units while preserving the current input.
- A pinned conversion is a favorite selected for quick access. The
  blue-when-active pin beside the heart pins/unpins the current pair
  (pinning also favorites it when necessary), and Favorites/History rows
  expose the same heart/pin toggles. Pinned pairs are deduplicated,
  shown newest-pinned first as symbol chips on Convert, and restore only
  their category/units—the current numeric input remains editable.
  Removing a favorite also removes its pin.
- History follows the established copy-to-save rule: only explicitly
  copying a valid result records its category, units, input, and
  timestamp. Typing, invalid states, recompositions, and restores do
  not create entries. History is newest first, deduplicated by the
  full conversion and input, and bounded to 50 entries.
- Favorites and History are separate bottom-navigation destinations;
  History is grouped only for presentation into Today, Yesterday, and
  Earlier. Restoring history also restores its saved input. History can
  be cleared only after a localized confirmation; clearing history does
  not delete favorites, pins, or display settings.
- Persistence uses app-private `SharedPreferences` strings behind
  small pure-Kotlin serializer/store abstractions. Library reads run on
  `Dispatchers.IO`; writes are serialized through the same store and
  use Android's asynchronous preference apply. Theme/language use a
  separate `app_preferences_v1` key in that same private file, loaded
  by `MainActivity` before composition for locale/theme application;
  this intentionally does not alter the library payload. No dependency
  was added, and no data leaves the device. The Phase C4 `UC_LIBRARY_V1`
  header and `F`/`H` records are unchanged; Phase C5 adds optional
  `P` precision and `N` pin records. A C4 payload therefore loads with
  all favorites/history preserved, default Smart precision, and no
  pins. Malformed optional records are ignored without discarding the
  library.

### Display precision and swap

- **Smart** keeps the established presentation: at most 10 significant
  figures, insignificant trailing zeros removed, and automatic
  scientific notation for very large/small magnitudes.
- **Fixed** displays exactly the selected number of decimal places,
  from 0 through 12, including trailing zeros.
- **Scientific** always uses normalized scientific notation and keeps
  exactly the selected 0–12 digits after the decimal separator in the
  mantissa. Actual negative zero is displayed without a sign, while a
  genuinely non-zero `-1E-10` remains `-1E-10` in Smart mode.
- All display rounding is HALF_UP and occurs only while formatting;
  precision settings never alter the conversion engine's internal
  `BigDecimal`, a stored history input, or a saved unit pair.
- The precision pill on Convert opens these same settings in a bottom
  sheet and applies changes immediately. This is only a relocation of
  the Phase C5 controls.
- The existing swap action exchanges only source and target units,
  preserves category and input, and recalculates immediately. The
  reversed pair remains a distinct directed favorite/pin.

### Phase C3 conversion conventions

- **Mass:** pound and ounce are international avoirdupois units
  (1 lb = 0.45359237 kg exactly; 1 oz = 1/16 lb).
- **Area:** 1 ft² = 0.09290304 m² exactly; 1 acre = 43,560 ft² =
  4,046.8564224 m² exactly; 1 ha = 10,000 m².
- **Volume / culinary:** Teaspoon, tablespoon, and cup are explicitly
  **US customary**. They are neither metric spoons nor the 240 mL US
  nutrition-label cup. From 1 US gallon = 3.785411784 L exactly:
  1 US cup = 236.5882365 mL, 1 US tablespoon = 14.78676478125 mL, and
  1 US Teaspoon = 4.92892159375 mL. 1 imperial gallon = 4.54609 L.
- **Speed:** 1 mph = 0.44704 m/s exactly; 1 knot is one international
  nautical mile (1,852 m exactly) per hour.
- **Time:** a day is exactly 24 hours and a week exactly 7 days.
  Months and years are deliberately absent because a fixed factor
  would hide a calendar convention.
- **Digital storage:** SI decimal prefixes are powers of 1,000
  (kB, MB, GB, TB); IEC binary prefixes are powers of 1,024
  (KiB, MiB, GiB, TiB). 1 byte = 8 bits.
- **Data rate:** SI decimal prefixes only for bit/s and B/s families;
  byte rates are exactly eight times the corresponding bit rates.
  Symbols keep bits (`bit/s`, `Mbit/s`) distinct from bytes
  (`B/s`, `MB/s`).

All seven Phase C3 categories are linear and non-negative.

### Phase C5 scientific conversion conventions

- **Pressure:** canonical pascal. 1 bar = 100,000 Pa and
  1 standard atmosphere = 101,325 Pa exactly. 1 psi uses the exact
  pound-force and square inch, i.e.
  4.4482216152605 N / 0.00064516 m², materialized at 50 significant
  digits. The conventional millimeter of mercury is
  133.322387415 Pa (`13,595.1 kg/m³ × 9.80665 m/s² × 0.001 m`); it is
  not silently conflated with the torr (`101,325/760 Pa`).
- **Energy:** canonical joule. The calorie is the **thermochemical**
  calorie, exactly 4.184 J, so 1 kcal = 4,184 J. 1 Wh = 3,600 J,
  1 kWh = 3,600,000 J, and 1 eV = 1.602176634×10⁻¹⁹ J exactly.
- **Power:** canonical watt. Mechanical/imperial horsepower (`hp`) is
  exactly 550 ft·lbf/s = 745.69987158227022 W. Metric horsepower
  (`PS`) is a separate unit, exactly 735.49875 W.
- **Force:** canonical Newton. 1 lbf = 4.4482216152605 N exactly and
  1 dyn = 10⁻⁵ N. Signed values are accepted for directed force
  components.
- **Frequency:** canonical hertz. `rpm` is included strictly as a
  rotational frequency: 1 rpm = 1/60 Hz and 60 rpm = 1 Hz. It is not
  angular velocity in rad/s (60 rpm would be 2π rad/s).
- **Angle:** canonical degree, preserving exact 1 turn = 360° and
  1 gon = 0.9°. The radian factor is 180/π degrees at 50 significant
  digits. Signed angles are accepted.
- **Torque:** canonical Newton meter. 1 lbf·ft =
  1.3558179483314004 N·m and 1 lbf·in = 0.1129848290276167 N·m.
  Signed values are accepted. Torque remains deliberately separate
  from Energy: joules and Newton meters are dimensionally equivalent
  but do not measure the same physical quantity, so the catalog never
  silently converts one into the other.

The conversion engine and parser are covered by JVM unit tests. The
formatter includes an explicit Scientific precision mode while
preserving the established Smart/Fixed behavior and the `-1E-10`
contract. The implementation adds no new dependency. The current test
suite contains 153 JVM tests, covering conversion behavior, number
formatting and parsing, catalog integrity, preferences, navigation,
search, and the conversion library. Existing inventory assertions were
extended for the current catalog; no test or assertion was deleted to
make a failing behavior pass.

## Scope and future work

The current release contains the 17 categories and 110 units documented
above. Future catalog additions should be based on verified conversion
definitions and covered by tests.
## Application identity and signing

The release application ID is `com.kirilliodas.unitconverter`.
The Kotlin namespace remains `app.unitconverter`.

Release signing is configured in `app/build.gradle.kts`. The keystore
is stored outside this repository. Passwords are supplied through the
environment variables `UNIT_CONVERTER_STORE_PASSWORD` and
`UNIT_CONVERTER_KEY_PASSWORD`. The optional
`UNIT_CONVERTER_KEYSTORE` variable selects a custom keystore path.

Never commit a keystore, signing password, or local machine configuration.

## Build prerequisites

- Windows with a compatible Android SDK installed
- JDK 17
- Android SDK Platform 36 and the required build tools
- Network access on the first build so Gradle can download dependencies

The Gradle Wrapper is included in this repository and pins Gradle 8.13.
Do not replace the pinned Android Gradle Plugin, Kotlin, or Compose
versions without testing the resulting toolchain.

### Build commands

Run these commands from the repository root:

```powershell
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:assembleDebug
```

To build the signed release APK, configure the three signing environment
variables described above, then run:

```powershell
.\gradlew.bat :app:assembleRelease
```

The debug APK is generated at
`app/build/outputs/apk/debug/app-debug.apk`.

The release APK is generated at
`app/build/outputs/apk/release/app-release.apk`.

Generated build outputs, local APKs, and signing files are excluded from
version control. Publish distributable APKs as assets of a GitHub Release.
### Regression checklist for future releases

Do not accept offered AGP/Gradle/Kotlin/Compose upgrades: validate the
pinned matrix as exported. The targeted lint exception already in
`app/build.gradle.kts` (`disable += "PropertyEscape"`, for the Windows
`local.properties` reading issue) stays as is; lint itself remains fully
enabled with `abortOnError = true`.

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:assembleDebug
```

- Tests: expect 153 JVM unit tests. Update this count when tests are
  added or removed. Reports are generated in
  `app/build/reports/tests/testDebugUnitTest/`.
- APK: `app/build/outputs/apk/debug/app-debug.apk`, signed with the
  standard debug key provided by the SDK tools.
- Manual check on a Galaxy A23 5G: convert in all 17 categories; test
  Smart, Fixed at 0/2/12 places, and Scientific for large, tiny,
  negative, and negative-zero results from both the Convert precision
  pill and Settings; restart to confirm precision persists; swap units
  and confirm input/category stay unchanged; exercise every ordered
  pair among all four bottom destinations, repeatedly tap the current
  tab, rapidly cycle through all four tabs, and verify Android Back;
  browse/search every Units
  category, apply units as source/destination, and check the `B`, `bit`,
  `km`, and `KM` searches; heart/pin current pairs, use Convert pinned
  chips, toggle from Favorites/History, restart to confirm persistence,
  remove a pinned favorite, restore C4/C5 favorites/history, copy
  valid/invalid results to recheck the history rule, cancel then confirm
  clear-history, and verify scientific defaults/reference conversions
  (including 60 rpm = 1 Hz, 180° in radians, hp versus PS, and J not
  converting to N·m). Retest keyboard/bottom-bar and system-bar insets,
  reset/sign, invalid/domain messages, all ten offered languages
  (especially Arabic RTL and Simplified Chinese), System/Light/Dark
  persistence after restart, picker opening without a keyboard, the
  fixed swap position across precision modes, TalkBack
  order/descriptions, rotation/process recreation, 360×640dp-class
  layouts, and 130%/200% font sizes.

The navigation inset handling and Android theme resources are
integrated into the current source. No backup files belong under
`res`. Drawable resource constructor parameters use explicit
`@param:DrawableRes` annotations. The remaining review items include
Android backup/data-extraction configuration and the non-fatal native
library stripping note observed during release packaging.

Pinned toolchain (validated during the release build):

- Android Gradle Plugin 8.13.2, Gradle 8.13, JDK 17
- Kotlin 2.2.21 with the Compose compiler plugin at the same version
- Compose BOM 2026.04.01 (Compose 1.11); Material 3 version-managed by it
- Activity Compose 1.13.0, kotlinx-coroutines-android 1.10.2
- minSdk 26, target/compile SDK 36

## Privacy (design)

Offline and local-only: no network permission, no analytics, no
advertising SDKs, no accounts. Favorites, pins, history, and display
settings contain only stable category/unit IDs, user-entered numeric
text, timestamps, selected precision, theme mode, and language code
in an app-private preferences file. Android backup is currently
disabled in the
manifest, so this local library is not advertised as backed up or
transferred.
