package app.spectr.mood.ui.diary

import androidx.compose.runtime.Immutable
import app.spectr.mood.domain.calculator.AnalyticsCalculator
import app.spectr.mood.domain.calculator.SleepDurationCalculator
import app.spectr.mood.domain.calculator.SleepParseResult
import app.spectr.mood.domain.model.AlcoholStatus
import app.spectr.mood.domain.model.DailyDynamics
import app.spectr.mood.domain.model.DailyEntry
import app.spectr.mood.domain.model.DailySymptoms
import app.spectr.mood.domain.model.MedicationStatus
import app.spectr.mood.domain.model.MomentCheckIn
import app.spectr.mood.domain.model.SleepInterval
import app.spectr.mood.domain.model.SleepSource
import java.time.LocalDate
import java.util.UUID

enum class SleepInputMode {
    INTERVALS,
    MANUAL_HOURS
}

enum class SymptomType {
    APATHY,
    SELF_CRITICISM,
    BRAIN_FOG,
    BINGE_EATING,
    DEATH_THOUGHTS,
    HYPERFOCUS,
    RACING_THOUGHTS
}

sealed interface DiaryHelpTopic {
    data object MoodScale : DiaryHelpTopic
    data object AllSymptoms : DiaryHelpTopic
    data class SingleSymptom(val symptom: SymptomType) : DiaryHelpTopic
}

enum class DiarySnackbarMessage {
    ENTRY_SAVED,
    ENTRY_UPDATED,
    ENTRY_DELETED,
    MOMENT_SAVED,
    MOMENT_UPDATED,
    MOMENT_DELETED,
    SLEEP_INPUT_INVALID
}

@Immutable
data class SleepIntervalDraft(
    val id: String = UUID.randomUUID().toString(),
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
    val awakeDeductionMin: Int = 0,
    val source: SleepSource = SleepSource.MANUAL
) {
    val rawMinutes: Int
        get() = SleepDurationCalculator.intervalRawMinutes(startMinuteOfDay, endMinuteOfDay)

    val netMinutes: Int
        get() = SleepDurationCalculator.intervalNetMinutes(
            startMinuteOfDay = startMinuteOfDay,
            endMinuteOfDay = endMinuteOfDay,
            awakeDeductionMin = awakeDeductionMin
        )
}

@Immutable
data class SleepIntervalEditorState(
    val editingId: String? = null,
    val startMinuteOfDay: Int = DEFAULT_SLEEP_START_MIN,
    val endMinuteOfDay: Int = DEFAULT_SLEEP_END_MIN,
    val awakeDeductionMin: Int = 0
) {
    val rawMinutes: Int
        get() = SleepDurationCalculator.intervalRawMinutes(startMinuteOfDay, endMinuteOfDay)

    val netMinutes: Int
        get() = SleepDurationCalculator.intervalNetMinutes(
            startMinuteOfDay = startMinuteOfDay,
            endMinuteOfDay = endMinuteOfDay,
            awakeDeductionMin = awakeDeductionMin
        )

    companion object {
        const val DEFAULT_SLEEP_START_MIN = 23 * 60
        const val DEFAULT_SLEEP_END_MIN = 7 * 60
    }
}

@Immutable
data class MomentEditorState(
    val editingId: String? = null,
    val timestampEpochMs: Long,
    val mood: Int = 0,
    val energy: Int? = null,
    val anxiety: Int? = null,
    val irritability: Int? = null,
    val note: String = ""
)

@Immutable
data class DailyEntryFormState(
    val mood: Int = 0,
    val dailyDynamics: DailyDynamics = DailyDynamics.STEADY,
    val sleepInputMode: SleepInputMode = SleepInputMode.INTERVALS,
    val sleepIntervals: List<SleepIntervalDraft> = emptyList(),
    val manualSleepText: String = "",
    val sleepNote: String = "",
    val energy: Int = DEFAULT_ENERGY,
    val anxiety: Int = 0,
    val irritability: Int = 0,
    val symptoms: DailySymptoms = DailySymptoms(),
    val alcoholStatus: AlcoholStatus = AlcoholStatus.NONE,
    val medicationStatus: MedicationStatus = MedicationStatus.NOT_PRESCRIBED,
    val note: String = ""
) {
    val manualSleepParseResult: SleepParseResult
        get() = SleepDurationCalculator.parseSleepText(manualSleepText)

    val isManualSleepInvalid: Boolean
        get() = sleepInputMode == SleepInputMode.MANUAL_HOURS &&
            manualSleepText.isNotBlank() &&
            !manualSleepParseResult.isRecognized

    val hasSleepData: Boolean
        get() = when (sleepInputMode) {
            SleepInputMode.INTERVALS -> sleepIntervals.isNotEmpty()
            SleepInputMode.MANUAL_HOURS -> manualSleepParseResult.isRecognized
        }

    val effectiveSleepMinutes: Int
        get() = when (sleepInputMode) {
            SleepInputMode.INTERVALS -> sleepIntervals.sumOf { it.netMinutes }
            SleepInputMode.MANUAL_HOURS -> if (manualSleepParseResult.isRecognized) {
                manualSleepParseResult.totalMinutes
            } else {
                0
            }
        }

    val isShortSleep: Boolean
        get() = hasSleepData && effectiveSleepMinutes < SHORT_SLEEP_THRESHOLD_MINUTES

    val isMixedDay: Boolean
        get() = AnalyticsCalculator.isMixedDay(
            DailyEntry(
                date = REFERENCE_DATE,
                mood = mood,
                energy = energy,
                irritability = irritability,
                symptoms = symptoms
            )
        )

    fun isSymptomSelected(symptom: SymptomType): Boolean = when (symptom) {
        SymptomType.APATHY -> symptoms.apathy
        SymptomType.SELF_CRITICISM -> symptoms.selfCriticism
        SymptomType.BRAIN_FOG -> symptoms.brainFog
        SymptomType.BINGE_EATING -> symptoms.bingeEating
        SymptomType.DEATH_THOUGHTS -> symptoms.deathThoughts
        SymptomType.HYPERFOCUS -> symptoms.hyperfocus
        SymptomType.RACING_THOUGHTS -> symptoms.racingThoughts
    }

    fun toggleSymptom(symptom: SymptomType): DailyEntryFormState {
        val updated = when (symptom) {
            SymptomType.APATHY -> symptoms.copy(apathy = !symptoms.apathy)
            SymptomType.SELF_CRITICISM -> symptoms.copy(selfCriticism = !symptoms.selfCriticism)
            SymptomType.BRAIN_FOG -> symptoms.copy(brainFog = !symptoms.brainFog)
            SymptomType.BINGE_EATING -> symptoms.copy(bingeEating = !symptoms.bingeEating)
            SymptomType.DEATH_THOUGHTS -> symptoms.copy(deathThoughts = !symptoms.deathThoughts)
            SymptomType.HYPERFOCUS -> symptoms.copy(hyperfocus = !symptoms.hyperfocus)
            SymptomType.RACING_THOUGHTS -> symptoms.copy(racingThoughts = !symptoms.racingThoughts)
        }
        return copy(symptoms = updated)
    }

    fun toDailyEntry(date: LocalDate, existingId: String? = null, stepsCount: Int? = null): DailyEntry {
        val entryId = existingId ?: UUID.randomUUID().toString()
        val resolvedIntervals: List<SleepInterval>
        val resolvedOverrideMinutes: Int?

        when (sleepInputMode) {
            SleepInputMode.INTERVALS -> {
                resolvedIntervals = sleepIntervals.map { draft ->
                    SleepInterval(
                        id = draft.id,
                        entryId = entryId,
                        startMinuteOfDay = draft.startMinuteOfDay,
                        endMinuteOfDay = draft.endMinuteOfDay,
                        awakeDeductionMin = draft.awakeDeductionMin,
                        source = draft.source
                    )
                }
                resolvedOverrideMinutes = null
            }

            SleepInputMode.MANUAL_HOURS -> {
                val parsed = manualSleepParseResult
                if (parsed.isRecognized && parsed.intervals.isNotEmpty()) {
                    resolvedIntervals = parsed.intervals.map { item ->
                        SleepInterval(
                            entryId = entryId,
                            startMinuteOfDay = item.startMinuteOfDay,
                            endMinuteOfDay = item.endMinuteOfDay,
                            awakeDeductionMin = item.awakeDeductionMin,
                            source = SleepSource.MANUAL
                        )
                    }
                    resolvedOverrideMinutes = null
                } else {
                    resolvedIntervals = emptyList()
                    resolvedOverrideMinutes = if (parsed.isRecognized) parsed.overrideMinutes else null
                }
            }
        }

        return DailyEntry(
            id = entryId,
            date = date,
            mood = mood.coerceIn(DailyEntry.MIN_MOOD, DailyEntry.MAX_MOOD),
            energy = energy.coerceIn(DailyEntry.MIN_RATING, DailyEntry.MAX_RATING),
            dailyDynamics = dailyDynamics,
            sleepIntervals = resolvedIntervals,
            sleepMinutesOverride = resolvedOverrideMinutes,
            sleepNote = sleepNote.trim(),
            anxiety = anxiety.coerceIn(DailyEntry.MIN_RATING, DailyEntry.MAX_RATING),
            irritability = irritability.coerceIn(DailyEntry.MIN_RATING, DailyEntry.MAX_RATING),
            symptoms = symptoms,
            alcoholStatus = alcoholStatus,
            medicationStatus = medicationStatus,
            note = note.trim(),
            stepsCount = stepsCount
        )
    }

    companion object {
        const val DEFAULT_ENERGY = 5
        private const val SHORT_SLEEP_THRESHOLD_MINUTES = 360
        private const val MINUTES_PER_HOUR = 60
        private val REFERENCE_DATE: LocalDate = LocalDate.of(2026, 1, 1)

        fun fromEntry(entry: DailyEntry): DailyEntryFormState {
            val activeIntervals = entry.sleepIntervals.filter { it.deletedAt == null }
            val useManual = entry.sleepMinutesOverride != null && activeIntervals.isEmpty()
            return DailyEntryFormState(
                mood = entry.mood,
                dailyDynamics = entry.dailyDynamics,
                sleepInputMode = if (useManual) SleepInputMode.MANUAL_HOURS else SleepInputMode.INTERVALS,
                sleepIntervals = activeIntervals.map { interval ->
                    SleepIntervalDraft(
                        id = interval.id,
                        startMinuteOfDay = interval.startMinuteOfDay,
                        endMinuteOfDay = interval.endMinuteOfDay,
                        awakeDeductionMin = interval.awakeDeductionMin,
                        source = interval.source
                    )
                },
                manualSleepText = entry.sleepMinutesOverride?.let(::formatMinutesAsEditableText).orEmpty(),
                sleepNote = entry.sleepNote,
                energy = entry.energy,
                anxiety = entry.anxiety,
                irritability = entry.irritability,
                symptoms = entry.symptoms,
                alcoholStatus = entry.alcoholStatus,
                medicationStatus = entry.medicationStatus,
                note = entry.note
            )
        }

        internal fun formatMinutesAsEditableText(totalMinutes: Int): String {
            val safe = totalMinutes.coerceAtLeast(0)
            val hours = safe / MINUTES_PER_HOUR
            val minutes = safe % MINUTES_PER_HOUR
            return when {
                hours > 0 && minutes > 0 -> "${hours}ч ${minutes}м"
                hours > 0 -> "${hours}ч"
                else -> "${minutes}м"
            }
        }
    }
}

@Immutable
data class DiaryUiState(
    val selectedDate: LocalDate,
    val today: LocalDate,
    val isLoading: Boolean = true,
    val hasSavedEntry: Boolean = false,
    val hasUnsavedChanges: Boolean = false,
    val savedEntryId: String? = null,
    val stepsCount: Int? = null,
    val form: DailyEntryFormState = DailyEntryFormState(),
    val moments: List<MomentCheckIn> = emptyList(),
    val momentEditor: MomentEditorState? = null,
    val sleepIntervalEditor: SleepIntervalEditorState? = null,
    val activeHelpTopic: DiaryHelpTopic? = null,
    val isDatePickerOpen: Boolean = false,
    val isDeleteEntryDialogOpen: Boolean = false,
    val snackbarMessage: DiarySnackbarMessage? = null
) {
    val isToday: Boolean
        get() = selectedDate == today

    val isYesterday: Boolean
        get() = selectedDate == today.minusDays(1)

    val canGoNextDay: Boolean
        get() = selectedDate.isBefore(today)

    val canSaveEntry: Boolean
        get() = !form.isManualSleepInvalid
}
