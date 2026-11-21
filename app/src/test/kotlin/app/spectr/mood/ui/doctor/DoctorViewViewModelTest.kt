package app.spectr.mood.ui.doctor

import app.spectr.mood.domain.model.DailyEntry
import app.spectr.mood.domain.model.MomentCheckIn
import app.spectr.mood.domain.model.ScaleAssessment
import app.spectr.mood.domain.model.ScaleType
import app.spectr.mood.platform.notifications.FakeReminderDiaryRepository
import app.spectr.mood.platform.notifications.FakeReminderScaleRepository
import app.spectr.mood.ui.overview.OverviewPeriodOption
import java.time.Clock
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DoctorViewViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val zone = ZoneOffset.UTC
    private val fixedNow = ZonedDateTime.of(2026, 11, 21, 15, 0, 0, 0, zone)
    private val fixedClock = Clock.fixed(fixedNow.toInstant(), zone)
    private val today = fixedNow.toLocalDate()

    private lateinit var diaryRepo: FakeReminderDiaryRepository
    private lateinit var scaleRepo: FakeReminderScaleRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        diaryRepo = FakeReminderDiaryRepository()
        scaleRepo = FakeReminderScaleRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `doctor view strips all private notes and moments and supports period and month browsing`() =
        runTest(testDispatcher) {
            diaryRepo.seedEntry(
                DailyEntry(
                    id = "e1",
                    date = today.minusDays(20),
                    mood = -2,
                    energy = 4,
                    sleepNote = "Секретный комментарий сна",
                    note = "Личная заметка"
                )
            )
            diaryRepo.seedEntry(
                DailyEntry(
                    id = "e2",
                    date = today,
                    mood = 1,
                    energy = 6,
                    note = "Ещё одна личная заметка"
                )
            )
            diaryRepo.saveMoment(
                MomentCheckIn(
                    id = "m1",
                    entryDate = today,
                    timestampEpochMs = fixedNow.toInstant().toEpochMilli(),
                    mood = 2,
                    note = "Скрытый момент"
                )
            )
            scaleRepo.saveAssessment(
                ScaleAssessment(
                    id = "s1",
                    scaleType = ScaleType.PHQ9,
                    date = today.minusDays(3),
                    answers = List(9) { 1 },
                    totalScore = 9,
                    isConfirmed = true
                )
            )

            val vm = DoctorViewViewModel(
                diaryRepository = diaryRepo,
                scaleAssessmentRepository = scaleRepo,
                clock = fixedClock
            )
            advanceUntilIdle()

            // Default 30d includes both entries, zero personal notes or moments leaked
            assertEquals(2, vm.uiState.value.dailyRows.size)
            vm.uiState.value.dailyRows.forEach { row ->
                assertEquals("", row.note)
                assertEquals("", row.sleepNote)
                assertTrue(row.moments.isEmpty())
            }
            val phq9Overview = vm.uiState.value.scalesOverview.first { it.scaleType == ScaleType.PHQ9 }
            assertEquals(9, phq9Overview.latestConfirmed?.totalScore)

            // Switch to 14d -> only today's entry remains
            vm.onIntent(DoctorViewIntent.SelectPeriod(OverviewPeriodOption.DAYS_14))
            assertEquals(1, vm.uiState.value.dailyRows.size)

            // Navigate calendar month backwards & clamp future navigation
            vm.onIntent(DoctorViewIntent.PreviousMonth)
            assertEquals(YearMonth.of(2026, 10), vm.uiState.value.displayedMonth)
            vm.onIntent(DoctorViewIntent.NextMonth)
            assertEquals(YearMonth.of(2026, 11), vm.uiState.value.displayedMonth)
            vm.onIntent(DoctorViewIntent.NextMonth)
            assertEquals(YearMonth.of(2026, 11), vm.uiState.value.displayedMonth)

            // Toggle sanitized daily log expansion
            assertFalse(vm.uiState.value.isDailyLogExpanded)
            vm.onIntent(DoctorViewIntent.ToggleDailyLogExpanded)
            assertTrue(vm.uiState.value.isDailyLogExpanded)
        }
}
