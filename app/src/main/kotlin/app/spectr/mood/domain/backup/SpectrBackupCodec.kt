package app.spectr.mood.domain.backup

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
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Pure-Kotlin JSON v1 serializer, deserializer, and validator for Spectr local backups.
 */
object SpectrBackupCodec {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    /**
     * Serializes domain records into a formatted JSON v1 backup string.
     */
    fun encode(data: SpectrBackupData): String {
        val dto = SpectrBackupV1(
            schemaVersion = SpectrBackupV1.CURRENT_SCHEMA_VERSION,
            app = SpectrBackupV1.APP_IDENTIFIER,
            exportedAtEpochMs = data.exportedAtEpochMs,
            dailyEntries = data.dailyEntries.sortedBy { it.date }.map { it.toBackupDto() },
            momentCheckIns = data.momentCheckIns.sortedBy { it.timestampEpochMs }.map { it.toBackupDto() },
            scaleAssessments = data.scaleAssessments.sortedBy { it.date }.map { it.toBackupDto() },
            medicationRecords = data.medicationRecords.sortedBy { it.entryDate }.map { it.toBackupDto() }
        )
        return json.encodeToString(SpectrBackupV1.serializer(), dto)
    }

    /**
     * Decodes and strictly validates a JSON v1 backup string into domain models.
     *
     * @throws BackupCodecException if JSON syntax, schema version, dates, enums, or value bounds are invalid.
     */
    fun decode(rawJson: String): SpectrBackupData {
        ensureBackupValid(rawJson.isNotBlank()) { "Backup JSON is empty" }
        val dto = parseBackupDto(rawJson)
        validateHeader(dto)

        return SpectrBackupData(
            exportedAtEpochMs = dto.exportedAtEpochMs,
            dailyEntries = dto.dailyEntries.map { it.toDomain() },
            momentCheckIns = dto.momentCheckIns.map { it.toDomain() },
            scaleAssessments = dto.scaleAssessments.map { it.toDomain() },
            medicationRecords = dto.medicationRecords.map { it.toDomain() }
        )
    }

    private fun parseBackupDto(rawJson: String): SpectrBackupV1 = try {
        json.decodeFromString(SpectrBackupV1.serializer(), rawJson)
    } catch (ex: SerializationException) {
        throw BackupCodecException("Malformed backup JSON: ${ex.message}", ex)
    } catch (ex: IllegalArgumentException) {
        throw BackupCodecException("Invalid backup JSON structure: ${ex.message}", ex)
    }

    private fun validateHeader(dto: SpectrBackupV1) {
        ensureBackupValid(dto.schemaVersion == SpectrBackupV1.CURRENT_SCHEMA_VERSION) {
            "Unsupported backup schemaVersion: ${dto.schemaVersion} " +
                "(expected ${SpectrBackupV1.CURRENT_SCHEMA_VERSION})"
        }
        ensureBackupValid(dto.app.equals(SpectrBackupV1.APP_IDENTIFIER, ignoreCase = true)) {
            "Unexpected backup app identifier: ${dto.app}"
        }
    }

    private inline fun ensureBackupValid(condition: Boolean, message: () -> String) {
        if (!condition) {
            throw BackupCodecException(message())
        }
    }

    private fun DailyEntry.toBackupDto(): BackupDailyEntry = BackupDailyEntry(
        id = id,
        date = date.toString(),
        mood = mood,
        energy = energy,
        dailyDynamics = dailyDynamics.name,
        sleepIntervals = sleepIntervals.map { it.toBackupDto() },
        sleepMinutesOverride = sleepMinutesOverride,
        sleepNote = sleepNote,
        anxiety = anxiety,
        irritability = irritability,
        symptoms = BackupSymptoms(
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
        deletedAt = deletedAt
    )

    private fun SleepInterval.toBackupDto(): BackupSleepInterval = BackupSleepInterval(
        id = id,
        startMinuteOfDay = startMinuteOfDay,
        endMinuteOfDay = endMinuteOfDay,
        awakeDeductionMin = awakeDeductionMin,
        source = source.name,
        updatedAt = updatedAt,
        deletedAt = deletedAt
    )

    private fun MomentCheckIn.toBackupDto(): BackupMomentCheckIn = BackupMomentCheckIn(
        id = id,
        entryDate = entryDate.toString(),
        timestampEpochMs = timestampEpochMs,
        mood = mood,
        energy = energy,
        anxiety = anxiety,
        irritability = irritability,
        note = note,
        updatedAt = updatedAt,
        deletedAt = deletedAt
    )

    private fun ScaleAssessment.toBackupDto(): BackupScaleAssessment = BackupScaleAssessment(
        id = id,
        scaleType = scaleType.name,
        date = date.toString(),
        answers = answers,
        totalScore = totalScore,
        isConfirmed = isConfirmed,
        updatedAt = updatedAt,
        deletedAt = deletedAt
    )

    private fun MedicationRecord.toBackupDto(): BackupMedicationRecord = BackupMedicationRecord(
        id = id,
        entryDate = entryDate.toString(),
        medicationName = medicationName,
        dosage = dosage,
        takenAtEpochMs = takenAtEpochMs,
        status = status.name,
        sideEffectsNote = sideEffectsNote,
        updatedAt = updatedAt,
        deletedAt = deletedAt
    )

    private fun BackupDailyEntry.toDomain(): DailyEntry = wrapValidation("dailyEntry[$id]") {
        requireNonBlankId(id, "dailyEntry")
        val parsedDate = parseIsoDate(date, "dailyEntry[$id].date")
        val intervals = sleepIntervals.map { intervalDto ->
            requireNonBlankId(intervalDto.id, "sleepInterval")
            SleepInterval(
                id = intervalDto.id,
                entryId = id,
                startMinuteOfDay = intervalDto.startMinuteOfDay,
                endMinuteOfDay = intervalDto.endMinuteOfDay,
                awakeDeductionMin = intervalDto.awakeDeductionMin,
                source = parseEnum<SleepSource>(intervalDto.source, "sleepInterval[${intervalDto.id}].source"),
                updatedAt = intervalDto.updatedAt,
                deletedAt = intervalDto.deletedAt,
                syncState = SyncState.PENDING
            )
        }
        DailyEntry(
            id = id,
            date = parsedDate,
            mood = mood,
            energy = energy,
            dailyDynamics = parseEnum<DailyDynamics>(dailyDynamics, "dailyEntry[$id].dailyDynamics"),
            sleepIntervals = intervals,
            sleepMinutesOverride = sleepMinutesOverride,
            sleepNote = sleepNote,
            anxiety = anxiety,
            irritability = irritability,
            symptoms = DailySymptoms(
                apathy = symptoms.apathy,
                selfCriticism = symptoms.selfCriticism,
                brainFog = symptoms.brainFog,
                bingeEating = symptoms.bingeEating,
                deathThoughts = symptoms.deathThoughts,
                hyperfocus = symptoms.hyperfocus,
                racingThoughts = symptoms.racingThoughts
            ),
            alcoholStatus = parseEnum<AlcoholStatus>(alcoholStatus, "dailyEntry[$id].alcoholStatus"),
            medicationStatus = parseEnum<MedicationStatus>(medicationStatus, "dailyEntry[$id].medicationStatus"),
            note = note,
            stepsCount = stepsCount,
            updatedAt = updatedAt,
            deletedAt = deletedAt,
            syncState = SyncState.PENDING
        )
    }

    private fun BackupMomentCheckIn.toDomain(): MomentCheckIn = wrapValidation("momentCheckIn[$id]") {
        requireNonBlankId(id, "momentCheckIn")
        MomentCheckIn(
            id = id,
            entryDate = parseIsoDate(entryDate, "momentCheckIn[$id].entryDate"),
            timestampEpochMs = timestampEpochMs,
            mood = mood,
            energy = energy,
            anxiety = anxiety,
            irritability = irritability,
            note = note,
            updatedAt = updatedAt,
            deletedAt = deletedAt,
            syncState = SyncState.PENDING
        )
    }

    private fun BackupScaleAssessment.toDomain(): ScaleAssessment = wrapValidation("scaleAssessment[$id]") {
        requireNonBlankId(id, "scaleAssessment")
        ScaleAssessment(
            id = id,
            scaleType = parseEnum<ScaleType>(scaleType, "scaleAssessment[$id].scaleType"),
            date = parseIsoDate(date, "scaleAssessment[$id].date"),
            answers = answers,
            totalScore = totalScore,
            isConfirmed = isConfirmed,
            updatedAt = updatedAt,
            deletedAt = deletedAt,
            syncState = SyncState.PENDING
        )
    }

    private fun BackupMedicationRecord.toDomain(): MedicationRecord = wrapValidation("medicationRecord[$id]") {
        requireNonBlankId(id, "medicationRecord")
        MedicationRecord(
            id = id,
            entryDate = parseIsoDate(entryDate, "medicationRecord[$id].entryDate"),
            medicationName = medicationName,
            dosage = dosage,
            takenAtEpochMs = takenAtEpochMs,
            status = parseEnum<MedicationStatus>(status, "medicationRecord[$id].status"),
            sideEffectsNote = sideEffectsNote,
            updatedAt = updatedAt,
            deletedAt = deletedAt,
            syncState = SyncState.PENDING
        )
    }

    private fun requireNonBlankId(id: String, entityName: String) {
        if (id.isBlank()) {
            throw BackupCodecException("$entityName id must not be blank")
        }
    }

    private fun parseIsoDate(raw: String, fieldName: String): LocalDate = try {
        LocalDate.parse(raw)
    } catch (ex: DateTimeParseException) {
        throw BackupCodecException("Invalid ISO date '$raw' in $fieldName", ex)
    }

    private inline fun <reified T : Enum<T>> parseEnum(raw: String, fieldName: String): T = try {
        enumValueOf<T>(raw.trim().uppercase(Locale.ROOT))
    } catch (ex: IllegalArgumentException) {
        throw BackupCodecException("Invalid enum value '$raw' for $fieldName", ex)
    }

    private inline fun <T> wrapValidation(context: String, block: () -> T): T = try {
        block()
    } catch (ex: BackupCodecException) {
        throw ex
    } catch (ex: IllegalArgumentException) {
        throw BackupCodecException("Validation failed for $context: ${ex.message}", ex)
    }
}
