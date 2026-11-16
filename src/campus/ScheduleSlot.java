package campus;

import java.time.LocalTime;
import java.util.Objects;

/** Suggested same-day times for an insertion point in the daily schedule. */
record ScheduleSlot(LocalTime start, LocalTime end) {
    private static final int LAST_MINUTE = 23 * 60 + 59;

    ScheduleSlot {
        Objects.requireNonNull(start, "시작 시각");
        Objects.requireNonNull(end, "종료 시각");
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("종료 시각은 시작 시각보다 늦어야 합니다.");
        }
    }

    static ScheduleSlot before(LocalTime boundary) {
        int endMinute = floorMinute(boundary);
        return fromMinutes(Math.max(0, endMinute - 60), endMinute);
    }

    static ScheduleSlot after(LocalTime boundary) {
        int startMinute = ceilingMinute(boundary);
        return fromMinutes(startMinute, Math.min(LAST_MINUTE, startMinute + 60));
    }

    static ScheduleSlot between(LocalTime previousEnd, LocalTime nextStart) {
        return fromMinutes(ceilingMinute(previousEnd), floorMinute(nextStart));
    }

    private static int floorMinute(LocalTime time) {
        Objects.requireNonNull(time, "기준 시각");
        return time.getHour() * 60 + time.getMinute();
    }

    private static int ceilingMinute(LocalTime time) {
        int minute = floorMinute(time);
        return minute + (time.getSecond() != 0 || time.getNano() != 0 ? 1 : 0);
    }

    private static ScheduleSlot fromMinutes(int startMinute, int endMinute) {
        if (startMinute < 0 || endMinute > LAST_MINUTE || startMinute >= endMinute) return null;
        return new ScheduleSlot(LocalTime.of(startMinute / 60, startMinute % 60),
                LocalTime.of(endMinute / 60, endMinute % 60));
    }
}
