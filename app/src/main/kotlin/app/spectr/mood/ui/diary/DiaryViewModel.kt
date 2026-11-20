package app.spectr.mood.ui.diary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.spectr.mood.domain.model.DailyEntry
import app.spectr.mood.domain.model.MomentCheckIn
import app.spectr.mood.domain.model.SleepInterval
import app.spectr.mood.domain.repository.DiaryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Unidirectional state holder for the Diary tab (Daily Summary + Momentary Check-ins).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DiaryViewModel @Inject constructor(private val diaryRepository: DiaryRepository, private val clock: Clock) :
    ViewModel() {

    private val initialDate: LocalDate = LocalDate.now(clock)
    private val selectedDateFlow = MutableStateFlow(initialDate)

    private var loadedDate: LocalDate? = null
    private var baselineForm: DailyEntryFormState = DailyEntryFormState()
    private var forceNextDbSync: Boolean = true

    private val _uiState = MutableStateFlow(
        DiaryUiState(
            selectedDate = initialDate,
            today = initialDate,
            isLoading = true
        )
    )
    val uiState: StateFlow<DiaryUiState> = _uiState.asStateFlow()

    init {
        observeSelectedDate()
    }

    fun onIntent(intent: DiaryIntent) {
        when (intent) {
            is DiaryIntent.SelectDate -> selectDate(intent.date)
            DiaryIntent.SelectToday -> selectDate(currentToday())
            DiaryIntent.SelectYesterday -> selectDate(currentToday().minusDays(1))
            DiaryIntent.PreviousDay -> selectDate(_uiState.value.selectedDate.minusDays(1))
            DiaryIntent.NextDay -> selectDate(_uiState.value.selectedDate.plusDays(1))
            DiaryIntent.OpenDatePicker -> _uiState.update { it.copy(isDatePickerOpen = true) }
            DiaryIntent.DismissDatePicker -> _uiState.update { it.copy(isDatePickerOpen = false) }
            is DiaryIntent.OpenHelp -> _uiState.update { it.copy(activeHelpTopic = intent.topic) }
            DiaryIntent.DismissHelp -> _uiState.update { it.copy(activeHelpTopic = null) }
            DiaryIntent.ConsumeSnackbar -> _uiState.update { it.copy(snackbarMessage = null) }
            else -> handleFormIntent(intent)
        }
    }

    private fun handleFormIntent(intent: DiaryIntent) {
        when (intent) {
            is DiaryIntent.UpdateMood -> updateForm {
                it.copy(mood = intent.mood.coerceIn(DailyEntry.MIN_MOOD, DailyEntry.MAX_MOOD))
            }
            is DiaryIntent.UpdateDynamics -> updateForm { it.copy(dailyDynamics = intent.dynamics) }
            is DiaryIntent.UpdateEnergy -> updateForm {
                it.copy(energy = intent.energy.coerceIn(DailyEntry.MIN_RATING, DailyEntry.MAX_RATING))
            }
            is DiaryIntent.UpdateAnxiety -> updateForm {
                it.copy(anxiety = intent.anxiety.coerceIn(DailyEntry.MIN_RATING, DailyEntry.MAX_RATING))
            }
            is DiaryIntent.UpdateIrritability -> updateForm {
                it.copy(irritability = intent.irritability.coerceIn(DailyEntry.MIN_RATING, DailyEntry.MAX_RATING))
            }
            is DiaryIntent.ToggleSymptom -> updateForm { it.toggleSymptom(intent.symptom) }
            is DiaryIntent.UpdateAlcohol -> updateForm { it.copy(alcoholStatus = intent.status) }
            is DiaryIntent.UpdateMedication -> updateForm { it.copy(medicationStatus = intent.status) }
            is DiaryIntent.UpdateNote -> updateForm { it.copy(note = intent.note) }
            else -> handleSleepOrEntryActionIntent(intent)
        }
    }

    private fun handleSleepOrEntryActionIntent(intent: DiaryIntent) {
        when (intent) {
            is DiaryIntent.UpdateSleepInputMode -> updateForm { it.copy(sleepInputMode = intent.mode) }
            is DiaryIntent.UpdateManualSleepText -> updateForm { it.copy(manualSleepText = intent.text) }
            is DiaryIntent.UpdateSleepNote -> updateForm { it.copy(sleepNote = intent.note) }
            DiaryIntent.OpenNewSleepInterval -> _uiState.update {
                it.copy(sleepIntervalEditor = SleepIntervalEditorState())
            }
            is DiaryIntent.OpenEditSleepInterval -> openEditSleepInterval(intent.id)
            is DiaryIntent.UpdateSleepIntervalDraft -> updateSleepIntervalDraft(intent)
            DiaryIntent.ConfirmSleepInterval -> confirmSleepInterval()
            DiaryIntent.DismissSleepIntervalEditor -> _uiState.update { it.copy(sleepIntervalEditor = null) }
            is DiaryIntent.RemoveSleepInterval -> updateForm { form ->
                form.copy(sleepIntervals = form.sleepIntervals.filterNot { it.id == intent.id })
            }
            else -> handleEntryActionIntent(intent)
        }
    }

    private fun handleEntryActionIntent(intent: DiaryIntent) {
        when (intent) {
            DiaryIntent.SaveDailyEntry -> saveDailyEntry()
            DiaryIntent.RequestDeleteEntry -> _uiState.update { it.copy(isDeleteEntryDialogOpen = true) }
            DiaryIntent.ConfirmDeleteEntry -> confirmDeleteEntry()
            DiaryIntent.DismissDeleteEntry -> _uiState.update { it.copy(isDeleteEntryDialogOpen = false) }
            else -> handleMomentIntent(intent)
        }
    }

    private fun handleMomentIntent(intent: DiaryIntent) {
        when (intent) {
            DiaryIntent.OpenNewMoment -> openNewMomentEditor()
            is DiaryIntent.OpenEditMoment -> openEditMomentEditor(intent.moment)
            is DiaryIntent.UpdateMomentTime -> updateMomentTime(intent.hour, intent.minute)
            is DiaryIntent.UpdateMomentMood -> updateMomentEditor {
                it.copy(mood = intent.mood.coerceIn(DailyEntry.MIN_MOOD, DailyEntry.MAX_MOOD))
            }
            is DiaryIntent.UpdateMomentEnergy -> updateMomentEditor {
                it.copy(energy = intent.energy?.coerceIn(DailyEntry.MIN_RATING, DailyEntry.MAX_RATING))
            }
            is DiaryIntent.UpdateMomentAnxiety -> updateMomentEditor {
                it.copy(anxiety = intent.anxiety?.coerceIn(DailyEntry.MIN_RATING, DailyEntry.MAX_RATING))
            }
            is DiaryIntent.UpdateMomentIrritability -> updateMomentEditor {
                it.copy(irritability = intent.irritability?.coerceIn(DailyEntry.MIN_RATING, DailyEntry.MAX_RATING))
            }
            is DiaryIntent.UpdateMomentNote -> updateMomentEditor { it.copy(note = intent.note) }
            DiaryIntent.SaveMoment -> saveMoment()
            is DiaryIntent.DeleteMoment -> deleteMoment(intent.id)
            DiaryIntent.DismissMomentEditor -> _uiState.update { it.copy(momentEditor = null) }
            else -> Unit
        }
    }

    private fun observeSelectedDate() {
        viewModelScope.launch {
            selectedDateFlow
                .flatMapLatest { date ->
                    combine(
                        diaryRepository.observeEntryByDate(date),
                        diaryRepository.observeMomentsByDate(date)
                    ) { entry, moments ->
                        Triple(date, entry, moments)
                    }
                }
                .collect { (date, entry, moments) ->
                    val today = currentToday()
                    val sortedMoments = moments.sortedBy { it.timestampEpochMs }
                    val shouldSyncForm = date != loadedDate || forceNextDbSync || !_uiState.value.hasUnsavedChanges

                    if (shouldSyncForm) {
                        val loadedForm = entry?.let(DailyEntryFormState::fromEntry) ?: DailyEntryFormState()
                        loadedDate = date
                        baselineForm = loadedForm
                        forceNextDbSync = false
                        _uiState.update { state ->
                            state.copy(
                                selectedDate = date,
                                today = today,
                                isLoading = false,
                                hasSavedEntry = entry != null,
                                hasUnsavedChanges = false,
                                savedEntryId = entry?.id,
                                stepsCount = entry?.stepsCount,
                                form = loadedForm,
                                moments = sortedMoments
                            )
                        }
                    } else {
                        _uiState.update { state ->
                            state.copy(
                                today = today,
                                hasSavedEntry = entry != null,
                                savedEntryId = entry?.id ?: state.savedEntryId,
                                stepsCount = entry?.stepsCount ?: state.stepsCount,
                                moments = sortedMoments
                            )
                        }
                    }
                }
        }
    }

    private fun selectDate(rawDate: LocalDate) {
        val today = currentToday()
        val clamped = if (rawDate.isAfter(today)) today else rawDate
        if (clamped == _uiState.value.selectedDate) {
            _uiState.update { it.copy(today = today, isDatePickerOpen = false) }
            return
        }
        forceNextDbSync = true
        _uiState.update { state ->
            state.copy(
                selectedDate = clamped,
                today = today,
                isLoading = true,
                hasUnsavedChanges = false,
                isDatePickerOpen = false,
                isDeleteEntryDialogOpen = false,
                momentEditor = null,
                sleepIntervalEditor = null
            )
        }
        selectedDateFlow.value = clamped
    }

    private fun updateForm(transform: (DailyEntryFormState) -> DailyEntryFormState) {
        _uiState.update { state ->
            val updatedForm = transform(state.form)
            state.copy(
                form = updatedForm,
                hasUnsavedChanges = updatedForm != baselineForm
            )
        }
    }

    private fun openEditSleepInterval(id: String) {
        val existing = _uiState.value.form.sleepIntervals.firstOrNull { it.id == id } ?: return
        _uiState.update {
            it.copy(
                sleepIntervalEditor = SleepIntervalEditorState(
                    editingId = existing.id,
                    startMinuteOfDay = existing.startMinuteOfDay,
                    endMinuteOfDay = existing.endMinuteOfDay,
                    awakeDeductionMin = existing.awakeDeductionMin
                )
            )
        }
    }

    private fun updateSleepIntervalDraft(intent: DiaryIntent.UpdateSleepIntervalDraft) {
        _uiState.update { state ->
            val editor = state.sleepIntervalEditor ?: return@update state
            state.copy(
                sleepIntervalEditor = editor.copy(
                    startMinuteOfDay = intent.startMinuteOfDay.coerceIn(0, SleepInterval.MINUTES_PER_DAY - 1),
                    endMinuteOfDay = intent.endMinuteOfDay.coerceIn(0, SleepInterval.MINUTES_PER_DAY - 1),
                    awakeDeductionMin = intent.awakeDeductionMin.coerceAtLeast(0)
                )
            )
        }
    }

    private fun confirmSleepInterval() {
        val editor = _uiState.value.sleepIntervalEditor ?: return
        val editingId = editor.editingId
        _uiState.update { state ->
            val currentIntervals = state.form.sleepIntervals
            val updatedIntervals = if (editingId != null) {
                currentIntervals.map { item ->
                    if (item.id == editingId) {
                        item.copy(
                            startMinuteOfDay = editor.startMinuteOfDay,
                            endMinuteOfDay = editor.endMinuteOfDay,
                            awakeDeductionMin = editor.awakeDeductionMin
                        )
                    } else {
                        item
                    }
                }
            } else {
                currentIntervals + SleepIntervalDraft(
                    startMinuteOfDay = editor.startMinuteOfDay,
                    endMinuteOfDay = editor.endMinuteOfDay,
                    awakeDeductionMin = editor.awakeDeductionMin
                )
            }
            val updatedForm = state.form.copy(
                sleepInputMode = SleepInputMode.INTERVALS,
                sleepIntervals = updatedIntervals
            )
            state.copy(
                form = updatedForm,
                hasUnsavedChanges = updatedForm != baselineForm,
                sleepIntervalEditor = null
            )
        }
    }

    private fun saveDailyEntry() {
        val snapshot = _uiState.value
        if (snapshot.form.isManualSleepInvalid) {
            _uiState.update { it.copy(snackbarMessage = DiarySnackbarMessage.SLEEP_INPUT_INVALID) }
            return
        }

        val wasExisting = snapshot.hasSavedEntry
        val entryToSave = snapshot.form.toDailyEntry(
            date = snapshot.selectedDate,
            existingId = snapshot.savedEntryId,
            stepsCount = snapshot.stepsCount
        )

        viewModelScope.launch {
            val saved = diaryRepository.saveEntry(entryToSave)
            val savedForm = DailyEntryFormState.fromEntry(saved)
            baselineForm = savedForm
            forceNextDbSync = true
            _uiState.update { state ->
                if (state.selectedDate != saved.date) {
                    state
                } else {
                    state.copy(
                        hasSavedEntry = true,
                        hasUnsavedChanges = false,
                        savedEntryId = saved.id,
                        stepsCount = saved.stepsCount,
                        form = savedForm,
                        snackbarMessage = if (wasExisting) {
                            DiarySnackbarMessage.ENTRY_UPDATED
                        } else {
                            DiarySnackbarMessage.ENTRY_SAVED
                        }
                    )
                }
            }
        }
    }

    private fun confirmDeleteEntry() {
        val snapshot = _uiState.value
        val entryId = snapshot.savedEntryId
        if (entryId == null) {
            _uiState.update { it.copy(isDeleteEntryDialogOpen = false) }
            return
        }

        viewModelScope.launch {
            diaryRepository.deleteEntry(entryId)
            val emptyForm = DailyEntryFormState()
            baselineForm = emptyForm
            forceNextDbSync = true
            _uiState.update { state ->
                state.copy(
                    hasSavedEntry = false,
                    hasUnsavedChanges = false,
                    savedEntryId = null,
                    stepsCount = null,
                    form = emptyForm,
                    isDeleteEntryDialogOpen = false,
                    snackbarMessage = DiarySnackbarMessage.ENTRY_DELETED
                )
            }
        }
    }

    private fun openNewMomentEditor() {
        val selectedDate = _uiState.value.selectedDate
        val today = currentToday()
        val defaultTimestamp = if (selectedDate == today) {
            clock.millis()
        } else {
            selectedDate.atTime(LocalTime.now(clock)).atZone(clock.zone).toInstant().toEpochMilli()
        }
        _uiState.update {
            it.copy(
                momentEditor = MomentEditorState(
                    timestampEpochMs = defaultTimestamp,
                    mood = 0
                )
            )
        }
    }

    private fun openEditMomentEditor(moment: MomentCheckIn) {
        _uiState.update {
            it.copy(
                momentEditor = MomentEditorState(
                    editingId = moment.id,
                    timestampEpochMs = moment.timestampEpochMs,
                    mood = moment.mood,
                    energy = moment.energy,
                    anxiety = moment.anxiety,
                    irritability = moment.irritability,
                    note = moment.note
                )
            )
        }
    }

    private fun updateMomentTime(hour: Int, minute: Int) {
        val selectedDate = _uiState.value.selectedDate
        val safeTime = LocalTime.of(hour.coerceIn(0, MAX_HOUR), minute.coerceIn(0, MAX_MINUTE))
        val newEpochMs = selectedDate.atTime(safeTime).atZone(clock.zone).toInstant().toEpochMilli()
        updateMomentEditor { it.copy(timestampEpochMs = newEpochMs) }
    }

    private fun updateMomentEditor(transform: (MomentEditorState) -> MomentEditorState) {
        _uiState.update { state ->
            val current = state.momentEditor ?: return@update state
            state.copy(momentEditor = transform(current))
        }
    }

    private fun saveMoment() {
        val state = _uiState.value
        val editor = state.momentEditor ?: return
        val isEdit = editor.editingId != null
        val moment = MomentCheckIn(
            id = editor.editingId ?: UUID.randomUUID().toString(),
            entryDate = state.selectedDate,
            timestampEpochMs = editor.timestampEpochMs,
            mood = editor.mood.coerceIn(DailyEntry.MIN_MOOD, DailyEntry.MAX_MOOD),
            energy = editor.energy?.coerceIn(DailyEntry.MIN_RATING, DailyEntry.MAX_RATING),
            anxiety = editor.anxiety?.coerceIn(DailyEntry.MIN_RATING, DailyEntry.MAX_RATING),
            irritability = editor.irritability?.coerceIn(DailyEntry.MIN_RATING, DailyEntry.MAX_RATING),
            note = editor.note.trim()
        )
        viewModelScope.launch {
            diaryRepository.saveMoment(moment)
            _uiState.update { current ->
                current.copy(
                    momentEditor = null,
                    snackbarMessage = if (isEdit) {
                        DiarySnackbarMessage.MOMENT_UPDATED
                    } else {
                        DiarySnackbarMessage.MOMENT_SAVED
                    }
                )
            }
        }
    }

    private fun deleteMoment(id: String) {
        viewModelScope.launch {
            diaryRepository.deleteMoment(id)
            _uiState.update { current ->
                val closeEditor = current.momentEditor?.editingId == id
                current.copy(
                    momentEditor = if (closeEditor) null else current.momentEditor,
                    snackbarMessage = DiarySnackbarMessage.MOMENT_DELETED
                )
            }
        }
    }

    private fun currentToday(): LocalDate = LocalDate.now(clock)

    companion object {
        private const val MAX_HOUR = 23
        private const val MAX_MINUTE = 59
    }
}
