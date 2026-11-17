package campus;

import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import campus.PlannerModel.Category;
import campus.PlannerModel.Event;

/** Weekday replacement and shared ID collisions must preserve course identity. */
public final class DemoGroupCheck {
    private static final Semester TERM = new Semester(2027, Semester.Season.SPRING);
    private static final LocalDate MONDAY = TERM.start().with(java.time.temporal.TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
    private static final List<String> failures = new ArrayList<>();
    private static int checks;

    public static void main(String[] args) throws Exception {
        Path temporary = Files.createTempDirectory("campus-demo-groups-");
        try {
            renamedWeekdayKeepsMaterials(temporary);
            conflictingImportKeepsGroup();
            disjointWeekdaysKeepDifferentCourses();
            matchingWeekdaysExtendCourse();
            if (!failures.isEmpty()) throw new AssertionError(String.join("\n", failures));
            System.out.println("PASS " + checks + " course identity, material links and grouped import checks");
        } finally {
            try (var paths = Files.walk(temporary)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }

    private static Event event(String id, String title, DayOfWeek day) {
        return new Event(id, title, "building-one", LocalTime.of(9, 0), LocalTime.of(10, 0), Category.CLASS, day, null, "");
    }

    private static void renamedWeekdayKeepsMaterials(Path temporary) throws Exception {
        PlannerModel model = new PlannerModel();
        Event original = event("existing-course", "이전 과목명", DayOfWeek.MONDAY);
        model.putEvent(original, TERM);
        AtomicReference<CourseMaterialsPanel> panel = new AtomicReference<>();
        Path home = temporary.resolve("planner");
        SwingUtilities.invokeAndWait(() -> panel.set(new CourseMaterialsPanel(model, home.resolve("materials"), ignored -> { })));
        Path oldFolder;
        try (MaterialsLibrary library = new MaterialsLibrary(model, panel.get(), home, () -> { })) {
            library.sync();
            oldFolder = panel.get().prepareFolder(original.title(), TERM);
            Files.writeString(oldFolder.resolve("원래자료.txt"), "keep original bytes");
            WeeklyScheduleService weekly = new WeeklyScheduleService(model, TERM);
            List<Event> replacement = weekly.prepare(original, "새 과목명", "building-two", original.start(), original.end(),
                    Category.CLASS, EnumSet.of(DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), "바뀐 메모");
            weekly.apply(original, replacement);
            library.sync();
            check(model.events().stream().noneMatch(e -> e.id().equals(original.id())), "all original weekdays are replaced");
            check(library.courseFolder("새 과목명", TERM).equals(oldFolder), "renaming while replacing all weekdays keeps original materials directory");
            check(Files.readString(oldFolder.resolve("원래자료.txt")).equals("keep original bytes"), "weekday replacement preserves original material bytes");
            PlannerModel.Occurrence occurrence = model.occurrences(MONDAY.plusDays(2)).get(0);
            check(library.occurrenceFolder(occurrence).equals(oldFolder), "numbered marker resolves retained course folder after weekday replacement");
        }
        try (MaterialsLibrary reopened = new MaterialsLibrary(model, panel.get(), home, () -> { })) {
            check(reopened.courseFolder("새 과목명", TERM).equals(oldFolder), "group material alias survives restart");
        }
    }

    private static void conflictingImportKeepsGroup() {
        PlannerModel local = new PlannerModel();
        Event localMonday = event("tt:shared-course:MONDAY", "기존 수업", DayOfWeek.MONDAY);
        Event localWednesday = event("tt:shared-course:WEDNESDAY", "기존 수업", DayOfWeek.WEDNESDAY);
        local.putEvent(localMonday, TERM);
        local.putEvent(localWednesday, TERM);
        PlannerModel incoming = new PlannerModel();
        incoming.putEvent(event(localMonday.id(), "공유 수업", DayOfWeek.MONDAY), TERM);
        incoming.putEvent(event(localWednesday.id(), "공유 수업", DayOfWeek.WEDNESDAY), TERM);
        incoming.putEvent(event("tt:shared-course:FRIDAY", "공유 수업", DayOfWeek.FRIDAY), TERM);
        incoming.toggleCancellation(localMonday.id(), MONDAY);
        local.mergeFrom(incoming);
        List<Event> imported = local.events().stream().filter(e -> e.title().equals("공유 수업")).toList();
        WeeklyScheduleService weekly = new WeeklyScheduleService(local, TERM);
        check(local.events().size() == 5 && imported.size() == 3, "import preserves both existing course and three incoming weekdays");
        check(weekly.group(imported.get(0)).size() == 3, "colliding imported weekdays keep one course group");
        check(weekly.group(localMonday).size() == 2, "imported extra weekday never joins the pre-existing course");
        check(local.events().contains(localMonday) && local.events().contains(localWednesday), "group import never rewrites pre-existing course fields");
        check(local.occurrences(MONDAY).stream().anyMatch(o -> o.event().title().equals("공유 수업") && o.canceled()),
                "incoming cancellation follows its remapped weekday ID");
        local.mergeFrom(incoming);
        check(local.events().size() == 5, "importing same colliding group twice stays idempotent");
        Event importedWednesday = local.events().stream().filter(e -> e.title().equals("공유 수업") && e.weekday() == DayOfWeek.WEDNESDAY).findFirst().orElseThrow();
        weekly.delete(importedWednesday);
        check(local.events().size() == 2 && local.events().contains(localMonday) && local.events().contains(localWednesday),
                "deleting imported group removes all its weekdays and preserves original course");
    }

    private static void disjointWeekdaysKeepDifferentCourses() {
        PlannerModel local = new PlannerModel();
        Event monday = event("tt:disjoint-course:MONDAY", "기존 수업", DayOfWeek.MONDAY);
        local.putEvent(monday, TERM);
        PlannerModel incoming = new PlannerModel();
        incoming.putEvent(new Event("tt:disjoint-course:WEDNESDAY", "다른 수업", "building-one",
                LocalTime.of(11, 0), LocalTime.of(12, 0), Category.CLASS, DayOfWeek.WEDNESDAY, null, ""), TERM);
        local.mergeFrom(incoming);
        Event wednesday = local.events().stream().filter(e -> e.weekday() == DayOfWeek.WEDNESDAY).findFirst().orElseThrow();
        WeeklyScheduleService weekly = new WeeklyScheduleService(local, TERM);
        check(local.events().size() == 2 && local.events().contains(monday), "disjoint conflicting import preserves original weekday");
        check(!wednesday.recurringGroup().equals(monday.recurringGroup()), "different course on disjoint weekday gets a separate root");
        check(weekly.group(monday).size() == 1 && weekly.group(wednesday).size() == 1,
                "same root without shared weekdays never mixes different course metadata");
        List<Event> imported = local.events();
        local.mergeFrom(incoming);
        check(local.events().equals(imported), "reimporting disjoint conflicting course preserves IDs and adds no duplicates");
    }

    private static void matchingWeekdaysExtendCourse() {
        PlannerModel local = new PlannerModel();
        Event monday = event("tt:extended-course:MONDAY", "같은 수업", DayOfWeek.MONDAY);
        local.putEvent(monday, TERM);
        PlannerModel incoming = new PlannerModel();
        Event wednesday = event("tt:extended-course:WEDNESDAY", monday.title(), DayOfWeek.WEDNESDAY);
        incoming.putEvent(wednesday, TERM);
        local.mergeFrom(incoming);
        WeeklyScheduleService weekly = new WeeklyScheduleService(local, TERM);
        check(local.events().size() == 2 && local.events().contains(monday) && local.events().contains(wednesday),
                "matching course extension preserves both source weekday IDs");
        check(weekly.group(monday).size() == 2 && weekly.group(wednesday).size() == 2,
                "same root and matching metadata merge disjoint weekdays into one course");
        List<Event> imported = local.events();
        local.mergeFrom(incoming);
        check(local.events().equals(imported), "reimporting matching course extension adds no duplicate weekday");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) failures.add(message);
    }
}
