package app.spectr.mood.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import app.spectr.mood.data.local.db.dao.DailyEntryDao
import app.spectr.mood.data.local.db.dao.MedicationRecordDao
import app.spectr.mood.data.local.db.dao.MomentCheckInDao
import app.spectr.mood.data.local.db.dao.ScaleAssessmentDao
import app.spectr.mood.data.local.db.entity.DailyEntryEntity
import app.spectr.mood.data.local.db.entity.MedicationRecordEntity
import app.spectr.mood.data.local.db.entity.MomentCheckInEntity
import app.spectr.mood.data.local.db.entity.ScaleAssessmentEntity
import app.spectr.mood.data.local.db.entity.SleepIntervalEntity

/**
 * Offline-first local SQLite database for Spectr.
 */
@Database(
    entities = [
        DailyEntryEntity::class,
        SleepIntervalEntity::class,
        MomentCheckInEntity::class,
        ScaleAssessmentEntity::class,
        MedicationRecordEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class SpectrDatabase : RoomDatabase() {
    abstract fun dailyEntryDao(): DailyEntryDao
    abstract fun momentCheckInDao(): MomentCheckInDao
    abstract fun scaleAssessmentDao(): ScaleAssessmentDao
    abstract fun medicationRecordDao(): MedicationRecordDao

    companion object {
        const val DATABASE_NAME = "spectr.db"
    }
}
