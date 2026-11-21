package app.spectr.mood.ui.more

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.spectr.mood.domain.reminder.ReminderSchedulerCalculator
import app.spectr.mood.domain.repository.DiaryRepository
import app.spectr.mood.domain.repository.ReminderSettingsRepository
import app.spectr.mood.domain.repository.ScaleAssessmentRepository
import app.spectr.mood.platform.notifications.ReminderCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.ZonedDateTime
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State holder for the «Ещё» tab's reminder settings and system permission banners.
 */
@HiltViewModel
class MoreViewModel @Inject constructor(
    private val reminderSettingsRepository: ReminderSettingsRepository,
    private val diaryRepository: DiaryRepository,
    private val scaleAssessmentRepository: ScaleAssessmentRepository,
    private val reminderCoordinator: ReminderCoordinator,
    private val clock: Clock
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        MoreUiState(
            today = LocalDate.now(clock),
            canScheduleExactAlarms = reminderCoordinator.canScheduleExactAlarms()
        )
    )
    val uiState: StateFlow<MoreUiState> = _uiState.asStateFlow()

    init {
        observeReminderState()
    }

    fun onIntent(intent: MoreIntent) {
        when (intent) {
            is MoreIntent.SetDailyReminderEnabled -> viewModelScope.launch {
                reminderCoordinator.setDailyEnabled(intent.enabled)
            }
            MoreIntent.OpenDailyTimePicker -> _uiState.update { it.copy(isTimePickerOpen = true) }
            MoreIntent.DismissDailyTimePicker -> _uiState.update { it.copy(isTimePickerOpen = false) }
            is MoreIntent.UpdateDailyReminderTime -> {
                _uiState.update { it.copy(isTimePickerOpen = false) }
                viewModelScope.launch {
                    reminderCoordinator.setDailyTime(intent.hour, intent.minute)
                }
            }
            MoreIntent.ClearDailySnooze -> viewModelScope.launch {
                reminderCoordinator.clearSnooze()
            }
            is MoreIntent.SetScalesReminderEnabled -> viewModelScope.launch {
                reminderCoordinator.setScalesEnabled(intent.enabled)
            }
            is MoreIntent.RefreshSystemPermissions -> {
                _uiState.update { state ->
                    state.copy(
                        notificationsGranted = intent.notificationsGranted,
                        canScheduleExactAlarms = intent.canScheduleExactAlarms
                    )
                }
                viewModelScope.launch {
                    reminderCoordinator.syncAllReminders()
                }
            }
        }
    }

    private fun observeReminderState() {
        viewModelScope.launch {
            combine(
                reminderSettingsRepository.reminderConfig,
                diaryRepository.observeEntries(),
                scaleAssessmentRepository.observeAssessments()
            ) { config, entries, assessments ->
                Triple(config, entries, assessments)
            }.collect { (config, entries, assessments) ->
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
                    current.copy(
                        today = today,
                        reminderConfig = config,
                        isTodayEntryFilled = isTodayFilled,
                        nextDailySchedule = nextDaily,
                        nextScalesSchedule = nextScales,
                        canScheduleExactAlarms = reminderCoordinator.canScheduleExactAlarms()
                    )
                }
            }
        }
    }
}
