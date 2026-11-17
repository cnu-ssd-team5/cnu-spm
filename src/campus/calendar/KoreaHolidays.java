package campus.calendar;


import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/** KASI/KASA calendar dates, including government amendments checked 2026-11-17. */
public final class KoreaHolidays {
    // shortcut: confirmed 2025–2027 dates only; extend from official announcements for later years.
    private static final String DATES = """
            2025-01-01 신정
            2025-01-27 임시공휴일
            2025-01-28 설날 연휴
            2025-01-29 설날
            2025-01-30 설날 연휴
            2025-03-01 삼일절
            2025-03-03 대체공휴일
            2025-05-05 어린이날 · 부처님오신날
            2025-05-06 대체공휴일
            2025-06-03 대통령선거일
            2025-06-06 현충일
            2025-08-15 광복절
            2025-10-03 개천절
            2025-10-05 추석 연휴
            2025-10-06 추석
            2025-10-07 추석 연휴
            2025-10-08 대체공휴일
            2025-10-09 한글날
            2025-12-25 성탄절
            2026-01-01 신정
            2026-02-16 설날 연휴
            2026-02-17 설날
            2026-02-18 설날 연휴
            2026-03-01 삼일절
            2026-03-02 대체공휴일
            2026-05-01 노동절
            2026-05-05 어린이날
            2026-05-24 부처님오신날
            2026-05-25 대체공휴일
            2026-06-03 전국동시지방선거일
            2026-06-06 현충일
            2026-07-17 제헌절
            2026-08-15 광복절
            2026-08-17 대체공휴일
            2026-09-24 추석 연휴
            2026-09-25 추석
            2026-09-26 추석 연휴
            2026-10-03 개천절
            2026-10-05 대체공휴일
            2026-10-09 한글날
            2026-12-25 성탄절
            2027-01-01 신정
            2027-02-06 설날 연휴
            2027-02-07 설날
            2027-02-08 설날 연휴
            2027-02-09 대체공휴일
            2027-03-01 삼일절
            2027-05-01 노동절
            2027-05-03 대체공휴일
            2027-05-05 어린이날
            2027-05-13 부처님오신날
            2027-06-06 현충일
            2027-07-17 제헌절
            2027-07-19 대체공휴일
            2027-08-15 광복절
            2027-08-16 대체공휴일
            2027-09-14 추석 연휴
            2027-09-15 추석
            2027-09-16 추석 연휴
            2027-10-03 개천절
            2027-10-04 대체공휴일
            2027-10-09 한글날
            2027-10-11 대체공휴일
            2027-12-25 성탄절
            2027-12-27 대체공휴일
            """;
    private static final Map<LocalDate, String> HOLIDAYS = dates();

    private KoreaHolidays() { }

    static boolean supports(int year) { return year >= 2025 && year <= 2027; }
    static String name(LocalDate date) { return HOLIDAYS.getOrDefault(date, ""); }

    private static Map<LocalDate, String> dates() {
        Map<LocalDate, String> dates = new LinkedHashMap<>();
        for (String line : DATES.lines().toList()) {
            String[] fields = line.split(" ", 2);
            LocalDate date = LocalDate.parse(fields[0]);
            if (dates.put(date, fields[1]) != null) throw new IllegalStateException("중복 공휴일: " + date);
        }
        return Map.copyOf(dates);
    }
}
