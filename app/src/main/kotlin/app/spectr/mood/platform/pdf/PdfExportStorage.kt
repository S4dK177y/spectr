package app.spectr.mood.platform.pdf

import android.content.Context
import android.net.Uri
import app.spectr.mood.domain.report.DoctorReportSnapshot
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Generated PDF file metadata ready for viewing or sharing via [FileProvider].
 */
data class ExportedPdfFile(val file: File, val fileName: String, val pageCount: Int)

/**
 * Abstraction for exporting a [DoctorReportSnapshot] to a cached [FileProvider] file or SAF [Uri].
 */
interface PdfReportExporter {
    suspend fun exportToCache(snapshot: DoctorReportSnapshot): ExportedPdfFile
    suspend fun exportToUri(snapshot: DoctorReportSnapshot, destinationUri: Uri): Int
}

/**
 * Handles writing generated PDF reports to cache (`FileProvider`-backed) or a user-chosen SAF [Uri].
 */
@Singleton
class PdfExportStorage @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val generator: DoctorPdfReportGenerator
) : PdfReportExporter {

    override suspend fun exportToCache(snapshot: DoctorReportSnapshot): ExportedPdfFile = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, EXPORTS_DIR).apply { mkdirs() }
        pruneOldExports(dir)
        val fileName = snapshot.suggestedFileName
        val targetFile = File(dir, fileName)
        val pageCount = FileOutputStream(targetFile).use { stream ->
            generator.generate(snapshot, stream)
        }
        ExportedPdfFile(
            file = targetFile,
            fileName = fileName,
            pageCount = pageCount
        )
    }

    override suspend fun exportToUri(snapshot: DoctorReportSnapshot, destinationUri: Uri): Int =
        withContext(Dispatchers.IO) {
            val stream = requireNotNull(context.contentResolver.openOutputStream(destinationUri)) {
                "Unable to open output stream for $destinationUri"
            }
            stream.use { output ->
                generator.generate(snapshot, output)
            }
        }

    private fun pruneOldExports(dir: File) {
        val existing = dir.listFiles().orEmpty().sortedByDescending { it.lastModified() }
        existing.drop(MAX_CACHED_EXPORTS).forEach { file -> file.delete() }
    }

    companion object {
        private const val EXPORTS_DIR = "exports"
        private const val MAX_CACHED_EXPORTS = 5
    }
}
