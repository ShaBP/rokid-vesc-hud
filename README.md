# Rokid VESC HUD

A small, read-only Android HUD for Rokid AI Glasses. It connects directly to a VESC-compatible
motor controller over Bluetooth Low Energy and shows the information needed while riding:

- Speed
- Battery percentage
- Trip distance
- Estimated remaining range
- Motor and controller temperatures
- Input power and VESC fault status

The current profiles support the **Floatwheel ADV2** (20s2p Samsung 50S) and **Floatwheel Atom**
(22s1p Reliance RS50). Board and battery definitions live in two deliberately simple Kotlin
configuration files, making other VESC boards straightforward to add.

> This is an independent community project. It is not affiliated with Rokid, Floatwheel,
> Future Motion, Vedder, or the VESC Project. Never rely on a HUD as a safety system.

## How it works

```text
VESC / VESC Express ── Nordic UART over BLE ── Android app on Rokid glasses
       │                                             │
       └── optional CAN motor controller             └── monochrome green HUD
```

The app scans for the Nordic UART Service (NUS), asks the connected device for its VESC firmware
identity, and matches its hardware name against the configured board profiles. If the BLE device
is only a VESC Express bridge, the app scans CAN and probes each node until it finds a configured
motor controller. It then polls `COMM_GET_VALUES` four times per second.

Only read-only VESC commands are sent: firmware identification, CAN discovery, and telemetry.

## Requirements

- Rokid AI Glasses or another Android device running Android 7.0/API 24 or newer
- Android Studio with JDK 17
- Android SDK 35
- A VESC BLE adapter exposing the standard Nordic UART Service
- A supported board profile, or the information needed to add one

NUS UUIDs used by the app:

| Purpose | UUID |
|---|---|
| Service | `6e400001-b5a3-f393-e0a9-e50e24dcca9e` |
| RX, app writes | `6e400002-b5a3-f393-e0a9-e50e24dcca9e` |
| TX, app receives | `6e400003-b5a3-f393-e0a9-e50e24dcca9e` |

## Build and install

1. Clone the repository and open its root folder in Android Studio.
2. Allow Gradle to sync and install Android SDK 35 if prompted.
3. Select the `app` configuration and the `debug` build variant.
4. Choose **Build → Build APK(s)**.
5. Install `app/build/outputs/apk/debug/app-debug.apk` on the glasses with Android Studio or:

   ```sh
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

6. Turn on the board, launch **VESC HUD**, and grant Nearby Devices permission. Android 11 and
   older request Location because those Android versions require it for BLE scanning.

The status line progresses through `SCANNING`, `CONNECTING`, `IDENTIFYING`, optional CAN search,
and finally `LIVE • <board name>`.

## Add or edit a battery curve

Edit [`BatteryCurves.kt`](app/src/main/java/dev/veschud/config/BatteryCurves.kt). A curve is an
ascending list of per-cell voltage and **remaining** percentage pairs:

```kotlin
val MY_CELL = listOf(
    CurvePoint(3.00, 0.0),
    CurvePoint(3.50, 18.0),
    CurvePoint(3.70, 52.0),
    CurvePoint(4.00, 89.0),
    CurvePoint(4.20, 100.0)
)
```

Use measured low-current discharge data, ideally 0.1C–0.2C near room temperature. If a graph
reports discharged capacity, convert it with:

```text
remaining % = 100 × (1 − discharged capacity / measured total capacity)
```

Add points around sharp knees in the curve. The app linearly interpolates between them. Do not
copy a generic lithium-ion voltage table: different cell models can differ by several percentage
points at the same voltage.

## Add another board

Edit [`BoardProfiles.kt`](app/src/main/java/dev/veschud/config/BoardProfiles.kt):

```kotlin
val MY_BOARD = BoardProfile(
    id = "my_board",                       // unique stable identifier
    displayName = "My Board",             // shown on the HUD
    hardwareTokens = setOf("MY_HW_NAME"), // BLE or COMM_FW_VERSION substrings
    config = BoardConfig(
        wheelDiameterM = 0.27,
        motorPolePairs = 15,
        batterySeriesCells = 20,
        usableBatteryWh = 700.0,
        packVoltageCorrectionV = 0.0
    ),
    idleCurve = BatteryCurves.MY_CELL
)
```

Then add it to `ALL`:

```kotlin
val ALL = listOf(ADV2, ATOM, MY_BOARD)
```

`hardwareTokens` matching is case-insensitive and applies to both the advertised BLE name and
the VESC hardware name. It also works for controllers found behind a CAN bridge. If tokens
overlap, put the most specific profile first.

`unambiguousPackVoltageAboveV` is an optional fallback for a pack whose voltage cannot overlap
lower-voltage profiles. Leave it `null` unless the threshold is mathematically unambiguous.

### Board parameter reference

| Field | Meaning |
|---|---|
| `wheelDiameterM` | Loaded tire diameter. Calibrate speed and distance against GPS. |
| `motorPolePairs` | Motor pole-pair count used to convert electrical RPM. |
| `batterySeriesCells` | Series count used to calculate voltage per cell. |
| `usableBatteryWh` | Usable pack energy; controls moving SOC and range estimation. |
| `packVoltageCorrectionV` | Known whole-pack VESC measurement offset, normally `0.0`. |
| `hardwareTokens` | Substrings identifying the board over BLE or VESC firmware. |
| `idleCurve` | Cell-specific low-current voltage-to-remaining-capacity table. |

## Battery estimation

Voltage alone becomes inaccurate under acceleration and regenerative braking. The estimator uses
a hybrid method:

1. When stopped below 0.8 km/h and below 2 A input current, filter pack voltage for 1.5 seconds.
2. Divide by the configured series count and anchor SOC to the cell curve.
3. While moving, subtract net controller energy (`Wh consumed − Wh regenerated`) from that anchor.
4. Re-anchor from voltage at the next stable stop.

The percentage is an estimate. Temperature, cell aging, balance, tire pressure, terrain, rider
weight, and VESC measurement accuracy all affect it. `usableBatteryWh` should reflect usable,
not merely nominal, pack energy.

## Project structure

```text
app/src/main/java/dev/veschud/
├── config/       Battery curves and supported board profiles
├── data/         TelemetrySource abstraction and Android BLE implementation
├── domain/       Telemetry conversion, battery and range estimation
├── model/        Transport-independent data models
├── protocol/     VESC framing, CRC, commands and parsers
├── ui/           Rokid-oriented monochrome Canvas HUD
└── MainActivity.kt
```

`TelemetrySource` is the boundary between transport and the rest of the app. A future phone
companion can implement that interface and forward data to the glasses without changing the HUD,
models, or estimation logic.

## Testing and troubleshooting

Run unit tests from Android Studio or with `testDebugUnitTest`. Before riding, test with the board
secured on a stand and compare speed, pack voltage, percentage, and temperatures with VESC Tool or
Float Control.

- **No Nordic UART VESC found:** confirm Bluetooth is enabled and the adapter advertises NUS.
- **No CAN devices found:** confirm VESC Express is connected to the board CAN bus and powered.
- **Supported motor controller not found:** add the controller's exact hardware-name token to a
  profile. VESC Tool can show the firmware hardware name.
- **Wrong battery percentage at rest:** verify series count, curve, pack voltage, and voltage offset.
- **Wrong percentage while riding:** calibrate `usableBatteryWh`; voltage sag is intentionally not
  used while moving.
- **Wrong speed or distance:** calibrate wheel diameter and motor pole pairs.

## References and acknowledgements

- [Rokid developer documentation](https://x-docs.rokid.com/docs/en/)
- [VESC firmware and protocol](https://github.com/vedderb/bldc)
- [VESC Tool](https://github.com/vedderb/vesc_tool)
- [Vescape](https://github.com/vescape-app/vescape) — Android BLE/session reference
- [Samsung INR21700-50S specification](https://www.akku500.de/media/10/d1/b6/1768807367/INR2170050S2CellSpecification.pdf)
- [Reliance INR21700-RS50 specification](https://manuals.plus/m/25641b0d89a05cc0f8b879f1eb52fcab8ca7032b89a10769bc7a32d40949503f)

VESC is a registered trademark of Benjamin Vedder. Review the licenses of reference projects
before copying their implementation code; this project uses independently written protocol code.
See [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) for the complete attribution and dependency
record.

## Contributing

Issues and pull requests for tested board profiles and cell curves are welcome. Include the exact
hardware name, pack configuration, cell model, source of discharge data, test temperature/current,
and comparison logs where possible. See [`CONTRIBUTING.md`](CONTRIBUTING.md).

## License

Copyright © 2026 Rokid VESC HUD contributors.

This project is released under the [MIT License](LICENSE). You may use, modify, and redistribute it,
including in commercial applications, provided that the copyright and license notice is retained.

The MIT License applies to Rokid VESC HUD's original source code and documentation. Referenced
projects and third-party components remain under their respective licenses; see
[`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).
