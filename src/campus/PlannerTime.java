package campus;

import java.time.*;
import java.util.Objects;

/** Campus dates use Korea time even when the desktop is in another time zone. */
final class PlannerTime {
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private PlannerTime() { }

    static LocalDate today() { return LocalDate.now(ZONE); }
    static LocalTime now() { return LocalTime.now(ZONE); }
    static LocalDateTime nowDateTime() { return LocalDateTime.now(ZONE); }

    static LocalDate forNewItem(LocalDate selected) { return forNewItem(selected, today()); }

    static LocalDate forNewItem(LocalDate selected, LocalDate today) {
        Objects.requireNonNull(selected, "선택 날짜");
        return selected.isBefore(today) ? today : selected;
    }
}
