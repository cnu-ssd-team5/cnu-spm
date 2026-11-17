package campus;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;

/** Run with java -cp build/classes;build/test-classes campus.SemesterCheck. */
public final class SemesterCheck {
    private static int checks;
    private static final Semester SPRING = new Semester(2026, Semester.Season.SPRING);
    private static final Semester FALL = new Semester(2026, Semester.Season.FALL);
    private static final Semester SUMMER = new Semester(2026, Semester.Season.SUMMER);
    private static final Semester WINTER = new Semester(2026, Semester.Season.WINTER);
    private SemesterCheck() { }

    public static void main(String[] args) throws Exception {
        checkDates();
        checkSchedulesAndConflicts();
        checkPinRemoval();
        Path directory = Files.createTempDirectory("campus-semester-check-");
        try {
            checkStorage(directory);
            checkImport(directory);
        } finally {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
        System.out.println("PASS " + checks + " semester, overlap, pin and storage checks");
    }

    private static void checkDates() {
        check(SPRING.start().equals(LocalDate.of(2026, 3, 2)), "weekday spring anchor included");
        check(FALL.start().equals(LocalDate.of(2026, 9, 1)), "weekday fall anchor included");
        check(new Semester(2024, Semester.Season.SPRING).start().equals(LocalDate.of(2024, 3, 4)),
                "spring weekend advances to Monday");
        check(new Semester(2018, Semester.Season.FALL).start().equals(LocalDate.of(2018, 9, 3)),
                "fall weekend advances to Monday");
        check(ChronoUnit.DAYS.between(SPRING.start(), SPRING.end()) + 1 == 112, "sixteen complete weeks");
        check(SPRING.contains(SPRING.start()) && SPRING.contains(SPRING.end()), "inclusive boundaries");
        check(!SPRING.contains(SPRING.start().minusDays(1)) && !SPRING.contains(SPRING.end().plusDays(1)),
                "outside term excluded");
        check(Semester.forDate(LocalDate.of(2027, 1, 5)).equals(WINTER), "winter belongs to previous academic year");
        check(Semester.forDate(LocalDate.of(2026, 8, 20)).equals(SUMMER), "summer has its own season");
        check(Semester.parse(SPRING.id()).equals(SPRING) && FALL.toString().equals("26-2학기"), "stable id and renamed label");
        check(SPRING.toString().equals("26-1학기") && SUMMER.toString().equals("26-여름방학")
                && WINTER.toString().equals("26-겨울방학"), "all four seasonal labels");
        check(SPRING.id().equals("2026-spring") && FALL.id().equals("2026-fall"), "stored teaching term ids stay compatible");
        check(SUMMER.start().equals(SPRING.end().plusDays(1)) && SUMMER.end().equals(FALL.start().minusDays(1)), "summer fills the teaching gap");
        check(WINTER.start().equals(FALL.end().plusDays(1))
                && WINTER.end().equals(new Semester(2027, Semester.Season.SPRING).start().minusDays(1)), "winter includes the next calendar year");
        check(SUMMER.folderName().equals("2026-여름방학") && FALL.folderName().equals("2026-2학기"), "folder names preserve full year");
        for (Semester.Season season : Semester.Season.values()) {
            Semester term = new Semester(2026, season);
            check(Semester.parse(term.id()).equals(term), "four-season stored id round trip " + season);
            check(Semester.forDate(term.start()).equals(term) && Semester.forDate(term.end()).equals(term), "inclusive season lookup " + season);
        }
        for (LocalDate day : java.util.List.of(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 6, 22),
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 22), LocalDate.of(2027, 3, 1))) {
            check(Semester.forDate(day).contains(day), "season contains boundary date " + day);
        }
        rejects(() -> Semester.parse("26-가을"), "invalid stored semester rejected");
        rejects(() -> new Semester(0, Semester.Season.FALL), "invalid year rejected");
    }

    private static void checkSchedulesAndConflicts() {
        PlannerModel model = new PlannerModel();
        PlannerModel.Event spring = weekly("spring", DayOfWeek.MONDAY, 9, 10, "w1");
        PlannerModel.Event fall = weekly("fall", DayOfWeek.MONDAY, 9, 10, "w1");
        model.putEvent(spring, SPRING);
        model.putEvent(fall, FALL);
        check(model.occurrences(SPRING.start()).size() == 1, "only spring recurrence appears in spring");
        check(model.occurrences(SPRING.start().plusWeeks(16)).isEmpty(), "recurrence ends after week sixteen");
        check(model.occurrences(FALL.start().with(java.time.temporal.TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY)))
                .get(0).event().id().equals("fall"), "next semester has own schedule");
        check(model.events(SPRING).equals(java.util.List.of(spring)) && model.semesters().equals(java.util.List.of(SPRING, FALL)),
                "semester filtering and chronological list");
        model.putEvent(spring);
        check(model.semesterOf(spring.id()).equals(SPRING), "plain edit keeps term");
        check(model.conflicts(weekly("candidate", DayOfWeek.MONDAY, 9, 10, "w2"), SPRING).equals(java.util.List.of(spring)),
                "same-time schedules overlap only in matching semester");
        check(model.conflicts(weekly("candidate", DayOfWeek.MONDAY, 10, 11, "w2"), SPRING).isEmpty(),
                "adjacent intervals do not overlap");
        check(model.conflicts(weekly("candidate", DayOfWeek.TUESDAY, 9, 10, "w2"), SPRING).isEmpty(),
                "different weekdays do not overlap");
        check(model.conflicts(spring, SPRING).isEmpty(), "editing same event ignores self and other term");
        LocalDate monday = SPRING.start();
        PlannerModel.Event once = once("once", monday, 9, 10, "w2");
        check(model.conflicts(once, SPRING).equals(java.util.List.of(spring)), "date-specific event overlaps recurrence");
        model.toggleCancellation("spring", monday);
        check(model.conflicts(once, SPRING).isEmpty(), "canceled occurrence is not a conflict");
        check(model.occurrences(monday).get(0).canceled(), "canceled occurrence still visible for calendar");
        model.putEvent(once);
        check(model.conflicts(weekly("candidate", DayOfWeek.MONDAY, 9, 10, "w3"), SPRING).contains(once),
                "recurrence detects one-off overlap");
        PlannerModel.Event vacation = once("vacation", LocalDate.of(2026, 8, 15), 11, 12, "w2");
        model.putEvent(vacation, SPRING);
        check(model.occurrences(vacation.date()).get(0).event().equals(vacation), "one-off is retained outside teaching weeks");
        check(model.conflicts(once("later", monday.plusYears(1), 9, 10, "w2"), FALL).isEmpty(),
                "separate dates do not overlap");
        for (int week = 1; week < 16; week++) model.toggleCancellation("spring", monday.plusWeeks(week));
        model.removeEvent("once");
        check(model.conflicts(weekly("candidate", DayOfWeek.MONDAY, 9, 10, "w2"), SPRING).isEmpty(),
                "all sixteen canceled weeks produce no overlap");
        model.removeEvent("spring");
        check(model.semesterOf("spring") == null && model.cancellations().isEmpty(), "event removal clears term and exceptions");
        rejects(() -> model.toggleCancellation("fall", monday), "cannot cancel outside assigned semester");
        PlannerModel fourSeasons = new PlannerModel();
        for (Semester.Season season : Semester.Season.values()) {
            Semester term = new Semester(2026, season);
            fourSeasons.putEvent(weekly(season.name(), DayOfWeek.MONDAY, 9, 10, "w1"), term);
        }
        for (Semester.Season season : Semester.Season.values()) {
            Semester term = new Semester(2026, season);
            LocalDate firstMonday = term.start().with(java.time.temporal.TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
            check(fourSeasons.occurrences(firstMonday).size() == 1
                    && fourSeasons.occurrences(firstMonday).get(0).event().id().equals(season.name()), "recurring schedule stays in assigned season " + season);
            check(fourSeasons.conflicts(weekly("new", DayOfWeek.MONDAY, 9, 10, "w2"), term).size() == 1,
                    "conflicts only consider assigned season " + season);
        }
    }

    private static void checkPinRemoval() {
        PlannerModel model = new PlannerModel();
        model.putPin(new PlannerModel.Pin("pin:one", "모임 장소", 35.17, 126.90));
        model.toggleFavorite("pin:one");
        model.renameBuilding("pin:one", "별명");
        model.putEvent(weekly("pin-event", DayOfWeek.MONDAY, 9, 10, "pin:one"), SPRING);
        model.toggleCancellation("pin-event", SPRING.start());
        model.putEvent(weekly("building-event", DayOfWeek.MONDAY, 10, 11, "w1"), SPRING);
        model.removePin("w1");
        check(model.events().size() == 2, "unknown pin deletion cannot delete a building schedule");
        model.removePin("pin:one");
        check(model.pins().isEmpty() && !model.isFavorite("pin:one") && model.customName("pin:one").isEmpty(),
                "pin removal clears pin favorite and alias");
        check(model.events().size() == 1 && model.events().get(0).id().equals("building-event")
                && model.semesterOf("pin-event") == null && model.cancellations().isEmpty(),
                "pin removal cascades only its schedules and exceptions");
    }

    private static void checkStorage(Path directory) throws Exception {
        Path path = directory.resolve("state.xml");
        PlannerModel model = new PlannerModel();
        model.putEvent(weekly("term", DayOfWeek.MONDAY, 9, 10, "w1"), SPRING);
        model.toggleCancellation("term", SPRING.start());
        model.putEvent(weekly("unscoped", DayOfWeek.FRIDAY, 11, 12, "w2"));
        model.putEvent(weekly("summer", DayOfWeek.MONDAY, 9, 10, "w3"), SUMMER);
        model.putEvent(weekly("winter", DayOfWeek.TUESDAY, 9, 10, "w4"), WINTER);
        PlannerStore.save(path, model);
        check(Files.readString(path).contains("version=\"3\"")
                && Files.readString(path).contains("semester=\"2026-spring\""), "version three stores semester");
        PlannerModel restored = PlannerStore.load(path);
        check(restored.semesterOf("term").equals(SPRING) && restored.semesterOf("unscoped") == null,
                "scoped and explicitly unscoped schedules round-trip");
        check(restored.cancellations().equals(model.cancellations()), "term cancellation round-trip");
        check(restored.semesterOf("summer").equals(SUMMER) && restored.semesterOf("winter").equals(WINTER),
                "vacation schedules round-trip through XML version three");
        for (int version = 1; version <= 2; version++) {
            Files.writeString(path, """
                    <campus-planner version="%d"><events>
                    <event id="old" title="이전 수업" buildingId="w1" start="09:00" end="10:00" weekday="MONDAY"/>
                    <event id="day" title="이전 약속" buildingId="w2" start="11:00" end="12:00" date="2024-04-01"/>
                    </events><cancellations><occurrence eventId="old" date="2024-04-01"/></cancellations></campus-planner>
                    """.formatted(version));
            PlannerModel legacy = PlannerStore.load(path);
            check(legacy.events().size() == 2 && legacy.semesterOf("old").equals(Semester.forDate(LocalDate.now()))
                    && legacy.semesterOf("day").equals(new Semester(2024, Semester.Season.SPRING)),
                    "version " + version + " migration keeps events and assigns terms");
            check(legacy.cancellations().contains(new PlannerModel.Cancellation("old", LocalDate.of(2024, 4, 1))),
                    "historical cancellation retained through migration");
            PlannerStore.save(path, legacy);
            check(PlannerStore.load(path).cancellations().equals(legacy.cancellations()), "historical cancellation resaves without loss");
        }
    }

    private static void checkImport(Path directory) throws Exception {
        PlannerModel personal = new PlannerModel();
        PlannerModel incoming = new PlannerModel();
        PlannerModel.Event course = weekly("same-id", DayOfWeek.MONDAY, 9, 10, "w1");
        personal.putEvent(course, SPRING);
        incoming.putEvent(course, FALL);
        LocalDate fallMonday = FALL.start().with(java.time.temporal.TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        incoming.toggleCancellation(course.id(), fallMonday);
        personal.mergeFrom(incoming);
        check(personal.events().size() == 2, "identical course in different semester is not deduplicated");
        PlannerModel.Event imported = personal.events(FALL).get(0);
        check(!imported.id().equals(course.id()) && personal.semesterOf(imported.id()).equals(FALL),
                "same id collision remaps and retains semester");
        check(personal.occurrences(fallMonday).get(0).canceled()
                && !personal.occurrences(SPRING.start()).get(0).canceled(), "import cancellation follows remapped term event");
        personal.mergeFrom(incoming);
        check(personal.events().size() == 2, "term-aware repeat import is idempotent");
        Path path = directory.resolve("shared.xml");
        PlannerStore.save(path, personal);
        PlannerModel restored = PlannerStore.load(path);
        restored.mergeFrom(incoming);
        check(restored.events().equals(personal.events()) && restored.semesters().equals(personal.semesters()),
                "shared terms survive save load and repeat import");
    }

    private static PlannerModel.Event weekly(String id, DayOfWeek weekday, int start, int end, String place) {
        return new PlannerModel.Event(id, "운영체제", place, LocalTime.of(start, 0), LocalTime.of(end, 0),
                PlannerModel.Category.CLASS, weekday, null, "");
    }

    private static PlannerModel.Event once(String id, LocalDate date, int start, int end, String place) {
        return new PlannerModel.Event(id, "약속", place, LocalTime.of(start, 0), LocalTime.of(end, 0),
                PlannerModel.Category.APPOINTMENT, null, date, "");
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        checks++;
    }

    private static void rejects(Runnable action, String label) {
        try {
            action.run();
            throw new AssertionError(label);
        } catch (IllegalArgumentException expected) {
            checks++;
        }
    }
}
