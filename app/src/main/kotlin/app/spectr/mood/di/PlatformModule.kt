package app.spectr.mood.di

import app.spectr.mood.data.local.prefs.ReminderPreferencesRepository
import app.spectr.mood.data.local.prefs.SecurityPreferencesRepository
import app.spectr.mood.domain.reminder.LocalDataChangeHook
import app.spectr.mood.domain.repository.ReminderSettingsRepository
import app.spectr.mood.domain.repository.SecuritySettingsRepository
import app.spectr.mood.platform.SpectrPlatformDataHook
import app.spectr.mood.platform.notifications.AlarmScheduler
import app.spectr.mood.platform.notifications.NotificationPublisher
import app.spectr.mood.platform.notifications.SpectrAlarmScheduler
import app.spectr.mood.platform.notifications.SpectrNotificationPublisher
import app.spectr.mood.platform.pdf.PdfExportStorage
import app.spectr.mood.platform.pdf.PdfReportExporter
import app.spectr.mood.security.BiometricAvailabilityProvider
import app.spectr.mood.security.BiometricLockManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PlatformModule {

    @Binds
    @Singleton
    abstract fun bindReminderSettingsRepository(impl: ReminderPreferencesRepository): ReminderSettingsRepository

    @Binds
    @Singleton
    abstract fun bindAlarmScheduler(impl: SpectrAlarmScheduler): AlarmScheduler

    @Binds
    @Singleton
    abstract fun bindNotificationPublisher(impl: SpectrNotificationPublisher): NotificationPublisher

    @Binds
    @Singleton
    abstract fun bindLocalDataChangeHook(impl: SpectrPlatformDataHook): LocalDataChangeHook

    @Binds
    @Singleton
    abstract fun bindSecuritySettingsRepository(impl: SecurityPreferencesRepository): SecuritySettingsRepository

    @Binds
    @Singleton
    abstract fun bindPdfReportExporter(impl: PdfExportStorage): PdfReportExporter

    @Binds
    @Singleton
    abstract fun bindBiometricAvailabilityProvider(impl: BiometricLockManager): BiometricAvailabilityProvider
}
