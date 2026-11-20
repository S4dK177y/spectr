package app.spectr.mood.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Semantic color scale for the -5 .. +5 mood spectrum.
 * Cool muted indigos/blues indicate low mood, neutral sage marks euthymia (0),
 * and warm amber/coral tones indicate elevated activation.
 */
@Immutable
data class MoodColorScale(
    private val colorsByScore: Map<Int, Color>,
    private val contentColorsByScore: Map<Int, Color>
) {
    /**
     * Returns the background color for any mood score in [-5, +5] (clamped safely).
     */
    fun containerColor(score: Int): Color {
        val clamped = score.coerceIn(MIN_MOOD, MAX_MOOD)
        return colorsByScore.getValue(clamped)
    }

    /**
     * Returns a legible foreground text/icon color paired with [containerColor].
     */
    fun contentColor(score: Int): Color {
        val clamped = score.coerceIn(MIN_MOOD, MAX_MOOD)
        return contentColorsByScore.getValue(clamped)
    }

    companion object {
        const val MIN_MOOD = -5
        const val MAX_MOOD = 5

        val Light = MoodColorScale(
            colorsByScore = mapOf(
                -5 to Color(0xFF3949AB),
                -4 to Color(0xFF5C6BC0),
                -3 to Color(0xFF7986CB),
                -2 to Color(0xFF9FA8DA),
                -1 to Color(0xFFC5CAE9),
                0 to Color(0xFFDCE5DF),
                1 to Color(0xFFFFECB3),
                2 to Color(0xFFFFD54F),
                3 to Color(0xFFFFB74D),
                4 to Color(0xFFFF8A65),
                5 to Color(0xFFE57373)
            ),
            contentColorsByScore = mapOf(
                -5 to Color(0xFFFFFFFF),
                -4 to Color(0xFFFFFFFF),
                -3 to Color(0xFFFFFFFF),
                -2 to Color(0xFF1A2044),
                -1 to Color(0xFF1D2340),
                0 to Color(0xFF1F2B24),
                1 to Color(0xFF3A2C00),
                2 to Color(0xFF362700),
                3 to Color(0xFF382000),
                4 to Color(0xFF3B1508),
                5 to Color(0xFFFFFFFF)
            )
        )

        val Dark = MoodColorScale(
            colorsByScore = mapOf(
                -5 to Color(0xFF283593),
                -4 to Color(0xFF3949AB),
                -3 to Color(0xFF4E5DB3),
                -2 to Color(0xFF5C6BC0),
                -1 to Color(0xFF454E78),
                0 to Color(0xFF384740),
                1 to Color(0xFF6B5A28),
                2 to Color(0xFF8C6D1F),
                3 to Color(0xFFB56E22),
                4 to Color(0xFFC45535),
                5 to Color(0xFFB83B3B)
            ),
            contentColorsByScore = mapOf(
                -5 to Color(0xFFE8EAFF),
                -4 to Color(0xFFECEEFF),
                -3 to Color(0xFFF0F2FF),
                -2 to Color(0xFFF4F6FF),
                -1 to Color(0xFFE6E9F8),
                0 to Color(0xFFE3EEE8),
                1 to Color(0xFFFFF4D1),
                2 to Color(0xFFFFF3CC),
                3 to Color(0xFFFFF0DB),
                4 to Color(0xFFFFECE6),
                5 to Color(0xFFFFEBEB)
            )
        )
    }
}

val LocalMoodColors = staticCompositionLocalOf { MoodColorScale.Light }
