package campus.timetable;

import campus.calendar.Semester;
import campus.map.CampusMap;
import campus.model.PlannerModel;
import campus.model.PlannerModel.Category;
import campus.model.PlannerModel.Event;
import campus.ui.PlacePicker;
import campus.ui.Theme;
import javax.swing.*;
import java.awt.*;
import java.time.*;
import java.util.*;
import java.util.List;
import java.util.function.BooleanSupplier;

/** 선택한 주의 일정 보기 및 일회성/여러 요일 반복 일정 편집. */
public final class TimetablePanel extends JPanel {
    private CampusMap map;
    private PlannerModel model;
    private boolean readOnly;
    private final JScrollPane scroll;
    private final TimetableService service;
    private final BooleanSupplier save;
    private final WeekGrid grid;
    private final JLabel period = Theme.heading("주간 시간표", 17);

    public TimetablePanel(CampusMap map, PlannerModel model, BooleanSupplier save) {
        super(new BorderLayout());
        setOpaque(false);
        this.map = map;
        this.model = model;
        this.service = new TimetableService(model);
        this.save = save;
        grid = new WeekGrid(service, map, this::edit, save);
        period.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        add(period, BorderLayout.NORTH);
        scroll = grid.scrollPane();
        add(scroll, BorderLayout.CENTER);
    }

    public void setScheduleContext(CampusMap map, PlannerModel model, boolean readOnly) {
        this.map = map;
        this.model = model;
        this.readOnly = readOnly;
        service.setModel(model);
        grid.setScheduleContext(map, readOnly);
    }

    public void linkVerticalScroll(TimetablePanel other) {
        scroll.getVerticalScrollBar().setModel(other.scroll.getVerticalScrollBar().getModel());
    }

    public void refresh(LocalDate date) { refresh(date, 8); }

    public void refresh(LocalDate date, int earliestHour) {
        service.weekOf(date);
        LocalDate sunday = TimetableService.sunday(date), saturday = sunday.plusDays(6);
        period.setText("주간 시간표 · " + Semester.forDate(date) + " · "
                + sunday.getMonthValue() + "/" + sunday.getDayOfMonth() + "–"
                + saturday.getMonthValue() + "/" + saturday.getDayOfMonth());
        grid.refresh(earliestHour);
    }

    /** 지도·날짜별 일정 목록·주간 시간표가 같은 편집창을 사용한다. */
    public void addEvent(LocalDate date, CampusMap.Building initialPlace) {
        refresh(date);
        edit(null, date.getDayOfWeek(), LocalTime.of(9, 0), LocalTime.of(10, 0), initialPlace);
    }

    public void editEvent(Event event) {
        edit(event, event.recurring() ? event.weekday() : event.date().getDayOfWeek(), event.start(), event.end());
    }

    private void edit(Event event, DayOfWeek day, LocalTime start, LocalTime end) {
        edit(event, day, start, end, null);
    }

    private void edit(Event event, DayOfWeek day, LocalTime start, LocalTime end, CampusMap.Building place) {
        if (!readOnly) createEditor(event, day, start, end, place).setVisible(true);
    }

    // 폼 생성과 표시를 분리하여 저장 동작을 숨겨진 창에서도 검증한다.
    JDialog createEditor(Event event, DayOfWeek day, LocalTime start, LocalTime end, CampusMap.Building place) {
        return new EventEditor(event, day, start, end, place);
    }

    /** 입력값과 검증은 창 하나가 맡고, 실제 일정 변경은 서비스에 위임한다. */
    private final class EventEditor extends JDialog {
        private final Event original;
        private final JTextField title, date;
        private final TimePicker start = new TimePicker(false), end = new TimePicker(true);
        private final PlacePicker places;
        private final JComboBox<Category> category = new JComboBox<>(Category.values());
        private final JTextArea note;
        private final JRadioButton once, repeat;
        private final Map<DayOfWeek, JCheckBox> checks = new EnumMap<>(DayOfWeek.class);
        private final JLabel error = Theme.label(" ", 12, Theme.WARNING);

        EventEditor(Event original, DayOfWeek day, LocalTime from, LocalTime until, CampusMap.Building initialPlace) {
            super(SwingUtilities.getWindowAncestor(TimetablePanel.this),
                    original == null ? "일정 추가" : "일정 편집", Dialog.ModalityType.APPLICATION_MODAL);
            this.original = original;
            title = new JTextField(original == null ? "" : original.title());
            start.setTime(original == null ? from : original.start());
            end.setTime(original == null ? until : original.end());
            JTextField query = new JTextField();
            places = new PlacePicker(map, model, query, original == null ? initialPlace : map.building(original.buildingId()),
                    original == null ? "" : original.customPlace());
            category.setEditable(true);
            category.getAccessibleContext().setAccessibleName("일정 종류");
            if (original != null)
                category.setSelectedItem(original.customCategory().isEmpty() ? original.category() : original.customCategory());
            note = new JTextArea(original == null ? "" : original.note(), 3, 25);
            note.setLineWrap(true);
            note.setWrapStyleWord(true);
            once = new JRadioButton("선택한 날짜만", original != null && !original.recurring());
            repeat = new JRadioButton("매주 반복", !once.isSelected());
            date = new JTextField((once.isSelected() ? original.date() : service.dateFor(day)).toString());
            JPanel content = new JPanel(new BorderLayout(0, 16));
            content.setBackground(Color.WHITE);
            content.setBorder(BorderFactory.createEmptyBorder(24, 28, 24, 28));
            content.add(Theme.heading(original == null ? "어떤 일정을 추가할까요?" : "일정 편집", 21), BorderLayout.NORTH);
            content.add(Theme.scroll(fields(day, query)), BorderLayout.CENTER);
            content.add(actions(), BorderLayout.SOUTH);
            setContentPane(content);
            getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke("ESCAPE"), JComponent.WHEN_IN_FOCUSED_WINDOW);
            setDefaultCloseOperation(DISPOSE_ON_CLOSE);
            setMinimumSize(new Dimension(500, 620));
            setSize(560, Math.min(900, Toolkit.getDefaultToolkit().getScreenSize().height - 65));
            setLocationRelativeTo(TimetablePanel.this);
        }

        private JPanel fields(DayOfWeek day, JTextField query) {
            ButtonGroup modes = new ButtonGroup();
            JPanel mode = Theme.transparent(new FlowLayout(FlowLayout.LEFT, 8, 0));
            for (JRadioButton button : List.of(once, repeat)) {
                button.setOpaque(false);
                modes.add(button);
                mode.add(button);
                button.addActionListener(e -> updateMode());
            }
            JPanel days = weekdays(day);
            updateMode();
            for (JComponent field : List.of(title, date, query, note)) Theme.field(field);
            category.setFont(Theme.font(13, Font.PLAIN));
            category.setBackground(Color.WHITE);
            JPanel times = Theme.transparent(new GridLayout(1, 2, 12, 0));
            times.add(start);
            times.add(end);
            JScrollPane memo = new JScrollPane(note);
            memo.setPreferredSize(new Dimension(0, 80));
            memo.setMinimumSize(new Dimension(0, 65));
            JPanel fields = Theme.transparent(new GridBagLayout());
            GridBagConstraints c = new GridBagConstraints();
            c.gridx = 0; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
            int row = row(fields, c, 0, "일정 이름", title);
            row = row(fields, c, row, "반복", mode);
            row = row(fields, c, row, "날짜 (YYYY-MM-DD)", date);
            row = row(fields, c, row, "반복 요일 · 여러 요일 선택", days);
            row = row(fields, c, row, "시작 / 종료", times);
            row = row(fields, c, row, "일정 종류 · 선택 또는 직접 입력", category);
            row = row(fields, c, row, "건물 검색", query);
            row = row(fields, c, row, "장소 · 선택 또는 직접 입력", places);
            row(fields, c, row, "메모 · 강의실, 준비물, 모임 안내", memo);
            return fields;
        }

        private JPanel weekdays(DayOfWeek day) {
            Set<DayOfWeek> selected = EnumSet.noneOf(DayOfWeek.class);
            if (original == null || !original.recurring()) selected.add(day);
            else service.group(original).forEach(event -> selected.add(event.weekday()));
            JPanel days = Theme.transparent(new FlowLayout(FlowLayout.LEFT, 4, 0));
            String[] labels = {"일", "월", "화", "수", "목", "금", "토"};
            for (int i = 0; i < labels.length; i++) {
                DayOfWeek weekday = DayOfWeek.SUNDAY.plus(i);
                JCheckBox check = new JCheckBox(labels[i], selected.contains(weekday));
                check.setOpaque(false);
                check.setFont(Theme.font(13, Font.PLAIN));
                checks.put(weekday, check);
                days.add(check);
            }
            return days;
        }

        private void updateMode() {
            date.setEnabled(once.isSelected());
            checks.values().forEach(check -> check.setEnabled(repeat.isSelected()));
        }

        private JPanel actions() {
            JPanel buttons = Theme.transparent(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            if (original != null)
                buttons.add(Theme.deleteButton(original.recurring() ? "반복 일정 전체 삭제" : "일정 삭제", () -> {
                    if (grid.confirmDelete(this, original)) dispose();
                }));
            buttons.add(Theme.button("취소", false, this::dispose));
            JButton commit = Theme.button("일정 저장", true, this::commit);
            buttons.add(commit);
            getRootPane().setDefaultButton(commit);
            JPanel south = Theme.transparent(new BorderLayout(0, 8));
            south.add(error, BorderLayout.NORTH);
            south.add(buttons, BorderLayout.CENTER);
            return south;
        }

        private void commit() {
            try {
                PlacePicker.Selection place = places.selection();
                String text = ((JTextField) category.getEditor().getEditorComponent()).getText().strip();
                if (text.isEmpty()) throw new IllegalArgumentException("일정 종류를 선택하거나 직접 입력해 주세요.");
                Category kind = Category.fromLabel(text);
                String custom = kind.toString().equals(text) ? "" : text;
                Set<DayOfWeek> chosen = EnumSet.noneOf(DayOfWeek.class);
                checks.forEach((day, check) -> { if (check.isSelected()) chosen.add(day); });
                List<Event> proposed = repeat.isSelected()
                        ? service.prepare(original, title.getText(), place.buildingId(), start.time(), end.time(),
                                kind, chosen, note.getText(), custom, place.customPlace())
                        : List.of(service.prepareOnce(original, title.getText(), place.buildingId(), start.time(), end.time(),
                                kind, LocalDate.parse(date.getText().strip()), note.getText(), custom, place.customPlace()));
                if (original != null && original.recurring() && once.isSelected()
                        && !confirm("기존 반복 일정의 모든 요일을 삭제하고 선택한 날짜의 일정 하나로 바꿀까요?", "반복 변경")) return;
                if (service.overlaps(original, proposed) && !confirm("다른 일정과 시간이 겹칩니다. 저장할까요?", "시간 겹침")) return;
                service.apply(original, proposed);
                save.getAsBoolean();
                grid.refresh();
                dispose();
            } catch (java.time.format.DateTimeParseException ex) {
                error.setText("날짜는 2026-11-17 형식으로 입력하세요.");
            } catch (IllegalArgumentException ex) {
                error.setText(ex.getMessage());
            }
        }

        private boolean confirm(String message, String title) {
            return JOptionPane.showConfirmDialog(this, message, title, JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
        }
    }

    private static int row(JPanel panel, GridBagConstraints c, int row, String label, JComponent field) {
        c.gridy = row++; c.insets = new Insets(8, 0, 4, 0);
        panel.add(Theme.label(label, 12, Theme.MUTED), c);
        c.gridy = row++; c.insets = new Insets(0, 0, 4, 0);
        panel.add(field, c);
        return row;
    }
}
