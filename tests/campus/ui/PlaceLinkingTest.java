package campus.ui;

import campus.folder.FolderModule;
import campus.map.CampusMap;
import campus.map.CampusMap.Building;
import campus.map.RouteService;
import campus.model.PlannerModel;
import campus.model.PlannerModel.Category;
import campus.model.PlannerModel.Event;
import campus.model.PlannerStore;
import campus.places.PlaceService;
import campus.service.DayPlanService;
import java.lang.reflect.Constructor;
import java.nio.file.*;
import java.time.*;
import java.util.List;
import javax.swing.*;

/** Place resolution is tested with isolated models and files, never the user's schedules. */
public final class PlaceLinkingTest {
    private static int passed, failed;
    private static final LocalDate DATE = LocalDate.of(2026, 12, 8);
    private interface Check { void run() throws Exception; }
    private static void test(String name, Check check) {
        try { check.run(); passed++; System.out.println("PASS " + name); }
        catch (Throwable ex) { failed++; System.out.println("FAIL " + name); ex.printStackTrace(System.out); }
    }
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static Object field(Object object, String name) throws Exception {
        return ScheduleEntryWhiteBoxTest.field(object, name);
    }
    private static PlacePicker picker(CampusMap map, Building initial) {
        return new PlacePicker(map, new PlannerModel(), new JTextField(), initial, "");
    }
    private static void type(PlacePicker picker, String text) {
        ((JTextField) picker.getEditor().getEditorComponent()).setText(text);
    }
    private static Event legacy(String id, DayOfWeek day, String place) {
        return new Event(id, "장소 연결 검사", "", LocalTime.NOON, LocalTime.of(13, 15),
                Category.CLASS, day, null, "메모 보존", "교양", place);
    }
    private static RouteDialog routeDialog(CampusMap map, RouteService routes, Building initial) throws Exception {
        Constructor<RouteDialog> ctor = RouteDialog.class.getDeclaredConstructor(JFrame.class, CampusMap.class,
                RouteService.class, PlannerModel.class, Building.class, Building.class);
        ctor.setAccessible(true);
        return ctor.newInstance(null, map, routes, new PlannerModel(), initial, null);
    }
    private static void namedOptions(JComboBox<?> choices) {
        for (int index = 0; index < choices.getItemCount(); index++) {
            Object value = choices.getItemAt(index);
            require(value == null || PlaceService.hasName((Building) value), "unnamed item in selection list");
        }
    }

    public static void main(String[] args) throws Exception {
        CampusMap base = CampusMap.load(Path.of(args[0]));
        Building jinri = PlaceService.matchingPlaces(base, "진리관").get(0);
        Building unnamed = base.buildings().stream().filter(b -> !PlaceService.hasName(b)).findFirst().orElseThrow();
        RouteService routes = new RouteService(base);
        Path home = Files.createTempDirectory("dispatcher-place-linking-");
        String previousHome = System.getProperty("campus.home");
        System.setProperty("campus.home", home.toString());
        SwingUtilities.invokeAndWait(() -> {
            Theme.configureLookAndFeel();
            test("typed-campus-name-links-with-spaces-and-unicode-whitespace", () -> {
                PlacePicker picker = picker(base, null);
                type(picker, "  진\u3000리\u00a0관  ");
                require(picker.selection().buildingId().equals(jinri.id()) && picker.selection().customPlace().isEmpty(), "known place stayed text");
            });
            test("english-names-and-original-aliases-resolve-without-changing-ids", () -> {
                CampusMap map = base.copyBasePlaces();
                Building old = new Building("pin:alias", "Lab East", List.of(), jinri.center());
                map.putPlace(old);
                map.putPlace(new Building(old.id(), "동쪽 실습실", List.of(), old.center()));
                PlacePicker picker = picker(map, null); type(picker, "LAB east");
                require(picker.selection().buildingId().equals(old.id()), "original name or case match failed");
                type(picker, "동쪽실습실");
                require(picker.selection().buildingId().equals(old.id()), "current name match failed");
            });
            test("external-and-partial-names-remain-custom-places", () -> {
                PlacePicker picker = picker(base, jinri);
                for (String text : List.of("온라인 · 회의 링크", "진리", "진리관 옆 카페")) {
                    type(picker, text);
                    require(picker.selection().buildingId().isEmpty() && picker.selection().customPlace().equals(text), "partial or external text guessed a building");
                }
            });
            test("duplicate-name-requires-explicit-building-selection", () -> {
                CampusMap map = base.copyBasePlaces();
                Building duplicate = new Building("pin:duplicate", "진리관", List.of(), jinri.center()); map.putPlace(duplicate);
                PlacePicker picker = picker(map, null); type(picker, "진리관");
                try { picker.selection(); throw new AssertionError("ambiguous name silently linked"); }
                catch (IllegalArgumentException expected) { require(expected.getMessage().contains("목록"), "missing selection guidance"); }
                picker.setSelectedItem(duplicate);
                require(picker.selection().buildingId().equals(duplicate.id()), "explicit ID lost for duplicate name");
            });
            test("event-picker-hides-unnamed-options-and-keeps-existing-selection", () -> {
                int count = base.buildings().size();
                namedOptions(picker(base, null));
                PlacePicker existing = picker(base, unnamed);
                require(existing.selection().buildingId().equals(unnamed.id()), "legacy unnamed schedule location lost");
                require(base.building(unnamed.id()) != null && base.buildings().size() == count, "map geometry deleted with UI item");
            });
            test("renamed-unnamed-building-returns-to-place-options", () -> {
                CampusMap map = base.copyBasePlaces(); PlannerModel model = new PlannerModel();
                model.renameBuilding(unnamed.id(), "팀 프로젝트실"); PlaceService.applyPlaces(map, model);
                PlacePicker picker = picker(map, null); type(picker, "팀 프로젝트실");
                require(picker.selection().buildingId().equals(unnamed.id()), "renamed place hidden or not linked");
            });
            test("route-picker-hides-unnamed-options", () -> {
                RouteDialog dialog = routeDialog(base, routes, null);
                try {
                    namedOptions((JComboBox<?>) field(field(dialog, "from"), "choices"));
                    namedOptions((JComboBox<?>) field(field(dialog, "to"), "choices"));
                } finally { dialog.dispose(); }
            });
            test("map-selected-unnamed-route-endpoint-is-preserved", () -> {
                RouteDialog dialog = routeDialog(base, routes, unnamed);
                try {
                    JComboBox<?> choice = (JComboBox<?>) field(field(dialog, "from"), "choices");
                    require(unnamed.equals(choice.getSelectedItem()), "explicit map endpoint disappeared");
                } finally { dialog.dispose(); }
            });
            test("legacy-link-preserves-recurring-days-notes-and-cancellation", () -> {
                PlannerModel model = new PlannerModel();
                Event tuesday = legacy("tt:legacy:TUESDAY", DayOfWeek.TUESDAY, "진리관");
                Event thursday = legacy("tt:legacy:THURSDAY", DayOfWeek.THURSDAY, "진리 관");
                model.putEvent(tuesday); model.putEvent(thursday); model.toggleCancellation(tuesday.id(), DATE);
                require(PlaceService.applyPlaces(base, model), "legacy places not linked");
                for (Event event : model.events()) {
                    require(event.buildingId().equals(jinri.id()) && event.customPlace().isEmpty(), "incomplete group linking");
                    require(event.note().equals("메모 보존") && event.customCategory().equals("교양"), "event metadata changed");
                }
                require(model.events().size() == 2 && model.occurrences(DATE).get(0).canceled(), "group or cancellation lost");
                require(!PlaceService.applyPlaces(base, model), "linking not idempotent");
                Path state = home.resolve("roundtrip.xml"); PlannerStore.save(state, model);
                PlannerModel loaded = PlannerStore.load(state);
                require(loaded.events().equals(model.events()) && loaded.occurrences(DATE).get(0).canceled(), "linked state did not roundtrip");
            });
            test("legacy-ambiguous-and-external-events-stay-unchanged", () -> {
                CampusMap map = base.copyBasePlaces();
                PlannerModel model = new PlannerModel();
                model.putPin(new PlannerModel.Pin("pin:duplicate", "진리관", jinri.center().lat(), jinri.center().lon()));
                model.putEvent(legacy("duplicate", DayOfWeek.TUESDAY, "진리관"));
                model.putEvent(legacy("external", DayOfWeek.THURSDAY, "교외 회의실"));
                List<Event> before = model.events();
                require(!PlaceService.applyPlaces(map, model) && model.events().equals(before), "uncertain place overwritten");
            });
            test("legacy-linked-once-event-produces-real-jinri-route", () -> {
                PlannerModel model = new PlannerModel();
                Building from = base.buildings().stream().filter(b -> base.originalName(b.id()).equals("공과대 6호관")).findFirst().orElseThrow();
                model.putEvent(new Event("from", "앞 일정", from.id(), LocalTime.of(10, 30), LocalTime.of(11, 50), Category.CLASS, null, DATE, ""));
                model.putEvent(new Event("to", "진리관 수업", "", LocalTime.NOON, LocalTime.of(13, 15), Category.CLASS, null, DATE, "보존", "", "진리관"));
                require(new PlaceService(base, model, routes).sync(), "once event not linked");
                var legs = new DayPlanService(base, routes).legs(model.occurrences(DATE));
                require(legs.size() == 1 && legs.get(0).route().reachable() && legs.get(0).route().meters() > 0, "Jinri route missing");
                require(SchedulePanel.showsWalkingLeg(legs.get(0)), "linked route still hidden as custom text");
            });
            test("frame-startup-links-legacy-events-and-auto-saves", () -> {
                PlannerModel model = new PlannerModel(); model.putEvent(legacy("startup", DayOfWeek.TUESDAY, "진리관"));
                MainFrame frame = null;
                try {
                    frame = new MainFrame(base, routes, model, home.resolve("data/state.xml"));
                    require(model.events().get(0).buildingId().equals(jinri.id()), "startup did not link known name");
                    Timer save = (Timer) field(frame, "noteTimer");
                    require(Boolean.TRUE.equals(field(frame, "unsaved")) && save.isRunning(), "linked state not scheduled for saving");
                    save.stop();
                    for (var listener : save.getActionListeners()) listener.actionPerformed(null);
                    PlannerModel loaded = PlannerStore.load(home.resolve("data/state.xml"));
                    require(loaded.events().get(0).buildingId().equals(jinri.id()) && !Boolean.TRUE.equals(field(frame, "unsaved")), "auto-save lost the linked place");
                } finally {
                    if (frame != null) { ((FolderModule) field(frame, "folders")).close(); frame.dispose(); }
                }
            });
        });
        if (previousHome == null) System.clearProperty("campus.home"); else System.setProperty("campus.home", previousHome);
        System.out.println("PLACE LINKING RESULT: " + passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }
}
