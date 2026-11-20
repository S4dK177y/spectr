package app.spectr.mood.di

import app.spectr.mood.data.local.db.repository.LocalBackupRepository
import app.spectr.mood.data.local.db.repository.OfflineDiaryRepository
import app.spectr.mood.data.local.db.repository.OfflineScaleAssessmentRepository
import app.spectr.mood.domain.repository.BackupRepository
import app.spectr.mood.domain.repository.DiaryRepository
import app.spectr.mood.domain.repository.ScaleAssessmentRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindDiaryRepository(impl: OfflineDiaryRepository): DiaryRepository

    @Binds
    @Singleton
    abstract fun bindScaleAssessmentRepository(impl: OfflineScaleAssessmentRepository): ScaleAssessmentRepository

    @Binds
    @Singleton
    abstract fun bindBackupRepository(impl: LocalBackupRepository): BackupRepository
}
