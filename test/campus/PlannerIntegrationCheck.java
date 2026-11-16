package campus;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import campus.PlannerModel.Deadline;
import campus.PlannerModel.Event;
import campus.PlannerModel.Occurrence;

/** Hidden Swing integration checks using the actual campus OSM extract and isolated state. */
public final class PlannerIntegrationCheck {
    private static final LocalDate DATE = LocalDate.of(2026, 11, 16);
    private static int checks;

    private PlannerIntegrationCheck() { }

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args.length == 0 ? "." : args[0]).toAbsolutePath();
        MapData data = MapData.load(root.resolve("data/chonnam-campus.osm"));
        Path temporary = Files.createTempDirectory("campus-integration-check-");
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                CampusPlanner.configureLookAndFeel();
                runChecks(data, temporary);
            } catch (Throwable exception) {
                failure.set(exception);
            } finally {
                for (Window window : Window.getWindows()) window.dispose();
            }
        });
        try {
            if (failure.get() != null) throw new AssertionError("Integration check failed", failure.get());
            System.out.println("PASS " + checks + " planner integration checks using " + data.buildings().size() + " OSM buildings");
        } finally {
            try (var files = Files.walk(temporary)) {
                for (Path path : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }

    private static void runChecks(MapData data, Path temporary) throws Exception {
        PlannerModel model = new PlannerModel();
        PlaceCatalog places = new PlaceCatalog(data, model);
        Place building = places.namedPlaces().stream().filter(place -> place.name().contains("만들마루")).findFirst().orElseThrow();
        Place other = places.namedPlaces().stream().filter(place -> place.name().contains("G&R")).findFirst().orElseThrow();
        Place unnamed = places.all().stream().filter(place -> !place.named()).findFirst().orElseThrow();
        int namedBefore = places.namedPlaces().size();
        check(places.all().size() > namedBefore, "unnamed buildings remain selectable in catalog");
        check(places.namedPlaces().stream().noneMatch(place -> place.id().equals(unnamed.id())), "unnamed building hidden from listed places");
        places.rename(unnamed, "검증용 건물");
        check(places.namedPlaces().size() == namedBefore + 1 && places.find(unnamed.id()).name().equals("검증용 건물"), "naming adds building to visible places");
        MapData.Route road = places.route(building, other);
        check(road.reachable() && road.points().size() > 1, "real OSM route available");
        Place pin = places.addPin(road.points().get(road.points().size() / 2), "검증용 핀");
        check(pin.pin() && pin.id().startsWith("pin:") && places.namedPlaces().contains(pin), "pin appears as named place");
        check(places.route(building, pin).reachable() && places.route(pin, other).reachable(), "pin routes connect to OSM paths");
        PlacePicker picker = new PlacePicker(places, model);
        JComboBox<?> choices = field(picker, "choices");
        check(choices.getItemCount() == places.namedPlaces().size() + 1, "shared selector lists only named places");
        picker.select(pin);
        check(picker.selected().id().equals(pin.id()), "selector accepts pin");
        JTextField query = field(picker, "query");
        query.setText("검증용 핀");
        check(choices.getItemCount() == 2 && picker.selected().id().equals(pin.id()), "selector searches pin names");

        Event first = new Event("class", "검증 수업", building.id(), LocalTime.of(13, 0), LocalTime.of(15, 0),
                PlannerModel.Category.CLASS, DayOfWeek.MONDAY, null, "강의실 메모");
        Event second = new Event("meeting", "검증 모임", pin.id(), LocalTime.of(16, 0), LocalTime.of(17, 0),
                PlannerModel.Category.GROUP, null, DATE, "");
        model.putEvent(first);
        model.putEvent(second);
        model.putDeadline(new Deadline("pending", "미제출 검증 과제", DATE, "마감 메모", false));
        model.putDeadline(new Deadline("submitted", "완료 검증 과제", DATE, "", true, DATE.minusDays(2)));
        CampusMapPanel map = new CampusMapPanel(data, model, places, place -> { }, place -> { }, point -> { });
        map.setOccurrences(model.occurrences(DATE));
        check(map.legs().size() == 1 && map.legs().get(0).route().reachable(), "daily route includes pin event");

        List<ScheduleSlot> requestedSlots = new ArrayList<>();
        SchedulePanel schedule = new SchedulePanel(model, places, new SchedulePanel.Actions() {
            public void add(ScheduleSlot slot) { requestedSlots.add(slot); }
            public void edit(Event event) { }
            public void toggle(Occurrence occurrence) { model.toggleCancellation(occurrence.event().id(), occurrence.date()); }
            public void select(Place place) { }
            public void addDeadline() { }
            public void editDeadline(Deadline deadline) { }
            public void toggleDeadline(Deadline deadline) { model.toggleDeadline(deadline.id()); }
            public void noteChanged(String text) { model.setDayNote(DATE, text); }
        });
        schedule.refresh(DATE, map.legs());
        List<JButton> addButtons = buttons(schedule).stream().filter(button -> button.getText().equals("+ 일정 추가")).toList();
        check(addButtons.size() == 3, "compact insert buttons above between and below schedules");
        addButtons.forEach(JButton::doClick);
        check(requestedSlots.equals(List.of(ScheduleSlot.before(first.start()),
                ScheduleSlot.between(first.end(), second.start()), ScheduleSlot.after(second.end()))), "compact buttons preserve contextual time defaults");
        check(buttons(schedule).stream().noneMatch(button -> button.getText().contains("13:00")
                || button.getText().contains("15:00")), "time values omitted from insert button captions");
        List<JLabel> walking = labels(schedule).stream().filter(label -> label.getText().startsWith("도보 ")).toList();
        check(walking.size() == 1 && walking.get(0).getText().contains(" m · 약 ")
                && walking.get(0).getText().endsWith("분"), "schedule transfer shows distance and time only");
        check(labels(schedule).stream().noneMatch(label -> label.getText().contains("여유")
                || label.getText().contains("까지 출발") || label.getText().contains("매주 반복")), "unnecessary transfer and category captions removed");
        JLabel pending = labels(schedule).stream().filter(label -> label.getText().equals("11월 16일 마감")).findFirst().orElseThrow();
        JLabel submitted = labels(schedule).stream().filter(label -> label.getText().equals("11월 14일 제출완료")).findFirst().orElseThrow();
        check(pending.getForeground().getRed() > pending.getForeground().getGreen() * 2, "unsubmitted due date is red");
        check(submitted.getForeground().equals(Theme.MUTED), "submitted actual date is gray");
        check(labels(schedule).stream().anyMatch(label -> label.getText().equals("미제출 검증 과제")), "assignments appear in daily sidebar");
        JTextArea sidebarNote = field(schedule, "note");
        sidebarNote.setText("작성 중인 일일 메모");
        check(model.dayNote(DATE).equals("작성 중인 일일 메모"), "sidebar memo updates model immediately");

        List<CampusMapPanel.Leg> dailyLegs = map.legs();
        int[] before = render(map);
        map.setSearchRoute(pin, building, places.route(pin, building));
        check(map.isSearchRouteActive() && map.legs().equals(dailyLegs), "independent search preserves scheduled legs");
        map.setOccurrences(model.occurrences(DATE));
        check(map.isSearchRouteActive(), "schedule refresh preserves independent search");
        map.clearSearchRoute();
        check(!map.isSearchRouteActive() && Arrays.equals(before, render(map)), "leaving search restores map exactly");
        check(model.events().size() == 2 && model.deadlines().size() == 2, "search never creates schedule or task records");

        checkEventDialog(places, model, pin, first);
        checkRouteDialog(places, model, building, pin);
        checkDeadlineDialog(model.deadlines().stream().filter(task -> task.completed()).findFirst().orElseThrow());

        WorkspacePanel deck = new WorkspacePanel(new JPanel(), new JPanel(), new JPanel(), new JPanel());
        check(deck.selectedView() == 0, "workspace starts on map");
        deck.showView(3);
        JButton next = field(deck, "next");
        JButton previous = field(deck, "previous");
        next.doClick();
        check(deck.selectedView() == 0, "workspace next wraps cyclically");
        previous.doClick();
        check(deck.selectedView() == 3, "workspace previous wraps cyclically");
        check(!next.isContentAreaFilled() && !previous.isContentAreaFilled(), "navigation arrows use custom translucent paint");

        PlannerFrame frame = new PlannerFrame(data, model, temporary.resolve("state.xml"), temporary);
        try {
            setField(frame, "selectedDate", DATE);
            invoke(frame, "refresh");
            JList<?> listed = field(frame, "placeList");
            check(listed.getModel().getSize() == places.namedPlaces().size(), "frame sidebar uses named catalog entries");
            for (int index = 0; index < listed.getModel().getSize(); index++) {
                check(((Place) listed.getModel().getElementAt(index)).named(), "frame excludes anonymous list rows");
            }
            check(buttons(frame).stream().noneMatch(button -> button.getText().equals("고정 시간표 추가")
                    || button.getText().equals("맞춤")), "removed redundant header and map fit controls");
            check(buttons(frame).stream().filter(button -> button.getText().equals("최단거리 찾기")).count() == 1,
                    "map contains one independent distance control");
            SchedulePanel frameSchedule = field(frame, "schedule");
            JTextArea note = field(frameSchedule, "note");
            note.setText("날짜 이동 직전에 수정한 메모");
            WorkspacePanel frameDeck = field(frame, "workspace");
            frameDeck.showView(2);
            check(model.dayNote(DATE).equals(note.getText()), "view swap keeps pending memo edit");
            CampusMapPanel frameMap = field(frame, "map");
            RouteDialog.Result result = new RouteDialog.Result(building, pin, places.route(building, pin));
            setField(frame, "searchedRoute", result);
            frameMap.setSearchRoute(building, pin, result.route());
            invoke(frame, "changeDate", new Class<?>[] { LocalDate.class }, DATE.plusDays(1));
            check(PlannerStore.load(temporary.resolve("state.xml")).dayNote(DATE).equals("날짜 이동 직전에 수정한 메모"), "date navigation flushes note to isolated state");
            check(!frameMap.isSearchRouteActive() && field(frame, "searchedRoute") == null, "date navigation clears both search states");
            check(frameDeck.selectedView() == 2, "date change keeps selected workspace view");
            setField(frame, "searchedRoute", result);
            frameMap.setSearchRoute(building, pin, result.route());
            invoke(frame, "showScheduleRoute");
            check(!frameMap.isSearchRouteActive() && field(frame, "searchedRoute") == null, "schedule route button clears independent search");
            check(PlannerStore.load(temporary.resolve("state.xml")).events().equals(model.events()), "integrated persistence retains schedules and pins references");
        } finally {
            ((javax.swing.Timer) field(frame, "noteTimer")).stop();
            frame.dispose();
        }
    }

    private static void checkEventDialog(PlaceCatalog places, PlannerModel model, Place pin, Event original) throws Exception {
        Constructor<EventDialog> constructor = EventDialog.class.getDeclaredConstructor(javax.swing.JFrame.class,
                PlaceCatalog.class, PlannerModel.class, LocalDate.class, Place.class, Event.class, ScheduleSlot.class);
        constructor.setAccessible(true);
        EventDialog adding = constructor.newInstance(null, places, model, DATE, pin, null, ScheduleSlot.before(LocalTime.of(13, 0)));
        check(((JTextField) field(adding, "start")).getText().equals("12:00")
                && ((JTextField) field(adding, "end")).getText().equals("13:00"), "event dialog preloads insertion times");
        ((JTextField) field(adding, "title")).setText("핀 일정 저장 확인");
        invoke(adding, "save");
        EventDialog.Result added = field(adding, "result");
        check(added.event().buildingId().equals(pin.id()) && added.event().date().equals(DATE), "new event saves pin and selected date");
        EventDialog editing = constructor.newInstance(null, places, model, DATE, pin, original, ScheduleSlot.after(LocalTime.of(18, 0)));
        check(((JTextField) field(editing, "start")).getText().equals("13:00")
                && ((JTextField) field(editing, "end")).getText().equals("15:00"), "editing keeps original time over insertion defaults");
        check(((JCheckBox) field(editing, "recurring")).isSelected(), "single dialog retains weekly schedule mode");
        invoke(editing, "save");
        EventDialog.Result saved = field(editing, "result");
        check(saved.event().equals(original), "editing round-trips existing event and note");
        adding.dispose();
        editing.dispose();
    }

    private static void checkRouteDialog(PlaceCatalog places, PlannerModel model, Place building, Place pin) throws Exception {
        Constructor<RouteDialog> constructor = RouteDialog.class.getDeclaredConstructor(javax.swing.JFrame.class,
                PlaceCatalog.class, PlannerModel.class, Place.class);
        constructor.setAccessible(true);
        RouteDialog dialog = constructor.newInstance(null, places, model, building);
        invoke(dialog, "findRoute");
        check(field(dialog, "result") == null && !((JLabel) field(dialog, "error")).getText().isBlank(), "route dialog validates missing destination");
        PlacePicker from = field(dialog, "from");
        PlacePicker to = field(dialog, "to");
        to.select(pin);
        invoke(dialog, "swap");
        check(from.selected().id().equals(pin.id()) && to.selected().id().equals(building.id()), "route dialog swaps pin and building");
        invoke(dialog, "findRoute");
        RouteDialog.Result found = field(dialog, "result");
        check(found.route().reachable() && found.from().pin(), "route dialog searches pin to building");
        RouteDialog same = constructor.newInstance(null, places, model, pin);
        ((PlacePicker) field(same, "to")).select(pin);
        invoke(same, "findRoute");
        RouteDialog.Result zero = field(same, "result");
        check(zero.route().reachable() && zero.route().meters() == 0, "same-place search reports zero distance");
        dialog.dispose();
        same.dispose();
    }

    private static void checkDeadlineDialog(Deadline original) throws Exception {
        Constructor<DeadlineDialog> constructor = DeadlineDialog.class.getDeclaredConstructor(javax.swing.JFrame.class,
                LocalDate.class, Deadline.class);
        constructor.setAccessible(true);
        DeadlineDialog dialog = constructor.newInstance(null, DATE, original);
        ((JTextArea) field(dialog, "note")).setText("제출 후 메모 수정");
        invoke(dialog, "save");
        DeadlineDialog.Result result = field(dialog, "result");
        check(result.deadline().completedOn().equals(original.completedOn()), "editing submitted assignment retains actual completion date");
        dialog.dispose();
    }

    private static int[] render(JComponent component) {
        component.setSize(680, 560);
        component.doLayout();
        BufferedImage image = new BufferedImage(680, 560, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        component.paint(graphics);
        graphics.dispose();
        return image.getRGB(0, 0, 680, 560, null, 0, 680);
    }

    private static List<JLabel> labels(Component component) { return components(component, JLabel.class); }
    private static List<JButton> buttons(Component component) { return components(component, JButton.class); }

    private static <T extends Component> List<T> components(Component component, Class<T> type) {
        List<T> result = new ArrayList<>();
        if (type.isInstance(component)) result.add(type.cast(component));
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) result.addAll(components(child, type));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return (T) field.get(target);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void invoke(Object target, String name) throws Exception {
        invoke(target, name, new Class<?>[0]);
    }

    private static void invoke(Object target, String name, Class<?>[] parameters, Object... args) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        method.invoke(target, args);
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        checks++;
    }
}
