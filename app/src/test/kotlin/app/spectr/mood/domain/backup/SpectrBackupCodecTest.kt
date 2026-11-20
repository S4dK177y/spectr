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
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpectrBackupCodecTest {

    @Test
    fun `encode and decode perform lossless round-trip across all collections`() {
        val original = SpectrBackupData(
            exportedAtEpochMs = 1_763_680_000_000L,
            dailyEntries = listOf(
                DailyEntry(
                    id = "11111111-1111-4111-8111-111111111111",
                    date = LocalDate.of(2026, 11, 19),
                    mood = -2,
                    energy = 4,
                    dailyDynamics = DailyDynamics.WORSE_EVENING,
                    sleepIntervals = listOf(
                        SleepInterval(
                            id = "22222222-2222-4222-8222-222222222222",
                            entryId = "11111111-1111-4111-8111-111111111111",
                            startMinuteOfDay = 1320,
                            endMinuteOfDay = 90,
                            awakeDeductionMin = 15,
                            source = SleepSource.MANUAL,
                            updatedAt = 1_763_600_000_000L
                        )
                    ),
                    sleepNote = "Прерывистый сон",
                    anxiety = 6,
                    irritability = 5,
                    symptoms = DailySymptoms(apathy = true, brainFog = true),
                    alcoholStatus = AlcoholStatus.LIGHT_OCCASION,
                    medicationStatus = MedicationStatus.TAKEN_AS_PRESCRIBED,
                    note = "Спокойная заметка без лишних деталей",
                    stepsCount = 8420,
                    updatedAt = 1_763_600_000_000L
                )
            ),
            momentCheckIns = listOf(
                MomentCheckIn(
                    id = "33333333-3333-4333-8333-333333333333",
                    entryDate = LocalDate.of(2026, 11, 19),
                    timestampEpochMs = 1_763_550_000_000L,
                    mood = -1,
                    energy = 5,
                    anxiety = 4,
                    irritability = 2,
                    note = "После прогулки стало легче",
                    updatedAt = 1_763_550_000_000L
                )
            ),
            scaleAssessments = listOf(
                ScaleAssessment(
                    id = "44444444-4444-4444-8444-444444444444",
                    scaleType = ScaleType.PHQ9,
                    date = LocalDate.of(2026, 11, 19),
                    answers = listOf(1, 2, 1, 1, 0, 1, 1, 0, 0),
                    totalScore = 7,
                    isConfirmed = true,
                    updatedAt = 1_763_610_000_000L
                )
            ),
            medicationRecords = listOf(
                MedicationRecord(
                    id = "55555555-5555-4555-8555-555555555555",
                    entryDate = LocalDate.of(2026, 11, 19),
                    medicationName = "SampleMed",
                    dosage = "50 mg",
                    takenAtEpochMs = 1_763_530_000_000L,
                    status = MedicationStatus.TAKEN_AS_PRESCRIBED,
                    updatedAt = 1_763_530_000_000L
                )
            )
        )

        val encoded = SpectrBackupCodec.encode(original)
        assertTrue(encoded.contains("\"schemaVersion\": 1"))
        assertTrue(encoded.contains("Спокойная заметка"))

        val decoded = SpectrBackupCodec.decode(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `decode ignores unknown forward-compatible fields`() {
        val json = """
            {
              "schemaVersion": 1,
              "app": "spectr",
              "exportedAtEpochMs": 1763680000000,
              "futureField": "ignored",
              "dailyEntries": [
                {
                  "id": "e1",
                  "date": "2026-11-20",
                  "mood": 1,
                  "energy": 6,
                  "extraNested": 42
                }
              ]
            }
        """.trimIndent()

        val decoded = SpectrBackupCodec.decode(json)
        assertEquals(1, decoded.dailyEntries.size)
        assertEquals(1, decoded.dailyEntries.single().mood)
        assertEquals(LocalDate.of(2026, 11, 20), decoded.dailyEntries.single().date)
    }

    @Test(expected = BackupCodecException::class)
    fun `decode rejects empty payload`() {
        SpectrBackupCodec.decode("   ")
    }

    @Test(expected = BackupCodecException::class)
    fun `decode rejects unsupported schemaVersion`() {
        val json = """{"schemaVersion": 2, "app": "spectr", "exportedAtEpochMs": 100}"""
        SpectrBackupCodec.decode(json)
    }

    @Test(expected = BackupCodecException::class)
    fun `decode rejects wrong app identifier`() {
        val json = """{"schemaVersion": 1, "app": "other-app", "exportedAtEpochMs": 100}"""
        SpectrBackupCodec.decode(json)
    }

    @Test(expected = BackupCodecException::class)
    fun `decode rejects out-of-range mood`() {
        val json = """
            {
              "schemaVersion": 1,
              "app": "spectr",
              "exportedAtEpochMs": 100,
              "dailyEntries": [{"id": "e1", "date": "2026-11-20", "mood": 9, "energy": 5}]
            }
        """.trimIndent()
        SpectrBackupCodec.decode(json)
    }

    @Test(expected = BackupCodecException::class)
    fun `decode rejects invalid ISO date`() {
        val json = """
            {
              "schemaVersion": 1,
              "app": "spectr",
              "exportedAtEpochMs": 100,
              "dailyEntries": [{"id": "e1", "date": "20.11.2026", "mood": 0, "energy": 5}]
            }
        """.trimIndent()
        SpectrBackupCodec.decode(json)
    }

    @Test(expected = BackupCodecException::class)
    fun `decode rejects unknown enum value`() {
        val json = """
            {
              "schemaVersion": 1,
              "app": "spectr",
              "exportedAtEpochMs": 100,
              "dailyEntries": [{"id": "e1", "date": "2026-11-20", "mood": 0, "energy": 5, "alcoholStatus": "WINE"}]
            }
        """.trimIndent()
        SpectrBackupCodec.decode(json)
    }
}
