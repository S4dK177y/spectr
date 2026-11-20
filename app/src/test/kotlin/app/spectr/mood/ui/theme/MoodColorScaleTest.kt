package app.spectr.mood.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MoodColorScaleTest {
    @Test
    fun `every mood score from -5 to +5 has distinct container and content colors`() {
        for (scale in listOf(MoodColorScale.Light, MoodColorScale.Dark)) {
            for (score in MoodColorScale.MIN_MOOD..MoodColorScale.MAX_MOOD) {
                assertNotEquals(
                    "Container and content color must differ for score $score",
                    scale.containerColor(score),
                    scale.contentColor(score)
                )
            }
        }
    }

    @Test
    fun `out of range mood scores are safely clamped`() {
        assertEquals(
            MoodColorScale.Light.containerColor(MoodColorScale.MIN_MOOD),
            MoodColorScale.Light.containerColor(-99)
        )
        assertEquals(
            MoodColorScale.Dark.containerColor(MoodColorScale.MAX_MOOD),
            MoodColorScale.Dark.containerColor(99)
        )
    }
}
