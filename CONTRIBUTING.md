# Contributing

Thank you for helping extend Rokid VESC HUD.

## Board profiles

Submit board profiles in `BoardProfiles.kt`. Include:

- Board and battery model
- Exact BLE advertised name, if useful
- Exact `COMM_FW_VERSION` hardware name
- Whether the motor controller is local or behind CAN
- Series/parallel configuration, usable Wh, wheel diameter, and pole-pair count
- A stationary comparison against VESC Tool or another trusted telemetry app

Do not use a voltage fallback unless it is impossible for every lower-voltage supported pack to
cross that threshold, including during charging or regenerative braking.

## Battery curves

Submit curves in `BatteryCurves.kt` with a link to the underlying discharge data. Prefer measured
0.1C–0.2C data at 20–25°C. State whether values were digitized from a graph or taken from raw data.
All points must increase in voltage and may not decrease in remaining percentage.

## Pull requests

Keep changes focused, document non-obvious protocol decisions, and add unit coverage for new
identity tokens and representative battery lookup points. Confirm the debug APK builds before
opening the pull request. Never add control, configuration-write, or firmware-update commands
without a separate design and safety review; the project is intentionally read-only.
