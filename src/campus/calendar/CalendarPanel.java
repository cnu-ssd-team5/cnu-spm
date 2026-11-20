package campus.calendar;
import campus.model.PlannerModel;
import campus.ui.Theme;
import campus.weather.WeatherForecast.Day;
import campus.weather.WeatherBadge;
import java.util.Map;


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
    private final Consumer<LocalDate> onOpenPlanner;
    private final Consumer<Semester> onSemesterSelect;
    private final JTextField year = new JTextField(6);
    private final JPopupMenu monthPicker = new JPopupMenu();
    private final JButton monthSelector;
    private final JButton[] monthButtons = new JButton[12];
    private final JLabel yearError = Theme.label(" ", 11, DUE_COLOR);
    private final JComboBox<Semester.Season> season = new JComboBox<>(Semester.Season.values());
    private final JPanel days = new JPanel(new GridLayout(0, 7, 3, 3));
    private final JLabel holidayRange = Theme.label(" ", 11, Theme.MUTED);
    private final JLabel weatherStatus = Theme.label(" ", 11, Theme.MUTED);
    private Map<LocalDate, Day> weather = Map.of();
    private LocalDate selectedDate = PlannerTime.today();
    private Semester selectedSemester = Semester.forDate(selectedDate);
    private boolean refreshing;
    private LocalDate lastClickDate;
    private long lastClickWhen;
    private Point lastClickPoint;

    public CalendarPanel(PlannerModel model, Consumer<LocalDate> onDateSelect,
                         Consumer<LocalDate> onOpenPlanner) {
        this(model, onDateSelect, onOpenPlanner, term -> onDateSelect.accept(term.start()));
    }

    public CalendarPanel(PlannerModel model, Consumer<LocalDate> onDateSelect,
                  Consumer<LocalDate> onOpenPlanner,
                  Consumer<Semester> onSemesterSelect) {
        super(new BorderLayout(0, 10));
        this.model = model;
        this.onDateSelect = onDateSelect;
        this.onOpenPlanner = onOpenPlanner;
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
        monthControls.add(monthArrow("‹", "이전 달", -1));
        monthSelector = Theme.button(monthCaption(selectedDate), false, this::openMonthPicker);
        monthSelector.setName("calendarMonth");
        monthSelector.setToolTipText("연도와 월 선택");
        monthControls.add(monthSelector);
        monthControls.add(monthArrow("›", "다음 달", 1));
        buildMonthPicker();
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
        days.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke("ENTER"), "openPlanner");
        days.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("ENTER"), "openPlanner");
        days.getActionMap().put("openPlanner", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) {
                openPlanner(selectedDate);
            }
        });
        calendar.add(days, BorderLayout.CENTER);
        add(calendar, BorderLayout.CENTER);

        JPanel bottom = Theme.transparent(new BorderLayout());
        holidayRange.setToolTipText("확인된 공휴일만 표시하며, 공휴일이라고 일정을 자동으로 휴강 처리하지 않습니다.");
        bottom.add(holidayRange, BorderLayout.WEST);
        bottom.add(Theme.label("날짜 더블클릭 또는 Enter로 플래너 열기", 11, Theme.MUTED), BorderLayout.EAST);
        JPanel footer = Theme.transparent(new BorderLayout(0, 3));
        footer.add(bottom, BorderLayout.NORTH);
        footer.add(weatherStatus, BorderLayout.SOUTH);
        weatherStatus.setVisible(false);
        add(footer, BorderLayout.SOUTH);
    }

    public void showWeather(Map<LocalDate, Day> forecast, String status) {
        weatherStatus.setText(status);
        weatherStatus.setVisible(true);
        if (!weather.equals(forecast)) {
            weather = forecast;
            refresh(selectedDate, selectedSemester);
        }
    }

    public void refresh(LocalDate date) { refresh(date, Semester.forDate(date)); }

    public void refresh(LocalDate date, Semester semester) {
        Component focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        boolean keepFocus = focus != null && SwingUtilities.isDescendingFrom(focus, days);
        selectedDate = date;
        selectedSemester = semester;
        refreshing = true;
        try {
            monthSelector.setText(monthCaption(date));
            season.setSelectedItem(semester.season());
            season.repaint();
        } finally {
            refreshing = false;
        }
        YearMonth displayMonth = YearMonth.from(date);
        holidayRange.setText(KoreaHolidays.supports(date.getYear()) ? "공휴일: 기본 목록 (2025~2027)"
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

    private JButton monthArrow(String text, String label, int offset) {
        JButton button = Theme.button(text, false, () -> changeMonth(offset));
        button.setToolTipText(label);
        button.getAccessibleContext().setAccessibleName(label);
        return button;
    }

    private static String monthCaption(LocalDate date) {
        return date.getYear() + "년 " + date.getMonthValue() + "월";
    }

    private void buildMonthPicker() {
        JPanel picker = Theme.card(new BorderLayout(0, 10));
        picker.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JPanel yearRow = Theme.transparent(new BorderLayout(8, 0));
        JLabel yearLabel = Theme.heading("연도", 12);
        yearLabel.setLabelFor(year);
        year.setName("calendarYear");
        year.setHorizontalAlignment(JTextField.CENTER);
        Theme.field(year);
        year.addActionListener(e -> selectMonth(selectedDate.getMonthValue()));
        yearRow.add(yearLabel, BorderLayout.WEST);
        yearRow.add(year, BorderLayout.CENTER);
        picker.add(yearRow, BorderLayout.NORTH);

        JPanel months = Theme.transparent(new GridLayout(4, 3, 4, 4));
        for (int index = 0; index < monthButtons.length; index++) {
            int month = index + 1;
            JButton button = Theme.button(month + "월", false, () -> selectMonth(month));
            button.setBorder(BorderFactory.createEmptyBorder(6, 9, 6, 9));
            monthButtons[index] = button;
            months.add(button);
        }
        picker.add(months, BorderLayout.CENTER);
        picker.add(yearError, BorderLayout.SOUTH);
        monthPicker.setBorder(BorderFactory.createLineBorder(Theme.LINE));
        monthPicker.add(picker);
    }

    private void openMonthPicker() {
        year.setText(Integer.toString(selectedDate.getYear()));
        yearError.setText(" ");
        for (int index = 0; index < monthButtons.length; index++) {
            boolean selected = index + 1 == selectedDate.getMonthValue();
            monthButtons[index].putClientProperty("calendar.fill", selected ? Theme.PALE_BLUE : null);
            monthButtons[index].setForeground(selected ? Theme.BLUE : Theme.INK);
        }
        monthPicker.show(monthSelector, 0, monthSelector.getHeight() + 4);
        SwingUtilities.invokeLater(() -> {
            year.requestFocusInWindow();
            year.selectAll();
        });
    }

    private void selectMonth(int month) {
        int selectedYear;
        try {
            selectedYear = Integer.parseInt(year.getText().strip());
            if (selectedYear < 1900 || selectedYear > 9999) throw new NumberFormatException();
        } catch (NumberFormatException error) {
            yearError.setText("1900~9999년을 입력하세요.");
            year.requestFocusInWindow();
            year.selectAll();
            return;
        }
        monthPicker.setVisible(false);
        changeMonth(YearMonth.of(selectedYear, month));
    }

    private void changeMonth(int amount) {
        changeMonth(YearMonth.from(selectedDate).plusMonths(amount));
    }

    private void changeMonth(YearMonth target) {
        clearDateClick();
        onDateSelect.accept(target.atDay(Math.min(selectedDate.getDayOfMonth(), target.lengthOfMonth())));
    }

    private JPanel dayCell(LocalDate date, YearMonth month) {
        String holiday = KoreaHolidays.name(date);
        Day forecast = weather.get(date);
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
        day.getAccessibleContext().setAccessibleName(date.toString());
        day.getAccessibleContext().setAccessibleDescription("더블클릭 또는 Enter로 플래너 열기");
        day.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("ENTER"), "openPlanner");
        day.getActionMap().put("openPlanner", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) { openPlanner(date); }
        });
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
        if (forecast != null) detail.append("<br>").append(forecast.description());
        for (PlannerModel.Occurrence occurrence : canceled) {
            detail.append("<br>").append(occurrence.event().category() == PlannerModel.Category.CLASS ? "휴강 · " : "제외 · ")
                    .append(Theme.html(occurrence.event().title()));
        }
        for (Deadline deadline : deadlines) {
            detail.append("<br>").append(Theme.html(deadline.title())).append(" · ").append(status(deadline));
        }
        detail.append("<br>더블클릭 또는 Enter로 플래너 열기");
        String tooltip = detail.append("</html>").toString();
        int visible = Math.max(0, Math.min(deadlines.size(), 2 - assignments.getComponentCount()));
        if (deadlines.size() > visible && visible > 0) visible--;
        for (Deadline deadline : deadlines.stream().limit(visible).toList()) {
            JLabel item = Theme.label("", 11, deadline.completed() ? Theme.MUTED : DUE_COLOR);
            item.putClientProperty("html.disable", true);
            item.setText(deadline.title());
            assignments.add(item);
        }
        if (deadlines.size() > visible && assignments.getComponentCount() < 2) {
            String text = visible == 0 ? "마감 " + deadlines.size() + "개" : "+ " + (deadlines.size() - visible) + "개";
            JLabel more = Theme.label(text, 10, deadlines.stream().anyMatch(deadline -> !deadline.completed()) ? DUE_COLOR : Theme.MUTED);
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
        if (forecast != null) {
            JLabel badge = Theme.label("", 11, Theme.INK);
            badge.setIcon(new WeatherBadge(forecast, badge.getFontMetrics(badge.getFont())));
            badge.getAccessibleContext().setAccessibleName(forecast.description());
            badge.setToolTipText(tooltip);
            badge.addMouseListener(select);
            cell.add(badge, BorderLayout.SOUTH);
        }
        return cell;
    }

    private void clearDateClick() {
        lastClickDate=null;lastClickPoint=null;lastClickWhen=0;
    }

    private void openPlanner(LocalDate date) {
        clearDateClick();
        onOpenPlanner.accept(date);
    }

    private void dateClicked(LocalDate date, MouseEvent event) {
        Point point=SwingUtilities.convertPoint(event.getComponent(),event.getPoint(),this);
        Object setting=Toolkit.getDefaultToolkit().getDesktopProperty("awt.multiClickInterval");
        int interval=setting instanceof Number value?value.intValue():500;
        long elapsed=event.getWhen()-lastClickWhen;
        boolean nearby=lastClickPoint!=null && lastClickPoint.distance(point)<=6;
        boolean repeated=date.equals(lastClickDate) && nearby && elapsed>=0 && elapsed<=interval;
        boolean doubleClick=event.getClickCount()==2 || (event.getClickCount()==1 && repeated);
        if (doubleClick) {
            openPlanner(date);
            return;
        }
        lastClickDate=date;lastClickWhen=event.getWhen();lastClickPoint=point;
        onDateSelect.accept(date);
        SwingUtilities.invokeLater(this::focusSelectedDay);
    }

    static String status(Deadline deadline) {
        DateTimeFormatter format = DateTimeFormatter.ofPattern("M월 d일");
        if (!deadline.completed()) return deadline.date().format(format) + " 미완료";
        return (deadline.completedOn() == null ? "" : deadline.completedOn().format(format) + " ") + "완료";
    }
}
