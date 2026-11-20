package app.spectr.mood.di

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import app.spectr.mood.data.local.db.DatabaseTransactionRunner
import app.spectr.mood.data.local.db.EpochClock
import app.spectr.mood.data.local.db.SpectrDatabase
import app.spectr.mood.data.local.db.dao.DailyEntryDao
import app.spectr.mood.data.local.db.dao.MedicationRecordDao
import app.spectr.mood.data.local.db.dao.MomentCheckInDao
import app.spectr.mood.data.local.db.dao.ScaleAssessmentDao
import app.spectr.mood.data.local.prefs.ThemePreferencesRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideSpectrDatabase(@ApplicationContext context: Context): SpectrDatabase = Room.databaseBuilder(
        context,
        SpectrDatabase::class.java,
        SpectrDatabase.DATABASE_NAME
    ).build()

    @Provides
    fun provideDailyEntryDao(database: SpectrDatabase): DailyEntryDao = database.dailyEntryDao()

    @Provides
    fun provideMomentCheckInDao(database: SpectrDatabase): MomentCheckInDao = database.momentCheckInDao()

    @Provides
    fun provideScaleAssessmentDao(database: SpectrDatabase): ScaleAssessmentDao = database.scaleAssessmentDao()

    @Provides
    fun provideMedicationRecordDao(database: SpectrDatabase): MedicationRecordDao = database.medicationRecordDao()

    @Provides
    @Singleton
    fun provideDatabaseTransactionRunner(database: SpectrDatabase): DatabaseTransactionRunner =
        object : DatabaseTransactionRunner {
            override suspend fun <T> runInTransaction(block: suspend () -> T): T = database.withTransaction(block)
        }

    @Provides
    @Singleton
    fun provideEpochClock(): EpochClock = EpochClock { System.currentTimeMillis() }

    @Provides
    @Singleton
    fun provideSystemClock(): Clock = Clock.systemDefaultZone()

    @Provides
    @Singleton
    fun provideThemePreferencesRepository(@ApplicationContext context: Context): ThemePreferencesRepository =
        ThemePreferencesRepository(context)
}
