package campus;

import java.time.LocalDate;

public final class PlannerTimeCheck {
    public static void main(String[] args) {
        LocalDate today = LocalDate.of(2026, 11, 17);
        check(PlannerTime.forNewItem(today.minusDays(1), today).equals(today), "past defaults to today");
        check(PlannerTime.forNewItem(today, today).equals(today), "today preserved");
        check(PlannerTime.forNewItem(today.plusDays(5), today).equals(today.plusDays(5)), "future preserved");
        check(!KoreaHolidays.supports(2024) && KoreaHolidays.supports(2025)
                && KoreaHolidays.supports(2027) && !KoreaHolidays.supports(2028), "coverage explicit");
        check(KoreaHolidays.name(LocalDate.of(2026, 7, 17)).equals("제헌절"), "2026 amendment included");
        check(KoreaHolidays.name(LocalDate.of(2026, 5, 1)).equals("노동절"), "2026 Labor Day included");
        check(KoreaHolidays.name(LocalDate.of(2025, 7, 17)).isEmpty(), "amendment not applied retroactively");
        check(!KoreaHolidays.name(LocalDate.of(2025, 1, 27)).isEmpty(), "temporary holiday included");
        check(!KoreaHolidays.name(LocalDate.of(2027, 7, 19)).isEmpty(), "2027 substitute included");
        int[] expected = {19, 22, 24};
        for (int year = 2025; year <= 2027; year++) {
            int count = 0;
            for (LocalDate date = LocalDate.of(year, 1, 1); date.getYear() == year; date = date.plusDays(1)) {
                if (!KoreaHolidays.name(date).isEmpty()) count++;
            }
            check(count == expected[year - 2025], "verified calendar date count " + year);
        }
        System.out.println("PASS 12 date defaults and official holiday checks");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
