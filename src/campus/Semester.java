package campus;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;

/** Teaching terms and the vacations between them, named by the academic year. */
public record Semester(int year, Season season) {
    public enum Season {
        SPRING("1학기"), SUMMER("여름방학"), FALL("2학기"), WINTER("겨울방학");

        private final String label;
        Season(String label) { this.label = label; }
        public String label() { return label; }
    }

    public Semester {
        if (year < 1 || year > 9999) throw new IllegalArgumentException("연도는 1~9999 사이여야 합니다.");
        Objects.requireNonNull(season, "학기");
    }

    public LocalDate start() {
        return switch (season) {
            case SPRING -> firstWeekday(year, 3, 2);
            case SUMMER -> firstWeekday(year, 3, 2).plusWeeks(16);
            case FALL -> firstWeekday(year, 9, 1);
            case WINTER -> firstWeekday(year, 9, 1).plusWeeks(16);
        };
    }

    private static LocalDate firstWeekday(int year, int month, int day) {
        LocalDate date = LocalDate.of(year, month, day);
        while (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            date = date.plusDays(1);
        }
        return date;
    }

    public LocalDate end() {
        return switch (season) {
            case SPRING, FALL -> start().plusWeeks(16).minusDays(1);
            case SUMMER -> firstWeekday(year, 9, 1).minusDays(1);
            case WINTER -> firstWeekday(year + 1, 3, 2).minusDays(1);
        };
    }

    public boolean contains(LocalDate date) {
        Objects.requireNonNull(date, "날짜");
        return !date.isBefore(start()) && !date.isAfter(end());
    }

    public String id() {
        return String.format(Locale.ROOT, "%04d-%s", year, season.name().toLowerCase(Locale.ROOT));
    }

    public static Semester parse(String id) {
        if (id == null || !id.matches("[0-9]{4}-(spring|summer|fall|winter)")) {
            throw new IllegalArgumentException("학기 형식이 올바르지 않습니다.");
        }
        return new Semester(Integer.parseInt(id.substring(0, 4)),
                Season.valueOf(id.substring(5).toUpperCase(Locale.ROOT)));
    }

    public static Semester forDate(LocalDate date) {
        Objects.requireNonNull(date, "날짜");
        for (Season season : Season.values()) {
            Semester semester = new Semester(date.getYear(), season);
            if (semester.contains(date)) return semester;
        }
        return new Semester(Math.max(1, date.getYear() - 1), Season.WINTER);
    }

    public String folderName() { return year + "-" + season.label(); }

    @Override public String toString() {
        return String.format(Locale.ROOT, "%02d-%s", year % 100, season.label());
    }
}
