package campus;

import java.nio.file.*;
import java.time.*;
import java.util.*;
import javax.swing.*;
import campus.PlannerModel.Event;
import campus.PlannerModel.Category;

/** Demo features are checked against temporary schedules and actual temporary files. */
public final class DemoIntegrationCheck {
    private static int checks;
    public static void main(String[] args) throws Exception {
        Path temporary = Files.createTempDirectory("campus-demo-check-");
        try {
            weekly(temporary);
            files(temporary);
            materialLinks(temporary);
            markers(Path.of(args.length == 0 ? "." : args[0]));
            System.out.println("PASS " + checks + " demo file-browser, course-group and storage checks");
        } finally {
            try (var paths = Files.walk(temporary)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }
    private static Event event(String id, String title, DayOfWeek day) {
        return new Event(id, title, "building-one", LocalTime.of(9, 0), LocalTime.of(10, 0), Category.CLASS, day, null, "메모");
    }
    private static void weekly(Path temporary) throws Exception {
        PlannerModel model = new PlannerModel();
        Semester term = new Semester(2027, Semester.Season.SPRING);
        WeeklyScheduleService service = new WeeklyScheduleService(model, term);
        Event original = event("original", "운영체제", DayOfWeek.MONDAY);
        model.putEvent(original, term);
        LocalDate monday = term.start().with(java.time.temporal.TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        model.toggleCancellation(original.id(), monday);
        var proposed = service.prepare(original, "운영체제", "building-one", original.start(), original.end(), Category.CLASS,
                EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), "메모");
        check(proposed.size() == 2 && proposed.get(0).id().equals(original.id()), "multi-day edit retains original day ID");
        check(service.conflicts(original, proposed).isEmpty(), "group edits do not conflict with themselves");
        service.apply(original, proposed);
        check(model.events().size() == 2 && service.group(proposed.get(1)).size() == 2, "group can be selected through any weekday");
        check(model.occurrences(monday).get(0).canceled(), "editing group retains date-specific cancellation");
        check(model.occurrences(term.end().plusWeeks(1).with(DayOfWeek.MONDAY)).isEmpty(), "recurrence remains within its season");
        Event overlap = event("overlap", "다른 수업", DayOfWeek.WEDNESDAY);
        model.putEvent(overlap, term);
        check(service.conflicts(proposed.get(1), proposed).stream().anyMatch(e -> e.id().equals(overlap.id())), "overlap with another course is detected");
        model.removeEvent(overlap.id());
        var oneDay = service.prepare(proposed.get(1), "바뀐 수업", "building-two", original.start(), original.end(), Category.CLASS,
                EnumSet.of(DayOfWeek.MONDAY), "수정");
        service.apply(proposed.get(1), oneDay);
        check(model.events().size() == 1 && model.events().get(0).id().equals(original.id()), "removed weekdays disappear without replacing retained IDs");
        Path xml = temporary.resolve("group.xml"); PlannerStore.save(xml, model);
        PlannerModel restored = PlannerStore.load(xml);
        check(restored.events().equals(model.events()) && term.equals(restored.semesterOf(original.id())), "multi-day data uses existing XML storage");
        service.delete(oneDay.get(0));
        check(model.events().isEmpty(), "group deletion removes retained day");
        Event single = new Event("once", "약속", "building-one", original.start(), original.end(), Category.APPOINTMENT,
                null, term.start(), "");
        model.putEvent(single, term);
        var converted = service.prepare(single, "반복 약속", "building-one", original.start(), original.end(), Category.APPOINTMENT,
                EnumSet.of(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY), "");
        service.apply(single, converted);
        check(model.events().size() == 2 && model.events().stream().allMatch(Event::recurring), "one-off conversion does not leave old duplicate");
        check(new WeeklyScheduleService(model, new Semester(2027, Semester.Season.SUMMER)).events().isEmpty(), "weekly grid does not mix seasons");
        try { service.prepare(null, "빈요일", "place", original.start(), original.end(), Category.CLASS, Set.of(), ""); throw new AssertionError(); }
        catch (IllegalArgumentException expected) { checks++; }
    }
    private static void files(Path temporary) throws Exception {
        FolderService service = new FolderService(temporary.resolve("file-materials"));
        FileOperations operations = new FileOperations(service);
        Path folder = service.createFolder(service.getRoot(), "강의 자료");
        Path outside = temporary.resolve("원본.txt"); Files.writeString(outside, "original bytes");
        operations.paste(List.of(outside), folder, false);
        check(Files.exists(outside) && Files.readString(folder.resolve("원본.txt")).equals("original bytes"), "copy preserves source and bytes");
        operations.paste(List.of(outside), folder, false);
        check(Files.exists(folder.resolve("원본 (1).txt")), "copy collision gets a distinct name");
        operations.undo();
        check(!Files.exists(folder.resolve("원본 (1).txt")) && Files.exists(folder.resolve("원본.txt")), "undo restores only the latest copy");
        operations.paste(List.of(outside), folder, true);
        check(!Files.exists(outside) && Files.exists(folder.resolve("원본 (1).txt")), "move removes source after successful transfer");
        operations.undo();
        check(Files.readString(outside).equals("original bytes"), "move undo restores original path and content");
        Path file = folder.resolve("원본.txt");
        operations.rename(file, folder.resolve("이름변경.txt"));
        operations.undo();
        check(Files.exists(file) && !Files.exists(folder.resolve("이름변경.txt")), "rename undo is reversible");
        operations.delete(List.of(file));
        check(!Files.exists(file), "delete moves file out of visible materials");
        operations.undo();
        check(Files.readString(file).equals("original bytes"), "delete undo recovers bytes");
        Path grouped = operations.createFolderWithSelection(folder, "묶음", List.of(file));
        check(Files.exists(grouped.resolve("원본.txt")), "selected files can be grouped in a new folder");
        operations.undo();
        check(Files.exists(file) && !Files.exists(grouped), "grouping undo restores files and removes empty group");
        new RecentFiles(service, service.getRoot()).record(file);
        check(new RecentFiles(service, service.getRoot()).list().equals(List.of(file)), "recent files survive a new reader");
        check(MapFileSearch.matches(service.getRoot(), file, "원본"), "file search matches Korean filenames");
        check(!MapFileSearch.matches(service.getRoot(), folder.resolve(".hidden.txt"), "hidden"), "file search hides metadata");
        try { service.checked(temporary.resolve("outside")); throw new AssertionError(); }
        catch (java.io.IOException expected) { checks++; }
        for (String name : List.of("..", "A/B", "CON", "NUL.txt")) {
            try { FolderService.validName(name); throw new AssertionError(name); }
            catch (IllegalArgumentException expected) { checks++; }
        }
        try { operations.paste(List.of(folder), grouped, false); throw new AssertionError(); }
        catch (java.io.IOException expected) { checks++; }
        check(!Files.exists(grouped), "rejected self-copy does not create a new directory");
        try { operations.delete(List.of(service.getRoot())); throw new AssertionError(); }
        catch (java.io.IOException expected) { checks++; }
        try { operations.rename(service.getRoot(), temporary.resolve("moved-materials")); throw new AssertionError(); }
        catch (java.io.IOException expected) { checks++; }
    }
    private static void materialLinks(Path temporary) throws Exception {
        PlannerModel model = new PlannerModel();
        Semester term = new Semester(2027, Semester.Season.FALL);
        Event original = event("linked-course", "자료 연결", DayOfWeek.MONDAY);
        model.putEvent(original, term);
        Path home = temporary.resolve("planner-home");
        CourseMaterialsPanel[] panel = new CourseMaterialsPanel[1];
        SwingUtilities.invokeAndWait(() -> panel[0] = new CourseMaterialsPanel(model, home.resolve("materials"), e -> { }));
        Path stored;
        try (MaterialsLibrary library = new MaterialsLibrary(model, panel[0], home, () -> { })) {
            library.sync();
            stored = panel[0].prepareFolder(original.title(), term);
            Files.writeString(stored.resolve("자료.pdf"), "lecture data");
            Event renamed = new Event(original.id(), "새 수업 이름", "building-two", original.start(), original.end(), original.category(), original.weekday(), null, "");
            model.putEvent(renamed, term); library.sync();
            check(library.courseFolder(renamed.title(), term).equals(stored), "course rename and building change retain the existing directory");
            Place place = new Place("building-two", "다른 건물", new MapData.GeoPoint(35, 127), false);
            check(library.locations(place, term).get(0).path().equals(stored), "building double-click resolves the same course files");
            LocalDate date = term.start().with(java.time.temporal.TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
            check(library.occurrenceFolder(model.occurrences(date).get(0)).equals(stored), "numbered class opens its exact linked course folder");
            Event appointment = new Event("meet", "약속", place.id(), LocalTime.of(14,0), LocalTime.of(15,0), Category.APPOINTMENT, null, date, "");
            model.putEvent(appointment, term);
            check(library.occurrenceFolder(model.occurrences(date).get(1)).equals(library.locations(place, term).get(1).path()), "non-course number opens that place's general materials");
            check(!library.locations(place, new Semester(2028, Semester.Season.FALL)).get(0).path().equals(stored), "another season has separate materials");
            try { new MaterialsLibrary(model, panel[0], home, () -> { }); throw new AssertionError(); }
            catch (java.io.IOException expected) { checks++; }
        }
        try (MaterialsLibrary reopened = new MaterialsLibrary(model, panel[0], home, () -> { })) {
            check(reopened.courseFolder("새 수업 이름", term).equals(stored), "course aliases survive restart");
            check(Files.readString(stored.resolve("자료.pdf")).equals("lecture data"), "existing materials are never moved during synchronization");
        }
    }
    private static void markers(Path project) throws Exception {
        MapData data = MapData.load(project.resolve("data/chonnam-campus.osm"));
        PlannerModel model = new PlannerModel();
        PlaceCatalog places = new PlaceCatalog(data, model);
        Place building = places.namedPlaces().stream().filter(place -> !place.busStop()).findFirst().orElseThrow();
        LocalDate date = LocalDate.of(2027, 3, 8);
        Event first = new Event("number-one", "운영체제", building.id(), LocalTime.of(9,0), LocalTime.of(10,0), Category.CLASS, date.getDayOfWeek(), null, "");
        Event second = new Event("number-two", "네트워크", building.id(), LocalTime.of(11,0), LocalTime.of(12,0), Category.CLASS, date.getDayOfWeek(), null, "");
        model.putEvent(first, Semester.forDate(date));
        SwingUtilities.invokeAndWait(() -> {
            try {
                List<Event> opened = new ArrayList<>();
                CampusMapPanel map = new CampusMapPanel(data, model, places, place -> { }, place -> { }, point -> { });
                map.setScheduleOpen(value -> opened.add(value.event()));
                map.setSize(800,600);
                map.setOccurrences(model.occurrences(date));
                var transform = CampusMapPanel.class.getDeclaredMethod("point", MapData.GeoPoint.class);
                transform.setAccessible(true);
                var point = (java.awt.geom.Point2D) transform.invoke(map, building.point());
                int x = (int) point.getX(), y = (int) point.getY();
                map.dispatchEvent(new java.awt.event.MouseEvent(map, java.awt.event.MouseEvent.MOUSE_PRESSED, 1, 0, x, y, 1, false, java.awt.event.MouseEvent.BUTTON1));
                map.dispatchEvent(new java.awt.event.MouseEvent(map, java.awt.event.MouseEvent.MOUSE_RELEASED, 2, 0, x, y, 1, false, java.awt.event.MouseEvent.BUTTON1));
                check(opened.equals(List.of(first)), "number-one click opens its occurrence rather than a building folder chooser");
                model.putEvent(second, Semester.forDate(date));
                map.setOccurrences(model.occurrences(date));
                JPopupMenu menu = map.scheduleMenu(model.occurrences(date));
                check(((JMenuItem) menu.getComponent(0)).getText().equals("1. 운영체제")
                        && ((JMenuItem) menu.getComponent(1)).getText().equals("2. 네트워크"), "shared building menu distinguishes schedule numbers and course names");
                ((JMenuItem) menu.getComponent(1)).doClick();
                check(opened.get(1).equals(second), "shared building choice maps to the selected course");
            } catch (Exception failure) { throw new RuntimeException(failure); }
        });
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
