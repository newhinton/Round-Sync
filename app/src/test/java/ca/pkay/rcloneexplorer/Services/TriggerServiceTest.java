package ca.pkay.rcloneexplorer.Services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Calendar;
import java.util.TimeZone;

public class TriggerServiceTest {

    @Test
    public void futureScheduleKeepsTodayAndClearsSubMinutePrecision() {
        TimeZone zone = TimeZone.getTimeZone("UTC");
        long now = utc(2026, Calendar.NOVEMBER, 4, 10, 29, 15, 250);

        long scheduled = TriggerService.nextScheduleTime(now, 630, zone);

        Calendar result = calendar(scheduled, zone);
        assertEquals(2026, result.get(Calendar.YEAR));
        assertEquals(Calendar.NOVEMBER, result.get(Calendar.MONTH));
        assertEquals(4, result.get(Calendar.DAY_OF_MONTH));
        assertEquals(10, result.get(Calendar.HOUR_OF_DAY));
        assertEquals(30, result.get(Calendar.MINUTE));
        assertEquals(0, result.get(Calendar.SECOND));
        assertEquals(0, result.get(Calendar.MILLISECOND));
    }

    @Test
    public void scheduleAtSameMinuteButLaterSecondUsesNextDay() {
        long now = utc(2026, Calendar.NOVEMBER, 4, 10, 30, 15, 0);

        long scheduled = TriggerService.nextScheduleTime(
                now,
                630,
                TimeZone.getTimeZone("UTC")
        );

        Calendar result = calendar(scheduled, TimeZone.getTimeZone("UTC"));
        assertEquals(2026, result.get(Calendar.YEAR));
        assertEquals(Calendar.NOVEMBER, result.get(Calendar.MONTH));
        assertEquals(5, result.get(Calendar.DAY_OF_MONTH));
        assertEquals(10, result.get(Calendar.HOUR_OF_DAY));
        assertEquals(30, result.get(Calendar.MINUTE));
    }

    @Test
    public void pastScheduleUsesNextCalendarDayRatherThanFixedTwentyFourHours() {
        TimeZone zone = TimeZone.getTimeZone("America/New_York");
        Calendar now = Calendar.getInstance(zone);
        now.set(2026, Calendar.NOVEMBER, 1, 0, 0, 0);
        now.set(Calendar.MILLISECOND, 0);

        long scheduled = TriggerService.nextScheduleTime(now.getTimeInMillis(), 0, zone);

        Calendar result = calendar(scheduled, zone);
        assertEquals(2026, result.get(Calendar.YEAR));
        assertEquals(Calendar.NOVEMBER, result.get(Calendar.MONTH));
        assertEquals(2, result.get(Calendar.DAY_OF_MONTH));
        assertEquals(0, result.get(Calendar.HOUR_OF_DAY));
        assertEquals(0, result.get(Calendar.MINUTE));
        assertTrue(scheduled - now.getTimeInMillis() > 24L * 60L * 60L * 1000L);
    }

    private static long utc(
            int year,
            int month,
            int day,
            int hour,
            int minute,
            int second,
            int millisecond
    ) {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        calendar.set(year, month, day, hour, minute, second);
        calendar.set(Calendar.MILLISECOND, millisecond);
        return calendar.getTimeInMillis();
    }

    private static Calendar calendar(long timeInMillis, TimeZone zone) {
        Calendar calendar = Calendar.getInstance(zone);
        calendar.setTimeInMillis(timeInMillis);
        return calendar;
    }
}
