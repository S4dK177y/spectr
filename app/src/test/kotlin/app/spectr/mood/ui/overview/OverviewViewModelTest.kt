package app.spectr.mood.ui.overview

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
import java.time.YearMonth
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OverviewViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val fixedInstant = Instant.parse("2026-11-21T12:00:00Z")
    private val fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC)
    private val today = LocalDate.of(2026, 11, 21)

    private lateinit var repository: FakeOverviewDiaryRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeOverviewDiaryRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial load computes monthly calendar KPI summary and chart data`() = runTest(testDispatcher) {
        repository.seed(
            DailyEntry(
                id = "e1",
                date = today.minusDays(2),
                mood = -2,
                energy = 7,
                dailyDynamics = DailyDynamics.SWINGS,
                sleepMinutesOverride = 300,
                anxiety = 8,
                irritability = 5,
                symptoms = DailySymptoms(apathy = true, deathThoughts = true),
                alcoholStatus = AlcoholStatus.STRESS_COPING,
                medicationStatus = MedicationStatus.TAKEN_WITH_SIDE_EFFECTS
            ),
            DailyEntry(
                id = "e2",
                date = today,
                mood = 2,
                energy = 6,
                sleepMinutesOverride = 480,
                anxiety = 2,
                irritability = 3,
                symptoms = DailySymptoms(hyperfocus = true)
            )
        )

        val vm = OverviewViewModel(repository, fixedClock)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertEquals(YearMonth.of(2026, 11), state.displayedMonth)
        assertEquals(2, state.calendarGrid.filledDaysInMonth)
        assertEquals(2, state.summary.filledDays)
        assertEquals(0.0, state.summary.averageMood!!, 0.001)
        assertEquals(-2, state.summary.minMood)
        assertEquals(2, state.summary.maxMood)
        assertEquals(1, state.summary.mixedDays)
        assertEquals(1, state.summary.shortSleepDays)
        assertEquals(1, state.summary.intraDaySwingsDays)
        assertEquals(1, state.summary.highAnxietyOrIrritabilityDays)
        assertEquals(1, state.summary.markers.deathThoughtsDays)
        assertEquals(1, state.summary.markers.alcoholStressDays)
        assertEquals(1, state.summary.markers.medicationSideEffectsDays)
        assertEquals(2, state.chartData.points.size)
    }

    @Test
    fun `month navigation clamps future months and supports month-year picker`() = runTest(testDispatcher) {
        val vm = OverviewViewModel(repository, fixedClock)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isCurrentMonthDisplayed)
        assertFalse(vm.uiState.value.canGoNextMonth)

        vm.onIntent(OverviewIntent.NextMonth)
        advanceUntilIdle()
        assertEquals(YearMonth.of(2026, 11), vm.uiState.value.displayedMonth)

        vm.onIntent(OverviewIntent.PreviousMonth)
        advanceUntilIdle()
        assertEquals(YearMonth.of(2026, 10), vm.uiState.value.displayedMonth)
        assertTrue(vm.uiState.value.canGoNextMonth)

        vm.onIntent(OverviewIntent.OpenMonthYearPicker)
        assertTrue(vm.uiState.value.isMonthYearPickerOpen)

        vm.onIntent(OverviewIntent.SelectMonthYear(YearMonth.of(2027, 3)))
        advanceUntilIdle()
        assertEquals(YearMonth.of(2026, 11), vm.uiState.value.displayedMonth)
        assertFalse(vm.uiState.value.isMonthYearPickerOpen)

        vm.onIntent(OverviewIntent.SelectMonthYear(YearMonth.of(2025, 5)))
        advanceUntilIdle()
        assertEquals(YearMonth.of(2025, 5), vm.uiState.value.displayedMonth)

        vm.onIntent(OverviewIntent.JumpToCurrentMonth)
        advanceUntilIdle()
        assertEquals(YearMonth.of(2026, 11), vm.uiState.value.displayedMonth)
    }

    @Test
    fun `period switching filters entries and clears chart point selection`() = runTest(testDispatcher) {
        repository.seed(
            DailyEntry(id = "old", date = today.minusDays(60), mood = -3, energy = 2),
            DailyEntry(id = "mid", date = today.minusDays(20), mood = 0, energy = 5),
            DailyEntry(id = "recent", date = today.minusDays(3), mood = 2, energy = 7)
        )

        val vm = OverviewViewModel(repository, fixedClock)
        advanceUntilIdle()

        // Default 30 days -> mid + recent
        assertEquals(2, vm.uiState.value.chartData.points.size)

        vm.onIntent(OverviewIntent.SelectChartPoint(OverviewChartKind.MOOD, 1))
        assertEquals(1, vm.uiState.value.selectedMoodIndex)

        vm.onIntent(OverviewIntent.SelectPeriod(OverviewPeriodOption.DAYS_14))
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.chartData.points.size)
        assertNull(vm.uiState.value.selectedMoodIndex)

        vm.onIntent(OverviewIntent.SelectPeriod(OverviewPeriodOption.ALL_TIME))
        advanceUntilIdle()
        assertEquals(3, vm.uiState.value.chartData.points.size)
    }
}

private class FakeOverviewDiaryRepository : DiaryRepository {
    private val entries = LinkedHashMap<String, DailyEntry>()
    private val version = MutableStateFlow(0)

    fun seed(vararg items: DailyEntry) {
        items.forEach { entries[it.id] = it }
        version.value += 1
    }

    override fun observeEntries(): Flow<List<DailyEntry>> = version.map {
        entries.values.filter { it.deletedAt == null }.sortedByDescending { it.date }
    }

    override fun observeEntryByDate(date: LocalDate): Flow<DailyEntry?> = version.map {
        entries.values.firstOrNull { it.date == date && it.deletedAt == null }
    }

    override fun observeEntriesInRange(startDate: LocalDate, endDate: LocalDate): Flow<List<DailyEntry>> = version.map {
        entries.values.filter { it.deletedAt == null && !it.date.isBefore(startDate) && !it.date.isAfter(endDate) }
    }

    override suspend fun getActiveEntries(): List<DailyEntry> = entries.values.filter { it.deletedAt == null }
    override suspend fun getEntryByDate(date: LocalDate): DailyEntry? =
        entries.values.firstOrNull { it.date == date && it.deletedAt == null }
    override suspend fun saveEntry(entry: DailyEntry): DailyEntry = entry
    override suspend fun deleteEntry(id: String) = Unit
    override fun observeMomentsByDate(date: LocalDate): Flow<List<MomentCheckIn>> = MutableStateFlow(emptyList())
    override fun observeMomentsInRange(startDate: LocalDate, endDate: LocalDate): Flow<List<MomentCheckIn>> =
        MutableStateFlow(emptyList())
    override suspend fun getActiveMoments(): List<MomentCheckIn> = emptyList()
    override suspend fun saveMoment(moment: MomentCheckIn): MomentCheckIn = moment
    override suspend fun deleteMoment(id: String) = Unit
}
