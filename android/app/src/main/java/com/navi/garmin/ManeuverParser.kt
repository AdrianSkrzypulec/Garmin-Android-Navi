package com.navi.garmin

import android.graphics.Bitmap
import java.util.regex.Pattern

/**
 * Parses turn-by-turn navigation notifications from Google Maps & Mapy.com / Mapy.cz (Polish & English).
 * Supports text analysis, resource name matching, and icon bitmap shape analysis.
 */
object ManeuverParser {

    private val DISTANCE_REGEX = Pattern.compile(
        "(\\d+([.,]\\d+)?)\\s*(m|km|ft|mi|metr[óo]w|metry|metr|kilometr[óo]w|kilometry)\\b",
        Pattern.CASE_INSENSITIVE
    )

    private val DISTANCE_PREFIX_REGEX = Pattern.compile(
        "^(?:po|za|in)\\s*(\\d+([.,]\\d+)?)\\s*(?:m|km|ft|mi|metr[óo]w|metry|metr|kilometr[óo]w|kilometry)?\\s*",
        Pattern.CASE_INSENSITIVE
    )

    private val SUBTEXT_SPLIT_REGEX = Pattern.compile("[·•\\-–|]")
    private val TIME_FORMAT_REGEX = Pattern.compile("(\\d{1,2}:\\d{2}(\\s*[AaPp][Mm])?)")
    private val DURATION_REGEX = Pattern.compile("(\\d+\\s*(min|godz|h|hr|hrs|min\\.|godz\\.))", Pattern.CASE_INSENSITIVE)

    private val ROUNDABOUT_EXIT_PRIMARY_REGEX = Pattern.compile(
        "(?:zjed[zź]|we[zź]|take|wybierz|wyjazd|zjazd)\\s*(?:the)?\\s*(\\d+)(?:[.-]|st|nd|rd|th)?\\s*(?:zjazdem|zjazd|exit)?",
        Pattern.CASE_INSENSITIVE
    )

    private val ROUNDABOUT_EXIT_FALLBACK_REGEX = Pattern.compile(
        "(\\d+)\\s*\\.?\\s*(?:zjazdem|zjazd|exit)",
        Pattern.CASE_INSENSITIVE
    )

    /**
     * Parses notification fields & optional graphic icon into structured NavigationData.
     */
    fun parse(
        title: CharSequence?,
        text: CharSequence?,
        subText: CharSequence?,
        bigText: CharSequence?,
        iconResName: String? = null,
        iconBitmap: Bitmap? = null
    ): NavigationData {
        val titleStr = title?.toString()?.trim() ?: ""
        val textStr = text?.toString()?.trim() ?: ""
        val subTextStr = subText?.toString()?.trim() ?: ""
        val bigTextStr = bigText?.toString()?.trim() ?: ""

        val combinedText = listOf(titleStr, textStr, bigTextStr)
            .filter { it.isNotEmpty() }
            .joinToString(" ")

        if (combinedText.isEmpty() && iconResName.isNullOrEmpty() && iconBitmap == null) {
            return NavigationData(hasRoute = false)
        }

        // 1. Extract Distance
        val distance = extractDistance(titleStr, textStr, combinedText)

        // 2. Extract Maneuver Type & Roundabout Exit using text, icon resource name, or bitmap pixels
        val (action, exitNumber) = detectManeuver(combinedText, iconResName, iconBitmap)

        // 3. Extract Street / Target Name
        val street = extractStreet(titleStr, textStr, action)

        // 4. Extract ETA, Remaining Distance, and Duration from subText, text, or bigText
        val (eta, remainDist, remainTime) = extractTripInfo(subTextStr, textStr, bigTextStr)

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

    private fun detectManeuver(
        text: String,
        iconResName: String?,
        iconBitmap: Bitmap?
    ): Pair<ManeuverType, Int> {
        // 1. Check icon resource name (if notification uses a drawable resource)
        if (!iconResName.isNullOrEmpty()) {
            val resResult = detectFromResName(iconResName, text)
            if (resResult.first != ManeuverType.NONE) {
                return resResult
            }
        }

        // 2. Check text keywords and Unicode direction symbols
        val textResult = detectFromText(text)
        if (textResult.first != ManeuverType.STRAIGHT && textResult.first != ManeuverType.NONE) {
            return textResult
        }

        // 3. Perform bitmap shape analysis on graphic arrow icon
        if (iconBitmap != null) {
            val bitmapResult = detectFromBitmap(iconBitmap)
            if (bitmapResult.first != ManeuverType.NONE) {
                return bitmapResult
            }
        }

        return textResult
    }

    private fun detectFromResName(resName: String, text: String): Pair<ManeuverType, Int> {
        val lower = resName.lowercase()
        var exitNumber = 0

        if (lower.contains("roundabout") || lower.contains("rondo") || lower.contains("circle") || lower.contains("kruh")) {
            val primaryMatcher = ROUNDABOUT_EXIT_PRIMARY_REGEX.matcher(text)
            if (primaryMatcher.find()) {
                exitNumber = primaryMatcher.group(1)?.toIntOrNull() ?: 0
            }
            if (exitNumber == 0) {
                val fallbackMatcher = ROUNDABOUT_EXIT_FALLBACK_REGEX.matcher(text)
                if (fallbackMatcher.find()) {
                    exitNumber = fallbackMatcher.group(1)?.toIntOrNull() ?: 0
                }
            }
            if (exitNumber == 0) {
                val digitMatcher = Pattern.compile("(\\d+)").matcher(resName)
                if (digitMatcher.find()) {
                    exitNumber = digitMatcher.group(1)?.toIntOrNull() ?: 0
                }
            }
            return Pair(ManeuverType.ROUNDABOUT, exitNumber)
        }

        if (lower.contains("u_turn") || lower.contains("uturn") || lower.contains("turn_u") || lower.contains("zawrac")) {
            return Pair(ManeuverType.U_TURN, 0)
        }

        if (lower.contains("destination") || lower.contains("finish") || lower.contains("arrived") || lower.contains("cel")) {
            return Pair(ManeuverType.DESTINATION, 0)
        }

        if (lower.contains("sharp_left") || lower.contains("sharp_l") || lower.contains("ostry_w_lewo")) {
            return Pair(ManeuverType.SHARP_LEFT, 0)
        }
        if (lower.contains("sharp_right") || lower.contains("sharp_r") || lower.contains("ostry_w_prawo")) {
            return Pair(ManeuverType.SHARP_RIGHT, 0)
        }

        if (lower.contains("slight_left") || lower.contains("slight_l") || lower.contains("bear_left") || lower.contains("lagodny_w_lewo")) {
            return Pair(ManeuverType.SLIGHT_LEFT, 0)
        }
        if (lower.contains("slight_right") || lower.contains("slight_r") || lower.contains("bear_right") || lower.contains("lagodny_w_prawo")) {
            return Pair(ManeuverType.SLIGHT_RIGHT, 0)
        }

        if (lower.contains("keep_left") || lower.contains("fork_left") || lower.contains("lewy_pas")) {
            return Pair(ManeuverType.KEEP_LEFT, 0)
        }
        if (lower.contains("keep_right") || lower.contains("fork_right") || lower.contains("prawy_pas")) {
            return Pair(ManeuverType.KEEP_RIGHT, 0)
        }

        if (lower.contains("left") || lower.contains("lewo") || lower.contains("l_turn")) {
            return Pair(ManeuverType.TURN_LEFT, 0)
        }
        if (lower.contains("right") || lower.contains("prawo") || lower.contains("r_turn")) {
            return Pair(ManeuverType.TURN_RIGHT, 0)
        }

        if (lower.contains("straight") || lower.contains("prosto") || lower.contains("ahead") || lower.contains("continue")) {
            return Pair(ManeuverType.STRAIGHT, 0)
        }

        return Pair(ManeuverType.NONE, 0)
    }

    private fun detectFromText(text: String): Pair<ManeuverType, Int> {
        val lower = text.lowercase()
        var exitNumber = 0

        // Roundabout check
        if (lower.contains("rondo") || lower.contains("rondzie") || lower.contains("ronda") ||
            lower.contains("roundabout") || lower.contains("↻") || lower.contains("⟳")) {
            val primaryMatcher = ROUNDABOUT_EXIT_PRIMARY_REGEX.matcher(text)
            if (primaryMatcher.find()) {
                exitNumber = primaryMatcher.group(1)?.toIntOrNull() ?: 0
            }
            if (exitNumber == 0) {
                val fallbackMatcher = ROUNDABOUT_EXIT_FALLBACK_REGEX.matcher(text)
                if (fallbackMatcher.find()) {
                    exitNumber = fallbackMatcher.group(1)?.toIntOrNull() ?: 0
                }
            }
            return Pair(ManeuverType.ROUNDABOUT, exitNumber)
        }

        // U-turn
        if (lower.contains("zawróć") || lower.contains("nawróć") || lower.contains("zawracan") ||
            lower.contains("u-turn") || lower.contains("uturn") || lower.contains("↶") ||
            lower.contains("↺") || lower.contains("⟲") || lower.contains("⤺")) {
            return Pair(ManeuverType.U_TURN, 0)
        }

        // Destination / arrival
        if (lower.contains("dotarłeś") || lower.contains("cel podróży") || lower.contains("jesteś na miejscu") ||
            lower.contains("jesteś u celu") || lower.contains("dojechałeś") || lower.contains("u celu") ||
            lower.contains("arrived") || lower.contains("destination reached") || lower.contains("meta")) {
            return Pair(ManeuverType.DESTINATION, 0)
        }

        // Sharp turns
        if (lower.contains("ostro w lewo") || lower.contains("ostry w lewo") || lower.contains("ostry skręt w lewo") ||
            lower.contains("sharp left") || lower.contains("↙") || lower.contains("⇙")) {
            return Pair(ManeuverType.SHARP_LEFT, 0)
        }
        if (lower.contains("ostro w prawo") || lower.contains("ostry w prawo") || lower.contains("ostry skręt w prawo") ||
            lower.contains("sharp right") || lower.contains("↘") || lower.contains("⇘")) {
            return Pair(ManeuverType.SHARP_RIGHT, 0)
        }

        // Slight turns
        if (lower.contains("łagodnie w lewo") || lower.contains("łagodny w lewo") || lower.contains("łagodny skręt w lewo") ||
            lower.contains("lekko w lewo") || lower.contains("lekki w lewo") || lower.contains("lekki skręt w lewo") ||
            lower.contains("zjazd w lewo") || lower.contains("zjedź w lewo") ||
            lower.contains("slight left") || lower.contains("bear left") || lower.contains("↖") || lower.contains("⇖")) {
            return Pair(ManeuverType.SLIGHT_LEFT, 0)
        }
        if (lower.contains("łagodnie w prawo") || lower.contains("łagodny w prawo") || lower.contains("łagodny skręt w prawo") ||
            lower.contains("lekko w prawo") || lower.contains("lekki w prawo") || lower.contains("lekki skręt w prawo") ||
            lower.contains("zjazd w prawo") || lower.contains("zjedź w prawo") ||
            lower.contains("slight right") || lower.contains("bear right") || lower.contains("↗") || lower.contains("⇗")) {
            return Pair(ManeuverType.SLIGHT_RIGHT, 0)
        }

        // Keep left / Keep right
        if (lower.contains("trzymaj się lew") || lower.contains("zjedź na lewy") || lower.contains("lewy pas") ||
            lower.contains("lewego pasa") || lower.contains("keep left")) {
            return Pair(ManeuverType.KEEP_LEFT, 0)
        }
        if (lower.contains("trzymaj się praw") || lower.contains("zjedź na prawy") || lower.contains("prawy pas") ||
            lower.contains("prawego pasa") || lower.contains("keep right")) {
            return Pair(ManeuverType.KEEP_RIGHT, 0)
        }

        // Standard turns
        if (lower.contains("skręć w lewo") || lower.contains("skręt w lewo") || lower.contains("w lewo") ||
            lower.contains("turn left") || lower.contains("↰") || lower.contains("⬅") || lower.contains("◀") || lower.contains("↩")) {
            return Pair(ManeuverType.TURN_LEFT, 0)
        }
        if (lower.contains("skręć w prawo") || lower.contains("skręt w prawo") || lower.contains("w prawo") ||
            lower.contains("turn right") || lower.contains("↱") || lower.contains("➡") || lower.contains("▶") || lower.contains("↪")) {
            return Pair(ManeuverType.TURN_RIGHT, 0)
        }

        // Straight / continue
        if (lower.contains("kieruj się") || lower.contains("jedź prosto") || lower.contains("jedź dalej") ||
            lower.contains("prosto") || lower.contains("na wprost") || lower.contains("kontynuuj") ||
            lower.contains("head") || lower.contains("continue") || lower.contains("⬆") || lower.contains("↑") || lower.contains("↟")) {
            return Pair(ManeuverType.STRAIGHT, 0)
        }

        return Pair(ManeuverType.STRAIGHT, 0)
    }

    /**
     * Performs pixel geometry & distribution analysis on graphic arrow icon.
     */
    fun detectFromBitmap(bitmap: Bitmap): Pair<ManeuverType, Int> {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 8 || h < 8) return Pair(ManeuverType.NONE, 0)

        // Corner background pixel
        val bgPixel = bitmap.getPixel(0, 0)
        val bgAlpha = (bgPixel ushr 24) and 0xFF
        val bgR = (bgPixel ushr 16) and 0xFF
        val bgG = (bgPixel ushr 8) and 0xFF
        val bgB = bgPixel and 0xFF

        var minX = w
        var minY = h
        var maxX = -1
        var maxY = -1

        var foregroundCount = 0

        // 1. Scan bounding box of visible foreground pixels
        for (y in 0 until h) {
            for (x in 0 until w) {
                val pixel = bitmap.getPixel(x, y)
                val alpha = (pixel ushr 24) and 0xFF
                if (alpha < 30) continue

                val r = (pixel ushr 16) and 0xFF
                val g = (pixel ushr 8) and 0xFF
                val b = pixel and 0xFF

                if (bgAlpha > 200) {
                    val diff = Math.abs(r - bgR) + Math.abs(g - bgG) + Math.abs(b - bgB)
                    if (diff < 35) continue
                }

                foregroundCount++
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }

        if (foregroundCount < 12 || maxX <= minX || maxY <= minY) {
            return Pair(ManeuverType.NONE, 0)
        }

        val centerX = (minX + maxX) / 2.0
        val centerY = (minY + maxY) / 2.0

        var tl = 0
        var tr = 0
        var bl = 0
        var br = 0
        var centerCore = 0

        val coreMinX = (minX + (maxX - minX) * 0.35).toInt()
        val coreMaxX = (minX + (maxX - minX) * 0.65).toInt()
        val coreMinY = (minY + (maxY - minY) * 0.35).toInt()
        val coreMaxY = (minY + (maxY - minY) * 0.65).toInt()

        // 2. Count quadrant distribution
        for (y in minY..maxY) {
            for (x in minX..maxX) {
                val pixel = bitmap.getPixel(x, y)
                val alpha = (pixel ushr 24) and 0xFF
                if (alpha < 30) continue

                val r = (pixel ushr 16) and 0xFF
                val g = (pixel ushr 8) and 0xFF
                val b = pixel and 0xFF

                if (bgAlpha > 200) {
                    val diff = Math.abs(r - bgR) + Math.abs(g - bgG) + Math.abs(b - bgB)
                    if (diff < 35) continue
                }

                if (x >= coreMinX && x <= coreMaxX && y >= coreMinY && y <= coreMaxY) {
                    centerCore++
                }

                if (x < centerX && y < centerY) tl++
                else if (x >= centerX && y < centerY) tr++
                else if (x < centerX && y >= centerY) bl++
                else br++
            }
        }

        val leftCount = tl + bl
        val rightCount = tr + br
        val total = (leftCount + rightCount).coerceAtLeast(1)

        val tlRatio = tl.toDouble() / total
        val trRatio = tr.toDouble() / total
        val blRatio = bl.toDouble() / total
        val brRatio = br.toDouble() / total

        // Roundabout check: pixels distributed in 4 quadrants with hollow center core
        if (tlRatio > 0.12 && trRatio > 0.12 && blRatio > 0.12 && brRatio > 0.12) {
            if (centerCore.toDouble() / total < 0.15) {
                return Pair(ManeuverType.ROUNDABOUT, 0)
            }
        }

        // U-Turn check: loop at top, stem going down
        if (tlRatio > 0.15 && trRatio > 0.15 && blRatio > 0.15 && brRatio < 0.10) {
            return Pair(ManeuverType.U_TURN, 0)
        }

        // TURN LEFT (↰): TL is active (arrowhead tip), BR is active (stem), BL is empty (< 12%)
        if (tlRatio > 0.15 && brRatio > 0.15 && blRatio < 0.12) {
            return Pair(ManeuverType.TURN_LEFT, 0)
        }

        // TURN RIGHT (↱): TR is active (arrowhead tip), BL is active (stem), BR is empty (< 12%)
        if (trRatio > 0.15 && blRatio > 0.15 && brRatio < 0.12) {
            return Pair(ManeuverType.TURN_RIGHT, 0)
        }

        // SLIGHT LEFT (↖): TL is heavy, BR is low
        if (tlRatio > 0.35 && brRatio < 0.20) {
            return Pair(ManeuverType.SLIGHT_LEFT, 0)
        }

        // SLIGHT RIGHT (↗): TR is heavy, BL is low
        if (trRatio > 0.35 && blRatio < 0.20) {
            return Pair(ManeuverType.SLIGHT_RIGHT, 0)
        }

        // General left vs right bias fallback
        val biasX = (rightCount - leftCount).toDouble() / total
        if (biasX < -0.22) {
            return Pair(ManeuverType.TURN_LEFT, 0)
        } else if (biasX > 0.22) {
            return Pair(ManeuverType.TURN_RIGHT, 0)
        }

        return Pair(ManeuverType.STRAIGHT, 0)
    }

    private fun extractStreet(title: String, text: String, action: ManeuverType): String {
        val titleOnlyDistance = isDistanceOnly(title)
        var candidate = if (titleOnlyDistance || title.isEmpty()) text else title

        candidate = DISTANCE_PREFIX_REGEX.matcher(candidate).replaceFirst("")

        val prefixes = listOf(
            Regex("(?i)^skręć w lewo na\\s+"),
            Regex("(?i)^skręć w prawo na\\s+"),
            Regex("(?i)^skręć w lewo w\\s+"),
            Regex("(?i)^skręć w prawo w\\s+"),
            Regex("(?i)^skręć w lewo\\s+"),
            Regex("(?i)^skręć w prawo\\s+"),
            Regex("(?i)^skręt w lewo w\\s+"),
            Regex("(?i)^skręt w prawo w\\s+"),
            Regex("(?i)^skręt w lewo na\\s+"),
            Regex("(?i)^skręt w prawo na\\s+"),
            Regex("(?i)^skręć lekko w lewo na\\s+"),
            Regex("(?i)^skręć lekko w prawo na\\s+"),
            Regex("(?i)^skręć ostro w lewo na\\s+"),
            Regex("(?i)^skręć ostro w prawo na\\s+"),
            Regex("(?i)^łagodnie w lewo na\\s+"),
            Regex("(?i)^łagodnie w prawo na\\s+"),
            Regex("(?i)^kieruj się na\\s+"),
            Regex("(?i)^jedź dalej na\\s+"),
            Regex("(?i)^kontynuuj jazdę na\\s+"),
            Regex("(?i)^trzymaj się prawej strony na\\s+"),
            Regex("(?i)^trzymaj się lewej strony na\\s+"),
            Regex("(?i)^trzymaj się prawego pasa na\\s+"),
            Regex("(?i)^trzymaj się lewego pasa na\\s+"),
            Regex("(?i)^wyjdź na\\s+"),
            Regex("(?i)^wyjdź\\s*"),
            Regex("(?i)^wyjazd na\\s+"),
            Regex("(?i)^wjazd na\\s+"),
            Regex("(?i)^turn left onto\\s+"),
            Regex("(?i)^turn right onto\\s+"),
            Regex("(?i)^turn left on\\s+"),
            Regex("(?i)^turn right on\\s+"),
            Regex("(?i)^head (?:north|south|east|west|northeast|northwest|southeast|southwest) on\\s+"),
            Regex("(?i)^continue onto\\s+"),
            Regex("(?i)^continue on\\s+"),
            Regex("(?i)^na rondzie zjedź\\s+\\d+\\.?\\s+zjazdem\\s+(?:na|w)?\\s*")
        )

        for (prefix in prefixes) {
            candidate = candidate.replace(prefix, "")
        }

        val parts = candidate.split(SUBTEXT_SPLIT_REGEX)
        var result = parts.firstOrNull()?.trim() ?: candidate

        result = result.replace(DISTANCE_REGEX.toRegex(), "").trim()

        return result
    }

    private fun isDistanceOnly(text: String): Boolean {
        val trimmed = text.trim()
        if (DISTANCE_REGEX.matcher(trimmed).matches()) return true
        if (DISTANCE_PREFIX_REGEX.matcher(trimmed).matches()) return true
        return false
    }

    private fun extractTripInfo(
        subText: String,
        text: String,
        bigText: String
    ): Triple<String, String, String> {
        var eta = ""
        var remainDist = ""
        var remainTime = ""

        val sources = listOf(subText, text, bigText).filter { it.isNotEmpty() }

        for (source in sources) {
            if (eta.isEmpty()) {
                val timeMatcher = TIME_FORMAT_REGEX.matcher(source)
                if (timeMatcher.find()) {
                    eta = timeMatcher.group(1)?.trim() ?: ""
                }
            }

            if (remainTime.isEmpty()) {
                val durMatcher = DURATION_REGEX.matcher(source)
                if (durMatcher.find()) {
                    remainTime = durMatcher.group(1)?.trim() ?: ""
                }
            }

            if (remainDist.isEmpty()) {
                val distMatcher = DISTANCE_REGEX.matcher(source)
                if (distMatcher.find()) {
                    remainDist = normalizeDistance(distMatcher.group(0) ?: "")
                }
            }
        }

        return Triple(eta, remainDist, remainTime)
    }
}
