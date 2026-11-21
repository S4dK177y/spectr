package app.spectr.mood.platform.notifications

import app.spectr.mood.domain.model.DailyEntry
import app.spectr.mood.domain.model.MomentCheckIn
import app.spectr.mood.domain.model.ReminderConfig
import app.spectr.mood.domain.model.ScaleAssessment
import app.spectr.mood.domain.model.ScaleType
import app.spectr.mood.domain.repository.DiaryRepository
import app.spectr.mood.domain.repository.ReminderSettingsRepository
import app.spectr.mood.domain.repository.ScaleAssessmentRepository
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class ReminderCoordinatorTest {

    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 11, 21)

    private lateinit var settingsRepo: FakeReminderSettingsRepository
    private lateinit var diaryRepo: FakeReminderDiaryRepository
    private lateinit var scaleRepo: FakeReminderScaleRepository
    private lateinit var alarmScheduler: FakeAlarmScheduler
    private lateinit var notificationPublisher: FakeNotificationPublisher

    @Before
    fun setUp() {
        settingsRepo = FakeReminderSettingsRepository()
        diaryRepo = FakeReminderDiaryRepository()
        scaleRepo = FakeReminderScaleRepository()
        alarmScheduler = FakeAlarmScheduler()
        notificationPublisher = FakeNotificationPublisher()
    }

    private fun createCoordinator(now: ZonedDateTime): ReminderCoordinator = ReminderCoordinator(
        reminderSettingsRepository = settingsRepo,
        diaryRepository = diaryRepo,
        scaleAssessmentRepository = scaleRepo,
        alarmScheduler = alarmScheduler,
        notificationPublisher = notificationPublisher,
        clock = Clock.fixed(now.toInstant(), zone)
    )

    @Test
    fun `daily alarm fires when unfilled and reschedules tomorrow`() = runTest {
        val now = ZonedDateTime.of(2026, 11, 21, 22, 0, 0, 0, zone)
        val coordinator = createCoordinator(now)

        coordinator.onDailyAlarmFired()

        assertEquals(1, notificationPublisher.dailyShownCount)
        assertEquals(
            ZonedDateTime.of(2026, 11, 22, 22, 0, 0, 0, zone).toInstant().toEpochMilli(),
            alarmScheduler.scheduledDailyEpochMs
        )
    }

    @Test
    fun `daily alarm suppresses notification if today entry is already filled`() = runTest {
        diaryRepo.seedEntry(DailyEntry(id = "e1", date = today, mood = 1, energy = 6))
        val now = ZonedDateTime.of(2026, 11, 21, 22, 0, 0, 0, zone)
        val coordinator = createCoordinator(now)

        coordinator.onDailyAlarmFired()

        assertEquals(0, notificationPublisher.dailyShownCount)
        assertEquals(1, notificationPublisher.dailyCancelledCount)
        assertEquals(
            ZonedDateTime.of(2026, 11, 22, 22, 0, 0, 0, zone).toInstant().toEpochMilli(),
            alarmScheduler.scheduledDailyEpochMs
        )
    }

    @Test
    fun `snooze schedules +1h alarm and saving today entry cancels snooze and notification`() = runTest {
        val nowAt2205 = ZonedDateTime.of(2026, 11, 21, 22, 5, 0, 0, zone)
        val coordinatorAt2205 = createCoordinator(nowAt2205)

        coordinatorAt2205.onSnoozeDailyClicked()

        val expectedSnoozeMs = nowAt2205.plusHours(1).toInstant().toEpochMilli()
        assertEquals(1, notificationPublisher.dailyCancelledCount)
        assertEquals(expectedSnoozeMs, settingsRepo.current.snoozedUntilEpochMs)
        assertEquals(expectedSnoozeMs, alarmScheduler.scheduledDailyEpochMs)

        // User fills today's summary at 22:20 before snooze fires
        diaryRepo.seedEntry(DailyEntry(id = "e-today", date = today, mood = 0, energy = 5))
        val coordinatorAt2220 = createCoordinator(ZonedDateTime.of(2026, 11, 21, 22, 20, 0, 0, zone))
        coordinatorAt2220.onDailyEntryChanged(today)

        assertNull(settingsRepo.current.snoozedUntilEpochMs)
        assertEquals(2, notificationPublisher.dailyCancelledCount)
        assertEquals(
            ZonedDateTime.of(2026, 11, 22, 22, 0, 0, 0, zone).toInstant().toEpochMilli(),
            alarmScheduler.scheduledDailyEpochMs
        )
    }

    @Test
    fun `scales alarm shows notification after 14 days and records timestamp`() = runTest {
        scaleRepo.seed(
            ScaleAssessment(
                id = "s1",
                scaleType = ScaleType.PHQ9,
                date = LocalDate.of(2026, 11, 7),
                answers = List(9) { 1 },
                totalScore = 9,
                isConfirmed = true
            )
        )
        val now = ZonedDateTime.of(2026, 11, 21, 18, 0, 0, 0, zone)
        val coordinator = createCoordinator(now)

        coordinator.onScalesAlarmFired()

        assertEquals(1, notificationPublisher.scalesShownCount)
        assertEquals(now.toInstant().toEpochMilli(), settingsRepo.current.lastScalesReminderEpochMs)
        assertEquals(
            ZonedDateTime.of(2026, 12, 5, 18, 0, 0, 0, zone).toInstant().toEpochMilli(),
            alarmScheduler.scheduledScalesEpochMs
        )
    }

    @Test
    fun `confirming a scale assessment cancels active scales notification and moves alarm +14 days`() = runTest {
        scaleRepo.seed(
            ScaleAssessment(
                id = "s-new",
                scaleType = ScaleType.GAD7,
                date = today,
                answers = List(7) { 1 },
                totalScore = 7,
                isConfirmed = true
            )
        )
        val now = ZonedDateTime.of(2026, 11, 21, 19, 0, 0, 0, zone)
        val coordinator = createCoordinator(now)

        coordinator.onScaleAssessmentChanged()

        assertEquals(1, notificationPublisher.scalesCancelledCount)
        assertEquals(
            ZonedDateTime.of(2026, 12, 5, 18, 0, 0, 0, zone).toInstant().toEpochMilli(),
            alarmScheduler.scheduledScalesEpochMs
        )
    }

    @Test
    fun `disabling reminders cancels both alarms and active notifications`() = runTest {
        val now = ZonedDateTime.of(2026, 11, 21, 12, 0, 0, 0, zone)
        val coordinator = createCoordinator(now)

        coordinator.syncAllReminders()
        assertNotNull(alarmScheduler.scheduledDailyEpochMs)
        assertNotNull(alarmScheduler.scheduledScalesEpochMs)

        coordinator.setDailyEnabled(false)
        coordinator.setScalesEnabled(false)

        assertNull(alarmScheduler.scheduledDailyEpochMs)
        assertNull(alarmScheduler.scheduledScalesEpochMs)
        assertEquals(1, notificationPublisher.dailyCancelledCount)
        assertEquals(1, notificationPublisher.scalesCancelledCount)
    }
}

internal class FakeReminderSettingsRepository(initial: ReminderConfig = ReminderConfig()) : ReminderSettingsRepository {
    private val state = MutableStateFlow(initial)
    val current: ReminderConfig get() = state.value

    override val reminderConfig: Flow<ReminderConfig> = state

    override suspend fun getReminderConfig(): ReminderConfig = state.value

    override suspend fun setDailyEnabled(enabled: Boolean) {
        state.value = state.value.copy(
            dailyEnabled = enabled,
            snoozedUntilEpochMs = if (enabled) state.value.snoozedUntilEpochMs else null
        )
    }

    override suspend fun setDailyTime(hour: Int, minute: Int) {
        state.value = state.value.copy(
            dailyHour = hour,
            dailyMinute = minute,
            snoozedUntilEpochMs = null
        )
    }

    override suspend fun setScalesEnabled(enabled: Boolean) {
        state.value = state.value.copy(scalesEnabled = enabled)
    }

    override suspend fun setSnoozedUntil(epochMs: Long?) {
        state.value = state.value.copy(snoozedUntilEpochMs = epochMs)
    }

    override suspend fun setLastScalesReminder(epochMs: Long?) {
        state.value = state.value.copy(lastScalesReminderEpochMs = epochMs)
    }
}

internal class FakeAlarmScheduler(var exactAllowed: Boolean = true) : AlarmScheduler {
    var scheduledDailyEpochMs: Long? = null
    var scheduledScalesEpochMs: Long? = null

    override fun canScheduleExactAlarms(): Boolean = exactAllowed

    override fun scheduleDailyAlarm(triggerAtEpochMs: Long) {
        scheduledDailyEpochMs = triggerAtEpochMs
    }

    override fun cancelDailyAlarm() {
        scheduledDailyEpochMs = null
    }

    override fun scheduleScalesAlarm(triggerAtEpochMs: Long) {
        scheduledScalesEpochMs = triggerAtEpochMs
    }

    override fun cancelScalesAlarm() {
        scheduledScalesEpochMs = null
    }
}

internal class FakeNotificationPublisher : NotificationPublisher {
    var dailyShownCount: Int = 0
    var dailyCancelledCount: Int = 0
    var scalesShownCount: Int = 0
    var scalesCancelledCount: Int = 0

    override fun ensureChannelsCreated() = Unit

    override fun showDailyReminder() {
        dailyShownCount++
    }

    override fun cancelDailyReminder() {
        dailyCancelledCount++
    }

    override fun showScalesReminder() {
        scalesShownCount++
    }

    override fun cancelScalesReminder() {
        scalesCancelledCount++
    }
}

internal class FakeReminderDiaryRepository : DiaryRepository {
    private val entries = MutableStateFlow<List<DailyEntry>>(emptyList())
    private val moments = MutableStateFlow<List<MomentCheckIn>>(emptyList())

    fun seedEntry(entry: DailyEntry) {
        entries.value = entries.value.filterNot { it.date == entry.date } + entry
    }

    override fun observeEntries(): Flow<List<DailyEntry>> = entries

    override fun observeEntryByDate(date: LocalDate): Flow<DailyEntry?> =
        entries.map { list -> list.firstOrNull { it.date == date && it.deletedAt == null } }

    override fun observeEntriesInRange(startDate: LocalDate, endDate: LocalDate): Flow<List<DailyEntry>> =
        entries.map { list ->
            list.filter { it.deletedAt == null && !it.date.isBefore(startDate) && !it.date.isAfter(endDate) }
        }

    override suspend fun getActiveEntries(): List<DailyEntry> = entries.value.filter { it.deletedAt == null }

    override suspend fun getEntryByDate(date: LocalDate): DailyEntry? =
        entries.value.firstOrNull { it.date == date && it.deletedAt == null }

    override suspend fun saveEntry(entry: DailyEntry): DailyEntry {
        seedEntry(entry)
        return entry
    }

    override suspend fun deleteEntry(id: String) {
        entries.value = entries.value.filterNot { it.id == id }
    }

    override fun observeMomentsByDate(date: LocalDate): Flow<List<MomentCheckIn>> =
        moments.map { list -> list.filter { it.entryDate == date && it.deletedAt == null } }

    override fun observeMomentsInRange(startDate: LocalDate, endDate: LocalDate): Flow<List<MomentCheckIn>> =
        moments.map { list ->
            list.filter { it.deletedAt == null && !it.entryDate.isBefore(startDate) && !it.entryDate.isAfter(endDate) }
        }

    override suspend fun getActiveMoments(): List<MomentCheckIn> = moments.value.filter { it.deletedAt == null }

    override suspend fun saveMoment(moment: MomentCheckIn): MomentCheckIn {
        moments.value = moments.value.filterNot { it.id == moment.id } + moment
        return moment
    }

    override suspend fun deleteMoment(id: String) {
        moments.value = moments.value.filterNot { it.id == id }
    }
}

internal class FakeReminderScaleRepository : ScaleAssessmentRepository {
    private val assessments = MutableStateFlow<List<ScaleAssessment>>(emptyList())

    fun seed(assessment: ScaleAssessment) {
        assessments.value = assessments.value.filterNot { it.id == assessment.id } + assessment
    }

    override fun observeAssessments(): Flow<List<ScaleAssessment>> = assessments

    override fun observeAssessmentsByType(type: ScaleType): Flow<List<ScaleAssessment>> =
        assessments.map { list -> list.filter { it.scaleType == type && it.deletedAt == null } }

    override suspend fun getActiveAssessments(): List<ScaleAssessment> =
        assessments.value.filter { it.deletedAt == null }

    override suspend fun saveAssessment(assessment: ScaleAssessment): ScaleAssessment {
        seed(assessment)
        return assessment
    }

    override suspend fun deleteAssessment(id: String) {
        assessments.value = assessments.value.filterNot { it.id == id }
    }
}
