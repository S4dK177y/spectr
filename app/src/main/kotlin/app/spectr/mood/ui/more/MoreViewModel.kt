package app.spectr.mood.ui.more

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.spectr.mood.domain.calculator.AnalyticsPeriod
import app.spectr.mood.domain.model.DailyEntry
import app.spectr.mood.domain.model.MomentCheckIn
import app.spectr.mood.domain.model.ScaleAssessment
import app.spectr.mood.domain.reminder.ReminderSchedulerCalculator
import app.spectr.mood.domain.report.DoctorReportBuilder
import app.spectr.mood.domain.report.DoctorReportOptions
import app.spectr.mood.domain.report.DoctorReportSnapshot
import app.spectr.mood.domain.report.ReportPeriodPreset
import app.spectr.mood.domain.repository.DiaryRepository
import app.spectr.mood.domain.repository.ReminderSettingsRepository
import app.spectr.mood.domain.repository.ScaleAssessmentRepository
import app.spectr.mood.domain.repository.SecuritySettingsRepository
import app.spectr.mood.platform.notifications.ReminderCoordinator
import app.spectr.mood.platform.pdf.PdfReportExporter
import app.spectr.mood.security.BiometricAvailabilityProvider
import app.spectr.mood.security.PinHashManager
import app.spectr.mood.ui.pdf.PdfExportSheetState
import app.spectr.mood.ui.security.PinDialogMode
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.ZonedDateTime
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State holder for the «Ещё» tab: Doctor PDF export, local PIN/biometric/FLAG_SECURE settings,
 * reminders, and system permission banners.
 */
@HiltViewModel
class MoreViewModel @Inject constructor(
    private val reminderSettingsRepository: ReminderSettingsRepository,
    private val securitySettingsRepository: SecuritySettingsRepository,
    private val diaryRepository: DiaryRepository,
    private val scaleAssessmentRepository: ScaleAssessmentRepository,
    private val reminderCoordinator: ReminderCoordinator,
    private val pdfReportExporter: PdfReportExporter,
    private val biometricAvailabilityProvider: BiometricAvailabilityProvider,
    private val clock: Clock
) : ViewModel() {

    private val initialToday = LocalDate.now(clock)

    private val _uiState = MutableStateFlow(
        MoreUiState(
            today = initialToday,
            canScheduleExactAlarms = reminderCoordinator.canScheduleExactAlarms(),
            biometricAvailability = biometricAvailabilityProvider.checkAvailability(),
            pdfSheet = PdfExportSheetState(
                customStartDate = initialToday.minusDays(29),
                customEndDate = initialToday
            )
        )
    )
    val uiState: StateFlow<MoreUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<MoreOneShotEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<MoreOneShotEvent> = _events.asSharedFlow()

    private var cachedEntries: List<DailyEntry> = emptyList()
    private var cachedMoments: List<MomentCheckIn> = emptyList()
    private var cachedAssessments: List<ScaleAssessment> = emptyList()

    init {
        observeReminderAndDataState()
        observeSecurityState()
    }

    fun onIntent(intent: MoreIntent) {
        if (!handleReminderIntent(intent) && !handleSecurityIntent(intent)) {
            handlePdfIntent(intent)
        }
    }

    private fun handleReminderIntent(intent: MoreIntent): Boolean {
        when (intent) {
            is MoreIntent.SetDailyReminderEnabled -> viewModelScope.launch {
                reminderCoordinator.setDailyEnabled(intent.enabled)
            }
            MoreIntent.OpenDailyTimePicker -> _uiState.update { it.copy(isTimePickerOpen = true) }
            MoreIntent.DismissDailyTimePicker -> _uiState.update { it.copy(isTimePickerOpen = false) }
            is MoreIntent.UpdateDailyReminderTime -> {
                _uiState.update { it.copy(isTimePickerOpen = false) }
                viewModelScope.launch { reminderCoordinator.setDailyTime(intent.hour, intent.minute) }
            }
            MoreIntent.ClearDailySnooze -> viewModelScope.launch { reminderCoordinator.clearSnooze() }
            is MoreIntent.SetScalesReminderEnabled -> viewModelScope.launch {
                reminderCoordinator.setScalesEnabled(intent.enabled)
            }
            is MoreIntent.RefreshSystemPermissions -> {
                _uiState.update { state ->
                    state.copy(
                        notificationsGranted = intent.notificationsGranted,
                        canScheduleExactAlarms = intent.canScheduleExactAlarms,
                        biometricAvailability = biometricAvailabilityProvider.checkAvailability()
                    )
                }
                viewModelScope.launch { reminderCoordinator.syncAllReminders() }
            }
            else -> return false
        }
        return true
    }

    private fun handleSecurityIntent(intent: MoreIntent): Boolean {
        when (intent) {
            is MoreIntent.ToggleAppLock -> handleToggleAppLock(intent.enabled)
            MoreIntent.OpenChangePinDialog -> _uiState.update {
                it.copy(pinDialogMode = PinDialogMode.CHANGE_EXISTING)
            }
            MoreIntent.DismissPinDialog -> _uiState.update { it.copy(pinDialogMode = null) }
            is MoreIntent.SaveNewPin -> handleSaveNewPin(intent.pin)
            MoreIntent.ConfirmDisablePinLock -> handleConfirmDisablePinLock()
            is MoreIntent.SetBiometricEnabled -> viewModelScope.launch {
                securitySettingsRepository.setBiometricEnabled(intent.enabled)
            }
            is MoreIntent.SetLockTimeout -> viewModelScope.launch {
                securitySettingsRepository.setLockTimeout(intent.timeout)
            }
            is MoreIntent.SetScreenSecurityEnabled -> viewModelScope.launch {
                securitySettingsRepository.setScreenSecurityEnabled(intent.enabled)
            }
            else -> return false
        }
        return true
    }

    private fun handlePdfIntent(intent: MoreIntent) {
        when (intent) {
            MoreIntent.OpenPdfSheet -> openPdfSheet()
            MoreIntent.DismissPdfSheet -> _uiState.update {
                it.copy(pdfSheet = it.pdfSheet.copy(isOpen = false))
            }
            is MoreIntent.SelectPdfPeriodPreset -> updatePdfSheet { copy(periodPreset = intent.preset) }
            is MoreIntent.UpdatePdfCustomStart -> updatePdfSheet { copy(customStartDate = intent.date) }
            is MoreIntent.UpdatePdfCustomEnd -> updatePdfSheet { copy(customEndDate = intent.date) }
            is MoreIntent.SetPdfIncludeDailyTable -> updatePdfSheet { copy(includeDailyTable = intent.include) }
            is MoreIntent.SetPdfIncludePersonalNotes -> updatePdfSheet {
                copy(includePersonalNotesAndMoments = intent.include)
            }
            MoreIntent.GenerateAndSharePdf -> exportPdfToCache(shareAfter = true)
            MoreIntent.GenerateAndOpenPdf -> exportPdfToCache(shareAfter = false)
            else -> Unit
        }
    }

    suspend fun verifyCurrentPin(candidatePin: String): Boolean {
        val stored = securitySettingsRepository.getStoredPinCredential() ?: return false
        return PinHashManager.verifyPin(candidatePin, stored)
    }

    fun suggestedPdfFileName(): String = buildCurrentPdfSnapshot().suggestedFileName

    fun exportPdfToDestinationUri(destinationUri: Uri) {
        val snapshot = buildCurrentPdfSnapshot()
        _uiState.update { it.copy(pdfSheet = it.pdfSheet.copy(isGenerating = true)) }
        viewModelScope.launch {
            runCatching { pdfReportExporter.exportToUri(snapshot, destinationUri) }
                .onSuccess { pages ->
                    _uiState.update {
                        it.copy(pdfSheet = it.pdfSheet.copy(isGenerating = false, isOpen = false))
                    }
                    _events.tryEmit(MoreOneShotEvent.ShowPdfSaved(pages))
                }
                .onFailure {
                    _uiState.update { it.copy(pdfSheet = it.pdfSheet.copy(isGenerating = false)) }
                    _events.tryEmit(MoreOneShotEvent.ShowSnackbar(MoreSnackbarMessage.PDF_EXPORT_ERROR))
                }
        }
    }

    private fun handleToggleAppLock(enabled: Boolean) {
        if (enabled) {
            _uiState.update { it.copy(pinDialogMode = PinDialogMode.CREATE_NEW) }
        } else if (_uiState.value.securityConfig.hasPinConfigured) {
            _uiState.update { it.copy(pinDialogMode = PinDialogMode.VERIFY_TO_DISABLE) }
        }
    }

    private fun handleSaveNewPin(pin: String) {
        val wasChanging = _uiState.value.pinDialogMode == PinDialogMode.CHANGE_EXISTING
        _uiState.update { it.copy(pinDialogMode = null) }
        viewModelScope.launch {
            val credential = PinHashManager.createCredential(pin)
            securitySettingsRepository.savePinCredential(credential, enableAppLock = true)
            val msg = if (wasChanging) MoreSnackbarMessage.PIN_CHANGED else MoreSnackbarMessage.PIN_ENABLED
            _events.tryEmit(MoreOneShotEvent.ShowSnackbar(msg))
        }
    }

    private fun handleConfirmDisablePinLock() {
        _uiState.update { it.copy(pinDialogMode = null) }
        viewModelScope.launch {
            securitySettingsRepository.clearPinAndDisableLock()
            _events.tryEmit(MoreOneShotEvent.ShowSnackbar(MoreSnackbarMessage.PIN_DISABLED))
        }
    }

    private fun openPdfSheet() {
        viewModelScope.launch {
            cachedMoments = diaryRepository.getActiveMoments()
            val today = LocalDate.now(clock)
            _uiState.update { state ->
                val updatedSheet = state.pdfSheet.copy(
                    isOpen = true,
                    customEndDate = state.pdfSheet.customEndDate.takeIf { !it.isAfter(today) } ?: today
                )
                state.copy(pdfSheet = updatedSheet.copy(previewSnapshot = buildSnapshotFor(updatedSheet, today)))
            }
        }
    }

    private fun updatePdfSheet(transform: PdfExportSheetState.() -> PdfExportSheetState) {
        val today = LocalDate.now(clock)
        _uiState.update { state ->
            val next = state.pdfSheet.transform()
            val preview = if (next.periodPreset != ReportPeriodPreset.CUSTOM || next.isCustomRangeValid) {
                buildSnapshotFor(next, today)
            } else {
                null
            }
            state.copy(pdfSheet = next.copy(previewSnapshot = preview))
        }
    }

    private fun exportPdfToCache(shareAfter: Boolean) {
        val sheet = _uiState.value.pdfSheet
        if (sheet.periodPreset == ReportPeriodPreset.CUSTOM && !sheet.isCustomRangeValid) return

        _uiState.update { it.copy(pdfSheet = it.pdfSheet.copy(isGenerating = true)) }
        viewModelScope.launch {
            cachedMoments = diaryRepository.getActiveMoments()
            val snapshot = buildCurrentPdfSnapshot()
            runCatching { pdfReportExporter.exportToCache(snapshot) }
                .onSuccess { exported ->
                    _uiState.update {
                        it.copy(pdfSheet = it.pdfSheet.copy(isGenerating = false, isOpen = false))
                    }
                    _events.tryEmit(
                        if (shareAfter) {
                            MoreOneShotEvent.SharePdf(exported)
                        } else {
                            MoreOneShotEvent.OpenPdf(exported)
                        }
                    )
                }
                .onFailure {
                    _uiState.update { it.copy(pdfSheet = it.pdfSheet.copy(isGenerating = false)) }
                    _events.tryEmit(MoreOneShotEvent.ShowSnackbar(MoreSnackbarMessage.PDF_EXPORT_ERROR))
                }
        }
    }

    private fun buildCurrentPdfSnapshot(): DoctorReportSnapshot =
        buildSnapshotFor(_uiState.value.pdfSheet, LocalDate.now(clock))

    private fun buildSnapshotFor(sheet: PdfExportSheetState, today: LocalDate): DoctorReportSnapshot {
        val period = when (sheet.periodPreset) {
            ReportPeriodPreset.DAYS_14 -> AnalyticsPeriod.LAST_14_DAYS
            ReportPeriodPreset.DAYS_30 -> AnalyticsPeriod.LAST_30_DAYS
            ReportPeriodPreset.DAYS_90 -> AnalyticsPeriod.LAST_90_DAYS
            ReportPeriodPreset.ALL_TIME -> AnalyticsPeriod.AllTime
            ReportPeriodPreset.CUSTOM -> {
                val safeEnd = if (sheet.customEndDate.isBefore(sheet.customStartDate)) {
                    sheet.customStartDate
                } else {
                    sheet.customEndDate
                }
                AnalyticsPeriod.Custom(sheet.customStartDate, safeEnd)
            }
        }
        return DoctorReportBuilder.buildSnapshot(
            entries = cachedEntries,
            moments = cachedMoments,
            assessments = cachedAssessments,
            options = DoctorReportOptions(
                period = period,
                includeDailyTable = sheet.includeDailyTable,
                includePersonalNotesAndMoments = sheet.includePersonalNotesAndMoments
            ),
            today = today
        )
    }

    private fun observeReminderAndDataState() {
        viewModelScope.launch {
            combine(
                reminderSettingsRepository.reminderConfig,
                diaryRepository.observeEntries(),
                scaleAssessmentRepository.observeAssessments()
            ) { config, entries, assessments ->
                Triple(config, entries, assessments)
            }.collect { (config, entries, assessments) ->
                cachedEntries = entries
                cachedAssessments = assessments

                val now = ZonedDateTime.now(clock)
                val today = now.toLocalDate()
                val activeEntries = entries.filter { it.deletedAt == null }
                val isTodayFilled = activeEntries.any { it.date == today }
                val earliestEntryDate = activeEntries.minOfOrNull { it.date }
                val latestConfirmedDate = assessments
                    .asSequence()
                    .filter { it.deletedAt == null && it.isConfirmed }
                    .maxOfOrNull { it.date }

                val nextDaily = ReminderSchedulerCalculator.computeNextDailyTrigger(
                    config = config,
                    now = now,
                    isTodayEntryFilled = isTodayFilled
                )
                val nextScales = ReminderSchedulerCalculator.computeNextScalesTrigger(
                    config = config,
                    now = now,
                    latestConfirmedAssessmentDate = latestConfirmedDate,
                    earliestDiaryEntryDate = earliestEntryDate
                )

                _uiState.update { current ->
                    val updatedPdfSheet = if (current.pdfSheet.isOpen) {
                        current.pdfSheet.copy(previewSnapshot = buildSnapshotFor(current.pdfSheet, today))
                    } else {
                        current.pdfSheet
                    }
                    current.copy(
                        today = today,
                        reminderConfig = config,
                        isTodayEntryFilled = isTodayFilled,
                        nextDailySchedule = nextDaily,
                        nextScalesSchedule = nextScales,
                        canScheduleExactAlarms = reminderCoordinator.canScheduleExactAlarms(),
                        pdfSheet = updatedPdfSheet
                    )
                }
            }
        }
    }

    private fun observeSecurityState() {
        viewModelScope.launch {
            securitySettingsRepository.securityConfig.collect { securityConfig ->
                _uiState.update { current ->
                    current.copy(securityConfig = securityConfig)
                }
            }
        }
    }
}
