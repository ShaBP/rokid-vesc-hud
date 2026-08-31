package dev.veschud.config

import dev.veschud.model.BoardConfig
import dev.veschud.model.BoardProfile

/**
 * User-editable catalog of supported boards.
 *
 * Add a [BoardProfile] to [ALL] to support another board. Identification tokens are matched
 * case-insensitively against both the BLE advertised name and the VESC hardware name returned
 * by COMM_FW_VERSION. On bridge-based systems every CAN node is probed until a token matches.
 */
object BoardProfiles {
    val ADV2 = BoardProfile(
        id = "adv2",
        displayName = "ADV2",
        hardwareTokens = setOf("ADV500"),
        config = BoardConfig(
            wheelDiameterM = 0.27,
            motorPolePairs = 15,
            batterySeriesCells = 20,
            usableBatteryWh = 720.0,
            packVoltageCorrectionV = 0.0
        ),
        idleCurve = BatteryCurves.SAMSUNG_50S
    )

    val ATOM = BoardProfile(
        id = "atom",
        displayName = "Atom",
        hardwareTokens = setOf("ADV200"),
        config = BoardConfig(
            wheelDiameterM = 0.27,
            motorPolePairs = 15,
            batterySeriesCells = 22,
            usableBatteryWh = 407.0,
            packVoltageCorrectionV = 0.0
        ),
        idleCurve = BatteryCurves.RELIANCE_RS50,
        // A 20s pack cannot exceed 84 V, so >85 V safely identifies the 22s Atom.
        unambiguousPackVoltageAboveV = 85.0
    )

    /** Order matters only when tokens overlap: put the most specific profile first. */
    val ALL: List<BoardProfile> = listOf(ADV2, ATOM).also { profiles ->
        require(profiles.map { it.id }.distinct().size == profiles.size) { "Board profile IDs must be unique" }
        profiles.forEach(::validate)
    }

    fun fromHardwareName(name: String): BoardProfile? {
        val normalized = name.uppercase()
        return ALL.firstOrNull { profile -> profile.hardwareTokens.any { normalized.contains(it.uppercase()) } }
    }

    fun fromBleName(name: String?): BoardProfile? = name?.let(::fromHardwareName)

    /** Conservative fallback; returns a profile only when its threshold is explicitly configured. */
    fun fromUnambiguousPackVoltage(volts: Double): BoardProfile? = ALL
        .filter { profile -> profile.unambiguousPackVoltageAboveV?.let { volts > it } == true }
        .maxByOrNull { it.unambiguousPackVoltageAboveV!! }

    private fun validate(profile: BoardProfile) {
        require(profile.hardwareTokens.isNotEmpty()) { "${profile.id}: add at least one identity token" }
        require(profile.config.batterySeriesCells > 0) { "${profile.id}: series cell count must be positive" }
        require(profile.config.usableBatteryWh > 0) { "${profile.id}: usable Wh must be positive" }
        require(profile.idleCurve.size >= 2) { "${profile.id}: battery curve needs at least two points" }
        require(profile.idleCurve.zipWithNext().all { (a, b) ->
            a.voltsPerCell < b.voltsPerCell && a.percent <= b.percent
        }) { "${profile.id}: battery curve must increase by voltage and percentage" }
    }
}
