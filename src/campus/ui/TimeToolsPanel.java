package campus.ui;

import campus.calendar.PlannerTime;
import campus.model.PlannerModel;
import campus.service.CountdownTimer;
import java.awt.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import javax.swing.*;

/** Owns the header clock and timer state shared with the tools window. */
public final class TimeToolsPanel extends JPanel implements AutoCloseable {
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");
    private final CountdownTimer countdown;
    private PlannerModel model;
    private final Consumer<String> notify;
    private final JLabel clock = Theme.label(" ", 15, Theme.INK);
    private final JLabel remaining = Theme.heading("25:00", 26);
    private final JSpinner minutes = new JSpinner(new SpinnerNumberModel(25, 1, 1440, 1));
    private final JPanel timerPanel = Theme.card(new GridBagLayout());
    private final JButton start, pause, reset;
    private final javax.swing.Timer ticker;
    private LocalDate mainDate = PlannerTime.today();
    private ClockToolWindow window;
    private boolean active;
    private boolean closed;

    public TimeToolsPanel(Consumer<String> notify) { this(new PlannerModel(), notify); }

    TimeToolsPanel(PlannerModel model, Consumer<String> notify) {
        this(new CountdownTimer(1500), model, notify);
    }

    TimeToolsPanel(CountdownTimer countdown, Consumer<String> notify) {
        this(countdown, new PlannerModel(), notify);
    }

    private TimeToolsPanel(CountdownTimer countdown, PlannerModel model, Consumer<String> notify) {
        super(new FlowLayout(FlowLayout.LEFT, 5, 4));
        setOpaque(false);
        this.countdown = countdown;
        this.model = model;
        this.notify = notify;
        clock.setToolTipText("현재 한국 시각");
        clock.setHorizontalAlignment(SwingConstants.CENTER);
        clock.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.LINE, 1, true),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        Insets padding = clock.getInsets();
        FontMetrics clockMetrics = clock.getFontMetrics(clock.getFont());
        int digitWidth = 0;
        for (char digit = '0'; digit <= '9'; digit++)
            digitWidth = Math.max(digitWidth, clockMetrics.charWidth(digit));
        clock.setPreferredSize(new Dimension(digitWidth * 6 + clockMetrics.charWidth(':') * 2
                + padding.left + padding.right, clockMetrics.getHeight() + padding.top + padding.bottom));
        JButton tools = Theme.button("", false, this::openTools);
        tools.setIcon(clockIcon());
        tools.setBorder(BorderFactory.createEmptyBorder(5, 6, 5, 6));
        tools.setToolTipText("시계 · 타이머 · 스톱워치");
        tools.getAccessibleContext().setAccessibleName(tools.getToolTipText());
        add(tools);
        add(clock);

        start = Theme.button("시작", false, this::startTimer);
        pause = Theme.button("일시정지", false, () -> { countdown.pause(); tick(); });
        reset = Theme.button("초기화", false, () -> { resetDuration(); tick(); });
        buildTimerPanel();
        minutes.addChangeListener(e -> {
            if (!active) {
                resetDuration();
                tick();
            }
        });
        ticker = new javax.swing.Timer(100, e -> tick());
        tick();
        ticker.start();
    }

    private void buildTimerPanel() {
        GridBagConstraints layout = new GridBagConstraints();
        layout.gridx = 0;
        layout.insets = new Insets(4, 0, 4, 0);
        timerPanel.add(remaining, layout);
        JPanel input = Theme.transparent(new FlowLayout(FlowLayout.CENTER, 6, 0));
        JLabel unit = Theme.label("분", 12, Theme.MUTED);
        unit.setLabelFor(minutes);
        minutes.getAccessibleContext().setAccessibleName("타이머 시간(분)");
        input.add(minutes);
        input.add(unit);
        layout.gridy = 1;
        timerPanel.add(input, layout);
        JPanel actions = Theme.transparent(new FlowLayout(FlowLayout.CENTER, 6, 0));
        for (JButton button : List.of(start, pause, reset)) actions.add(button);
        layout.gridy = 2;
        timerPanel.add(actions, layout);
    }

    private void startTimer() {
        try {
            minutes.commitEdit();
            if (!active) resetDuration();
            countdown.start();
            active = true;
            tick();
        } catch (java.text.ParseException | IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(timerPanel, "1~1440분 사이의 시간을 입력해 주세요.", "타이머", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void resetDuration() {
        countdown.setDurationSeconds(((Number) minutes.getValue()).longValue() * 60);
        active = false;
    }

    private void openTools() {
        if (closed) return;
        if (window == null) window = new ClockToolWindow(SwingUtilities.getWindowAncestor(this), model, timerPanel, mainDate);
        window.tick();
        window.setVisible(true);
        window.toFront();
    }

    void setScheduleModel(PlannerModel model) {
        this.model = model;
        if (window != null) window.setScheduleModel(model);
    }

    void refreshSchedule(LocalDate selectedDate) {
        boolean dateChanged = !mainDate.equals(selectedDate);
        mainDate = selectedDate;
        if (window != null) {
            if (dateChanged) window.selectDate(mainDate);
            else window.refreshSchedule();
        }
    }

    void tick() {
        if (closed) return;
        clock.setText(PlannerTime.now().format(CLOCK));
        long seconds = countdown.remainingSeconds();
        remaining.setText(String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60));
        boolean running = countdown.isRunning();
        start.setEnabled(!running);
        pause.setEnabled(running);
        minutes.setEnabled(!active || seconds == 0);
        start.setText(active && seconds > 0 ? "계속" : "시작");
        if (countdown.pollFinished()) {
            active = false;
            minutes.setEnabled(true);
            notify.accept("설정한 타이머가 끝났습니다.");
        }
        if (window != null && window.isShowing()) window.tick();
    }

    private static Icon clockIcon() {
        return new Icon() {
            @Override public int getIconWidth() { return 16; }
            @Override public int getIconHeight() { return 16; }

            @Override public void paintIcon(Component c, Graphics graphics, int x, int y) {
                Graphics2D g = (Graphics2D) graphics.create();
                try {
                    g.translate(x, y);
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setColor(Theme.BLUE);
                    g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g.drawOval(1, 1, 13, 13);
                    g.drawLine(8, 4, 8, 8);
                    g.drawLine(8, 8, 11, 9);
                } finally {
                    g.dispose();
                }
            }
        };
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        ticker.stop();
        countdown.reset();
        if (window != null) window.dispose();
    }
}
