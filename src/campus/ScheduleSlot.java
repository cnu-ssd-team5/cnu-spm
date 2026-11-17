package campus;

import java.time.LocalTime;
import java.util.Objects;

/** Suggested same-day times for an insertion point in the daily schedule. */
record ScheduleSlot(LocalTime start, LocalTime end) {
    private static final int DAY_END = 24 * 60;
    private static final int DAY_START = 8 * 60;

    ScheduleSlot {
        Objects.requireNonNull(start, "시작 시각");
        Objects.requireNonNull(end, "종료 시각");
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("종료 시각은 시작 시각보다 늦어야 합니다.");
        }
    }

    static ScheduleSlot before(LocalTime boundary) {
        int endMinute = floorFiveMinutes(boundary);
        return fromMinutes(Math.max(DAY_START, endMinute - 60), endMinute);
    }

    static ScheduleSlot after(LocalTime boundary) {
        int startMinute = Math.max(DAY_START, ceilingFiveMinutes(boundary));
        return fromMinutes(startMinute, Math.min(DAY_END, startMinute + 60));
    }

    static ScheduleSlot between(LocalTime previousEnd, LocalTime nextStart) {
        return fromMinutes(Math.max(DAY_START, ceilingFiveMinutes(previousEnd)), floorFiveMinutes(nextStart));
    }

    private static int floorFiveMinutes(LocalTime time) {
        Objects.requireNonNull(time, "기준 시각");
        if (time.equals(LocalTime.MAX)) return DAY_END;
        return (time.getHour() * 60 + time.getMinute()) / 5 * 5;
    }

    private static int ceilingFiveMinutes(LocalTime time) {
        int minute = time.getHour() * 60 + time.getMinute();
        int rounded = (minute + 4) / 5 * 5;
        return rounded == minute && (time.getSecond() != 0 || time.getNano() != 0) ? rounded + 5 : rounded;
    }

    private static ScheduleSlot fromMinutes(int startMinute, int endMinute) {
        if (startMinute < 0 || endMinute > DAY_END || startMinute >= endMinute) return null;
        return new ScheduleSlot(LocalTime.of(startMinute / 60, startMinute % 60),
                endMinute == DAY_END ? LocalTime.MAX : LocalTime.of(endMinute / 60, endMinute % 60));
    }
}
