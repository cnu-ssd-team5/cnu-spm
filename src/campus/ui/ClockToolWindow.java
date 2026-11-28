package campus.ui;

import campus.calendar.PlannerTime;
import campus.model.PlannerModel;
import campus.service.Stopwatch;
import java.awt.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import javax.swing.*;
import javax.swing.plaf.basic.BasicTabbedPaneUI;

/** Modeless tools window. Schedule browsing is separate from the live clock. */
final class ClockToolWindow extends JDialog {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd (E)", Locale.KOREAN);
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("a h:mm:ss", Locale.KOREAN);
    private PlannerModel model;
    private final ScheduleClock face = new ScheduleClock();
    private final JLabel clock = Theme.heading(" ", 26);
    private final JLabel dateLabel = Theme.label(" ", 12, Theme.INK);
    private final JToggleButton morning = new JToggleButton("오전");
    private final JToggleButton afternoon = new JToggleButton("오후");
    private final Stopwatch stopwatch = new Stopwatch();
    private final JLabel elapsed = Theme.heading("00:00:00.0", 26);
    private final JButton start = Theme.button("시작", false, () -> { stopwatch.start(); tick(); });
    private final JButton pause = Theme.button("일시정지", false, () -> { stopwatch.pause(); tick(); });
    private LocalDate date;

    ClockToolWindow(Window owner, PlannerModel model, JPanel timerPanel, LocalDate selectedDate) {
        super(owner, "시계 · 타이머", ModalityType.MODELESS);
        this.model = model;
        setDefaultCloseOperation(HIDE_ON_CLOSE);
        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(Theme.font(13, Font.BOLD));
        tabs.setUI(new BasicTabbedPaneUI() {
            @Override protected int calculateTabHeight(int placement, int index, int fontHeight) {
                return Math.max(34, super.calculateTabHeight(placement, index, fontHeight));
            }

            @Override protected Insets getSelectedTabPadInsets(int placement) {
                return new Insets(0, 0, 0, 0);
            }

            @Override protected Insets getTabAreaInsets(int placement) {
                return new Insets(2, 0, 0, 0);
            }

            @Override protected int calculateTabWidth(int placement, int index, FontMetrics metrics) {
                if (tabPane.getWidth() <= 0) return super.calculateTabWidth(placement, index, metrics);
                Insets pane = tabPane.getInsets();
                Insets area = getTabAreaInsets(placement);
                int width = tabPane.getWidth() - pane.left - pane.right - area.left - area.right;
                if (width <= 0) return super.calculateTabWidth(placement, index, metrics);
                int count = tabPane.getTabCount();
                return width * (index + 1) / count - width * index / count;
            }
        });
        tabs.addTab("시계", clockTab());
        tabs.addTab("타이머", timerPanel);
        tabs.addTab("스톱워치", stopwatchTab());
        setContentPane(tabs);
        getRootPane().registerKeyboardAction(e -> setVisible(false), KeyStroke.getKeyStroke("ESCAPE"),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        followCurrentPeriod();
        selectDate(selectedDate);
        tick();
        setMinimumSize(new Dimension(340, 390));
        pack();
        setLocationRelativeTo(owner);
    }

    private JPanel clockTab() {
        JPanel contents = Theme.card(new BorderLayout(0, 8));
        clock.setHorizontalAlignment(SwingConstants.CENTER);
        clock.setToolTipText("현재 한국 시각");
        contents.add(clock, BorderLayout.NORTH);
        contents.add(face, BorderLayout.CENTER);
        JPanel controls = Theme.transparent(new GridLayout(2, 1, 0, 4));
        JPanel navigator = Theme.transparent(new FlowLayout(FlowLayout.CENTER, 6, 0));
        navigator.add(Theme.button("‹", false, () -> selectDate(date.minusDays(1))));
        navigator.add(dateLabel);
        navigator.add(Theme.button("›", false, () -> selectDate(date.plusDays(1))));
        controls.add(navigator);
        JPanel periods = Theme.transparent(new FlowLayout(FlowLayout.CENTER, 6, 0));
        ButtonGroup group = new ButtonGroup();
        for (JToggleButton period : java.util.List.of(morning, afternoon)) {
            period.setFont(Theme.font(12, Font.PLAIN));
            period.addActionListener(e -> refreshSchedule());
            group.add(period);
            periods.add(period);
        }
        periods.add(Theme.button("오늘", false, () -> {
            followCurrentPeriod();
            selectDate(PlannerTime.today());
        }));
        controls.add(periods);
        contents.add(controls, BorderLayout.SOUTH);
        return contents;
    }

    private JPanel stopwatchTab() {
        JPanel content = Theme.card(new GridBagLayout());
        elapsed.setHorizontalAlignment(SwingConstants.CENTER);
        GridBagConstraints layout = new GridBagConstraints();
        layout.gridx = 0;
        content.add(elapsed, layout);
        JPanel actions = Theme.transparent(new FlowLayout(FlowLayout.CENTER, 6, 12));
        actions.add(start);
        actions.add(pause);
        actions.add(Theme.button("초기화", false, () -> { stopwatch.reset(); tick(); }));
        layout.gridy = 1;
        content.add(actions, layout);
        return content;
    }

    void selectDate(LocalDate selectedDate) {
        date = selectedDate;
        dateLabel.setText(date.format(DATE));
        refreshSchedule();
    }

    private void followCurrentPeriod() {
        (PlannerTime.now().getHour() >= 12 ? afternoon : morning).setSelected(true);
    }

    void setScheduleModel(PlannerModel model) { this.model = model; }

    void refreshSchedule() { face.showSchedule(model.occurrences(date), afternoon.isSelected()); }

    void tick() {
        LocalTime now = PlannerTime.now();
        clock.setText(now.format(CLOCK));
        face.updateTime(now);
        long tenths = TimeUnit.NANOSECONDS.toMillis(stopwatch.elapsedNanos()) / 100;
        elapsed.setText(String.format(Locale.ROOT, "%02d:%02d:%02d.%d",
                tenths / 36_000, tenths / 600 % 60, tenths / 10 % 60, tenths % 10));
        start.setEnabled(!stopwatch.isRunning());
        start.setText(tenths > 0 ? "계속" : "시작");
        pause.setEnabled(stopwatch.isRunning());
    }
}
