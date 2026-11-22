package campus.ui;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.*;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.swing.*;
import campus.calendar.PlannerTime;
import campus.model.PlannerModel;
import campus.model.PlannerModel.Occurrence;
import campus.weather.WeatherForecast.Day;
import campus.weather.WeatherBadge;

/** 상단의 주간 달력. 날짜를 선택하거나 해당 날짜의 플래너를 엽니다. */
final class WeekBar {
    final JPanel panel = Theme.card(new BorderLayout(0, 13));
    private final MainFrame frame;
    private PlannerModel model;
    private final JLabel weekTitle = Theme.heading(" ", 19);
    private final JPanel weekDays = Theme.transparent(new GridLayout(1, 7, 8, 0));
    private Map<LocalDate, Day> weather = Map.of();
    private LocalDate displayedSunday;

    void setScheduleModel(PlannerModel model) { this.model = model; }

    WeekBar(MainFrame frame, PlannerModel model) {
        this.frame = frame;
        this.model = model;
        JPanel navigation = Theme.transparent(new BorderLayout());
        JPanel previous = Theme.transparent(new FlowLayout(FlowLayout.LEFT, 8, 0));
        previous.add(Theme.button("‹", false, () -> frame.changeDate(frame.selectedDate().minusWeeks(1))));
        previous.add(weekTitle);
        previous.add(Theme.button("›", false, () -> frame.changeDate(frame.selectedDate().plusWeeks(1))));
        navigation.add(previous, BorderLayout.WEST);
        navigation.add(Theme.button("오늘", false, () -> frame.selectWeekDate(PlannerTime.today())), BorderLayout.EAST);
        panel.add(navigation, BorderLayout.NORTH);
        weekDays.setPreferredSize(new Dimension(0, 58));
        panel.add(weekDays, BorderLayout.CENTER);
    }

    void showWeather(LocalDate selectedDate, Map<LocalDate, Day> forecast) {
        if (weather.equals(forecast)) return;
        weather = forecast;
        refresh(selectedDate);
    }

    void refresh(LocalDate selectedDate) {
        LocalDate sunday = selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
        weekTitle.setText(sunday.getYear()+"년 "+sunday.getMonthValue()+"월 "+sunday.getDayOfMonth()+"일 — "+sunday.plusDays(6).getMonthValue()+"월 "+sunday.plusDays(6).getDayOfMonth()+"일");
        if (!sunday.equals(displayedSunday)) {
            // Keep focused dates and the first click of a double-click on the same button.
            displayedSunday = sunday;
            weekDays.removeAll();
            for (int i = 0; i < 7; i++) weekDays.add(dateButton(sunday.plusDays(i)));
        }
        LocalDate today = PlannerTime.today();
        for (int i = 0; i < 7; i++) {
            LocalDate date = sunday.plusDays(i);
            List<Occurrence> daily = model.occurrences(date);
            long count = daily.stream().filter(o -> !o.canceled()).count()+model.deadlines(date).size();
            String day = date.getDayOfWeek().getDisplayName(TextStyle.SHORT,Locale.KOREAN);
            boolean chosen = date.equals(selectedDate);
            Day forecast = weather.get(date);
            String schedule = count == 0 ? "일정 없음" : count + "개 일정";
            JButton button = (JButton) weekDays.getComponent(i);
            button.setText("<html><center>" + day + "  " + date.getDayOfMonth()
                    + "<br><font size='2'>" + schedule + "</font></center></html>");
            String description = forecast == null ? "" : forecast.description() + " · Open-Meteo";
            String hint = "더블클릭 또는 Enter로 플래너 열기";
            if (date.equals(today)) hint = "오늘 · " + hint;
            button.setToolTipText(description.isEmpty() ? hint : hint + " · " + description);
            button.getAccessibleContext().setAccessibleDescription(button.getToolTipText());
            if (forecast != null) {
                button.setBorder(BorderFactory.createEmptyBorder(2, 3, 2, 3));
                button.setIcon(new WeatherBadge(forecast, button.getFontMetrics(Theme.font(11, Font.PLAIN))));
                button.setVerticalTextPosition(SwingConstants.TOP);
                button.setHorizontalTextPosition(SwingConstants.CENTER);
                button.setIconTextGap(1);
            } else if (button.getIcon() != null) {
                button.setIcon(null);
                button.setBorder(BorderFactory.createEmptyBorder(9, 13, 9, 13));
                button.setVerticalTextPosition(SwingConstants.CENTER);
                button.setHorizontalTextPosition(SwingConstants.TRAILING);
                button.setIconTextGap(4);
            }
            button.putClientProperty("calendar.selected", chosen);
            button.setForeground(date.equals(today) ? Theme.TODAY_TEXT : chosen ? Theme.BLUE : Theme.INK);
            button.repaint();
        }
        weekDays.revalidate();
        weekDays.repaint();
    }

    private JButton dateButton(LocalDate date) {
        JButton button = Theme.button(" ", false, () -> frame.selectWeekDate(date));
        button.setName(date.toString());
        if (date.getDayOfWeek() == DayOfWeek.SATURDAY) {
            button.putClientProperty("calendar.fill", new Color(190, 227, 250));
        } else if (date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            button.putClientProperty("calendar.fill", new Color(250, 207, 224));
        }
        button.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent event) {
                if (SwingUtilities.isLeftMouseButton(event) && event.getClickCount() == 2) frame.openPlanner(date);
            }
        });
        button.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("ENTER"), "openPlanner");
        button.getActionMap().put("openPlanner", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) { frame.openPlanner(date); }
        });
        return button;
    }
}
