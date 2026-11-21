package app.spectr.mood.domain.report

import app.spectr.mood.domain.calculator.AnalyticsPeriod
import app.spectr.mood.domain.calculator.AutoScalesSnapshot
import app.spectr.mood.domain.calculator.DiaryAnalyticsSummary
import app.spectr.mood.domain.calendar.CalendarMonthGrid
import app.spectr.mood.domain.chart.OverviewChartData
import app.spectr.mood.domain.model.AlcoholStatus
import app.spectr.mood.domain.model.DailyDynamics
import app.spectr.mood.domain.model.DailySymptoms
import app.spectr.mood.domain.model.MedicationStatus
import app.spectr.mood.domain.model.MomentCheckIn
import app.spectr.mood.domain.model.ScaleAssessment
import app.spectr.mood.domain.model.ScaleType
import java.time.LocalDate

/**
 * Period presets offered in the Doctor PDF export modal.
 */
enum class ReportPeriodPreset {
    DAYS_14,
    DAYS_30,
    DAYS_90,
    ALL_TIME,
    CUSTOM
}

/**
 * User-selected configuration for generating a clinical summary snapshot or PDF report.
 */
data class DoctorReportOptions(
    val period: AnalyticsPeriod = AnalyticsPeriod.LAST_30_DAYS,
    val includeDailyTable: Boolean = false,
    val includePersonalNotesAndMoments: Boolean = false
)

/**
 * Combined auto-calculated and latest confirmed status for a single clinical scale.
 */
data class ReportScaleOverview(
    val scaleType: ScaleType,
    val maxScore: Int,
    val windowDays: Int,
    val autoScore: Int?,
    val autoWindowStart: LocalDate?,
    val autoWindowEnd: LocalDate?,
    val autoFilledDays: Int?,
    val latestConfirmed: ScaleAssessment?
)

/**
 * One chronological row for the optional daily table / Doctor View log.
 *
 * When [DoctorReportOptions.includePersonalNotesAndMoments] is `false`, [note], [sleepNote],
 * and [moments] are strictly redacted (empty) at the domain layer.
 */
data class ReportDailyRow(
    val date: LocalDate,
    val mood: Int,
    val dailyDynamics: DailyDynamics,
    val sleepMinutes: Int?,
    val isShortSleep: Boolean,
    val energy: Int,
    val anxiety: Int,
    val irritability: Int,
    val symptoms: DailySymptoms,
    val isMixedDay: Boolean,
    val alcoholStatus: AlcoholStatus,
    val medicationStatus: MedicationStatus,
    val note: String = "",
    val sleepNote: String = "",
    val moments: List<MomentCheckIn> = emptyList()
) {
    val hasPersonalContent: Boolean
        get() = note.isNotBlank() || sleepNote.isNotBlank() || moments.isNotEmpty()
}

/**
 * Complete, immutable report snapshot shared by Doctor Mode and the vector PDF generator.
 */
data class DoctorReportSnapshot(
    val generatedDate: LocalDate,
    val period: AnalyticsPeriod,
    val effectiveStartDate: LocalDate?,
    val effectiveEndDate: LocalDate?,
    val summary: DiaryAnalyticsSummary,
    val chartData: OverviewChartData,
    val calendarGrid: CalendarMonthGrid,
    val autoScales: AutoScalesSnapshot?,
    val scalesOverview: List<ReportScaleOverview>,
    val dailyRows: List<ReportDailyRow>,
    val includeDailyTable: Boolean,
    val includePersonalNotesAndMoments: Boolean,
    val totalNotesInPeriod: Int,
    val totalMomentsInPeriod: Int
) {
    val isEmpty: Boolean
        get() = summary.filledDays == 0

    val suggestedFileName: String
        get() {
            val startTag = (effectiveStartDate ?: generatedDate).toString().replace("-", "")
            val endTag = (effectiveEndDate ?: generatedDate).toString().replace("-", "")
            return "spectr-report-$startTag-$endTag.pdf"
        }
}
