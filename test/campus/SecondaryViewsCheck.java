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
import java.util.concurrent.atomic.AtomicInteger;
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
        Semester semester = new Semester(2026, Semester.Season.FALL);
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
                AtomicReference<Semester> selectedSemester = new AtomicReference<>();
                AtomicInteger semesterChanges = new AtomicInteger();
                CalendarPanel calendar = new CalendarPanel(model, selected::set, selected::set, editTask::set, term -> {
                    selectedSemester.set(term);
                    semesterChanges.incrementAndGet();
                });
                calendar.refresh(date, semester);
                check(semesterChanges.get() == 0, "programmatic semester refresh does not fire selection callback");
                check(labels(calendar).stream().filter(l -> List.of("일", "월", "화", "수", "목", "금", "토").contains(l.getText()))
                        .map(JLabel::getText).toList().equals(List.of("일", "월", "화", "수", "목", "금", "토")), "Sunday-first calendar headers");
                check(labels(calendar).stream().anyMatch(l -> l.getText().equals("일") && l.getForeground().equals(new Color(196, 54, 54))), "Sunday header red");
                check(labels(calendar).stream().anyMatch(l -> l.getText().equals("토") && l.getForeground().equals(Theme.BLUE)), "Saturday header blue");
                check(dayButton(calendar, date.with(DayOfWeek.SATURDAY)).getForeground().equals(Theme.BLUE), "Saturday date blue");
                check(dayButton(calendar, date.with(DayOfWeek.SUNDAY)).getForeground().equals(new Color(196, 54, 54)), "Sunday date red");
                check(buttons(calendar).stream().anyMatch(b -> b.getText().equals("+ 할일 추가")), "generic calendar todo add button");
                JSpinner year = components(calendar, JSpinner.class).get(0);
                JComboBox<?> season = components(calendar, JComboBox.class).stream().filter(c -> "calendarSemester".equals(c.getName())).findFirst().orElseThrow();
                JComboBox<?> month = components(calendar, JComboBox.class).stream().filter(c -> "calendarMonth".equals(c.getName())).findFirst().orElseThrow();
                year.setValue(2027);
                check(selected.get().equals(date.plusYears(1)), "year selector changes displayed year while preserving month and day");
                check(semesterChanges.get() == 0, "year navigation uses date callback rather than semester jump");
                calendar.refresh(selected.get());
                month.setSelectedItem(10);
                check(selected.get().equals(LocalDate.of(2027, 10, 16)), "month selector changes displayed month");
                calendar.refresh(selected.get());
                season.setSelectedItem(Semester.Season.SPRING);
                check(selectedSemester.get().equals(new Semester(2027, Semester.Season.SPRING)), "season selector changes semester");
                check(semesterChanges.get() == 1, "one callback per user semester change");
                calendar.refresh(date, semester);
                check(semesterChanges.get() == 1, "refresh suppresses year month and semester listeners");
                Field daysField = CalendarPanel.class.getDeclaredField("days");
                daysField.setAccessible(true);
                JPanel days = (JPanel) daysField.get(calendar);
                check(days.getComponentCount() == 35, "ordinary month uses exactly five calendar rows");
                calendar.refresh(LocalDate.of(2026, 8, 1));
                check(days.getComponentCount() == 42 && CalendarPanel.rows(YearMonth.of(2026, 8)) == 6, "six rows appear when month actually needs them");
                calendar.refresh(LocalDate.of(2026, 2, 1));
                check(days.getComponentCount() == 35, "four-week February still uses requested five rows");
                calendar.refresh(LocalDate.of(2026, 1, 31));
                days.getActionMap().get("RIGHT").actionPerformed(new java.awt.event.ActionEvent(days, 0, "RIGHT"));
                check(selected.get().equals(LocalDate.of(2026, 2, 1)), "right arrow crosses month naturally");
                calendar.refresh(selected.get());
                days.getActionMap().get("LEFT").actionPerformed(new java.awt.event.ActionEvent(days, 0, "LEFT"));
                check(selected.get().equals(LocalDate.of(2026, 1, 31)), "left arrow crosses back to previous month");
                check(CalendarPanel.moveDate(LocalDate.of(2026, 11, 21), "RIGHT").equals(LocalDate.of(2026, 11, 22)), "Saturday right moves to adjacent Sunday rather than next month");
                check(CalendarPanel.moveDate(date, "UP").equals(date.minusWeeks(1))
                        && CalendarPanel.moveDate(date, "DOWN").equals(date.plusWeeks(1)), "vertical arrows move one calendar cell");
                calendar.refresh(LocalDate.of(2027, 1, 5));
                check(((Number) year.getValue()).intValue() == 2027 && season.getSelectedItem() == Semester.Season.WINTER,
                        "cross-year winter keeps actual displayed year");
                calendar.refresh(LocalDate.of(2026, 12, 25));
                check(dayButton(calendar, LocalDate.of(2026, 12, 25)).getForeground().equals(new Color(196, 54, 54))
                        && labels(calendar).stream().anyMatch(l -> l.getText().equals("성탄절")), "official holiday appears with red date and holiday name");
                calendar.refresh(LocalDate.of(2030, 1, 1));
                check(labels(calendar).stream().anyMatch(l -> l.getText().equals("2030년 공휴일 자료 없음")), "unsupported holiday years are identified");
                calendar.refresh(LocalDate.of(2026, 6, 1), semester);
                check(buttons(calendar).stream().filter(b -> b.getName() != null && b.getName().matches("\\d{4}-\\d{2}-\\d{2}"))
                        .findFirst().orElseThrow().getName().equals("2026-05-31"), "Monday month begins with previous Sunday");
                calendar.refresh(date, semester);
                check(buttons(calendar).stream().anyMatch(b -> date.toString().equals(b.getName())), "calendar day available");
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
                check(CalendarPanel.status(model.deadlines(date).get(0)).endsWith(" 완료")
                        && !CalendarPanel.status(model.deadlines(date).get(0)).contains("제출"), "generic completion status");
                check(labels((Container) dayButton(calendar, date).getParent()).stream().anyMatch(l -> l.getText().equals("5개")), "calendar count includes all four appointments and completed todo");
                LocalDate canceledDate = date.plusDays(4);
                model.putEvent(new Event("canceled-only", "휴강 수업", building, LocalTime.of(9, 0), LocalTime.of(10, 0),
                        PlannerModel.Category.CLASS, null, canceledDate, ""));
                model.toggleCancellation("canceled-only", canceledDate);
                calendar.refresh(canceledDate, semester);
                check(labels(calendar).stream().anyMatch(l -> l.getText().equals("휴강 · 휴강 수업") && l.getForeground().equals(Theme.MUTED)), "calendar displays canceled class without active classes");
                for (LocalDate crowdedDate : List.of(canceledDate, LocalDate.of(2026, 8, 17))) {
                    String suffix = crowdedDate.toString();
                    model.putEvent(new Event("compact-cancel-" + suffix, "취소 일정", building, LocalTime.of(9, 0), LocalTime.of(10, 0),
                            PlannerModel.Category.CLASS, null, crowdedDate, ""));
                    model.toggleCancellation("compact-cancel-" + suffix, crowdedDate);
                    model.putEvent(event("compact-active-" + suffix, "약속", building, crowdedDate, 15, 16));
                    model.putDeadline(new Deadline("compact-todo-a-" + suffix, "할일 <A>", crowdedDate, "", false, null));
                    model.putDeadline(new Deadline("compact-todo-b-" + suffix, "할일 B", crowdedDate, "", false, null));
                    calendar.refresh(crowdedDate);
                    JPanel row = (JPanel) dayButton(calendar, crowdedDate).getParent();
                    JPanel cell = (JPanel) row.getParent();
                    JPanel topAligned = (JPanel) cell.getComponent(1);
                    JPanel lines = (JPanel) topAligned.getComponent(0);
                    check(lines.getComponentCount() <= 2, "crowded five/six-week date has at most two content rows");
                    check(labels(row).stream().anyMatch(l -> l.getText().equals("3개")), "summary does not reduce complete daily item count");
                    check(cell.getToolTipText().contains("할일 &lt;A&gt;") && cell.getToolTipText().contains("할일 B"), "hidden todo names remain accessible in escaped tooltip");
                    check(labels(lines).stream().anyMatch(l -> l.getText().startsWith("휴강")), "cancellation marker has priority over todo titles");
                    if (KoreaHolidays.name(crowdedDate).isBlank()) {
                        JButton summary = buttons(lines).stream().filter(b -> b.getText().equals("할일 2개")).findFirst().orElseThrow();
                        summary.doClick();
                        check(selected.get().equals(crowdedDate), "todo summary opens selected day for complete sidebar list");
                    } else check(labels(lines).stream().anyMatch(l -> l.getText().equals(KoreaHolidays.name(crowdedDate))), "holiday marker remains when cell is crowded");
                    cell.setSize(108, 76);
                    layout(cell);
                    Rectangle contentBounds = SwingUtilities.convertRectangle(topAligned, lines.getBounds(), cell);
                    check(contentBounds.y + contentBounds.height <= cell.getHeight() - cell.getInsets().bottom,
                            "two content rows fit a76px calendar cell without bottom clipping: " + contentBounds
                                    + " inset=" + cell.getInsets().bottom + " date=" + row.getHeight());
                }
                model.putDeadline(new Deadline("second-visible-todo", "다른 할일", date, "", false, null));
                calendar.refresh(date);
                JPanel simpleCell = (JPanel) dayButton(calendar, date).getParent().getParent();
                JPanel simpleContents = (JPanel) ((JPanel) simpleCell.getComponent(1)).getComponent(0);
                simpleCell.setSize(108, 76);
                layout(simpleCell);
                check(buttons(simpleContents).size() == 2, "two ordinary todo titles remain directly editable when no annotations compete");
                Rectangle simpleBounds = SwingUtilities.convertRectangle(simpleContents.getParent(), simpleContents.getBounds(), simpleCell);
                check(simpleBounds.y + simpleBounds.height <= simpleCell.getHeight() - simpleCell.getInsets().bottom,
                        "two full todo buttons also fit the six-week cell height");

                PlannerModel weeklyModel = new PlannerModel();
                for (DayOfWeek day : DayOfWeek.values()) {
                    weeklyModel.putEvent(new Event("week-" + day.getValue(), day + " 수업", building,
                            LocalTime.of(9, 0), LocalTime.of(10, 0), PlannerModel.Category.CLASS, day, null, ""), semester);
                }
                weeklyModel.putEvent(new Event("overlap", "겹친 수업", building, LocalTime.of(9, 0), LocalTime.of(10, 0),
                        PlannerModel.Category.CLASS, DayOfWeek.MONDAY, null, ""), semester);
                weeklyModel.putEvent(new Event("early", "이른 수업", building, LocalTime.of(6, 0), LocalTime.of(7, 0),
                        PlannerModel.Category.CLASS, DayOfWeek.TUESDAY, null, ""), semester);
                weeklyModel.putEvent(new Event("clipped", "걸친 수업", building, LocalTime.of(7, 0), LocalTime.of(9, 0),
                        PlannerModel.Category.CLASS, DayOfWeek.TUESDAY, null, ""), semester);
                weeklyModel.putEvent(new Event("late", "늦은 수업", building, LocalTime.of(23, 0), LocalTime.MAX,
                        PlannerModel.Category.CLASS, DayOfWeek.SUNDAY, null, ""), semester);
                weeklyModel.putEvent(event("oneoff", "일일 약속", building, date, 12, 13), semester);
                Semester nextSemester = new Semester(2027, Semester.Season.SPRING);
                weeklyModel.putEvent(new Event("other-season", "다음 학기", building, LocalTime.of(9, 0), LocalTime.of(10, 0),
                        PlannerModel.Category.CLASS, DayOfWeek.MONDAY, null, ""), nextSemester);
                AtomicReference<LocalTime> addAt = new AtomicReference<>();
                AtomicReference<DayOfWeek> addDay = new AtomicReference<>();
                AtomicReference<Event> editEvent = new AtomicReference<>();
                WeeklyTimetablePanel timetable = new WeeklyTimetablePanel(weeklyModel, places, (event, day, time) -> {
                    editEvent.set(event); addDay.set(day); addAt.set(time);
                });
                timetable.refresh(semester);
                Field gridField = WeeklyTimetablePanel.class.getDeclaredField("grid");
                gridField.setAccessible(true);
                WeeklyGrid grid = (WeeklyGrid) gridField.get(timetable);
                timetable.setSize(650, 580);
                layout(timetable);
                JScrollPane gridScroll = components(timetable, JScrollPane.class).get(0);
                check(grid.getWidth() == gridScroll.getViewport().getExtentSize().width && grid.getWidth() < 980,
                        "seven weekday columns fit central viewport without horizontal clipping");
                check(grid.getPreferredSize().height == 38 + 16 * 72 + 12, "weekly timetable spans08 through24");
                check(!grid.getScrollableTracksViewportHeight(), "full day remains vertically scrollable");
                check(labels(timetable).stream().anyMatch(l -> l.getText().equals("주간 시간표")), "embedded weekly view has a title");
                check(labels(timetable).stream().anyMatch(l -> l.getText().contains(semester.toString())), "weekly view names selected season");
                BufferedImage image = new BufferedImage(grid.getWidth(), grid.getHeight(), BufferedImage.TYPE_INT_ARGB);
                Graphics2D graphics = image.createGraphics(); grid.paint(graphics); graphics.dispose();
                Field boxesField = WeeklyGrid.class.getDeclaredField("boxes");
                boxesField.setAccessible(true);
                @SuppressWarnings("unchecked")
                java.util.Map<Rectangle, Event> boxes = (java.util.Map<Rectangle, Event>) boxesField.get(grid);
                check(boxes.size() == 10, "weekly view shows all seven days, overlaps, boundary events and only current-season repeats");
                Rectangle first = boxes.entrySet().stream().filter(e -> e.getValue().id().equals("week-1")).findFirst().orElseThrow().getKey();
                Rectangle second = boxes.entrySet().stream().filter(e -> e.getValue().id().equals("overlap")).findFirst().orElseThrow().getKey();
                check(!first.intersects(second), "overlapping courses keep separate lanes");
                for (Rectangle rectangle : boxes.keySet()) {
                    check(rectangle.y >= 39 && rectangle.y + rectangle.height <= 38 + 16 * 72,
                            "weekly events stay inside08–24 grid");
                    check(rectangle.x >= 56 && rectangle.x + rectangle.width <= grid.getWidth(), "weekday event stays inside viewport");
                }
                Rectangle clipped = boxes.entrySet().stream().filter(e -> e.getValue().id().equals("clipped")).findFirst().orElseThrow().getKey();
                check(clipped.y == 39, "legacy course crossing08 is clipped below weekday header");
                grid.dispatchEvent(new MouseEvent(grid, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0,
                        first.x + 4, first.y + 4, 2, false, MouseEvent.BUTTON1));
                check(editEvent.get() != null && editEvent.get().id().equals("week-1"), "weekly doubleclick edits exact course");
                int sundayX = 56 + 6 * ((grid.getWidth() - 56) / 7) + 8;
                ((JMenuItem) grid.menuAt(new Point(sundayX, 38 + 8 * 72 + 6)).getComponent(0)).doClick();
                check(editEvent.get() == null && addDay.get() == DayOfWeek.SUNDAY && addAt.get().equals(LocalTime.of(16, 5)),
                        "empty weekly cell opens its weekday and five-minute time");
                ((JMenuItem) grid.menuAt(new Point(sundayX, 38 + 16 * 72 - 1)).getComponent(0)).doClick();
                check(addAt.get().equals(LocalTime.of(23, 55)), "weekly bottom insertion stays before24");
                check(grid.menuAt(new Point(sundayX, 20)) == null, "weekday header never creates an event");
                buttons(timetable).stream().filter(b -> b.getText().equals("+ 수업 추가")).findFirst().orElseThrow().doClick();
                check(editEvent.get() == null && addDay.get() == DayOfWeek.MONDAY && addAt.get().equals(LocalTime.of(9, 0)),
                        "embedded course add button uses shared editor");
                weeklyModel.toggleCancellation("week-1", date.with(DayOfWeek.MONDAY));
                timetable.refresh(semester);
                graphics = image.createGraphics(); grid.paint(graphics); graphics.dispose();
                check(boxes.size() == 10, "one-day cancellation preserves recurring season template");
                timetable.refresh(nextSemester);
                check(boxes.isEmpty(), "season change clears stale course hit targets immediately");
                graphics = image.createGraphics(); grid.paint(graphics); graphics.dispose();
                check(boxes.size() == 1 && boxes.values().iterator().next().id().equals("other-season"), "season change refreshes weekly courses");
                model.putEvent(new Event("weekly", "운영체제", building, LocalTime.of(9, 0), LocalTime.of(10, 0), PlannerModel.Category.CLASS, DayOfWeek.MONDAY, null, ""), semester);
                model.putEvent(new Event("weekly2", "운영체제", building, LocalTime.of(9, 0), LocalTime.of(10, 0), PlannerModel.Category.CLASS, DayOfWeek.WEDNESDAY, null, ""), semester);
                Semester spring = new Semester(2027, Semester.Season.SPRING);
                model.putEvent(new Event("next-term", "알고리즘", building, LocalTime.of(9, 0), LocalTime.of(10, 0), PlannerModel.Category.CLASS, DayOfWeek.MONDAY, null, ""), spring);
                CourseMaterialsPanel materials = new CourseMaterialsPanel(model, project.resolve("build/unused-materials"), e -> { });
                materials.refresh(semester);
                check(buttons(materials).stream().filter(b -> b.getText().contains("자료 폴더 열기")).count() == 1, "one materials tile per course");
                check(buttons(materials).stream().noneMatch(b -> b.getText().contains("알고리즘")), "materials excludes courses from another semester");
                Path courseFolder = materials.courseFolder("운영체제", semester);
                check(courseFolder.endsWith(Path.of("2026-2학기", "운영체제")), "materials uses full-year semester folder");
                check(!courseFolder.equals(materials.courseFolder("운영체제", spring)), "same course has distinct semester folders");
                check(!courseFolder.equals(materials.courseFolder("운영체제", new Semester(2126, Semester.Season.FALL))), "centuries never share course folders");
                check(materials.courseFolder("../과제", semester).getParent().equals(courseFolder.getParent()), "unsafe course stays inside semester folder");
                materials.refresh(spring);
                check(buttons(materials).stream().anyMatch(b -> b.getText().contains("알고리즘")), "changing semester refreshes course materials");
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
        return components(container, JButton.class);
    }

    private static List<JLabel> labels(Container container) {
        return components(container, JLabel.class);
    }

    private static JButton dayButton(CalendarPanel calendar, LocalDate date) {
        return buttons(calendar).stream().filter(b -> date.toString().equals(b.getName())).findFirst().orElseThrow();
    }

    private static void layout(Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) if (child instanceof Container nested) layout(nested);
    }

    private static <T extends Component> List<T> components(Component component, Class<T> type) {
        List<T> result = new ArrayList<>();
        if (type.isInstance(component)) result.add(type.cast(component));
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) result.addAll(components(child, type));
        }
        return result;
    }
}
