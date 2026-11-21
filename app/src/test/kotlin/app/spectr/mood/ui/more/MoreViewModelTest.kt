package app.spectr.mood.ui.more

import app.spectr.mood.domain.model.DailyEntry
import app.spectr.mood.domain.model.ReminderConfig
import app.spectr.mood.platform.notifications.FakeAlarmScheduler
import app.spectr.mood.platform.notifications.FakeNotificationPublisher
import app.spectr.mood.platform.notifications.FakeReminderDiaryRepository
import app.spectr.mood.platform.notifications.FakeReminderScaleRepository
import app.spectr.mood.platform.notifications.FakeReminderSettingsRepository
import app.spectr.mood.platform.notifications.ReminderCoordinator
import java.time.Clock
import java.time.LocalDate
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MoreViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val zone = ZoneOffset.UTC
    private val fixedNow = ZonedDateTime.of(2026, 11, 21, 18, 0, 0, 0, zone)
    private val fixedClock: Clock = Clock.fixed(fixedNow.toInstant(), zone)
    private val today: LocalDate = fixedNow.toLocalDate()

    private lateinit var settingsRepo: FakeReminderSettingsRepository
    private lateinit var diaryRepo: FakeReminderDiaryRepository
    private lateinit var scaleRepo: FakeReminderScaleRepository
    private lateinit var alarmScheduler: FakeAlarmScheduler
    private lateinit var notificationPublisher: FakeNotificationPublisher
    private lateinit var coordinator: ReminderCoordinator

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        settingsRepo = FakeReminderSettingsRepository()
        diaryRepo = FakeReminderDiaryRepository()
        scaleRepo = FakeReminderScaleRepository()
        alarmScheduler = FakeAlarmScheduler()
        notificationPublisher = FakeNotificationPublisher()
        coordinator = ReminderCoordinator(
            reminderSettingsRepository = settingsRepo,
            diaryRepository = diaryRepo,
            scaleAssessmentRepository = scaleRepo,
            alarmScheduler = alarmScheduler,
            notificationPublisher = notificationPublisher,
            clock = fixedClock
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `updates reminder time, toggles switches, and reflects today filled status`() = runTest(testDispatcher) {
        val viewModel = MoreViewModel(
            reminderSettingsRepository = settingsRepo,
            diaryRepository = diaryRepo,
            scaleAssessmentRepository = scaleRepo,
            reminderCoordinator = coordinator,
            clock = fixedClock
        )
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.reminderConfig.dailyEnabled)
        assertEquals(22, viewModel.uiState.value.reminderConfig.dailyHour)
        assertEquals(today, viewModel.uiState.value.nextDailySchedule?.targetDate)
        assertFalse(viewModel.uiState.value.isTodayEntryFilled)

        // Update time to 21:30 via picker
        viewModel.onIntent(MoreIntent.OpenDailyTimePicker)
        assertTrue(viewModel.uiState.value.isTimePickerOpen)

        viewModel.onIntent(MoreIntent.UpdateDailyReminderTime(hour = 21, minute = 30))
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isTimePickerOpen)
        assertEquals(21, viewModel.uiState.value.reminderConfig.dailyHour)
        assertEquals(30, viewModel.uiState.value.reminderConfig.dailyMinute)
        assertEquals(
            ZonedDateTime.of(2026, 11, 21, 21, 30, 0, 0, zone).toInstant().toEpochMilli(),
            alarmScheduler.scheduledDailyEpochMs
        )

        // Fill today's diary entry -> next schedule moves to tomorrow
        diaryRepo.seedEntry(DailyEntry(id = "e1", date = today, mood = 1, energy = 6))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isTodayEntryFilled)
        assertEquals(today.plusDays(1), viewModel.uiState.value.nextDailySchedule?.targetDate)

        // Disable scales reminder
        viewModel.onIntent(MoreIntent.SetScalesReminderEnabled(false))
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.reminderConfig.scalesEnabled)
        assertNull(viewModel.uiState.value.nextScalesSchedule)
    }

    @Test
    fun `clearing active snooze resets snoozed state and permission refresh updates banner flags`() =
        runTest(testDispatcher) {
            val snoozeUntilMs = fixedNow.plusHours(1).toInstant().toEpochMilli()
            settingsRepo = FakeReminderSettingsRepository(
                initial = ReminderConfig(dailyEnabled = true, snoozedUntilEpochMs = snoozeUntilMs)
            )
            coordinator = ReminderCoordinator(
                reminderSettingsRepository = settingsRepo,
                diaryRepository = diaryRepo,
                scaleAssessmentRepository = scaleRepo,
                alarmScheduler = alarmScheduler,
                notificationPublisher = notificationPublisher,
                clock = fixedClock
            )

            val viewModel = MoreViewModel(
                reminderSettingsRepository = settingsRepo,
                diaryRepository = diaryRepo,
                scaleAssessmentRepository = scaleRepo,
                reminderCoordinator = coordinator,
                clock = fixedClock
            )
            advanceUntilIdle()

            assertNotNull(viewModel.uiState.value.activeSnoozeEpochMs)

            viewModel.onIntent(MoreIntent.ClearDailySnooze)
            advanceUntilIdle()
            assertNull(viewModel.uiState.value.activeSnoozeEpochMs)

            viewModel.onIntent(
                MoreIntent.RefreshSystemPermissions(
                    notificationsGranted = false,
                    canScheduleExactAlarms = false
                )
            )
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.notificationsGranted)
            assertFalse(viewModel.uiState.value.canScheduleExactAlarms)
        }
}
