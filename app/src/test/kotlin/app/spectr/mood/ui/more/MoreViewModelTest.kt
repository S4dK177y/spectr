package app.spectr.mood.ui.more

import android.net.Uri
import app.spectr.mood.domain.model.DailyEntry
import app.spectr.mood.domain.model.LockTimeout
import app.spectr.mood.domain.model.ReminderConfig
import app.spectr.mood.domain.model.SecurityConfig
import app.spectr.mood.domain.model.StoredPinCredential
import app.spectr.mood.domain.report.DoctorReportSnapshot
import app.spectr.mood.domain.report.ReportPeriodPreset
import app.spectr.mood.domain.repository.SecuritySettingsRepository
import app.spectr.mood.platform.notifications.FakeAlarmScheduler
import app.spectr.mood.platform.notifications.FakeNotificationPublisher
import app.spectr.mood.platform.notifications.FakeReminderDiaryRepository
import app.spectr.mood.platform.notifications.FakeReminderScaleRepository
import app.spectr.mood.platform.notifications.FakeReminderSettingsRepository
import app.spectr.mood.platform.notifications.ReminderCoordinator
import app.spectr.mood.platform.pdf.ExportedPdfFile
import app.spectr.mood.platform.pdf.PdfReportExporter
import app.spectr.mood.security.BiometricAvailability
import app.spectr.mood.security.BiometricAvailabilityProvider
import app.spectr.mood.ui.security.PinDialogMode
import java.io.File
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
    private lateinit var securityRepo: FakeSecuritySettingsRepository
    private lateinit var diaryRepo: FakeReminderDiaryRepository
    private lateinit var scaleRepo: FakeReminderScaleRepository
    private lateinit var alarmScheduler: FakeAlarmScheduler
    private lateinit var notificationPublisher: FakeNotificationPublisher
    private lateinit var pdfExporter: FakePdfReportExporter
    private lateinit var coordinator: ReminderCoordinator

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        settingsRepo = FakeReminderSettingsRepository()
        securityRepo = FakeSecuritySettingsRepository()
        diaryRepo = FakeReminderDiaryRepository()
        scaleRepo = FakeReminderScaleRepository()
        alarmScheduler = FakeAlarmScheduler()
        notificationPublisher = FakeNotificationPublisher()
        pdfExporter = FakePdfReportExporter()
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

    private fun buildViewModel(): MoreViewModel = MoreViewModel(
        reminderSettingsRepository = settingsRepo,
        securitySettingsRepository = securityRepo,
        diaryRepository = diaryRepo,
        scaleAssessmentRepository = scaleRepo,
        reminderCoordinator = coordinator,
        pdfReportExporter = pdfExporter,
        biometricAvailabilityProvider = BiometricAvailabilityProvider { BiometricAvailability.AVAILABLE },
        clock = fixedClock
    )

    @Test
    fun `updates reminder time, toggles switches, and reflects today filled status`() = runTest(testDispatcher) {
        val viewModel = buildViewModel()
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

            val viewModel = buildViewModel()
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

    @Test
    fun `manages PIN setup, verification, biometric, timeout, and FLAG_SECURE`() = runTest(testDispatcher) {
        val viewModel = buildViewModel()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.securityConfig.isLockActive)
        assertFalse(viewModel.uiState.value.securityConfig.isScreenSecurityEnabled)

        // Enable app lock -> opens CREATE_NEW PIN dialog
        viewModel.onIntent(MoreIntent.ToggleAppLock(true))
        assertEquals(PinDialogMode.CREATE_NEW, viewModel.uiState.value.pinDialogMode)

        // Save new PIN -> hashed credential stored & lock becomes active
        viewModel.onIntent(MoreIntent.SaveNewPin("1357"))
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.pinDialogMode)
        assertTrue(viewModel.uiState.value.securityConfig.isLockActive)
        assertTrue(viewModel.verifyCurrentPin("1357"))
        assertFalse(viewModel.verifyCurrentPin("0000"))

        // Configure biometric, timeout, and FLAG_SECURE
        viewModel.onIntent(MoreIntent.SetBiometricEnabled(true))
        viewModel.onIntent(MoreIntent.SetLockTimeout(LockTimeout.MINUTE_1))
        viewModel.onIntent(MoreIntent.SetScreenSecurityEnabled(true))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.securityConfig.isBiometricEnabled)
        assertEquals(LockTimeout.MINUTE_1, viewModel.uiState.value.securityConfig.lockTimeout)
        assertTrue(viewModel.uiState.value.securityConfig.isScreenSecurityEnabled)

        // Request disabling app lock -> prompts verification, then disables
        viewModel.onIntent(MoreIntent.ToggleAppLock(false))
        assertEquals(PinDialogMode.VERIFY_TO_DISABLE, viewModel.uiState.value.pinDialogMode)
        viewModel.onIntent(MoreIntent.ConfirmDisablePinLock)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.securityConfig.isLockActive)
        assertFalse(viewModel.uiState.value.securityConfig.isBiometricEnabled)
    }

    @Test
    fun `pdf export sheet builds preview and exports snapshot with selected options`() = runTest(testDispatcher) {
        diaryRepo.seedEntry(DailyEntry(id = "e1", date = today, mood = 2, energy = 7, note = "День отличный"))
        val viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.onIntent(MoreIntent.OpenPdfSheet)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.pdfSheet.isOpen)
        assertEquals(1, viewModel.uiState.value.pdfSheet.previewSnapshot?.summary?.filledDays)

        viewModel.onIntent(MoreIntent.SelectPdfPeriodPreset(ReportPeriodPreset.DAYS_14))
        viewModel.onIntent(MoreIntent.SetPdfIncludeDailyTable(true))
        viewModel.onIntent(MoreIntent.SetPdfIncludePersonalNotes(false))
        advanceUntilIdle()

        viewModel.onIntent(MoreIntent.GenerateAndSharePdf)
        advanceUntilIdle()

        val exportedSnapshot = pdfExporter.lastExportedSnapshot
        assertNotNull(exportedSnapshot)
        assertTrue(exportedSnapshot!!.includeDailyTable)
        assertFalse(exportedSnapshot.includePersonalNotesAndMoments)
        assertEquals("", exportedSnapshot.dailyRows.single().note)
        assertFalse(viewModel.uiState.value.pdfSheet.isOpen)
    }
}

private class FakeSecuritySettingsRepository : SecuritySettingsRepository {
    private val configFlow = MutableStateFlow(SecurityConfig())
    private var credential: StoredPinCredential? = null

    override val securityConfig: Flow<SecurityConfig> = configFlow.asStateFlow()

    override suspend fun getSecurityConfig(): SecurityConfig = configFlow.value

    override suspend fun getStoredPinCredential(): StoredPinCredential? = credential

    override suspend fun savePinCredential(credential: StoredPinCredential, enableAppLock: Boolean) {
        this.credential = credential
        configFlow.update { it.copy(hasPinConfigured = true, isAppLockEnabled = enableAppLock) }
    }

    override suspend fun clearPinAndDisableLock() {
        credential = null
        configFlow.update {
            it.copy(hasPinConfigured = false, isAppLockEnabled = false, isBiometricEnabled = false)
        }
    }

    override suspend fun setBiometricEnabled(enabled: Boolean) {
        configFlow.update { it.copy(isBiometricEnabled = enabled) }
    }

    override suspend fun setLockTimeout(timeout: LockTimeout) {
        configFlow.update { it.copy(lockTimeout = timeout) }
    }

    override suspend fun setScreenSecurityEnabled(enabled: Boolean) {
        configFlow.update { it.copy(isScreenSecurityEnabled = enabled) }
    }
}

private class FakePdfReportExporter : PdfReportExporter {
    var lastExportedSnapshot: DoctorReportSnapshot? = null

    override suspend fun exportToCache(snapshot: DoctorReportSnapshot): ExportedPdfFile {
        lastExportedSnapshot = snapshot
        return ExportedPdfFile(
            file = File("/tmp/${snapshot.suggestedFileName}"),
            fileName = snapshot.suggestedFileName,
            pageCount = 1
        )
    }

    override suspend fun exportToUri(snapshot: DoctorReportSnapshot, destinationUri: Uri): Int {
        lastExportedSnapshot = snapshot
        return 1
    }
}
