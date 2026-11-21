package app.spectr.mood.ui.scales

import app.spectr.mood.domain.model.DailyEntry
import app.spectr.mood.domain.model.DailySymptoms
import app.spectr.mood.domain.model.MomentCheckIn
import app.spectr.mood.domain.model.ScaleAssessment
import app.spectr.mood.domain.model.ScaleType
import app.spectr.mood.domain.repository.DiaryRepository
import app.spectr.mood.domain.repository.ScaleAssessmentRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
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
class ScalesViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val fixedInstant = Instant.parse("2026-11-21T16:00:00Z")
    private val fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC)
    private val today = LocalDate.of(2026, 11, 21)

    private lateinit var diaryRepository: FakeScalesDiaryRepository
    private lateinit var scaleRepository: FakeScaleAssessmentRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        diaryRepository = FakeScalesDiaryRepository()
        scaleRepository = FakeScaleAssessmentRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `auto scales compute from diary and prefill confirmation editor cleanly`() = runTest(testDispatcher) {
        // Seed 7 days with apathy, mood <= -2, high anxiety, and racing thoughts
        repeat(7) { offset ->
            diaryRepository.seed(
                DailyEntry(
                    id = "e$offset",
                    date = today.minusDays(offset.toLong()),
                    mood = -2,
                    energy = 2,
                    sleepMinutesOverride = 300,
                    anxiety = 7,
                    irritability = 6,
                    symptoms = DailySymptoms(apathy = true, racingThoughts = true)
                )
            )
        }

        val vm = ScalesViewModel(diaryRepository, scaleRepository, fixedClock)
        advanceUntilIdle()

        val snapshot = vm.uiState.value.autoSnapshot
        assertNotNull(snapshot)
        assertEquals(today, snapshot!!.anchorDate)
        assertTrue(snapshot.phq9.totalScore > 0)
        assertTrue(snapshot.gad7.totalScore > 0)

        vm.onIntent(ScalesIntent.OpenConfirmScale(ScaleType.PHQ9))
        val editor = vm.uiState.value.editor
        assertNotNull(editor)
        assertEquals(snapshot.phq9.itemScores, editor!!.answers)
        assertEquals(snapshot.phq9.totalScore, editor.totalScore)
        assertFalse(editor.differsFromAutoBaseline)

        // Modify item 0 and test reset back to auto
        val modifiedScore = if (editor.answers[0] == 3) 0 else 3
        vm.onIntent(ScalesIntent.UpdateEditorAnswer(0, modifiedScore))
        assertTrue(vm.uiState.value.editor!!.differsFromAutoBaseline)

        vm.onIntent(ScalesIntent.ResetEditorToAuto)
        assertFalse(vm.uiState.value.editor!!.differsFromAutoBaseline)
        assertEquals(snapshot.phq9.itemScores, vm.uiState.value.editor!!.answers)
    }

    @Test
    fun `manual confirmation saves edits computes history deltas filters and deletes`() = runTest(testDispatcher) {
        val vm = ScalesViewModel(diaryRepository, scaleRepository, fixedClock)
        advanceUntilIdle()
        assertNull(vm.uiState.value.autoSnapshot)

        // Create first confirmed PHQ-9 (score 12) on Nov 7
        vm.onIntent(ScalesIntent.OpenConfirmScale(ScaleType.PHQ9))
        vm.onIntent(ScalesIntent.UpdateEditorDate(today.minusDays(14)))
        vm.onIntent(ScalesIntent.UpdateEditorAnswer(0, 3))
        vm.onIntent(ScalesIntent.UpdateEditorAnswer(1, 3))
        vm.onIntent(ScalesIntent.UpdateEditorAnswer(2, 3))
        vm.onIntent(ScalesIntent.UpdateEditorAnswer(3, 3))
        vm.onIntent(ScalesIntent.SaveAssessment)
        advanceUntilIdle()

        assertNull(vm.uiState.value.editor)
        assertEquals(ScalesSnackbarMessage.ASSESSMENT_SAVED, vm.uiState.value.snackbarMessage)
        assertEquals(1, vm.uiState.value.allAssessments.size)

        // Create second confirmed PHQ-9 (score 8) on Nov 21 -> delta should be -4
        vm.onIntent(ScalesIntent.OpenConfirmScale(ScaleType.PHQ9))
        vm.onIntent(ScalesIntent.UpdateEditorDate(today.plusDays(10))) // clamped to today
        assertEquals(today, vm.uiState.value.editor!!.date)
        vm.onIntent(ScalesIntent.UpdateEditorAnswer(0, 2))
        vm.onIntent(ScalesIntent.UpdateEditorAnswer(1, 2))
        vm.onIntent(ScalesIntent.UpdateEditorAnswer(2, 2))
        vm.onIntent(ScalesIntent.UpdateEditorAnswer(3, 2))
        vm.onIntent(ScalesIntent.SaveAssessment)
        advanceUntilIdle()

        // Create one ASRM (score 7) on Nov 20
        vm.onIntent(ScalesIntent.OpenConfirmScale(ScaleType.ASRM))
        vm.onIntent(ScalesIntent.UpdateEditorDate(today.minusDays(1)))
        vm.onIntent(ScalesIntent.UpdateEditorAnswer(0, 4))
        vm.onIntent(ScalesIntent.UpdateEditorAnswer(1, 3))
        vm.onIntent(ScalesIntent.SaveAssessment)
        advanceUntilIdle()

        val rows = vm.uiState.value.filteredHistory
        assertEquals(3, rows.size)
        assertEquals(ScaleType.PHQ9, rows[0].assessment.scaleType)
        assertEquals(8, rows[0].assessment.totalScore)
        assertEquals(-4, rows[0].deltaFromPrevious)
        assertEquals(2, vm.uiState.value.trendPoints.size)

        // Filter by ASRM
        vm.onIntent(ScalesIntent.SelectHistoryFilter(ScalesHistoryFilter.ASRM))
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.filteredHistory.size)
        assertEquals(ScaleType.ASRM, vm.uiState.value.trendScaleType)

        // Edit the ASRM assessment
        val asrm = vm.uiState.value.filteredHistory.single().assessment
        vm.onIntent(ScalesIntent.OpenEditAssessment(asrm))
        vm.onIntent(ScalesIntent.UpdateEditorAnswer(1, 4))
        vm.onIntent(ScalesIntent.SaveAssessment)
        advanceUntilIdle()
        assertEquals(8, vm.uiState.value.filteredHistory.single().assessment.totalScore)
        assertEquals(ScalesSnackbarMessage.ASSESSMENT_UPDATED, vm.uiState.value.snackbarMessage)

        // Delete the ASRM assessment
        val updatedAsrm = vm.uiState.value.filteredHistory.single().assessment
        vm.onIntent(ScalesIntent.RequestDeleteAssessment(updatedAsrm))
        assertEquals(updatedAsrm, vm.uiState.value.pendingDeleteAssessment)
        vm.onIntent(ScalesIntent.ConfirmDeleteAssessment)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.filteredHistory.isEmpty())
        assertEquals(2, vm.uiState.value.allAssessments.size)
        assertEquals(ScalesSnackbarMessage.ASSESSMENT_DELETED, vm.uiState.value.snackbarMessage)
    }
}

private class FakeScalesDiaryRepository : DiaryRepository {
    private val entries = LinkedHashMap<String, DailyEntry>()
    private val version = MutableStateFlow(0)

    fun seed(entry: DailyEntry) {
        entries[entry.id] = entry
        version.value += 1
    }

    override fun observeEntries(): Flow<List<DailyEntry>> = version.map {
        entries.values.filter { it.deletedAt == null }.sortedByDescending { it.date }
    }

    override fun observeEntryByDate(date: LocalDate): Flow<DailyEntry?> = MutableStateFlow(null)
    override fun observeEntriesInRange(startDate: LocalDate, endDate: LocalDate): Flow<List<DailyEntry>> =
        MutableStateFlow(emptyList())
    override suspend fun getActiveEntries(): List<DailyEntry> = entries.values.filter { it.deletedAt == null }
    override suspend fun getEntryByDate(date: LocalDate): DailyEntry? = null
    override suspend fun saveEntry(entry: DailyEntry): DailyEntry = entry
    override suspend fun deleteEntry(id: String) = Unit
    override fun observeMomentsByDate(date: LocalDate): Flow<List<MomentCheckIn>> = MutableStateFlow(emptyList())
    override fun observeMomentsInRange(startDate: LocalDate, endDate: LocalDate): Flow<List<MomentCheckIn>> =
        MutableStateFlow(emptyList())
    override suspend fun getActiveMoments(): List<MomentCheckIn> = emptyList()
    override suspend fun saveMoment(moment: MomentCheckIn): MomentCheckIn = moment
    override suspend fun deleteMoment(id: String) = Unit
}

private class FakeScaleAssessmentRepository : ScaleAssessmentRepository {
    private val assessments = LinkedHashMap<String, ScaleAssessment>()
    private val version = MutableStateFlow(0)
    private var nextTimestamp = 1_000L

    override fun observeAssessments(): Flow<List<ScaleAssessment>> = version.map {
        assessments.values.filter { it.deletedAt == null }.sortedWith(
            compareByDescending<ScaleAssessment> { it.date }.thenByDescending { it.updatedAt }
        )
    }

    override fun observeAssessmentsByType(type: ScaleType): Flow<List<ScaleAssessment>> =
        observeAssessments().map { list -> list.filter { it.scaleType == type } }

    override suspend fun getActiveAssessments(): List<ScaleAssessment> =
        assessments.values.filter { it.deletedAt == null }

    override suspend fun saveAssessment(assessment: ScaleAssessment): ScaleAssessment {
        nextTimestamp += 10L
        val saved = assessment.copy(updatedAt = nextTimestamp, deletedAt = null)
        assessments[saved.id] = saved
        version.value += 1
        return saved
    }

    override suspend fun deleteAssessment(id: String) {
        assessments[id]?.let { assessments[id] = it.copy(deletedAt = ++nextTimestamp) }
        version.value += 1
    }
}
