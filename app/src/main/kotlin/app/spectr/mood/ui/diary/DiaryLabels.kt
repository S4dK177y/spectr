package app.spectr.mood.ui.diary

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.spectr.mood.R
import app.spectr.mood.domain.model.AlcoholStatus
import app.spectr.mood.domain.model.DailyDynamics
import app.spectr.mood.domain.model.MedicationStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val RUSSIAN_LOCALE: Locale = Locale.forLanguageTag("ru")
private val DATE_HEADER_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM, EEE", RUSSIAN_LOCALE)
private val DATE_WITH_YEAR_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM yyyy, EEE", RUSSIAN_LOCALE)
private val TIME_HH_MM_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
private const val MINUTES_PER_HOUR = 60

internal fun formatSignedMood(score: Int): String = when {
    score > 0 -> "+$score"
    score < 0 -> "−${-score}"
    else -> "0"
}

internal fun formatDiaryDate(date: LocalDate, today: LocalDate): String = if (date.year == today.year) {
    date.format(DATE_HEADER_FORMATTER)
} else {
    date.format(DATE_WITH_YEAR_FORMATTER)
}

internal fun formatMomentTime(timestampEpochMs: Long, zoneId: ZoneId = ZoneId.systemDefault()): String =
    Instant.ofEpochMilli(timestampEpochMs).atZone(zoneId).toLocalTime().format(TIME_HH_MM_FORMATTER)

@Composable
internal fun formatSleepDuration(totalMinutes: Int): String {
    val safe = totalMinutes.coerceAtLeast(0)
    val hours = safe / MINUTES_PER_HOUR
    val minutes = safe % MINUTES_PER_HOUR
    return when {
        hours > 0 && minutes > 0 -> stringResource(R.string.diary_sleep_duration_hm, hours, minutes)
        hours > 0 -> stringResource(R.string.diary_sleep_duration_h, hours)
        else -> stringResource(R.string.diary_sleep_duration_m, minutes)
    }
}

@Composable
internal fun moodDescription(score: Int): String = when (score.coerceIn(-5, 5)) {
    -5 -> stringResource(R.string.mood_desc_m5)
    -4 -> stringResource(R.string.mood_desc_m4)
    -3 -> stringResource(R.string.mood_desc_m3)
    -2 -> stringResource(R.string.mood_desc_m2)
    -1 -> stringResource(R.string.mood_desc_m1)
    0 -> stringResource(R.string.mood_desc_0)
    1 -> stringResource(R.string.mood_desc_p1)
    2 -> stringResource(R.string.mood_desc_p2)
    3 -> stringResource(R.string.mood_desc_p3)
    4 -> stringResource(R.string.mood_desc_p4)
    else -> stringResource(R.string.mood_desc_p5)
}

@Composable
internal fun dailyDynamicsLabel(dynamics: DailyDynamics): String = when (dynamics) {
    DailyDynamics.STEADY -> stringResource(R.string.diary_dynamics_steady)
    DailyDynamics.WORSE_MORNING -> stringResource(R.string.diary_dynamics_worse_morning)
    DailyDynamics.WORSE_EVENING -> stringResource(R.string.diary_dynamics_worse_evening)
    DailyDynamics.WORSE_NIGHT -> stringResource(R.string.diary_dynamics_worse_night)
    DailyDynamics.SWINGS -> stringResource(R.string.diary_dynamics_swings)
}

@Composable
internal fun symptomLabel(symptom: SymptomType): String = when (symptom) {
    SymptomType.APATHY -> stringResource(R.string.symptom_apathy_label)
    SymptomType.SELF_CRITICISM -> stringResource(R.string.symptom_self_criticism_label)
    SymptomType.BRAIN_FOG -> stringResource(R.string.symptom_brain_fog_label)
    SymptomType.BINGE_EATING -> stringResource(R.string.symptom_binge_eating_label)
    SymptomType.DEATH_THOUGHTS -> stringResource(R.string.symptom_death_thoughts_label)
    SymptomType.HYPERFOCUS -> stringResource(R.string.symptom_hyperfocus_label)
    SymptomType.RACING_THOUGHTS -> stringResource(R.string.symptom_racing_thoughts_label)
}

@Composable
internal fun symptomWhenToMark(symptom: SymptomType): String = when (symptom) {
    SymptomType.APATHY -> stringResource(R.string.symptom_apathy_when)
    SymptomType.SELF_CRITICISM -> stringResource(R.string.symptom_self_criticism_when)
    SymptomType.BRAIN_FOG -> stringResource(R.string.symptom_brain_fog_when)
    SymptomType.BINGE_EATING -> stringResource(R.string.symptom_binge_eating_when)
    SymptomType.DEATH_THOUGHTS -> stringResource(R.string.symptom_death_thoughts_when)
    SymptomType.HYPERFOCUS -> stringResource(R.string.symptom_hyperfocus_when)
    SymptomType.RACING_THOUGHTS -> stringResource(R.string.symptom_racing_thoughts_when)
}

@Composable
internal fun symptomWhenNotToMark(symptom: SymptomType): String = when (symptom) {
    SymptomType.APATHY -> stringResource(R.string.symptom_apathy_when_not)
    SymptomType.SELF_CRITICISM -> stringResource(R.string.symptom_self_criticism_when_not)
    SymptomType.BRAIN_FOG -> stringResource(R.string.symptom_brain_fog_when_not)
    SymptomType.BINGE_EATING -> stringResource(R.string.symptom_binge_eating_when_not)
    SymptomType.DEATH_THOUGHTS -> stringResource(R.string.symptom_death_thoughts_when_not)
    SymptomType.HYPERFOCUS -> stringResource(R.string.symptom_hyperfocus_when_not)
    SymptomType.RACING_THOUGHTS -> stringResource(R.string.symptom_racing_thoughts_when_not)
}

@Composable
internal fun alcoholStatusLabel(status: AlcoholStatus): String = when (status) {
    AlcoholStatus.NONE -> stringResource(R.string.diary_alcohol_none)
    AlcoholStatus.LIGHT_OCCASION -> stringResource(R.string.diary_alcohol_light_occasion)
    AlcoholStatus.HEAVY_OCCASION -> stringResource(R.string.diary_alcohol_heavy_occasion)
    AlcoholStatus.STRESS_COPING -> stringResource(R.string.diary_alcohol_stress_coping)
}

@Composable
internal fun medicationStatusLabel(status: MedicationStatus): String = when (status) {
    MedicationStatus.NOT_PRESCRIBED -> stringResource(R.string.diary_medication_not_prescribed)
    MedicationStatus.TAKEN_AS_PRESCRIBED -> stringResource(R.string.diary_medication_taken)
    MedicationStatus.TAKEN_WITH_SIDE_EFFECTS -> stringResource(R.string.diary_medication_side_effects)
    MedicationStatus.MISSED_FORGOT -> stringResource(R.string.diary_medication_missed_forgot)
    MedicationStatus.MISSED_SIDE_EFFECTS -> stringResource(R.string.diary_medication_missed_side_effects)
}

@Composable
internal fun snackbarMessageText(message: DiarySnackbarMessage): String = when (message) {
    DiarySnackbarMessage.ENTRY_SAVED -> stringResource(R.string.diary_snackbar_entry_saved)
    DiarySnackbarMessage.ENTRY_UPDATED -> stringResource(R.string.diary_snackbar_entry_updated)
    DiarySnackbarMessage.ENTRY_DELETED -> stringResource(R.string.diary_snackbar_entry_deleted)
    DiarySnackbarMessage.MOMENT_SAVED -> stringResource(R.string.diary_snackbar_moment_saved)
    DiarySnackbarMessage.MOMENT_UPDATED -> stringResource(R.string.diary_snackbar_moment_updated)
    DiarySnackbarMessage.MOMENT_DELETED -> stringResource(R.string.diary_snackbar_moment_deleted)
    DiarySnackbarMessage.SLEEP_INPUT_INVALID -> stringResource(R.string.diary_snackbar_sleep_invalid)
}
