package app.spectr.mood.domain.reminder

import app.spectr.mood.domain.model.ReminderConfig
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderSchedulerCalculatorTest {

    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 11, 21)

    @Test
    fun `disabled daily reminder returns null schedule`() {
        val config = ReminderConfig(dailyEnabled = false, dailyHour = 22, dailyMinute = 0)
        val now = ZonedDateTime.of(2026, 11, 21, 18, 0, 0, 0, zone)

        val result = ReminderSchedulerCalculator.computeNextDailyTrigger(
            config = config,
            now = now,
            isTodayEntryFilled = false
        )

        assertNull(result)
        assertFalse(ReminderSchedulerCalculator.shouldShowDailyReminder(config, isTodayEntryFilled = false))
    }

    @Test
    fun `unfilled today before reminder time schedules today`() {
        val config = ReminderConfig(dailyEnabled = true, dailyHour = 22, dailyMinute = 0)
        val now = ZonedDateTime.of(2026, 11, 21, 18, 30, 0, 0, zone)

        val result = ReminderSchedulerCalculator.computeNextDailyTrigger(
            config = config,
            now = now,
            isTodayEntryFilled = false
        )

        assertNotNull(result)
        assertFalse(result!!.isSnoozed)
        assertEquals(today, result.targetDate)
        assertEquals(
            ZonedDateTime.of(2026, 11, 21, 22, 0, 0, 0, zone).toInstant().toEpochMilli(),
            result.triggerAtEpochMs
        )
    }

    @Test
    fun `unfilled today after reminder time schedules tomorrow`() {
        val config = ReminderConfig(dailyEnabled = true, dailyHour = 22, dailyMinute = 0)
        val now = ZonedDateTime.of(2026, 11, 21, 22, 15, 0, 0, zone)

        val result = ReminderSchedulerCalculator.computeNextDailyTrigger(
            config = config,
            now = now,
            isTodayEntryFilled = false
        )

        assertNotNull(result)
        assertFalse(result!!.isSnoozed)
        assertEquals(today.plusDays(1), result.targetDate)
        assertEquals(
            ZonedDateTime.of(2026, 11, 22, 22, 0, 0, 0, zone).toInstant().toEpochMilli(),
            result.triggerAtEpochMs
        )
    }

    @Test
    fun `smart cancellation skips today and schedules tomorrow when today is filled`() {
        val config = ReminderConfig(dailyEnabled = true, dailyHour = 22, dailyMinute = 0)
        val now = ZonedDateTime.of(2026, 11, 21, 19, 0, 0, 0, zone)

        val result = ReminderSchedulerCalculator.computeNextDailyTrigger(
            config = config,
            now = now,
            isTodayEntryFilled = true
        )

        assertNotNull(result)
        assertFalse(result!!.isSnoozed)
        assertEquals(today.plusDays(1), result.targetDate)
        assertFalse(ReminderSchedulerCalculator.shouldShowDailyReminder(config, isTodayEntryFilled = true))
    }

    @Test
    fun `active snooze is honored when unfilled and ignored when today is filled`() {
        val now = ZonedDateTime.of(2026, 11, 21, 22, 5, 0, 0, zone)
        val snoozeMs = ReminderSchedulerCalculator.computeSnoozeTriggerEpochMs(now.toInstant().toEpochMilli())
        val config = ReminderConfig(
            dailyEnabled = true,
            dailyHour = 22,
            dailyMinute = 0,
            snoozedUntilEpochMs = snoozeMs
        )

        val unfilledSchedule = ReminderSchedulerCalculator.computeNextDailyTrigger(
            config = config,
            now = now,
            isTodayEntryFilled = false
        )
        assertNotNull(unfilledSchedule)
        assertTrue(unfilledSchedule!!.isSnoozed)
        assertEquals(snoozeMs, unfilledSchedule.triggerAtEpochMs)
        assertEquals(now.plusHours(1).toInstant().toEpochMilli(), snoozeMs)

        val filledSchedule = ReminderSchedulerCalculator.computeNextDailyTrigger(
            config = config,
            now = now,
            isTodayEntryFilled = true
        )
        assertNotNull(filledSchedule)
        assertFalse(filledSchedule!!.isSnoozed)
        assertEquals(today.plusDays(1), filledSchedule.targetDate)
    }

    @Test
    fun `scales reminder anchors 14 days after latest confirmed assessment`() {
        val config = ReminderConfig(scalesEnabled = true, scalesIntervalDays = 14)
        val now = ZonedDateTime.of(2026, 11, 21, 10, 0, 0, 0, zone)
        val latestConfirmed = LocalDate.of(2026, 11, 15)

        val result = ReminderSchedulerCalculator.computeNextScalesTrigger(
            config = config,
            now = now,
            latestConfirmedAssessmentDate = latestConfirmed,
            earliestDiaryEntryDate = LocalDate.of(2026, 10, 1)
        )

        assertNotNull(result)
        assertEquals(LocalDate.of(2026, 11, 29), result!!.targetDate)
        assertEquals(
            ZonedDateTime.of(2026, 11, 29, 18, 0, 0, 0, zone).toInstant().toEpochMilli(),
            result.triggerAtEpochMs
        )
        assertFalse(ReminderSchedulerCalculator.shouldShowScalesReminder(config, now, latestConfirmed))
    }

    @Test
    fun `scales reminder falls back to earliest diary entry and handles overdue slots cleanly`() {
        val config = ReminderConfig(scalesEnabled = true, scalesIntervalDays = 14)
        val nowBeforeSlot = ZonedDateTime.of(2026, 11, 21, 14, 0, 0, 0, zone)
        val nowAfterSlot = ZonedDateTime.of(2026, 11, 21, 19, 30, 0, 0, zone)

        val overdueToday = ReminderSchedulerCalculator.computeNextScalesTrigger(
            config = config,
            now = nowBeforeSlot,
            latestConfirmedAssessmentDate = null,
            earliestDiaryEntryDate = LocalDate.of(2026, 11, 1)
        )
        assertNotNull(overdueToday)
        assertEquals(today, overdueToday!!.targetDate)

        val overdueTomorrow = ReminderSchedulerCalculator.computeNextScalesTrigger(
            config = config,
            now = nowAfterSlot,
            latestConfirmedAssessmentDate = null,
            earliestDiaryEntryDate = LocalDate.of(2026, 11, 1)
        )
        assertNotNull(overdueTomorrow)
        assertEquals(today.plusDays(1), overdueTomorrow!!.targetDate)
    }

    @Test
    fun `scales reminder respects last reminder timestamp to prevent daily spam`() {
        val lastRemindedAt = ZonedDateTime.of(2026, 11, 20, 18, 0, 0, 0, zone).toInstant().toEpochMilli()
        val config = ReminderConfig(
            scalesEnabled = true,
            scalesIntervalDays = 14,
            lastScalesReminderEpochMs = lastRemindedAt
        )
        val now = ZonedDateTime.of(2026, 11, 21, 18, 0, 0, 0, zone)

        assertFalse(
            ReminderSchedulerCalculator.shouldShowScalesReminder(
                config = config,
                now = now,
                latestConfirmedAssessmentDate = LocalDate.of(2026, 10, 1)
            )
        )

        val next = ReminderSchedulerCalculator.computeNextScalesTrigger(
            config = config,
            now = now,
            latestConfirmedAssessmentDate = LocalDate.of(2026, 10, 1),
            earliestDiaryEntryDate = LocalDate.of(2026, 10, 1)
        )
        assertNotNull(next)
        assertEquals(LocalDate.of(2026, 12, 4), next!!.targetDate)
    }

    @Test
    fun `scales reminder shows when 14 or more days elapsed since last confirmation`() {
        val config = ReminderConfig(scalesEnabled = true, scalesIntervalDays = 14)
        val now = ZonedDateTime.of(2026, 11, 21, 18, 0, 0, 0, zone)

        assertTrue(
            ReminderSchedulerCalculator.shouldShowScalesReminder(
                config = config,
                now = now,
                latestConfirmedAssessmentDate = LocalDate.of(2026, 11, 7)
            )
        )
    }
}
