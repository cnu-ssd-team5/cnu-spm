package campus.ui;

import java.awt.*;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import campus.map.CampusMap;
import campus.map.CampusMap.Building;
import campus.model.PlannerModel;
import campus.model.PlannerModel.Category;
import campus.model.PlannerModel.Event;
import campus.service.ScheduleSlot;

final class EventDialog extends JDialog {
    record Result(Event event, boolean deleted) { }
    private final JTextField title = new JTextField();
    private final JTextField start = new JTextField("09:00");
    private final JTextField end = new JTextField("10:00");
    private final JTextField date = new JTextField();
    private final JTextArea note = new JTextArea(4, 20);
    private final JComboBox<Category> category = new JComboBox<>(Category.values());
    private final JComboBox<DayOfWeek> weekday = new JComboBox<>(DayOfWeek.values());
    private final JComboBox<Building> building = new JComboBox<>();
    private final JRadioButton recurring = new JRadioButton("매주 반복");
    private final JRadioButton once = new JRadioButton("이 날짜만");
    private final JLabel error = Theme.label(" ", 12, Theme.WARNING);
    private final Event original;
    private Result result;

    static Result open(JFrame owner, CampusMap map, PlannerModel model, LocalDate selectedDate,
                       Building selectedBuilding, Event original, boolean repeat) {
        return open(owner, map, model, selectedDate, selectedBuilding, original, repeat, null);
    }

    static Result open(JFrame owner, CampusMap map, PlannerModel model, LocalDate selectedDate,
                       Building selectedBuilding, Event original, boolean repeat, ScheduleSlot slot) {
        EventDialog dialog = new EventDialog(owner, map, model, selectedDate, selectedBuilding, original, repeat, slot);
        dialog.setVisible(true);
        return dialog.result;
    }

    private EventDialog(JFrame owner, CampusMap map, PlannerModel model, LocalDate selectedDate,
                        Building selectedBuilding, Event original, boolean repeat, ScheduleSlot slot) {
        super(owner, original == null ? "캠퍼스 일정 추가" : "캠퍼스 일정 편집", true);
        this.original = original;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        JPanel content = new JPanel(new BorderLayout(0, 16));
        content.setBackground(Color.WHITE);
        content.setBorder(BorderFactory.createEmptyBorder(24, 26, 22, 26));
        content.add(Theme.heading(original == null ? "어떤 일정을 추가할까요?" : "일정 편집", 21), BorderLayout.NORTH);
        JPanel fields = Theme.transparent(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 0, 6, 0);
        int row = 0;
        row = row(fields, c, row, "일정 이름", title);
        JPanel types = Theme.transparent(new FlowLayout(FlowLayout.LEFT, 0, 0));
        ButtonGroup group = new ButtonGroup();
        group.add(recurring);
        group.add(once);
        recurring.setOpaque(false);
        once.setOpaque(false);
        recurring.setFont(Theme.font(13, Font.PLAIN));
        once.setFont(Theme.font(13, Font.PLAIN));
        types.add(recurring);
        types.add(Box.createHorizontalStrut(22));
        types.add(once);
        row = row(fields, c, row, "반복", types);
        JPanel when = Theme.transparent(new GridLayout(1, 2, 12, 0));
        when.add(weekday);
        when.add(date);
        row = row(fields, c, row, "요일 / 날짜 (YYYY-MM-DD)", when);
        JPanel times = Theme.transparent(new GridLayout(1, 2, 12, 0));
        times.add(start);
        times.add(end);
        row = row(fields, c, row, "시작 / 종료 (24시간 HH:mm)", times);
        row = row(fields, c, row, "일정 종류", category);
        JTextField query = new JTextField();
        query.setToolTipText("건물 이름을 입력한 뒤 아래 목록에서 장소를 선택하세요");
        row = row(fields, c, row, "건물 검색", query);
        row = row(fields, c, row, "장소 · 즐겨찾기 건물이 먼저 표시됩니다", building);
        note.setLineWrap(true);
        note.setWrapStyleWord(true);
        JScrollPane memoScroll = new JScrollPane(note);
        memoScroll.setPreferredSize(new Dimension(0, 80));
        memoScroll.setMinimumSize(new Dimension(0, 65));
        row(fields, c, row, "메모 · 강의실, 준비물, 모임 안내", memoScroll);
        content.add(Theme.scroll(fields), BorderLayout.CENTER);

        for (JComponent field : List.of(title, start, end, date, query, note)) Theme.field(field);
        for (JComboBox<?> combo : List.of(weekday, category, building)) {
            combo.setFont(Theme.font(13, Font.PLAIN));
            combo.setBackground(Color.WHITE);
        }
        weekday.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
                return super.getListCellRendererComponent(list,
                        value instanceof DayOfWeek day ? "매주 " + day.getDisplayName(TextStyle.FULL, Locale.KOREAN) : "", index, selected, focus);
            }
        });
        building.setMaximumRowCount(12);
        Set<String> duplicateNames = new HashSet<>();
        Set<String> seenNames = new HashSet<>();
        for (Building place : map.buildings()) {
            if (!seenNames.add(place.name())) duplicateNames.add(place.name());
        }
        building.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
                String text = "장소를 선택하세요";
                if (value instanceof Building b) {
                    text = (model.isFavorite(b.id()) ? "★ " : "") + b.name();
                    if (duplicateNames.contains(b.name())) text += " · " + b.id();
                }
                return super.getListCellRendererComponent(list, text, index, selected, focus);
            }
        });
        List<Building> choices = map.buildings().stream().sorted(Comparator
                .comparing((Building b) -> !model.isFavorite(b.id()))
                .thenComparing(Building::name)).toList();
        building.addItem(null);
        choices.forEach(building::addItem);
        Building initial = original == null ? selectedBuilding : map.building(original.buildingId());
        building.setSelectedItem(initial);
        query.getDocument().addDocumentListener(new DocumentListener() {
            private void update() {
                Building previous = (Building) building.getSelectedItem();
                String filter = query.getText().strip().toLowerCase(Locale.ROOT);
                building.removeAllItems();
                building.addItem(null);
                for (Building item : choices) {
                    if (item.name().toLowerCase(Locale.ROOT).contains(filter)) building.addItem(item);
                }
                if (previous != null && previous.name().toLowerCase(Locale.ROOT).contains(filter)) {
                    building.setSelectedItem(previous);
                } else if (building.getItemCount() == 2) {
                    building.setSelectedIndex(1);
                }
            }
            public void insertUpdate(DocumentEvent e) { update(); }
            public void removeUpdate(DocumentEvent e) { update(); }
            public void changedUpdate(DocumentEvent e) { update(); }
        });
        date.setText(selectedDate.toString());
        weekday.setSelectedItem(selectedDate.getDayOfWeek());
        recurring.setSelected(repeat);
        once.setSelected(!repeat);
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
            once.setSelected(!original.recurring());
            if (original.recurring()) weekday.setSelectedItem(original.weekday());
            else date.setText(original.date().toString());
            note.setText(original.note());
        } else if (!repeat) {
            category.setSelectedItem(Category.APPOINTMENT);
        }
        recurring.addActionListener(e -> updateMode());
        once.addActionListener(e -> updateMode());
        updateMode();

        JPanel south = Theme.transparent(new BorderLayout(0, 8));
        south.add(error, BorderLayout.NORTH);
        JPanel buttons = Theme.transparent(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        if (original != null) {
            JButton remove = Theme.button(original.recurring() ? "고정 일정 전체 삭제" : "일정 삭제", false, () -> {
                result = new Result(original, true);
                dispose();
            });
            remove.setForeground(Theme.WARNING);
            buttons.add(remove);
        }
        buttons.add(Theme.button("취소", false, this::dispose));
        JButton save = Theme.button("일정 저장", true, this::save);
        buttons.add(save);
        south.add(buttons, BorderLayout.CENTER);
        content.add(south, BorderLayout.SOUTH);
        setContentPane(content);
        getRootPane().setDefaultButton(save);
        getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke("ESCAPE"), JComponent.WHEN_IN_FOCUSED_WINDOW);
        setSize(540, 820);
        setMinimumSize(new Dimension(500, 620));
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        setSize(getWidth(), Math.min(getHeight(), screen.height - 65));
        setLocationRelativeTo(owner);
    }

    private static int row(JPanel panel, GridBagConstraints c, int row, String text, Component input) {
        c.gridy = row++;
        c.insets = new Insets(9, 0, 5, 0);
        panel.add(Theme.label(text, 12, Theme.MUTED), c);
        c.gridy = row++;
        c.insets = new Insets(0, 0, 3, 0);
        panel.add(input, c);
        return row;
    }

    private void updateMode() {
        weekday.setEnabled(recurring.isSelected());
        date.setEnabled(once.isSelected());
    }

    private void save() {
        Building selected = (Building) building.getSelectedItem();
        if (selected == null) {
            error.setText("OSM 건물 목록에서 장소를 선택해 주세요.");
            return;
        }
        try {
            Event event = new Event(original == null ? UUID.randomUUID().toString() : original.id(),
                    title.getText().strip(), selected.id(), LocalTime.parse(start.getText().strip()),
                    LocalTime.parse(end.getText().strip()), (Category)category.getSelectedItem(),
                    recurring.isSelected() ? (DayOfWeek)weekday.getSelectedItem() : null,
                    recurring.isSelected() ? null : LocalDate.parse(date.getText().strip()), note.getText());
            PlannerModel validation = new PlannerModel();
            validation.putEvent(event);
            result = new Result(event, false);
            dispose();
        } catch (DateTimeParseException ex) {
            error.setText("날짜는 2026-11-16, 시간은 09:00 형식으로 입력해 주세요.");
        } catch (IllegalArgumentException ex) {
            error.setText("이름을 입력하고 종료 시간을 시작 시간보다 늦게 지정해 주세요.");
        }
    }
}
