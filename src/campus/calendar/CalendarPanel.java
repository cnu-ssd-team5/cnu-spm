package campus.calendar;
import campus.model.PlannerModel;
import campus.ui.Theme;


import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.*;
import campus.model.PlannerModel.Deadline;

public final class CalendarPanel extends JPanel {
    private static final Color DUE_COLOR = new Color(196, 54, 54);
    private static final Color CELL_LINE = new Color(145, 161, 183);
    private final PlannerModel model;
    private final Consumer<LocalDate> onDateSelect;
    private final Consumer<LocalDate> onAddDeadline;
    private final Consumer<Deadline> onEditDeadline;
    private final Consumer<Semester> onSemesterSelect;
    private final JSpinner year = new JSpinner(new SpinnerNumberModel(PlannerTime.today().getYear(), 1900, 9999, 1));
    private final JComboBox<Integer> month = new JComboBox<>(new Integer[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12});
    private final JComboBox<Semester.Season> season = new JComboBox<>(Semester.Season.values());
    private final JPanel days = new JPanel(new GridLayout(0, 7, 3, 3));
    private final JLabel holidayRange = Theme.label(" ", 11, Theme.MUTED);
    private LocalDate selectedDate = PlannerTime.today();
    private Semester selectedSemester = Semester.forDate(selectedDate);
    private boolean refreshing;
    private LocalDate lastClickDate;
    private long lastClickWhen;
    private Point lastClickPoint;

    public CalendarPanel(PlannerModel model, Consumer<LocalDate> onDateSelect,
                  Consumer<LocalDate> onAddDeadline, Consumer<Deadline> onEditDeadline) {
        this(model, onDateSelect, onAddDeadline, onEditDeadline, term -> onDateSelect.accept(term.start()));
    }

    public CalendarPanel(PlannerModel model, Consumer<LocalDate> onDateSelect,
                  Consumer<LocalDate> onAddDeadline, Consumer<Deadline> onEditDeadline,
                  Consumer<Semester> onSemesterSelect) {
        super(new BorderLayout(0, 10));
        this.model = model;
        this.onDateSelect = onDateSelect;
        this.onAddDeadline = onAddDeadline;
        this.onEditDeadline = onEditDeadline;
        this.onSemesterSelect = onSemesterSelect;
        setOpaque(false);

        JPanel top = Theme.transparent(new BorderLayout(0, 9));
        JPanel heading = Theme.transparent(new BorderLayout(8, 0));
        heading.add(Theme.heading("달력", 18), BorderLayout.WEST);
        season.setName("calendarSemester");
        season.setFont(Theme.font(12, Font.PLAIN));
        season.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean selected, boolean focus) {
                super.getListCellRendererComponent(list, value, index, selected, focus);
                if (value instanceof Semester.Season choice) {
                    setText(new Semester(selectedSemester.year(), choice).toString());
                }
                return this;
            }
        });
        season.addActionListener(e -> {
            if (!refreshing) onSemesterSelect.accept(new Semester(selectedSemester.year(),
                    (Semester.Season) season.getSelectedItem()));
        });
        heading.add(season, BorderLayout.EAST);
        top.add(heading, BorderLayout.NORTH);

        JPanel navigation = Theme.transparent(new BorderLayout(8, 0));
        JPanel monthControls = Theme.transparent(new FlowLayout(FlowLayout.LEFT, 6, 0));
        monthControls.add(Theme.button("‹", false, () -> changeMonth(-1)));
        year.setEditor(new JSpinner.NumberEditor(year, "0"));
        year.setName("calendarYear");
        year.setPreferredSize(new Dimension(95, 34));
        monthControls.add(year);
        monthControls.add(Theme.label("년", 12, Theme.MUTED));
        month.setName("calendarMonth");
        month.setFont(Theme.font(12, Font.PLAIN));
        month.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean selected, boolean focus) {
                return super.getListCellRendererComponent(list, value + "월", index, selected, focus);
            }
        });
        monthControls.add(month);
        monthControls.add(Theme.button("›", false, () -> changeMonth(1)));
        year.addChangeListener(e -> selectMonth());
        month.addActionListener(e -> selectMonth());
        navigation.add(monthControls, BorderLayout.WEST);
        navigation.add(Theme.button("오늘", false, () -> onDateSelect.accept(PlannerTime.today())), BorderLayout.EAST);
        top.add(navigation, BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);

        JPanel calendar = Theme.transparent(new BorderLayout(0, 5));
        JPanel weekdays = Theme.transparent(new GridLayout(1, 7, 3, 0));
        for (String day : List.of("일", "월", "화", "수", "목", "금", "토")) {
            JLabel label = Theme.label(day, 12, day.equals("일") ? DUE_COLOR : day.equals("토") ? Theme.BLUE : Theme.MUTED);
            label.setHorizontalAlignment(SwingConstants.CENTER);
            weekdays.add(label);
        }
        calendar.add(weekdays, BorderLayout.NORTH);
        days.setBackground(CELL_LINE);
        days.setBorder(BorderFactory.createLineBorder(CELL_LINE));
        days.setFocusable(true);
        for (String key : List.of("LEFT", "RIGHT", "UP", "DOWN")) {
            days.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(key), key);
            days.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key), key);
            days.getActionMap().put(key, new AbstractAction() {
                @Override public void actionPerformed(ActionEvent event) {
                    clearDateClick();
                    onDateSelect.accept(moveDate(selectedDate, key));
                    SwingUtilities.invokeLater(() -> focusSelectedDay());
                }
            });
        }
        days.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke("ENTER"),"addDeadline");
        days.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("ENTER"),"addDeadline");
        days.getActionMap().put("addDeadline",new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) {
                clearDateClick();
                onAddDeadline.accept(selectedDate);
            }
        });
        calendar.add(days, BorderLayout.CENTER);
        add(calendar, BorderLayout.CENTER);

        JPanel bottom = Theme.transparent(new BorderLayout());
        holidayRange.setToolTipText("확인된 공휴일만 표시하며, 공휴일이라고 일정을 자동으로 휴강 처리하지 않습니다.");
        bottom.add(holidayRange, BorderLayout.WEST);
        bottom.add(Theme.label("날짜 더블클릭 또는 Enter로 할일 추가",11,Theme.MUTED),BorderLayout.EAST);
        add(bottom, BorderLayout.SOUTH);
    }

    public void refresh(LocalDate date) { refresh(date, Semester.forDate(date)); }

    public void refresh(LocalDate date, Semester semester) {
        Component focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        boolean keepFocus = focus != null && SwingUtilities.isDescendingFrom(focus, days);
        selectedDate = date;
        selectedSemester = semester;
        refreshing = true;
        try {
            year.setValue(date.getYear());
            month.setSelectedItem(date.getMonthValue());
            season.setSelectedItem(semester.season());
            season.repaint();
        } finally {
            refreshing = false;
        }
        YearMonth displayMonth = YearMonth.from(date);
        holidayRange.setText(KoreaHolidays.supports(date.getYear()) ? "공휴일 자료: 2025~2027년"
                : date.getYear() + "년 공휴일 자료 없음");
        LocalDate first = displayMonth.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
        days.removeAll();
        for (int index = 0; index < rows(displayMonth) * 7; index++) days.add(dayCell(first.plusDays(index), displayMonth));
        revalidate();
        repaint();
        if (keepFocus) SwingUtilities.invokeLater(() -> focusSelectedDay());
    }

    static int rows(YearMonth month) {
        int offset = month.atDay(1).getDayOfWeek().getValue() % 7;
        return offset + month.lengthOfMonth() > 35 ? 6 : 5;
    }

    static LocalDate moveDate(LocalDate date, String key) {
        return date.plusDays(switch (key) {
            case "LEFT" -> -1;
            case "RIGHT" -> 1;
            case "UP" -> -7;
            case "DOWN" -> 7;
            default -> 0;
        });
    }

    private void focusSelectedDay() {
        for (Component cell : days.getComponents()) {
            JPanel dateRow = (JPanel) ((JPanel) cell).getComponent(0);
            JButton button = (JButton) dateRow.getComponent(0);
            if (selectedDate.toString().equals(button.getName())) {
                button.requestFocusInWindow();
                break;
            }
        }
    }

    private void selectMonth() {
        if (refreshing) return;
        YearMonth target = YearMonth.of(((Number) year.getValue()).intValue(), (Integer) month.getSelectedItem());
        onDateSelect.accept(target.atDay(Math.min(selectedDate.getDayOfMonth(), target.lengthOfMonth())));
    }

    private void changeMonth(int amount) {
        YearMonth target = YearMonth.from(selectedDate).plusMonths(amount);
        onDateSelect.accept(target.atDay(Math.min(selectedDate.getDayOfMonth(), target.lengthOfMonth())));
    }

    private JPanel dayCell(LocalDate date, YearMonth month) {
        String holiday = KoreaHolidays.name(date);
        JPanel cell = new JPanel(new BorderLayout(0, 2));
        cell.setBackground(date.equals(selectedDate) ? new Color(216, 231, 255)
                : !YearMonth.from(date).equals(month) ? new Color(237, 241, 246)
                : date.getDayOfWeek() == DayOfWeek.SATURDAY ? new Color(234, 246, 255)
                : date.getDayOfWeek() == DayOfWeek.SUNDAY ? new Color(255, 239, 245) : Color.WHITE);
        cell.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(date.equals(selectedDate) ? Theme.BLUE : CELL_LINE,
                        date.equals(selectedDate) ? 2 : 1),
                BorderFactory.createEmptyBorder(2, 4, 2, 4)));
        cell.setPreferredSize(new Dimension(80, 76));
        cell.setFocusable(true);
        JPanel dateRow = Theme.transparent(new BorderLayout());
        JButton day = new JButton(Integer.toString(date.getDayOfMonth()));
        day.setFont(Theme.font(12, date.equals(PlannerTime.today()) ? Font.BOLD : Font.PLAIN));
        day.setForeground(!holiday.isBlank() || date.getDayOfWeek() == DayOfWeek.SUNDAY ? DUE_COLOR
                : date.getDayOfWeek() == DayOfWeek.SATURDAY ? Theme.BLUE
                : YearMonth.from(date).equals(month) ? Theme.INK : Theme.MUTED);
        day.setContentAreaFilled(false);
        day.setBorder(BorderFactory.createEmptyBorder(1, 2, 1, 2));
        day.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        day.setName(date.toString());
        day.setToolTipText(date + (holiday.isBlank() ? "" : " · " + holiday));
        boolean[] mousePressed = {false};
        day.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent event) {
                if (SwingUtilities.isLeftMouseButton(event)) mousePressed[0]=true;
            }
            @Override public void mouseReleased(MouseEvent event) { mousePressed[0]=false; }
        });
        day.addActionListener(e -> {
            // Mouse clicks are handled below; Space/accessibility activation still selects the day.
            if (!mousePressed[0]) {
                clearDateClick();
                onDateSelect.accept(date);
            }
        });
        dateRow.add(day, BorderLayout.WEST);
        List<Deadline> deadlines = model.deadlines(date);
        long scheduled = model.occurrences(date).stream().filter(o -> !o.canceled()).count() + deadlines.size();
        if (scheduled > 0) dateRow.add(Theme.label(scheduled + "개", 10, Theme.MUTED), BorderLayout.EAST);
        cell.add(dateRow, BorderLayout.NORTH);

        JPanel assignments = Theme.transparent(new GridLayout(0, 1, 0, 2));
        if (!holiday.isBlank()) {
            JLabel holidayLabel = Theme.label(holiday, 10, DUE_COLOR);
            holidayLabel.setToolTipText(holiday);
            assignments.add(holidayLabel);
        }
        List<PlannerModel.Occurrence> canceled = model.occurrences(date).stream().filter(PlannerModel.Occurrence::canceled).toList();
        if (!canceled.isEmpty()) {
            String state = canceled.get(0).event().category() == PlannerModel.Category.CLASS ? "휴강" : "제외";
            JLabel cancellation = Theme.label(state + " · " + canceled.get(0).event().title()
                    + (canceled.size() > 1 ? " 외 " + (canceled.size() - 1) + "개" : ""), 10, Theme.MUTED);
            cancellation.setToolTipText(String.join(", ", canceled.stream().map(o -> o.event().title()).toList()));
            assignments.add(cancellation);
        }
        StringBuilder detail = new StringBuilder("<html>").append(date);
        if (!holiday.isBlank()) detail.append("<br>").append(Theme.html(holiday));
        for (PlannerModel.Occurrence occurrence : canceled) {
            detail.append("<br>").append(occurrence.event().category() == PlannerModel.Category.CLASS ? "휴강 · " : "제외 · ")
                    .append(Theme.html(occurrence.event().title()));
        }
        for (Deadline deadline : deadlines) {
            detail.append("<br>").append(Theme.html(deadline.title())).append(" · ").append(status(deadline));
        }
        String tooltip = detail.append("</html>").toString();
        int visible = Math.max(0, Math.min(deadlines.size(), 2 - assignments.getComponentCount()));
        if (deadlines.size() > visible && visible > 0) visible--;
        for (Deadline deadline : deadlines.stream().limit(visible).toList()) {
            JButton item = new JButton(deadline.title());
            item.setFont(Theme.font(11, Font.PLAIN));
            item.setForeground(deadline.completed() ? Theme.MUTED : DUE_COLOR);
            item.setHorizontalAlignment(SwingConstants.LEFT);
            item.setContentAreaFilled(false);
            item.setBorder(BorderFactory.createEmptyBorder(0, 1, 0, 1));
            item.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            item.setToolTipText(deadline.title() + " · " + status(deadline));
            item.addActionListener(e -> onEditDeadline.accept(deadline));
            assignments.add(item);
        }
        if (deadlines.size() > visible && assignments.getComponentCount() < 2) {
            JButton more = new JButton(visible == 0 ? "할일 " + deadlines.size() + "개" : "+ " + (deadlines.size() - visible) + "개");
            more.setFont(Theme.font(10, Font.PLAIN));
            more.setForeground(deadlines.stream().anyMatch(deadline -> !deadline.completed()) ? DUE_COLOR : Theme.MUTED);
            more.setContentAreaFilled(false);
            more.setBorder(BorderFactory.createEmptyBorder());
            more.setToolTipText(tooltip);
            more.addActionListener(e -> onDateSelect.accept(date));
            assignments.add(more);
        }
        JPanel topAligned = Theme.transparent(new BorderLayout());
        topAligned.add(assignments, BorderLayout.NORTH);
        cell.add(topAligned, BorderLayout.CENTER);
        for (JComponent area : List.of(cell, day, dateRow, topAligned, assignments)) area.setToolTipText(tooltip);
        for (Component item : assignments.getComponents()) if (item instanceof JLabel label) label.setToolTipText(tooltip);
        MouseAdapter select = new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent event) {
                if (SwingUtilities.isLeftMouseButton(event)) {
                    dateClicked(date,event);
                }
            }
        };
        for (JComponent area : List.of(cell, day, dateRow, topAligned, assignments)) area.addMouseListener(select);
        for (Component item : assignments.getComponents()) if (item instanceof JLabel) item.addMouseListener(select);
        return cell;
    }

    private void clearDateClick() {
        lastClickDate=null;lastClickPoint=null;lastClickWhen=0;
    }

    private void dateClicked(LocalDate date, MouseEvent event) {
        Point point=SwingUtilities.convertPoint(event.getComponent(),event.getPoint(),this);
        Object setting=Toolkit.getDefaultToolkit().getDesktopProperty("awt.multiClickInterval");
        int interval=setting instanceof Number value?value.intValue():500;
        long elapsed=event.getWhen()-lastClickWhen;
        boolean nearby=lastClickPoint!=null && lastClickPoint.distance(point)<=6;
        boolean repeated=date.equals(lastClickDate) && nearby && elapsed>=0 && elapsed<=interval;
        boolean doubleClick=event.getClickCount()==2 || (event.getClickCount()==1 && repeated);
        if (doubleClick) clearDateClick();
        else {lastClickDate=date;lastClickWhen=event.getWhen();lastClickPoint=point;}
        onDateSelect.accept(date);
        SwingUtilities.invokeLater(this::focusSelectedDay);
        if (doubleClick) onAddDeadline.accept(date);
    }

    static String status(Deadline deadline) {
        DateTimeFormatter format = DateTimeFormatter.ofPattern("M월 d일");
        if (!deadline.completed()) return deadline.date().format(format) + " 미완료";
        return (deadline.completedOn() == null ? "" : deadline.completedOn().format(format) + " ") + "완료";
    }
}
