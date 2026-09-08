package com.navi.garmin

import java.util.regex.Pattern

/**
 * Parses turn-by-turn navigation notifications from Google Maps (Polish & English).
 */
object ManeuverParser {

    private val DISTANCE_REGEX = Pattern.compile(
        "(\\d+([.,]\\d+)?)\\s*(m|km|ft|mi|metr[óo]w|metry|metr|kilometr[óo]w|kilometry|km)\\b",
        Pattern.CASE_INSENSITIVE
    )

    private val SUBTEXT_SPLIT_REGEX = Pattern.compile("[·•\\-–|]")
    private val TIME_FORMAT_REGEX = Pattern.compile("(\\d{1,2}:\\d{2}(\\s*[AaPp][Mm])?)")
    private val DURATION_REGEX = Pattern.compile("(\\d+\\s*(min|godz|h|hr|hrs))", Pattern.CASE_INSENSITIVE)
    private val ROUNDABOUT_EXIT_REGEX = Pattern.compile(
        "(?:zjed[zź]|we[zź]|take)\\s*(?:the)?\\s*(\\d+)(?:[.-]|st|nd|rd|th)?\\s*(?:zjazdem|exit)?",
        Pattern.CASE_INSENSITIVE
    )

    /**
     * Parses notification fields into structured NavigationData.
     */
    fun parse(
        title: CharSequence?,
        text: CharSequence?,
        subText: CharSequence?,
        bigText: CharSequence?
    ): NavigationData {
        val titleStr = title?.toString()?.trim() ?: ""
        val textStr = text?.toString()?.trim() ?: ""
        val subTextStr = subText?.toString()?.trim() ?: ""
        val bigTextStr = bigText?.toString()?.trim() ?: ""

        val combinedText = listOf(titleStr, textStr, bigTextStr)
            .filter { it.isNotEmpty() }
            .joinToString(" ")

        if (combinedText.isEmpty()) {
            return NavigationData(hasRoute = false)
        }

        // 1. Extract Distance
        val distance = extractDistance(titleStr, textStr, combinedText)

        // 2. Extract Maneuver Type & Roundabout Exit
        val (action, exitNumber) = detectManeuver(combinedText)

        // 3. Extract Street / Target Name
        val street = extractStreet(titleStr, textStr, action)

        // 4. Extract ETA, Remaining Distance, and Duration from subText or text
        val (eta, remainDist, remainTime) = extractTripInfo(subTextStr, textStr)

        return NavigationData(
            action = action,
            distance = distance.ifEmpty { "--" },
            street = street,
            eta = eta,
            remainDist = remainDist,
            remainTime = remainTime,
            exitNumber = exitNumber,
            hasRoute = true
        )
    }

    private fun extractDistance(title: String, text: String, combined: String): String {
        // Distance is usually prominent in title (e.g. "250 m")
        val matcherTitle = DISTANCE_REGEX.matcher(title)
        if (matcherTitle.find()) {
            return normalizeDistance(matcherTitle.group(0) ?: "")
        }

        val matcherText = DISTANCE_REGEX.matcher(text)
        if (matcherText.find()) {
            return normalizeDistance(matcherText.group(0) ?: "")
        }

        val matcherCombined = DISTANCE_REGEX.matcher(combined)
        if (matcherCombined.find()) {
            return normalizeDistance(matcherCombined.group(0) ?: "")
        }

        return ""
    }

    private fun normalizeDistance(raw: String): String {
        return raw.replace(',', '.')
            .replace(Regex("(?i)metr[óo]w|metry|metr"), "m")
            .replace(Regex("(?i)kilometr[óo]w|kilometry"), "km")
            .trim()
    }

    private fun detectManeuver(text: String): Pair<ManeuverType, Int> {
        val lower = text.lowercase()
        var exitNumber = 0

        // Roundabout check
        if (lower.contains("rondo") || lower.contains("rondzie") || lower.contains("roundabout")) {
            val exitMatcher = ROUNDABOUT_EXIT_REGEX.matcher(text)
            if (exitMatcher.find()) {
                exitNumber = exitMatcher.group(1)?.toIntOrNull() ?: 0
            }
            return Pair(ManeuverType.ROUNDABOUT, exitNumber)
        }

        // U-turn
        if (lower.contains("zawróć") || lower.contains("nawróć") || lower.contains("u-turn") || lower.contains("uturn")) {
            return Pair(ManeuverType.U_TURN, 0)
        }

        // Destination / arrival
        if (lower.contains("dotarłeś") || lower.contains("cel podróży") || lower.contains("jesteś na miejscu") ||
            lower.contains("arrived") || lower.contains("destination reached")) {
            return Pair(ManeuverType.DESTINATION, 0)
        }

        // Sharp turns
        if (lower.contains("ostro w lewo") || lower.contains("sharp left")) {
            return Pair(ManeuverType.SHARP_LEFT, 0)
        }
        if (lower.contains("ostro w prawo") || lower.contains("sharp right")) {
            return Pair(ManeuverType.SHARP_RIGHT, 0)
        }

        // Slight turns
        if (lower.contains("łagodnie w lewo") || lower.contains("lekko w lewo") ||
            lower.contains("slight left") || lower.contains("bear left")) {
            return Pair(ManeuverType.SLIGHT_LEFT, 0)
        }
        if (lower.contains("łagodnie w prawo") || lower.contains("lekko w prawo") ||
            lower.contains("slight right") || lower.contains("bear right")) {
            return Pair(ManeuverType.SLIGHT_RIGHT, 0)
        }

        // Keep left / Keep right
        if (lower.contains("trzymaj się lew") || lower.contains("keep left")) {
            return Pair(ManeuverType.KEEP_LEFT, 0)
        }
        if (lower.contains("trzymaj się praw") || lower.contains("keep right")) {
            return Pair(ManeuverType.KEEP_RIGHT, 0)
        }

        // Standard turns
        if (lower.contains("skręć w lewo") || lower.contains("w lewo") || lower.contains("turn left")) {
            return Pair(ManeuverType.TURN_LEFT, 0)
        }
        if (lower.contains("skręć w prawo") || lower.contains("w prawo") || lower.contains("turn right")) {
            return Pair(ManeuverType.TURN_RIGHT, 0)
        }

        // Straight / continue
        if (lower.contains("kieruj się") || lower.contains("jedź prosto") || lower.contains("jedź dalej") ||
            lower.contains("prosto") || lower.contains("head") || lower.contains("continue")) {
            return Pair(ManeuverType.STRAIGHT, 0)
        }

        return Pair(ManeuverType.STRAIGHT, 0)
    }

    private fun extractStreet(title: String, text: String, action: ManeuverType): String {
        // If title is just distance (e.g. "200 m"), then street is in text
        val titleHasOnlyDistance = DISTANCE_REGEX.matcher(title).matches()
        var candidate = if (titleHasOnlyDistance || title.isEmpty()) text else title

        // Clean common prefixes
        val prefixes = listOf(
            Regex("(?i)^skręć w lewo na\\s+"),
            Regex("(?i)^skręć w prawo na\\s+"),
            Regex("(?i)^skręć w lewo w\\s+"),
            Regex("(?i)^skręć w prawo w\\s+"),
            Regex("(?i)^skręć w lewo\\s+"),
            Regex("(?i)^skręć w prawo\\s+"),
            Regex("(?i)^łagodnie w lewo na\\s+"),
            Regex("(?i)^łagodnie w prawo na\\s+"),
            Regex("(?i)^kieruj się na\\s+"),
            Regex("(?i)^jedź dalej na\\s+"),
            Regex("(?i)^turn left onto\\s+"),
            Regex("(?i)^turn right onto\\s+"),
            Regex("(?i)^turn left on\\s+"),
            Regex("(?i)^turn right on\\s+"),
            Regex("(?i)^head (?:north|south|east|west|northeast|northwest|southeast|southwest) on\\s+"),
            Regex("(?i)^continue onto\\s+"),
            Regex("(?i)^continue on\\s+")
        )

        for (prefix in prefixes) {
            candidate = candidate.replace(prefix, "")
        }

        // Remove trailing distance info if attached with dot or dash
        val parts = candidate.split(SUBTEXT_SPLIT_REGEX)
        var result = parts.firstOrNull()?.trim() ?: candidate

        // Remove standalone distance strings from street name
        result = result.replace(DISTANCE_REGEX.toRegex(), "").trim()

        return result
    }

    private fun extractTripInfo(subText: String, text: String): Triple<String, String, String> {
        var eta = ""
        var remainDist = ""
        var remainTime = ""

        val source = if (subText.isNotEmpty()) subText else text
        if (source.isEmpty()) {
            return Triple(eta, remainDist, remainTime)
        }

        // 1. ETA time (e.g. "14:30" or "2:30 PM")
        val timeMatcher = TIME_FORMAT_REGEX.matcher(source)
        if (timeMatcher.find()) {
            eta = timeMatcher.group(1)?.trim() ?: ""
        }

        // 2. Duration (e.g. "15 min", "1 godz. 20 min")
        val durMatcher = DURATION_REGEX.matcher(source)
        if (durMatcher.find()) {
            remainTime = durMatcher.group(1)?.trim() ?: ""
        }

        // 3. Remaining Distance (e.g. "4.2 km")
        val distMatcher = DISTANCE_REGEX.matcher(source)
        if (distMatcher.find()) {
            remainDist = normalizeDistance(distMatcher.group(0) ?: "")
        }

        return Triple(eta, remainDist, remainTime)
    }
}

