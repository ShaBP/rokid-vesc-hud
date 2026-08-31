# Third-party notices and acknowledgements

Rokid VESC HUD is independently written and distributed under the MIT License. It interoperates
with VESC-compatible hardware and was developed with help from the documentation, protocol
definitions, test data, and implementation experience published by the projects listed below.

No Vescape, VESC Tool, or VESC firmware source code or compiled component is included in this
application. Those projects are references and interoperating counterparts, not application
dependencies. Their mention does not imply endorsement or affiliation.

## Open-source references

### VESC firmware

- Project: https://github.com/vedderb/bldc
- Copyright: Benjamin Vedder and contributors
- License: GNU General Public License version 3
- Use here: authoritative VESC command identifiers, payload formats, scaling, and controller
  behavior used to implement an independent protocol client.

### VESC Tool

- Project: https://github.com/vedderb/vesc_tool
- Copyright: Benjamin Vedder and contributors
- License: GNU General Public License version 3
- Use here: reference client for validating VESC telemetry fields, CAN commands, packet framing,
  and controller responses.

### Vescape

- Project: https://github.com/vescape-app/vescape
- Copyright: Kacper Kozak and contributors
- License: GNU General Public License version 3 or later
- Use here: behavioral reference for Android BLE reliability, serialized GATT writes, Nordic UART
  transport, and discovering a motor controller behind VESC Express/CAN. Rokid VESC HUD uses a
  separate implementation, connection state machine, data model, and polling protocol.

## Build and runtime components

### Kotlin

- Project: https://github.com/JetBrains/kotlin
- Copyright: JetBrains and Kotlin contributors
- License: Apache License 2.0
- Use here: Kotlin compiler, Gradle plugin, and Kotlin standard library. The standard library is
  packaged into the Android application by the build process.

### Android Open Source Project APIs and build tools

- Project: https://source.android.com/
- Copyright: The Android Open Source Project contributors
- License: Primarily Apache License 2.0; individual components may carry their own notices
- Use here: Android platform APIs, SDK, Android Gradle plugin, and APK build tools.

### JUnit 4

- Project: https://github.com/junit-team/junit4
- Copyright: JUnit contributors
- License: Eclipse Public License 1.0
- Use here: unit-test dependency only; it is not packaged in the application APK.

## Specifications and data references

The following are cited sources rather than incorporated software components:

- Nordic UART Service UUID convention, used for BLE interoperability.
- Rokid developer documentation: https://x-docs.rokid.com/docs/en/
- Samsung INR21700-50S product specification and published discharge data.
- Reliance INR21700-RS50 product specification and published discharge data.
- Surfdado's public explanation of voltage anchoring and net-energy battery estimation.

Battery curves in this repository are independently prepared engineering lookup tables derived
from published measurements. Source links and test conditions are documented in the README and
configuration comments. Product names and trademarks belong to their respective owners.

## Trademarks and non-affiliation

VESC is a registered trademark of Benjamin Vedder. Rokid, Floatwheel, Onewheel, Samsung, Reliance,
Android, Kotlin, and other names are trademarks of their respective owners. This project is not
endorsed by or affiliated with those owners.
