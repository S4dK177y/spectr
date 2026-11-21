package app.spectr.mood.domain.report

import app.spectr.mood.domain.calculator.AnalyticsCalculator
import app.spectr.mood.domain.calculator.AnalyticsPeriod
import app.spectr.mood.domain.calculator.AutoScalesSnapshot
import app.spectr.mood.domain.calculator.ClinicalScaleCalculator
import app.spectr.mood.domain.calculator.SleepDurationCalculator
import app.spectr.mood.domain.calendar.CalendarMonthBuilder
import app.spectr.mood.domain.chart.ChartGeometry
import app.spectr.mood.domain.model.DailyEntry
import app.spectr.mood.domain.model.MomentCheckIn
import app.spectr.mood.domain.model.ScaleAssessment
import app.spectr.mood.domain.model.ScaleType
import java.time.LocalDate
import java.time.YearMonth

/**
 * Pure-Kotlin builder assembling a privacy-enforced [DoctorReportSnapshot] from local diary data.
 */
object DoctorReportBuilder {

    private const val PHQ9_MAX_SCORE = 27
    private const val GAD7_MAX_SCORE = 21
    private const val ASRM_MAX_SCORE = 20

    fun buildSnapshot(
        entries: List<DailyEntry>,
        moments: List<MomentCheckIn>,
        assessments: List<ScaleAssessment>,
        options: DoctorReportOptions,
        today: LocalDate,
        calendarMonthOverride: YearMonth? = null
    ): DoctorReportSnapshot {
        val activeEntries = deduplicateActiveEntries(entries)
        val filteredEntries = AnalyticsCalculator.filterEntriesForPeriod(
            entries = activeEntries,
            period = options.period,
            anchorDate = today
        )
        val summary = AnalyticsCalculator.calculateSummary(
            entries = activeEntries,
            period = options.period,
            anchorDate = today
        )
        val chartData = ChartGeometry.buildOverviewChartData(filteredEntries)

        val (effectiveStart, effectiveEnd) = resolveEffectiveRange(
            period = options.period,
            filteredEntries = filteredEntries,
            today = today
        )

        val entriesByDate = activeEntries.associateBy { it.date }
        val calendarMonth = calendarMonthOverride
            ?: filteredEntries.lastOrNull()?.date?.let(YearMonth::from)
            ?: effectiveEnd?.let(YearMonth::from)
            ?: YearMonth.from(today)
        val calendarGrid = CalendarMonthBuilder.buildMonthGrid(
            yearMonth = calendarMonth,
            today = today,
            entriesByDate = entriesByDate
        )

        val scaleAnchorDate = filteredEntries.lastOrNull()?.date
        val autoScales = scaleAnchorDate?.let { anchor ->
            ClinicalScaleCalculator.calculateAutoScales(
                entries = activeEntries,
                anchorDateOverride = anchor
            )
        }

        val activeMomentsInRange = filterMomentsInRange(moments, effectiveStart, effectiveEnd)
        val momentsByDate = activeMomentsInRange.groupBy { it.entryDate }
        val totalNotesInPeriod = filteredEntries.count { it.note.isNotBlank() || it.sleepNote.isNotBlank() }

        val dailyRows = buildDailyRows(
            filteredEntries = filteredEntries,
            momentsByDate = momentsByDate,
            includePersonalContent = options.includePersonalNotesAndMoments
        )

        val scalesOverview = buildScalesOverview(
            autoScales = autoScales,
            assessments = assessments,
            effectiveEnd = effectiveEnd
        )

        return DoctorReportSnapshot(
            generatedDate = today,
            period = options.period,
            effectiveStartDate = effectiveStart,
            effectiveEndDate = effectiveEnd,
            summary = summary,
            chartData = chartData,
            calendarGrid = calendarGrid,
            autoScales = autoScales,
            scalesOverview = scalesOverview,
            dailyRows = dailyRows,
            includeDailyTable = options.includeDailyTable,
            includePersonalNotesAndMoments = options.includePersonalNotesAndMoments,
            totalNotesInPeriod = totalNotesInPeriod,
            totalMomentsInPeriod = activeMomentsInRange.size
        )
    }

    private fun deduplicateActiveEntries(entries: List<DailyEntry>): List<DailyEntry> {
        val byDate = LinkedHashMap<LocalDate, DailyEntry>()
        for (entry in entries) {
            if (entry.deletedAt != null) continue
            val existing = byDate[entry.date]
            if (existing == null || entry.updatedAt >= existing.updatedAt) {
                byDate[entry.date] = entry
            }
        }
        return byDate.values.sortedBy { it.date }
    }

    private fun resolveEffectiveRange(
        period: AnalyticsPeriod,
        filteredEntries: List<DailyEntry>,
        today: LocalDate
    ): Pair<LocalDate?, LocalDate?> = when (period) {
        is AnalyticsPeriod.Custom -> period.startDate to period.endDate
        is AnalyticsPeriod.LastDays -> {
            val start = today.minusDays((period.days - 1).toLong())
            start to today
        }
        AnalyticsPeriod.AllTime -> {
            filteredEntries.firstOrNull()?.date to filteredEntries.lastOrNull()?.date
        }
    }

    private fun filterMomentsInRange(
        moments: List<MomentCheckIn>,
        startDate: LocalDate?,
        endDate: LocalDate?
    ): List<MomentCheckIn> = moments
        .asSequence()
        .filter { it.deletedAt == null }
        .filter { moment ->
            val afterStart = startDate == null || !moment.entryDate.isBefore(startDate)
            val beforeEnd = endDate == null || !moment.entryDate.isAfter(endDate)
            afterStart && beforeEnd
        }
        .sortedWith(compareBy<MomentCheckIn> { it.entryDate }.thenBy { it.timestampEpochMs })
        .toList()

    private fun buildDailyRows(
        filteredEntries: List<DailyEntry>,
        momentsByDate: Map<LocalDate, List<MomentCheckIn>>,
        includePersonalContent: Boolean
    ): List<ReportDailyRow> = filteredEntries.map { entry ->
        val hasSleep = SleepDurationCalculator.hasSleepData(entry)
        val sleepMinutes = if (hasSleep) SleepDurationCalculator.effectiveSleepMinutes(entry) else null
        val isShortSleep = hasSleep &&
            SleepDurationCalculator.effectiveSleepHours(entry) < ChartGeometry.SHORT_SLEEP_THRESHOLD_HOURS

        ReportDailyRow(
            date = entry.date,
            mood = entry.mood,
            dailyDynamics = entry.dailyDynamics,
            sleepMinutes = sleepMinutes,
            isShortSleep = isShortSleep,
            energy = entry.energy,
            anxiety = entry.anxiety,
            irritability = entry.irritability,
            symptoms = entry.symptoms,
            isMixedDay = AnalyticsCalculator.isMixedDay(entry),
            alcoholStatus = entry.alcoholStatus,
            medicationStatus = entry.medicationStatus,
            note = if (includePersonalContent) entry.note.trim() else "",
            sleepNote = if (includePersonalContent) entry.sleepNote.trim() else "",
            moments = if (includePersonalContent) momentsByDate[entry.date].orEmpty() else emptyList()
        )
    }

    private fun buildScalesOverview(
        autoScales: AutoScalesSnapshot?,
        assessments: List<ScaleAssessment>,
        effectiveEnd: LocalDate?
    ): List<ReportScaleOverview> {
        val activeConfirmed = assessments
            .asSequence()
            .filter { it.deletedAt == null && it.isConfirmed }
            .filter { assessment -> effectiveEnd == null || !assessment.date.isAfter(effectiveEnd) }
            .toList()

        fun latestConfirmed(type: ScaleType): ScaleAssessment? = activeConfirmed
            .asSequence()
            .filter { it.scaleType == type }
            .sortedWith(compareByDescending<ScaleAssessment> { it.date }.thenByDescending { it.updatedAt })
            .firstOrNull()

        return listOf(
            ReportScaleOverview(
                scaleType = ScaleType.PHQ9,
                maxScore = PHQ9_MAX_SCORE,
                windowDays = ClinicalScaleCalculator.PHQ9_WINDOW_DAYS,
                autoScore = autoScales?.phq9?.totalScore,
                autoWindowStart = autoScales?.phq9?.windowStart,
                autoWindowEnd = autoScales?.phq9?.windowEnd,
                autoFilledDays = autoScales?.phq9?.filledDaysInWindow,
                latestConfirmed = latestConfirmed(ScaleType.PHQ9)
            ),
            ReportScaleOverview(
                scaleType = ScaleType.GAD7,
                maxScore = GAD7_MAX_SCORE,
                windowDays = ClinicalScaleCalculator.GAD7_WINDOW_DAYS,
                autoScore = autoScales?.gad7?.totalScore,
                autoWindowStart = autoScales?.gad7?.windowStart,
                autoWindowEnd = autoScales?.gad7?.windowEnd,
                autoFilledDays = autoScales?.gad7?.filledDaysInWindow,
                latestConfirmed = latestConfirmed(ScaleType.GAD7)
            ),
            ReportScaleOverview(
                scaleType = ScaleType.ASRM,
                maxScore = ASRM_MAX_SCORE,
                windowDays = ClinicalScaleCalculator.ASRM_WINDOW_DAYS,
                autoScore = autoScales?.asrm?.totalScore,
                autoWindowStart = autoScales?.asrm?.windowStart,
                autoWindowEnd = autoScales?.asrm?.windowEnd,
                autoFilledDays = autoScales?.asrm?.filledDaysInWindow,
                latestConfirmed = latestConfirmed(ScaleType.ASRM)
            )
        )
    }
}
