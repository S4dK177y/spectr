package app.spectr.mood.domain.repository

import app.spectr.mood.domain.backup.BackupImportMode
import app.spectr.mood.domain.backup.BackupImportSummary

/**
 * Contract for exporting and importing full local diary backups in JSON v1 format.
 */
interface BackupRepository {
    suspend fun exportJson(exportedAtEpochMs: Long = System.currentTimeMillis()): String
    suspend fun importJson(rawJson: String, mode: BackupImportMode = BackupImportMode.MERGE): BackupImportSummary
}
