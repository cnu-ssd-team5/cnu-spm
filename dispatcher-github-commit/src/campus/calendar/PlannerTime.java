package campus.calendar;

import java.time.*;

/** Campus dates use Korea time even when the desktop is in another time zone. */
public final class PlannerTime {
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private PlannerTime() { }

    public static LocalDate today() { return LocalDate.now(ZONE); }
    public static LocalTime now() { return LocalTime.now(ZONE); }
}
