package campus;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.*;
import campus.PlannerModel.Deadline;
import campus.PlannerModel.Event;

public final class SecondaryViewsCheck {
    private static int checks;

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }

    public static void main(String[] args) throws Exception {
        check(CourseMaterialsPanel.folderName("운영체제").equals("운영체제"), "ordinary course name");
        check(!CourseMaterialsPanel.folderName("CON").equalsIgnoreCase("CON"), "reserved Windows name");
        check(!CourseMaterialsPanel.folderName("A/B").equals(CourseMaterialsPanel.folderName("A\\B")), "sanitized collision");
        check(!CourseMaterialsPanel.folderName("A/B").equals(CourseMaterialsPanel.folderName("A_B")), "safe and unsafe collision");
        for (String title : List.of("../과제", "..", "NUL.txt", "자료.", "A\nB", "a".repeat(200))) {
            String folder = CourseMaterialsPanel.folderName(title);
            check(!folder.matches(".*[\\\\/:*?\"<>|\\p{Cntrl}].*") && !folder.endsWith(".") && folder.length() < 100,
                    "safe course folder " + title);
        }
        check(CourseMaterialsPanel.folderName("A/B").equals(CourseMaterialsPanel.folderName("A/B")), "stable course folder");
        String emojiName = CourseMaterialsPanel.folderName("X" + "😀".repeat(70));
        check(!Character.isHighSurrogate(emojiName.charAt(emojiName.lastIndexOf('-') - 1)), "Unicode truncation preserves complete character");

        Path project = Path.of(args.length == 0 ? "." : args[0]);
        MapData data = MapData.load(project.resolve("data/chonnam-campus.osm"));
        PlannerModel model = new PlannerModel();
        LocalDate date = LocalDate.of(2026, 11, 16);
        String building = data.buildings().get(0).id();
        model.putEvent(event("a", "수업 A", building, date, 9, 12));
        model.putEvent(event("b", "수업 B", building, date, 10, 11));
        model.putEvent(event("c", "수업 C", building, date, 11, 13));
        model.putEvent(event("d", "수업 D", building, date, 14, 15));
        Deadline pending = new Deadline("task", "보고서 제출", date, "메모", false, null);
        model.putDeadline(pending);
        PlaceCatalog places = new PlaceCatalog(data, model);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                AtomicReference<LocalDate> selected = new AtomicReference<>();
                AtomicReference<Deadline> editTask = new AtomicReference<>();
                CalendarPanel calendar = new CalendarPanel(model, selected::set, selected::set, editTask::set);
                calendar.refresh(date);
                check(buttons(calendar).stream().anyMatch(b -> b.getToolTipText() != null && b.getToolTipText().equals(date.toString())), "calendar day available");
                JButton taskButton = buttons(calendar).stream().filter(b -> b.getText().equals(pending.title())).findFirst().orElseThrow();
                check(taskButton.getForeground().equals(new Color(196, 54, 54)), "unsubmitted task red");
                taskButton.doClick();
                check(editTask.get().id().equals(pending.id()), "calendar task opens editor callback");
                buttons(calendar).stream().filter(b -> b.getText().equals("›")).findFirst().orElseThrow().doClick();
                check(selected.get().equals(date.plusMonths(1)), "calendar next month selects synchronized date");
                model.toggleDeadline(pending.id());
                calendar.refresh(date);
                taskButton = buttons(calendar).stream().filter(b -> b.getText().equals(pending.title())).findFirst().orElseThrow();
                check(taskButton.getForeground().equals(Theme.MUTED), "submitted task gray");
                check(CalendarPanel.status(model.deadlines(date).get(0)).endsWith("제출완료"), "submitted date status");

                AtomicReference<LocalTime> addAt = new AtomicReference<>();
                AtomicReference<Event> editEvent = new AtomicReference<>();
                DailyTimetablePanel timetable = new DailyTimetablePanel(model, places, editEvent::set, addAt::set);
                timetable.refresh(date);
                Field gridField = DailyTimetablePanel.class.getDeclaredField("grid");
                gridField.setAccessible(true);
                JComponent grid = (JComponent) gridField.get(timetable);
                grid.setSize(600, 1330);
                Field blocksField = DailyTimetablePanel.class.getDeclaredField("blocks");
                blocksField.setAccessible(true);
                List<?> blocks = (List<?>) blocksField.get(timetable);
                check(blocks.size() == 4, "all active events displayed");
                Method bounds = DailyTimetablePanel.class.getDeclaredMethod("bounds", blocks.get(0).getClass());
                bounds.setAccessible(true);
                Rectangle first = (Rectangle) bounds.invoke(timetable, blocks.get(0));
                Rectangle second = (Rectangle) bounds.invoke(timetable, blocks.get(1));
                check(!first.intersects(second), "overlapping event rectangles are in separate lanes");
                grid.dispatchEvent(new MouseEvent(grid, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0,
                        first.x + 4, first.y + 4, 1, false, MouseEvent.BUTTON1));
                check(editEvent.get() != null && editEvent.get().id().equals("a"), "timetable event edit callback");
                grid.dispatchEvent(new MouseEvent(grid, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0,
                        300, 12 + 16 * 54, 1, false, MouseEvent.BUTTON1));
                check(addAt.get().equals(LocalTime.of(16, 0)), "empty timetable time callback");
                BufferedImage image = new BufferedImage(600, 1330, BufferedImage.TYPE_INT_ARGB);
                Graphics2D graphics = image.createGraphics();
                grid.paint(graphics);
                graphics.dispose();
                model.toggleCancellation("b", date);
                timetable.refresh(date);
                check(((List<?>) blocksField.get(timetable)).size() == 3, "canceled event omitted");

                model.putEvent(new Event("weekly", "운영체제", building, LocalTime.of(9, 0), LocalTime.of(10, 0), PlannerModel.Category.CLASS, DayOfWeek.MONDAY, null, ""));
                model.putEvent(new Event("weekly2", "운영체제", building, LocalTime.of(9, 0), LocalTime.of(10, 0), PlannerModel.Category.CLASS, DayOfWeek.WEDNESDAY, null, ""));
                CourseMaterialsPanel materials = new CourseMaterialsPanel(model, project.resolve("build/unused-materials"), e -> { });
                materials.refresh();
                check(buttons(materials).stream().filter(b -> b.getText().contains("자료 폴더 열기")).count() == 1, "one materials tile per course");
            } catch (Throwable error) {
                failure.set(error);
            }
        });
        if (failure.get() != null) throw new RuntimeException(failure.get());
        System.out.println("PASS " + checks + " secondary-view checks");
    }

    private static Event event(String id, String title, String place, LocalDate date, int start, int end) {
        return new Event(id, title, place, LocalTime.of(start, 0), LocalTime.of(end, 0), PlannerModel.Category.APPOINTMENT, null, date, "");
    }

    private static List<JButton> buttons(Container container) {
        List<JButton> result = new ArrayList<>();
        for (Component child : container.getComponents()) {
            if (child instanceof JButton button) result.add(button);
            if (child instanceof Container nested) result.addAll(buttons(nested));
        }
        return result;
    }
}
