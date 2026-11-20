package app.spectr.mood.ui.diary

import app.spectr.mood.domain.model.AlcoholStatus
import app.spectr.mood.domain.model.DailyDynamics
import app.spectr.mood.domain.model.DailyEntry
import app.spectr.mood.domain.model.DailySymptoms
import app.spectr.mood.domain.model.MedicationStatus
import app.spectr.mood.domain.model.MomentCheckIn
import app.spectr.mood.domain.repository.DiaryRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DiaryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val fixedInstant = Instant.parse("2026-11-21T14:30:00Z")
    private val fixedClock: Clock = Clock.fixed(fixedInstant, ZoneOffset.UTC)
    private val today: LocalDate = LocalDate.of(2026, 11, 21)

    private lateinit var repository: InMemoryDiaryRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = InMemoryDiaryRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial load populates saved entry and chronologically sorted moments`() = runTest(testDispatcher) {
        val existingEntry = DailyEntry(
            id = "e-today",
            date = today,
            mood = -2,
            energy = 4,
            dailyDynamics = DailyDynamics.WORSE_MORNING,
            anxiety = 6,
            irritability = 3,
            symptoms = DailySymptoms(apathy = true, brainFog = true),
            alcoholStatus = AlcoholStatus.LIGHT_OCCASION,
            medicationStatus = MedicationStatus.TAKEN_AS_PRESCRIBED,
            note = "Непростое утро",
            stepsCount = 8_500
        )
        repository.seedEntry(existingEntry)
        repository.seedMoment(
            MomentCheckIn(
                id = "m-later",
                entryDate = today,
                timestampEpochMs = fixedInstant.toEpochMilli(),
                mood = 1,
                note = "Стало легче"
            )
        )
        repository.seedMoment(
            MomentCheckIn(
                id = "m-earlier",
                entryDate = today,
                timestampEpochMs = fixedInstant.minusSeconds(3_600).toEpochMilli(),
                mood = -2,
                energy = 3,
                note = "Утром тяжело"
            )
        )

        val viewModel = DiaryViewModel(repository, fixedClock)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.hasSavedEntry)
        assertFalse(state.hasUnsavedChanges)
        assertEquals("e-today", state.savedEntryId)
        assertEquals(8_500, state.stepsCount)
        assertEquals(-2, state.form.mood)
        assertEquals(DailyDynamics.WORSE_MORNING, state.form.dailyDynamics)
        assertTrue(state.form.symptoms.apathy)
        assertTrue(state.form.symptoms.brainFog)
        assertEquals(listOf("m-earlier", "m-later"), state.moments.map { it.id })
    }

    @Test
    fun `date navigation supports yesterday today prev next and clamps future dates`() = runTest(testDispatcher) {
        val viewModel = DiaryViewModel(repository, fixedClock)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isToday)
        assertFalse(viewModel.uiState.value.canGoNextDay)

        viewModel.onIntent(DiaryIntent.NextDay)
        advanceUntilIdle()
        assertEquals(today, viewModel.uiState.value.selectedDate)

        viewModel.onIntent(DiaryIntent.SelectDate(today.plusDays(5)))
        advanceUntilIdle()
        assertEquals(today, viewModel.uiState.value.selectedDate)

        viewModel.onIntent(DiaryIntent.SelectYesterday)
        advanceUntilIdle()
        assertEquals(today.minusDays(1), viewModel.uiState.value.selectedDate)
        assertTrue(viewModel.uiState.value.isYesterday)
        assertTrue(viewModel.uiState.value.canGoNextDay)

        viewModel.onIntent(DiaryIntent.PreviousDay)
        advanceUntilIdle()
        assertEquals(today.minusDays(2), viewModel.uiState.value.selectedDate)

        viewModel.onIntent(DiaryIntent.NextDay)
        advanceUntilIdle()
        assertEquals(today.minusDays(1), viewModel.uiState.value.selectedDate)

        viewModel.onIntent(DiaryIntent.SelectToday)
        advanceUntilIdle()
        assertEquals(today, viewModel.uiState.value.selectedDate)
    }

    @Test
    fun `mixed day badge triggers accurately from mood energy irritability and racing thoughts`() =
        runTest(testDispatcher) {
            val viewModel = DiaryViewModel(repository, fixedClock)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.form.isMixedDay)

            viewModel.onIntent(DiaryIntent.UpdateMood(-1))
            viewModel.onIntent(DiaryIntent.UpdateEnergy(7))
            assertTrue(viewModel.uiState.value.form.isMixedDay)

            viewModel.onIntent(DiaryIntent.UpdateEnergy(4))
            assertFalse(viewModel.uiState.value.form.isMixedDay)

            viewModel.onIntent(DiaryIntent.UpdateIrritability(7))
            assertTrue(viewModel.uiState.value.form.isMixedDay)

            viewModel.onIntent(DiaryIntent.UpdateIrritability(2))
            viewModel.onIntent(DiaryIntent.ToggleSymptom(SymptomType.RACING_THOUGHTS))
            assertTrue(viewModel.uiState.value.form.isMixedDay)

            viewModel.onIntent(DiaryIntent.UpdateMood(0))
            assertFalse(viewModel.uiState.value.form.isMixedDay)
        }

    @Test
    fun `sleep interval constructor supports split sleep deductions edit remove and persistence`() =
        runTest(testDispatcher) {
            val viewModel = DiaryViewModel(repository, fixedClock)
            advanceUntilIdle()

            // Add first split interval: 22:00 - 01:30 = 210 min (3h 30m)
            viewModel.onIntent(DiaryIntent.OpenNewSleepInterval)
            viewModel.onIntent(
                DiaryIntent.UpdateSleepIntervalDraft(
                    startMinuteOfDay = 22 * 60,
                    endMinuteOfDay = 90,
                    awakeDeductionMin = 0
                )
            )
            viewModel.onIntent(DiaryIntent.ConfirmSleepInterval)

            assertEquals(1, viewModel.uiState.value.form.sleepIntervals.size)
            assertEquals(210, viewModel.uiState.value.form.effectiveSleepMinutes)
            assertTrue(viewModel.uiState.value.form.isShortSleep)

            // Add second interval: 09:00 - 12:30 (-20m) = 190 min -> total 400 min (6h 40m)
            viewModel.onIntent(DiaryIntent.OpenNewSleepInterval)
            viewModel.onIntent(
                DiaryIntent.UpdateSleepIntervalDraft(
                    startMinuteOfDay = 9 * 60,
                    endMinuteOfDay = 12 * 60 + 30,
                    awakeDeductionMin = 20
                )
            )
            viewModel.onIntent(DiaryIntent.ConfirmSleepInterval)

            assertEquals(2, viewModel.uiState.value.form.sleepIntervals.size)
            assertEquals(400, viewModel.uiState.value.form.effectiveSleepMinutes)
            assertFalse(viewModel.uiState.value.form.isShortSleep)

            // Edit first interval to add 30 min deduction -> total 370 min
            val firstId = viewModel.uiState.value.form.sleepIntervals.first().id
            viewModel.onIntent(DiaryIntent.OpenEditSleepInterval(firstId))
            viewModel.onIntent(
                DiaryIntent.UpdateSleepIntervalDraft(
                    startMinuteOfDay = 22 * 60,
                    endMinuteOfDay = 90,
                    awakeDeductionMin = 30
                )
            )
            viewModel.onIntent(DiaryIntent.ConfirmSleepInterval)
            assertEquals(370, viewModel.uiState.value.form.effectiveSleepMinutes)

            // Save and verify persisted intervals
            viewModel.onIntent(DiaryIntent.SaveDailyEntry)
            advanceUntilIdle()

            val persisted = repository.getEntryByDate(today)
            assertNotNull(persisted)
            assertEquals(2, persisted!!.sleepIntervals.size)
            assertNull(persisted.sleepMinutesOverride)
            assertEquals(DiarySnackbarMessage.ENTRY_SAVED, viewModel.uiState.value.snackbarMessage)

            // Remove second interval and update
            val secondId = viewModel.uiState.value.form.sleepIntervals[1].id
            viewModel.onIntent(DiaryIntent.RemoveSleepInterval(secondId))
            assertEquals(180, viewModel.uiState.value.form.effectiveSleepMinutes)
            viewModel.onIntent(DiaryIntent.SaveDailyEntry)
            advanceUntilIdle()

            assertEquals(1, repository.getEntryByDate(today)!!.sleepIntervals.size)
            assertEquals(DiarySnackbarMessage.ENTRY_UPDATED, viewModel.uiState.value.snackbarMessage)
        }

    @Test
    fun `manual sleep mode validates duration persists override and restores cleanly`() = runTest(testDispatcher) {
        val viewModel = DiaryViewModel(repository, fixedClock)
        advanceUntilIdle()

        viewModel.onIntent(DiaryIntent.UpdateSleepInputMode(SleepInputMode.MANUAL_HOURS))
        viewModel.onIntent(DiaryIntent.UpdateManualSleepText("неизвестно"))
        assertTrue(viewModel.uiState.value.form.isManualSleepInvalid)
        assertFalse(viewModel.uiState.value.canSaveEntry)

        viewModel.onIntent(DiaryIntent.SaveDailyEntry)
        advanceUntilIdle()
        assertEquals(DiarySnackbarMessage.SLEEP_INPUT_INVALID, viewModel.uiState.value.snackbarMessage)
        assertNull(repository.getEntryByDate(today))

        viewModel.onIntent(DiaryIntent.UpdateManualSleepText("7.5"))
        assertFalse(viewModel.uiState.value.form.isManualSleepInvalid)
        assertEquals(450, viewModel.uiState.value.form.effectiveSleepMinutes)

        viewModel.onIntent(DiaryIntent.SaveDailyEntry)
        advanceUntilIdle()

        val saved = repository.getEntryByDate(today)
        assertNotNull(saved)
        assertEquals(450, saved!!.sleepMinutesOverride)
        assertTrue(saved.sleepIntervals.isEmpty())

        // Reload in a fresh ViewModel to confirm MANUAL_HOURS mode restoration
        val reloadedVm = DiaryViewModel(repository, fixedClock)
        advanceUntilIdle()
        assertEquals(SleepInputMode.MANUAL_HOURS, reloadedVm.uiState.value.form.sleepInputMode)
        assertEquals("7ч 30м", reloadedVm.uiState.value.form.manualSleepText)
        assertEquals(450, reloadedVm.uiState.value.form.effectiveSleepMinutes)
    }

    @Test
    fun `save preserves stepsCount and delete soft-deletes entry resetting form`() = runTest(testDispatcher) {
        repository.seedEntry(
            DailyEntry(
                id = "e-steps",
                date = today,
                mood = 1,
                energy = 6,
                stepsCount = 10_240
            )
        )
        val viewModel = DiaryViewModel(repository, fixedClock)
        advanceUntilIdle()

        viewModel.onIntent(DiaryIntent.UpdateMood(2))
        viewModel.onIntent(DiaryIntent.UpdateAlcohol(AlcoholStatus.STRESS_COPING))
        viewModel.onIntent(DiaryIntent.UpdateMedication(MedicationStatus.TAKEN_WITH_SIDE_EFFECTS))
        viewModel.onIntent(DiaryIntent.UpdateNote("  Важная заметка  "))
        assertTrue(viewModel.uiState.value.hasUnsavedChanges)

        viewModel.onIntent(DiaryIntent.SaveDailyEntry)
        advanceUntilIdle()

        val updated = repository.getEntryByDate(today)!!
        assertEquals("e-steps", updated.id)
        assertEquals(2, updated.mood)
        assertEquals(10_240, updated.stepsCount)
        assertEquals("Важная заметка", updated.note)
        assertFalse(viewModel.uiState.value.hasUnsavedChanges)

        viewModel.onIntent(DiaryIntent.RequestDeleteEntry)
        assertTrue(viewModel.uiState.value.isDeleteEntryDialogOpen)
        viewModel.onIntent(DiaryIntent.ConfirmDeleteEntry)
        advanceUntilIdle()

        assertNull(repository.getEntryByDate(today))
        assertFalse(viewModel.uiState.value.hasSavedEntry)
        assertEquals(0, viewModel.uiState.value.form.mood)
        assertEquals(DiarySnackbarMessage.ENTRY_DELETED, viewModel.uiState.value.snackbarMessage)
    }

    @Test
    fun `moment check-ins support quick create time edit update and delete`() = runTest(testDispatcher) {
        val viewModel = DiaryViewModel(repository, fixedClock)
        advanceUntilIdle()

        viewModel.onIntent(DiaryIntent.OpenNewMoment)
        val editor = viewModel.uiState.value.momentEditor
        assertNotNull(editor)
        assertEquals(fixedInstant.toEpochMilli(), editor!!.timestampEpochMs)

        viewModel.onIntent(DiaryIntent.UpdateMomentMood(-3))
        viewModel.onIntent(DiaryIntent.UpdateMomentEnergy(4))
        viewModel.onIntent(DiaryIntent.UpdateMomentAnxiety(8))
        viewModel.onIntent(DiaryIntent.UpdateMomentIrritability(6))
        viewModel.onIntent(DiaryIntent.UpdateMomentNote("Накрыла тревога"))
        viewModel.onIntent(DiaryIntent.SaveMoment)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.momentEditor)
        assertEquals(DiarySnackbarMessage.MOMENT_SAVED, viewModel.uiState.value.snackbarMessage)
        assertEquals(1, viewModel.uiState.value.moments.size)

        val savedMoment = viewModel.uiState.value.moments.single()
        assertEquals(-3, savedMoment.mood)
        assertEquals(4, savedMoment.energy)
        assertEquals(8, savedMoment.anxiety)
        assertEquals(6, savedMoment.irritability)
        assertEquals("Накрыла тревога", savedMoment.note)

        // Edit moment time and clear optional anxiety
        viewModel.onIntent(DiaryIntent.OpenEditMoment(savedMoment))
        viewModel.onIntent(DiaryIntent.UpdateMomentTime(hour = 9, minute = 15))
        viewModel.onIntent(DiaryIntent.UpdateMomentAnxiety(null))
        viewModel.onIntent(DiaryIntent.SaveMoment)
        advanceUntilIdle()

        val updatedMoment = viewModel.uiState.value.moments.single()
        val expectedTime = today.atTime(LocalTime.of(9, 15)).atZone(ZoneOffset.UTC).toInstant().toEpochMilli()
        assertEquals(expectedTime, updatedMoment.timestampEpochMs)
        assertNull(updatedMoment.anxiety)
        assertEquals(DiarySnackbarMessage.MOMENT_UPDATED, viewModel.uiState.value.snackbarMessage)

        // Delete moment
        viewModel.onIntent(DiaryIntent.DeleteMoment(updatedMoment.id))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.moments.isEmpty())
        assertEquals(DiarySnackbarMessage.MOMENT_DELETED, viewModel.uiState.value.snackbarMessage)
    }

    @Test
    fun `help topics date picker and snackbar consume toggle cleanly`() = runTest(testDispatcher) {
        val viewModel = DiaryViewModel(repository, fixedClock)
        advanceUntilIdle()

        viewModel.onIntent(DiaryIntent.OpenHelp(DiaryHelpTopic.MoodScale))
        assertEquals(DiaryHelpTopic.MoodScale, viewModel.uiState.value.activeHelpTopic)

        viewModel.onIntent(DiaryIntent.OpenHelp(DiaryHelpTopic.SingleSymptom(SymptomType.DEATH_THOUGHTS)))
        assertEquals(
            DiaryHelpTopic.SingleSymptom(SymptomType.DEATH_THOUGHTS),
            viewModel.uiState.value.activeHelpTopic
        )

        viewModel.onIntent(DiaryIntent.DismissHelp)
        assertNull(viewModel.uiState.value.activeHelpTopic)

        viewModel.onIntent(DiaryIntent.OpenDatePicker)
        assertTrue(viewModel.uiState.value.isDatePickerOpen)

        viewModel.onIntent(DiaryIntent.DismissDatePicker)
        assertFalse(viewModel.uiState.value.isDatePickerOpen)
    }
}

private class InMemoryDiaryRepository : DiaryRepository {

    private val entries = LinkedHashMap<String, DailyEntry>()
    private val moments = LinkedHashMap<String, MomentCheckIn>()
    private val version = MutableStateFlow(0)

    fun seedEntry(entry: DailyEntry) {
        entries[entry.id] = entry
        version.value += 1
    }

    fun seedMoment(moment: MomentCheckIn) {
        moments[moment.id] = moment
        version.value += 1
    }

    override fun observeEntries(): Flow<List<DailyEntry>> = version.map {
        entries.values.filter { it.deletedAt == null }.sortedByDescending { it.date }
    }

    override fun observeEntryByDate(date: LocalDate): Flow<DailyEntry?> = version.map {
        entries.values.firstOrNull { it.date == date && it.deletedAt == null }
    }

    override fun observeEntriesInRange(startDate: LocalDate, endDate: LocalDate): Flow<List<DailyEntry>> = version.map {
        entries.values
            .filter { it.deletedAt == null && !it.date.isBefore(startDate) && !it.date.isAfter(endDate) }
            .sortedBy { it.date }
    }

    override suspend fun getActiveEntries(): List<DailyEntry> =
        entries.values.filter { it.deletedAt == null }.sortedByDescending { it.date }

    override suspend fun getEntryByDate(date: LocalDate): DailyEntry? =
        entries.values.firstOrNull { it.date == date && it.deletedAt == null }

    override suspend fun saveEntry(entry: DailyEntry): DailyEntry {
        val existing = getEntryByDate(entry.date)
        val effectiveId = existing?.id ?: entry.id
        val saved = entry.copy(
            id = effectiveId,
            sleepIntervals = entry.sleepIntervals.map { it.copy(entryId = effectiveId) }
        )
        entries[effectiveId] = saved
        version.value += 1
        return saved
    }

    override suspend fun deleteEntry(id: String) {
        entries[id]?.let { entries[id] = it.copy(deletedAt = 1L) }
        version.value += 1
    }

    override fun observeMomentsByDate(date: LocalDate): Flow<List<MomentCheckIn>> = version.map {
        moments.values.filter { it.entryDate == date && it.deletedAt == null }.sortedBy { it.timestampEpochMs }
    }

    override fun observeMomentsInRange(startDate: LocalDate, endDate: LocalDate): Flow<List<MomentCheckIn>> =
        version.map {
            moments.values
                .filter {
                    it.deletedAt == null && !it.entryDate.isBefore(startDate) && !it.entryDate.isAfter(endDate)
                }
                .sortedBy { it.timestampEpochMs }
        }

    override suspend fun getActiveMoments(): List<MomentCheckIn> =
        moments.values.filter { it.deletedAt == null }.sortedBy { it.timestampEpochMs }

    override suspend fun saveMoment(moment: MomentCheckIn): MomentCheckIn {
        moments[moment.id] = moment
        version.value += 1
        return moment
    }

    override suspend fun deleteMoment(id: String) {
        moments[id]?.let { moments[id] = it.copy(deletedAt = 1L) }
        version.value += 1
    }
}
