package campus.calendar;


import java.time.*;
import java.util.Objects;

/** Campus dates use Korea time even when the desktop is in another time zone. */
public final class PlannerTime {
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private PlannerTime() { }

    public static LocalDate today() { return LocalDate.now(ZONE); }
    public static LocalTime now() { return LocalTime.now(ZONE); }
    public static LocalDateTime nowDateTime() { return LocalDateTime.now(ZONE); }

    public static LocalDate forNewItem(LocalDate selected) { return forNewItem(selected, today()); }

    public static LocalDate forNewItem(LocalDate selected, LocalDate today) {
        Objects.requireNonNull(selected, "선택 날짜");
        return selected.isBefore(today) ? today : selected;
    }
}
