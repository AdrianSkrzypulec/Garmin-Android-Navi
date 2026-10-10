package com.navi.garmin

/**
 * Data packet sent to the Garmin Connect IQ watch app.
 */
data class NavigationData(
    val action: ManeuverType = ManeuverType.NONE,
    val distance: String = "--",
    val street: String = "",
    val eta: String = "",
    val remainDist: String = "",
    val remainTime: String = "",
    val exitNumber: Int = 0,
    val hasRoute: Boolean = false,
    val phoneBattery: Int = -1,    // Poziom baterii telefonu w % (-1 = brak danych)
    val rawDebugText: String = "", // Informacje diagnostyczne dla ekranu telefonu
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Converts to Map/Dictionary structure expected by Garmin Connect IQ.
     */
    fun toGarminMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        map["action"] = action.name
        map["distance"] = distance
        map["street"] = street
        map["eta"] = eta
        map["remainDist"] = remainDist
        map["remainTime"] = remainTime
        map["exitNumber"] = exitNumber
        map["hasRoute"] = hasRoute
        map["phoneBattery"] = phoneBattery
        return map
    }
}
