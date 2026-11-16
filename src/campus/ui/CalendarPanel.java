package campus.ui;

import java.awt.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.*;
import campus.model.PlannerModel.Deadline;
import campus.model.PlannerModel;

final class CalendarPanel extends JPanel {
    private static final Color DUE_COLOR = new Color(196, 54, 54);
    private final PlannerModel model;
    private final Consumer<LocalDate> onDateSelect;
    private final Consumer<LocalDate> onAddDeadline;
    private final Consumer<Deadline> onEditDeadline;
    private final JLabel monthTitle = Theme.heading(" ", 18);
    private final JPanel days = Theme.transparent(new GridLayout(0, 7, 4, 4));
    private LocalDate selectedDate = LocalDate.now();

    CalendarPanel(PlannerModel model, Consumer<LocalDate> onDateSelect,
                  Consumer<LocalDate> onAddDeadline, Consumer<Deadline> onEditDeadline) {
        super(new BorderLayout(0, 10));
        this.model = model;
        this.onDateSelect = onDateSelect;
        this.onAddDeadline = onAddDeadline;
        this.onEditDeadline = onEditDeadline;
        setOpaque(false);

        JPanel navigation = Theme.transparent(new BorderLayout(8, 0));
        JPanel month = Theme.transparent(new FlowLayout(FlowLayout.LEFT, 7, 0));
        month.add(Theme.button("‹", false, () -> changeMonth(-1)));
        month.add(monthTitle);
        month.add(Theme.button("›", false, () -> changeMonth(1)));
        navigation.add(month, BorderLayout.WEST);
        navigation.add(Theme.button("오늘", false, () -> onDateSelect.accept(LocalDate.now())), BorderLayout.EAST);
        add(navigation, BorderLayout.NORTH);

        JPanel calendar = Theme.transparent(new BorderLayout(0, 5));
        JPanel weekdays = Theme.transparent(new GridLayout(1, 7, 4, 0));
        for (String day : List.of("월", "화", "수", "목", "금", "토", "일")) {
            JLabel label = Theme.label(day, 12, day.equals("일") ? DUE_COLOR : Theme.MUTED);
            label.setHorizontalAlignment(SwingConstants.CENTER);
            weekdays.add(label);
        }
        calendar.add(weekdays, BorderLayout.NORTH);
        calendar.add(days, BorderLayout.CENTER);
        add(calendar, BorderLayout.CENTER);

        JPanel bottom = Theme.transparent(new BorderLayout());
        bottom.add(Theme.label("과제 마감일을 선택하거나 과제를 눌러 수정할 수 있습니다", 11, Theme.MUTED), BorderLayout.CENTER);
        bottom.add(Theme.button("+ 과제", true, () -> onAddDeadline.accept(selectedDate)), BorderLayout.EAST);
        add(bottom, BorderLayout.SOUTH);
    }

    void refresh(LocalDate date) {
        selectedDate = date;
        YearMonth month = YearMonth.from(date);
        monthTitle.setText(month.getYear() + "년 " + month.getMonthValue() + "월");
        LocalDate first = month.atDay(1).with(DayOfWeek.MONDAY);
        days.removeAll();
        for (int index = 0; index < 42; index++) days.add(dayCell(first.plusDays(index), month));
        revalidate();
        repaint();
    }

    private void changeMonth(int amount) {
        YearMonth target = YearMonth.from(selectedDate).plusMonths(amount);
        onDateSelect.accept(target.atDay(Math.min(selectedDate.getDayOfMonth(), target.lengthOfMonth())));
    }

    private JPanel dayCell(LocalDate date, YearMonth month) {
        JPanel cell = new JPanel(new BorderLayout(0, 3));
        cell.setBackground(date.equals(selectedDate) ? Theme.PALE_BLUE : Color.WHITE);
        cell.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(date.equals(selectedDate) ? Theme.BLUE : Theme.LINE),
                BorderFactory.createEmptyBorder(4, 4, 4, 4)));
        cell.setPreferredSize(new Dimension(80, 76));
        JPanel dateRow = Theme.transparent(new BorderLayout());
        JButton day = new JButton(Integer.toString(date.getDayOfMonth()));
        day.setFont(Theme.font(12, date.equals(LocalDate.now()) ? Font.BOLD : Font.PLAIN));
        day.setForeground(YearMonth.from(date).equals(month) ? Theme.INK : Theme.MUTED);
        day.setContentAreaFilled(false);
        day.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        day.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        day.setToolTipText(date.toString());
        day.addActionListener(e -> onDateSelect.accept(date));
        dateRow.add(day, BorderLayout.WEST);
        long scheduled = model.occurrences(date).stream().filter(o -> !o.canceled()).count();
        if (scheduled > 0) dateRow.add(Theme.label(scheduled + "개", 10, Theme.MUTED), BorderLayout.EAST);
        cell.add(dateRow, BorderLayout.NORTH);

        JPanel assignments = Theme.transparent(new GridLayout(0, 1, 0, 2));
        List<Deadline> deadlines = model.deadlines(date);
        for (Deadline deadline : deadlines.stream().limit(2).toList()) {
            JButton item = new JButton(deadline.title());
            item.setFont(Theme.font(11, Font.PLAIN));
            item.setForeground(deadline.completed() ? Theme.MUTED : DUE_COLOR);
            item.setHorizontalAlignment(SwingConstants.LEFT);
            item.setContentAreaFilled(false);
            item.setBorder(BorderFactory.createEmptyBorder(2, 1, 2, 1));
            item.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            item.setToolTipText(deadline.title() + " · " + status(deadline));
            item.addActionListener(e -> onEditDeadline.accept(deadline));
            assignments.add(item);
        }
        if (deadlines.size() > 2) {
            JButton more = new JButton("+" + (deadlines.size() - 2));
            more.setFont(Theme.font(10, Font.PLAIN));
            more.setForeground(Theme.BLUE);
            more.setContentAreaFilled(false);
            more.setBorder(BorderFactory.createEmptyBorder());
            more.addActionListener(e -> onDateSelect.accept(date));
            assignments.add(more);
        }
        JPanel topAligned = Theme.transparent(new BorderLayout());
        topAligned.add(assignments, BorderLayout.NORTH);
        cell.add(topAligned, BorderLayout.CENTER);
        return cell;
    }

    static String status(Deadline deadline) {
        DateTimeFormatter format = DateTimeFormatter.ofPattern("M월 d일");
        if (!deadline.completed()) return deadline.date().format(format) + " 마감";
        return (deadline.completedOn() == null ? "" : deadline.completedOn().format(format) + " ") + "제출완료";
    }
}
