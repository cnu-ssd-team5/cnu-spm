package campus;

import java.awt.*;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.UUID;
import javax.swing.*;
import campus.PlannerModel.Category;
import campus.PlannerModel.Event;

final class EventDialog extends JDialog {
    record Result(Event event, boolean deleted, java.util.List<Event> events) {
        Result(Event event, boolean deleted) { this(event, deleted, java.util.List.of(event)); }
    }

    private final JTextField title = new JTextField();
    private final TimePicker start = new TimePicker(false);
    private final TimePicker end = new TimePicker(true);
    private final JTextField date = new JTextField();
    private final JTextArea note = new JTextArea(4, 20);
    private final JComboBox<Category> category = new JComboBox<>(Category.values());
    private final java.util.Map<DayOfWeek, JCheckBox> weekdays = new java.util.EnumMap<>(DayOfWeek.class);
    private final JCheckBox recurring = new JCheckBox("매주 반복");
    private final JLabel error = Theme.label(" ", 12, Theme.WARNING);
    private final PlacePicker place;
    private final Event original;
    private final PlannerModel model;
    private final Semester semester;
    private Result result;

    static Result open(JFrame owner, PlaceCatalog places, PlannerModel model, LocalDate selectedDate,
                       Place selectedPlace, Event original, ScheduleSlot slot) {
        return open(owner, places, model, selectedDate, selectedPlace, original, slot, Semester.forDate(selectedDate));
    }

    static Result open(JFrame owner, PlaceCatalog places, PlannerModel model, LocalDate selectedDate,
                       Place selectedPlace, Event original, ScheduleSlot slot, Semester semester) {
        EventDialog dialog = new EventDialog(owner, places, model, selectedDate, selectedPlace, original, slot, semester);
        dialog.setVisible(true);
        return dialog.result;
    }

    static Result openRecurring(JFrame owner, PlaceCatalog places, PlannerModel model, LocalDate selectedDate,
                                Place selectedPlace, Event original, ScheduleSlot slot, Semester semester, DayOfWeek day) {
        EventDialog dialog = new EventDialog(owner, places, model, selectedDate, selectedPlace, original, slot, semester);
        dialog.recurring.setSelected(true);
        if (original == null) {
            dialog.weekdays.forEach((weekday, check) -> check.setSelected(weekday == day));
            dialog.category.setSelectedItem(Category.CLASS);
        }
        dialog.updateMode();
        dialog.setVisible(true);
        return dialog.result;
    }

    private EventDialog(JFrame owner, PlaceCatalog places, PlannerModel model, LocalDate selectedDate,
                        Place selectedPlace, Event original, ScheduleSlot slot, Semester semester) {
        super(owner, original == null ? "일정 추가" : "일정 편집", true);
        this.original = original;
        this.model = model;
        if (original == null) selectedDate = PlannerTime.forNewItem(selectedDate);
        this.semester = original == null ? Semester.forDate(selectedDate) : semester;
        place = new PlacePicker(places, model);
        place.select(original == null ? selectedPlace : places.find(original.buildingId()));
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        JPanel content = new JPanel(new BorderLayout(0, 14));
        content.setBackground(Color.WHITE);
        content.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));
        content.add(Theme.heading(original == null ? "일정 추가" : "일정 편집", 20), BorderLayout.NORTH);
        JPanel fields = Theme.transparent(new GridBagLayout());
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        int row = 0;
        row = row(fields, constraints, row, "이름", title);
        JPanel repetition = Theme.transparent(new BorderLayout(12, 0));
        recurring.setFont(Theme.font(13, Font.PLAIN));
        recurring.setOpaque(false);
        repetition.add(recurring, BorderLayout.WEST);
        JPanel days = Theme.transparent(new FlowLayout(FlowLayout.LEFT, 3, 0));
        for (DayOfWeek day : DayOfWeek.values()) {
            JCheckBox check = new JCheckBox(day.getDisplayName(TextStyle.SHORT, Locale.KOREAN));
            check.setOpaque(false);
            check.setFont(Theme.font(12, Font.PLAIN));
            weekdays.put(day, check);
            days.add(check);
        }
        repetition.add(days, BorderLayout.CENTER);
        boolean vacation = this.semester.season() == Semester.Season.SUMMER
                || this.semester.season() == Semester.Season.WINTER;
        DateTimeFormatter range = DateTimeFormatter.ofPattern("M/d");
        String period = vacation ? this.semester.start().format(range) + "~" + this.semester.end().format(range) : "16주";
        row = row(fields, constraints, row, "반복 · " + this.semester + " (" + period + ")", repetition);
        row = row(fields, constraints, row, "날짜", date);
        JPanel times = Theme.transparent(new GridLayout(1, 2, 10, 0));
        times.add(start);
        times.add(end);
        row = row(fields, constraints, row, "시작 / 종료", times);
        row = row(fields, constraints, row, "종류", category);
        row = row(fields, constraints, row, "장소", place);
        note.setLineWrap(true);
        note.setWrapStyleWord(true);
        JScrollPane memo = new JScrollPane(note);
        memo.setPreferredSize(new Dimension(0, 78));
        row(fields, constraints, row, "메모", memo);
        content.add(Theme.scroll(fields), BorderLayout.CENTER);
        for (JComponent field : new JComponent[] { title, date, note }) Theme.field(field);
        for (JComboBox<?> combo : new JComboBox<?>[] { category }) {
            combo.setFont(Theme.font(13, Font.PLAIN));
            combo.setBackground(Color.WHITE);
        }
        date.setText(selectedDate.toString());
        date.setToolTipText("YYYY-MM-DD");
        start.setToolTipText("시 / 분으로 시작 시각 선택");
        end.setToolTipText("시 / 분으로 종료 시각 선택");
        start.setTime(LocalTime.of(9, 0));
        end.setTime(LocalTime.of(10, 0));
        weekdays.get(selectedDate.getDayOfWeek()).setSelected(true);
        category.setSelectedItem(Category.APPOINTMENT);
        if (original == null && slot != null && slot.start().isBefore(LocalTime.of(8, 0))) {
            slot = new ScheduleSlot(LocalTime.of(8, 0), LocalTime.of(9, 0));
        }
        if (slot != null) {
            start.setTime(slot.start());
            end.setTime(slot.end());
        }
        if (original != null) {
            title.setText(original.title());
            start.setTime(original.start());
            end.setTime(original.end());
            category.setSelectedItem(original.category());
            recurring.setSelected(original.recurring());
            if (original.recurring()) {
                weekdays.values().forEach(check -> check.setSelected(false));
                new WeeklyScheduleService(model, semester).group(original)
                        .forEach(event -> weekdays.get(event.weekday()).setSelected(true));
                weekdays.get(original.weekday()).setSelected(true);
            }
            else date.setText(original.date().toString());
            note.setText(original.note());
        }
        recurring.addActionListener(event -> {
            if (recurring.isSelected() && category.getSelectedItem() == Category.APPOINTMENT) {
                category.setSelectedItem(Category.CLASS);
            }
            updateMode();
        });
        updateMode();
        JPanel south = Theme.transparent(new BorderLayout(0, 8));
        south.add(error, BorderLayout.NORTH);
        JPanel buttons = Theme.transparent(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        if (original != null) {
            JButton remove = Theme.button(original.recurring() ? "선택 수업 전체 요일 삭제" : "삭제", false, () -> {
                if (original.recurring() && new WeeklyScheduleService(model, semester).group(original).size() > 1
                        && JOptionPane.showConfirmDialog(this, "이 수업의 모든 반복 요일을 삭제할까요?", "반복 일정 삭제",
                        JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
                result = new Result(original, true);
                dispose();
            });
            remove.setForeground(Theme.WARNING);
            buttons.add(remove);
        }
        buttons.add(Theme.button("취소", false, this::dispose));
        JButton save = Theme.button("저장", true, this::save);
        buttons.add(save);
        south.add(buttons, BorderLayout.CENTER);
        content.add(south, BorderLayout.SOUTH);
        setContentPane(content);
        getRootPane().setDefaultButton(save);
        getRootPane().registerKeyboardAction(event -> dispose(), KeyStroke.getKeyStroke("ESCAPE"),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        setSize(650, Math.min(850, Toolkit.getDefaultToolkit().getScreenSize().height - 65));
        setMinimumSize(new Dimension(580, 640));
        setLocationRelativeTo(owner);
    }

    private static int row(JPanel panel, GridBagConstraints constraints, int row,
                           String text, Component input) {
        constraints.gridy = row++;
        constraints.insets = new Insets(9, 0, 5, 0);
        panel.add(Theme.label(text, 12, Theme.MUTED), constraints);
        constraints.gridy = row++;
        constraints.insets = new Insets(0, 0, 3, 0);
        panel.add(input, constraints);
        return row;
    }

    private void updateMode() {
        weekdays.values().forEach(check -> check.setEnabled(recurring.isSelected()));
        date.setEnabled(!recurring.isSelected());
    }

    private void save() {
        Place selected = place.selected();
        if (selected == null) {
            error.setText("장소를 선택해 주세요.");
            return;
        }
        try {
            LocalDate eventDate = recurring.isSelected() ? null : LocalDate.parse(date.getText().strip());
            if (original == null && eventDate != null) eventDate = PlannerTime.forNewItem(eventDate);
            WeeklyScheduleService weekly = new WeeklyScheduleService(model, semester);
            java.util.List<Event> proposed;
            if (recurring.isSelected()) {
                java.util.Set<DayOfWeek> chosen = java.util.EnumSet.noneOf(DayOfWeek.class);
                weekdays.forEach((day, check) -> { if (check.isSelected()) chosen.add(day); });
                proposed = weekly.prepare(original, title.getText().strip(), selected.id(), start.time(), end.time(),
                        (Category) category.getSelectedItem(), chosen, note.getText());
            } else proposed = java.util.List.of(new Event(original == null ? UUID.randomUUID().toString() : original.id(),
                    title.getText().strip(), selected.id(), start.time(), end.time(), (Category) category.getSelectedItem(), null, eventDate, note.getText()));
            Event event = proposed.get(0);
            java.util.List<Event> conflicts = recurring.isSelected() ? weekly.conflicts(original, proposed) : model.conflicts(event, semester);
            if (!conflicts.isEmpty()) {
                String names = conflicts.stream().map(Event::title).distinct()
                        .limit(4).collect(java.util.stream.Collectors.joining(", "));
                int choice = JOptionPane.showConfirmDialog(this,
                        "시간이 겹치는 일정이 있습니다.\n" + names + "\n그래도 추가할까요?",
                        "시간 중복", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (choice != JOptionPane.YES_OPTION) return;
            }
            result = new Result(event, false, proposed);
            dispose();
        } catch (DateTimeParseException failure) {
            error.setText("날짜는 YYYY-MM-DD로 입력해 주세요.");
        } catch (IllegalArgumentException failure) {
            error.setText(failure.getMessage());
        }
    }
}

