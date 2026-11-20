package app.spectr.mood.data.local.db.mapper

import app.spectr.mood.data.local.db.entity.DailyEntryEntity
import app.spectr.mood.data.local.db.entity.DailyEntryWithSleep
import app.spectr.mood.data.local.db.entity.DailySymptomsEmbed
import app.spectr.mood.data.local.db.entity.MedicationRecordEntity
import app.spectr.mood.data.local.db.entity.MomentCheckInEntity
import app.spectr.mood.data.local.db.entity.ScaleAssessmentEntity
import app.spectr.mood.data.local.db.entity.SleepIntervalEntity
import app.spectr.mood.domain.model.AlcoholStatus
import app.spectr.mood.domain.model.DailyDynamics
import app.spectr.mood.domain.model.DailyEntry
import app.spectr.mood.domain.model.DailySymptoms
import app.spectr.mood.domain.model.MedicationRecord
import app.spectr.mood.domain.model.MedicationStatus
import app.spectr.mood.domain.model.MomentCheckIn
import app.spectr.mood.domain.model.ScaleAssessment
import app.spectr.mood.domain.model.ScaleType
import app.spectr.mood.domain.model.SleepInterval
import app.spectr.mood.domain.model.SleepSource
import app.spectr.mood.domain.model.SyncState
import java.time.LocalDate
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val intListSerializer = ListSerializer(Int.serializer())

fun DailyEntryWithSleep.toDomain(): DailyEntry = DailyEntry(
    id = entry.id,
    date = LocalDate.parse(entry.date),
    mood = entry.mood,
    energy = entry.energy,
    dailyDynamics = safeEnumValueOf(entry.dailyDynamics, DailyDynamics.STEADY),
    sleepIntervals = sleepIntervals
        .filter { it.deletedAt == null }
        .sortedBy { it.startMinuteOfDay }
        .map { it.toDomain() },
    sleepMinutesOverride = entry.sleepMinutesOverride,
    sleepNote = entry.sleepNote,
    anxiety = entry.anxiety,
    irritability = entry.irritability,
    symptoms = DailySymptoms(
        apathy = entry.symptoms.apathy,
        selfCriticism = entry.symptoms.selfCriticism,
        brainFog = entry.symptoms.brainFog,
        bingeEating = entry.symptoms.bingeEating,
        deathThoughts = entry.symptoms.deathThoughts,
        hyperfocus = entry.symptoms.hyperfocus,
        racingThoughts = entry.symptoms.racingThoughts
    ),
    alcoholStatus = safeEnumValueOf(entry.alcoholStatus, AlcoholStatus.NONE),
    medicationStatus = safeEnumValueOf(entry.medicationStatus, MedicationStatus.NOT_PRESCRIBED),
    note = entry.note,
    stepsCount = entry.stepsCount,
    updatedAt = entry.updatedAt,
    deletedAt = entry.deletedAt,
    syncState = safeEnumValueOf(entry.syncState, SyncState.PENDING)
)

fun DailyEntry.toEntity(): DailyEntryEntity = DailyEntryEntity(
    id = id,
    date = date.toString(),
    mood = mood,
    dailyDynamics = dailyDynamics.name,
    energy = energy,
    sleepMinutesOverride = sleepMinutesOverride,
    sleepNote = sleepNote,
    anxiety = anxiety,
    irritability = irritability,
    symptoms = DailySymptomsEmbed(
        apathy = symptoms.apathy,
        selfCriticism = symptoms.selfCriticism,
        brainFog = symptoms.brainFog,
        bingeEating = symptoms.bingeEating,
        deathThoughts = symptoms.deathThoughts,
        hyperfocus = symptoms.hyperfocus,
        racingThoughts = symptoms.racingThoughts
    ),
    alcoholStatus = alcoholStatus.name,
    medicationStatus = medicationStatus.name,
    note = note,
    stepsCount = stepsCount,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    syncState = syncState.name
)

fun SleepIntervalEntity.toDomain(): SleepInterval = SleepInterval(
    id = id,
    entryId = entryId,
    startMinuteOfDay = startMinuteOfDay,
    endMinuteOfDay = endMinuteOfDay,
    awakeDeductionMin = awakeDeductionMin,
    source = safeEnumValueOf(source, SleepSource.MANUAL),
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    syncState = safeEnumValueOf(syncState, SyncState.PENDING)
)

fun SleepInterval.toEntity(): SleepIntervalEntity = SleepIntervalEntity(
    id = id,
    entryId = entryId,
    startMinuteOfDay = startMinuteOfDay,
    endMinuteOfDay = endMinuteOfDay,
    awakeDeductionMin = awakeDeductionMin,
    source = source.name,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    syncState = syncState.name
)

fun MomentCheckInEntity.toDomain(): MomentCheckIn = MomentCheckIn(
    id = id,
    entryDate = LocalDate.parse(entryDate),
    timestampEpochMs = timestampEpochMs,
    mood = mood,
    energy = energy,
    anxiety = anxiety,
    irritability = irritability,
    note = note,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    syncState = safeEnumValueOf(syncState, SyncState.PENDING)
)

fun MomentCheckIn.toEntity(): MomentCheckInEntity = MomentCheckInEntity(
    id = id,
    entryDate = entryDate.toString(),
    timestampEpochMs = timestampEpochMs,
    mood = mood,
    energy = energy,
    anxiety = anxiety,
    irritability = irritability,
    note = note,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    syncState = syncState.name
)

fun ScaleAssessmentEntity.toDomain(): ScaleAssessment = ScaleAssessment(
    id = id,
    scaleType = safeEnumValueOf(scaleType, ScaleType.PHQ9),
    date = LocalDate.parse(date),
    answers = runCatching { Json.decodeFromString(intListSerializer, answersJson) }.getOrDefault(emptyList()),
    totalScore = totalScore,
    isConfirmed = isConfirmed,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    syncState = safeEnumValueOf(syncState, SyncState.PENDING)
)

fun ScaleAssessment.toEntity(): ScaleAssessmentEntity = ScaleAssessmentEntity(
    id = id,
    scaleType = scaleType.name,
    date = date.toString(),
    answersJson = Json.encodeToString(intListSerializer, answers),
    totalScore = totalScore,
    isConfirmed = isConfirmed,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    syncState = syncState.name
)

fun MedicationRecordEntity.toDomain(): MedicationRecord = MedicationRecord(
    id = id,
    entryDate = LocalDate.parse(entryDate),
    medicationName = medicationName,
    dosage = dosage,
    takenAtEpochMs = takenAtEpochMs,
    status = safeEnumValueOf(status, MedicationStatus.TAKEN_AS_PRESCRIBED),
    sideEffectsNote = sideEffectsNote,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    syncState = safeEnumValueOf(syncState, SyncState.PENDING)
)

fun MedicationRecord.toEntity(): MedicationRecordEntity = MedicationRecordEntity(
    id = id,
    entryDate = entryDate.toString(),
    medicationName = medicationName,
    dosage = dosage,
    takenAtEpochMs = takenAtEpochMs,
    status = status.name,
    sideEffectsNote = sideEffectsNote,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    syncState = syncState.name
)

private inline fun <reified T : Enum<T>> safeEnumValueOf(raw: String, fallback: T): T =
    runCatching { enumValueOf<T>(raw) }.getOrDefault(fallback)
