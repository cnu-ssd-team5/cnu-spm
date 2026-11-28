package campus.ui;

import campus.model.PlannerModel;
import campus.model.PlannerModel.Deadline;
import campus.model.PlannerModel.TaskPriority;
import campus.weather.WeatherBadge;
import campus.weather.WeatherForecast;
import java.awt.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** The selected day's checklist, deadlines, and memo. */
final class DailyPlannerPanel extends JPanel {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy년 M월 d일 EEEE", Locale.KOREAN);
    private static final DateTimeFormatter DUE_DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd");
    private static final Color ACTIVE_TEXT = new Color(0x17191C);
    private static final Color COMPLETE_TEXT = new Color(0x707B89);
    private final MainFrame frame;
    private PlannerModel model;
    private final JLabel dateLabel = Theme.heading(" ", 16);
    private final JLabel weatherLabel = Theme.label("", 11, Theme.MUTED);
    private final JLabel taskCount = Theme.label(" ", 12, Theme.MUTED);
    private final JLabel deadlineCount = Theme.label(" ", 11, Theme.MUTED);
    private final JPanel deadlineRows = Theme.transparent(new GridLayout(0, 1, 0, 8));
    private final JButton addTask;
    private final JButton addDeadline = Theme.button("+ 추가", false, this::addDeadline);

    private final JTextArea memo = new JTextArea();
    private final JPanel tasks = new JPanel(new GridBagLayout()) {
        @Override public Dimension getPreferredSize() {
            return new Dimension(0, super.getPreferredSize().height);
        }
    };
    private LocalDate date;
    private boolean refreshing;

    DailyPlannerPanel(MainFrame frame, PlannerModel model) {
        super(new BorderLayout(0, 16));
        this.frame = frame;
        this.model = model;
        setOpaque(false);

        JPanel header = Theme.transparent(new FlowLayout(FlowLayout.LEFT, 8, 0));
        header.add(dateLabel);
        header.add(weatherLabel);
        weatherLabel.setVisible(false);
        add(header, BorderLayout.NORTH);

        JPanel columns = Theme.transparent(new GridLayout(1, 2, 12, 0));
        JPanel checklist = Theme.card(new BorderLayout(0, 12));
        checklist.setBackground(Theme.BACKGROUND);
        JPanel taskHeader = Theme.transparent(new BorderLayout(4, 0));
        JPanel taskHeading = Theme.transparent(new GridLayout(0, 1, 0, 4));
        taskHeading.add(Theme.heading("체크리스트", 15));
        taskHeading.add(taskCount);
        taskHeader.add(taskHeading, BorderLayout.CENTER);
        addTask = Theme.button("+ 추가", false, () -> frame.editDeadline(null, date, false));
        addTask.getAccessibleContext().setAccessibleName("체크리스트 추가");
        taskHeader.add(addTask, BorderLayout.EAST);
        checklist.add(taskHeader, BorderLayout.NORTH);
        tasks.setOpaque(false);
        checklist.add(Theme.scroll(tasks), BorderLayout.CENTER);
        columns.add(checklist);

        JPanel notebook = Theme.card(new BorderLayout(0, 12));
        notebook.setBackground(Theme.BACKGROUND);
        notebook.add(Theme.heading("메모", 15), BorderLayout.NORTH);
        Theme.field(memo);
        memo.setLineWrap(true);
        memo.setWrapStyleWord(true);
        memo.getAccessibleContext().setAccessibleName("날짜별 메모");
        memo.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { saveMemo(); }
            @Override public void removeUpdate(DocumentEvent e) { saveMemo(); }
            @Override public void changedUpdate(DocumentEvent e) { saveMemo(); }
        });
        notebook.add(Theme.scroll(memo), BorderLayout.CENTER);
        JPanel right = Theme.transparent(new GridLayout(2, 1, 0, 12));
        right.add(deadlinePanel());
        right.add(notebook);
        columns.add(right);
        add(columns, BorderLayout.CENTER);
    }

    private void addDeadline() { frame.editDeadline(null, date, true); }

    void setScheduleContext(PlannerModel model) {
        this.model = model;
    }

    void refresh(LocalDate selectedDate) {
        refresh(selectedDate, null);
    }

    void refresh(LocalDate selectedDate, WeatherForecast.Day forecast) {
        date = selectedDate;
        dateLabel.setText(date.format(DATE));
        showWeather(forecast);
        String saved = model.dayNote(date);
        if (!memo.getText().equals(saved)) {
            refreshing = true;
            try { memo.setText(saved); }
            finally { refreshing = false; }
        }

        var checklist = model.plannerTasks(date).stream().filter(task -> !task.deadlineLinked()).toList();
        long completed = checklist.stream().filter(Deadline::completed).count();
        taskCount.setText(completed + " / " + checklist.size() + " 완료");
        tasks.removeAll();
        GridBagConstraints row = new GridBagConstraints();
        row.gridx = 0;
        row.gridy = 0;
        row.weightx = 1;
        row.fill = GridBagConstraints.HORIZONTAL;
        row.insets = new Insets(0, 0, 8, 0);
        if (checklist.isEmpty()) {
            tasks.add(Theme.label("등록된 항목이 없습니다.", 12, Theme.MUTED), row);
            row.gridy++;
        }
        for (Deadline task : checklist) {
            tasks.add(taskRow(task), row);
            row.gridy++;
        }
        row.weighty = 1;
        tasks.add(Theme.transparent(new BorderLayout()), row);
        tasks.revalidate();
        tasks.repaint();
        refreshDeadlines();
    }

    private JPanel deadlinePanel() {
        JPanel panel = Theme.card(new BorderLayout(0, 8));
        panel.setBackground(Theme.BACKGROUND);
        JPanel heading = Theme.transparent(new BorderLayout(4, 0));
        heading.add(Theme.heading("과제·할일 마감", 15), BorderLayout.CENTER);
        JPanel tools = Theme.transparent(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        tools.add(deadlineCount);
        addDeadline.getAccessibleContext().setAccessibleName("과제·할일 마감 추가");
        tools.add(addDeadline);
        heading.add(tools, BorderLayout.EAST);
        panel.add(heading, BorderLayout.NORTH);
        JPanel rows = new JPanel(new BorderLayout()) {
            @Override public Dimension getPreferredSize() {
                return new Dimension(0, super.getPreferredSize().height);
            }
        };
        rows.setOpaque(false);
        rows.add(deadlineRows, BorderLayout.NORTH);
        panel.add(Theme.scroll(rows), BorderLayout.CENTER);
        return panel;
    }

    private void refreshDeadlines() {
        var visible = model.deadlines().stream()
                .filter(deadline -> !date.isBefore(deadline.plannerDate()) && !date.isAfter(deadline.date()))
                .toList();
        deadlineRows.removeAll();
        int pendingCount = 0;
        for (Deadline deadline : visible) {
            if (deadline.completed()) continue;
            deadlineRows.add(deadlineCard(deadline));
            pendingCount++;
        }
        deadlineCount.setText("미완료 " + pendingCount + "개");
        for (Deadline deadline : visible) {
            if (deadline.completed() && deadline.date().equals(date)) deadlineRows.add(deadlineCard(deadline));
        }
        if (deadlineRows.getComponentCount() == 0) {
            deadlineRows.add(Theme.label("표시할 마감이 없습니다.", 12, Theme.MUTED));
        }
        deadlineRows.revalidate();
        deadlineRows.repaint();
    }

    private JPanel deadlineCard(Deadline deadline) {
        long days = ChronoUnit.DAYS.between(date, deadline.date());
        String status = deadline.completed() ? "완료" : days == 0 ? "D-day · 마감일" : "D-" + days + " · " + days + "일 남음";
        String due = deadline.date().format(DUE_DATE);
        String title = Theme.html(deadline.title());
        JButton item = Theme.button("<html>" + title + "<br><small>" + due + " · " + status + "</small></html>",
                false, () -> frame.editDeadline(deadline, deadline.plannerDate(), true));
        item.setName("deadline:" + deadline.id());
        item.setHorizontalAlignment(SwingConstants.LEFT);
        item.setToolTipText("<html>" + title + " · " + due + " · " + status + "</html>");
        item.setForeground(deadline.completed() ? Theme.MUTED : days == 0 ? Theme.TODAY_TEXT : Theme.INK);
        JCheckBox complete = Theme.checkBox("",deadline.completed());
        complete.setSelected(deadline.completed());

        complete.setOpaque(false);
        complete.setToolTipText("완료 상태 변경");
        complete.getAccessibleContext().setAccessibleName(deadline.title() + " 완료");
        complete.addActionListener(e -> frame.toggleDailyTask(deadline));
        JPanel row = Theme.transparent(new BorderLayout(4, 0));
        row.add(complete, BorderLayout.WEST);
        row.add(item, BorderLayout.CENTER);
        return row;
    }

    void showWeather(WeatherForecast.Day forecast) {
        weatherLabel.setIcon(forecast == null ? null : new WeatherBadge(forecast, weatherLabel.getFontMetrics(weatherLabel.getFont())));
        String description = forecast == null ? null : forecast.description();
        weatherLabel.setToolTipText(description);
        weatherLabel.getAccessibleContext().setAccessibleName(description);
        weatherLabel.setVisible(forecast != null);
    }

    void focusAddTask() {
        addTask.requestFocusInWindow();
    }

    private JPanel taskRow(Deadline task) {
        JPanel row = new JPanel(new BorderLayout(4, 8)) {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int width = getWidth() - 2;
                int height = getHeight() - 3;
                g.setColor(new Color(40, 52, 72, 16));
                g.fillRoundRect(1, 2, width, height, 12, 12);
                g.setColor(task.completed() ? new Color(0xF7F8FA) : Color.WHITE);
                g.fillRoundRect(0, 0, width, height, 12, 12);
                g.setColor(Theme.LINE);
                g.drawRoundRect(0, 0, width - 1, height - 1, 12, 12);
                g.dispose();
            }
        };
        row.setOpaque(false);
        row.setBorder(BorderFactory.createEmptyBorder(8, 8, 12, 8));
        JCheckBox checkbox = Theme.checkBox("",task.completed());
        checkbox.putClientProperty("html.disable", true);
        checkbox.setText(task.title());
        checkbox.setSelected(task.completed());

        checkbox.setName("daily-task:" + task.id());
        checkbox.setToolTipText("<html>" + Theme.html(task.title()) + "</html>");
        checkbox.setFont(Theme.font(13, Font.BOLD));
        checkbox.setForeground(task.completed() ? COMPLETE_TEXT : ACTIVE_TEXT);
        checkbox.setOpaque(false);
        checkbox.addActionListener(e -> frame.toggleDailyTask(task));
        row.add(checkbox, BorderLayout.NORTH);
        JPanel actions = Theme.transparent(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        JButton edit = Theme.button("편집", false, () -> frame.editDeadline(task, task.plannerDate(), false));
        edit.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        edit.getAccessibleContext().setAccessibleName(task.title() + " 편집");
        JButton delete = Theme.deleteButton("삭제", () -> frame.deleteDailyTask(task));
        delete.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        delete.getAccessibleContext().setAccessibleName(task.title() + " 삭제");
        actions.add(edit);
        actions.add(delete);
        JPanel details = Theme.transparent(new BorderLayout(0, 4));
        JPanel meta = Theme.transparent(new BorderLayout(4, 0));
        meta.add(priorityBadge(task.priority(), task.completed()), BorderLayout.WEST);
        meta.add(actions, BorderLayout.EAST);
        details.add(meta, BorderLayout.NORTH);
        if (!task.note().isBlank()) {
            String escaped = Theme.html("#" + task.note().strip()).replaceAll("\\R", "<br>");
            JLabel description = Theme.label("<html><div style='width:170px'>" + escaped + "</div></html>", 12,
                    task.completed() ? COMPLETE_TEXT : ACTIVE_TEXT);
            description.setToolTipText("<html>" + escaped + "</html>");
            details.add(description, BorderLayout.CENTER);
        }
        row.add(details, BorderLayout.CENTER);
        return row;
    }

    private static JLabel priorityBadge(TaskPriority priority, boolean completed) {
        Color marker = completed ? new Color(0xEDF0F3) : switch (priority) {
            case HIGH -> new Color(0xFFD5D9);
            case NORMAL -> new Color(0xFFE0B0);
            case LOW -> new Color(0xD6EFAE);
        };
        JLabel badge = new JLabel("우선순위 " + priority) {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(marker);
                g.fillRoundRect(0, 3, getWidth(), getHeight() - 5, 7, 7);
                g.dispose();
                super.paintComponent(graphics);
            }
        };
        badge.setFont(Theme.font(11, Font.BOLD));
        badge.setForeground(completed ? COMPLETE_TEXT : ACTIVE_TEXT);
        badge.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        return badge;
    }

    private void saveMemo() {
        if (refreshing || date == null) return;
        model.setDayNote(date, memo.getText());
        frame.noteChanged();
    }
}
