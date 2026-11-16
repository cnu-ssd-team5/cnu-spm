package campus;

import java.awt.*;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.UUID;
import javax.swing.*;
import campus.PlannerModel.Category;
import campus.PlannerModel.Event;

final class EventDialog extends JDialog {
    record Result(Event event, boolean deleted) { }

    private final JTextField title = new JTextField();
    private final JTextField start = new JTextField("09:00");
    private final JTextField end = new JTextField("10:00");
    private final JTextField date = new JTextField();
    private final JTextArea note = new JTextArea(4, 20);
    private final JComboBox<Category> category = new JComboBox<>(Category.values());
    private final JComboBox<DayOfWeek> weekday = new JComboBox<>(DayOfWeek.values());
    private final JCheckBox recurring = new JCheckBox("매주 반복");
    private final JLabel error = Theme.label(" ", 12, Theme.WARNING);
    private final PlacePicker place;
    private final Event original;
    private Result result;

    static Result open(JFrame owner, PlaceCatalog places, PlannerModel model, LocalDate selectedDate,
                       Place selectedPlace, Event original, ScheduleSlot slot) {
        EventDialog dialog = new EventDialog(owner, places, model, selectedDate, selectedPlace, original, slot);
        dialog.setVisible(true);
        return dialog.result;
    }

    private EventDialog(JFrame owner, PlaceCatalog places, PlannerModel model, LocalDate selectedDate,
                        Place selectedPlace, Event original, ScheduleSlot slot) {
        super(owner, original == null ? "일정 추가" : "일정 편집", true);
        this.original = original;
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
        repetition.add(weekday, BorderLayout.CENTER);
        row = row(fields, constraints, row, "반복", repetition);
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
        for (JComponent field : new JComponent[] { title, start, end, date, note }) Theme.field(field);
        for (JComboBox<?> combo : new JComboBox<?>[] { weekday, category }) {
            combo.setFont(Theme.font(13, Font.PLAIN));
            combo.setBackground(Color.WHITE);
        }
        weekday.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean selected, boolean focus) {
                Object label = value instanceof DayOfWeek day
                        ? day.getDisplayName(TextStyle.FULL, Locale.KOREAN) : value;
                return super.getListCellRendererComponent(list, label, index, selected, focus);
            }
        });
        date.setText(selectedDate.toString());
        date.setToolTipText("YYYY-MM-DD");
        start.setToolTipText("HH:mm");
        end.setToolTipText("HH:mm");
        weekday.setSelectedItem(selectedDate.getDayOfWeek());
        category.setSelectedItem(Category.APPOINTMENT);
        if (slot != null) {
            start.setText(slot.start().toString());
            end.setText(slot.end().toString());
        }
        if (original != null) {
            title.setText(original.title());
            start.setText(original.start().toString());
            end.setText(original.end().toString());
            category.setSelectedItem(original.category());
            recurring.setSelected(original.recurring());
            if (original.recurring()) weekday.setSelectedItem(original.weekday());
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
            JButton remove = Theme.button(original.recurring() ? "고정 일정 삭제" : "삭제", false, () -> {
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
        setSize(510, Math.min(770, Toolkit.getDefaultToolkit().getScreenSize().height - 65));
        setMinimumSize(new Dimension(470, 600));
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
        weekday.setEnabled(recurring.isSelected());
        date.setEnabled(!recurring.isSelected());
    }

    private void save() {
        Place selected = place.selected();
        if (selected == null) {
            error.setText("장소를 선택해 주세요.");
            return;
        }
        try {
            Event event = new Event(original == null ? UUID.randomUUID().toString() : original.id(),
                    title.getText().strip(), selected.id(), LocalTime.parse(start.getText().strip()),
                    LocalTime.parse(end.getText().strip()), (Category) category.getSelectedItem(),
                    recurring.isSelected() ? (DayOfWeek) weekday.getSelectedItem() : null,
                    recurring.isSelected() ? null : LocalDate.parse(date.getText().strip()), note.getText());
            result = new Result(event, false);
            dispose();
        } catch (DateTimeParseException failure) {
            error.setText("날짜는 YYYY-MM-DD, 시간은 HH:mm으로 입력해 주세요.");
        } catch (IllegalArgumentException failure) {
            error.setText(failure.getMessage());
        }
    }
}

