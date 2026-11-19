package campus.ui;

import java.awt.*;
import java.time.*;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.swing.*;
import campus.model.PlannerModel;
import campus.model.PlannerModel.Occurrence;
import campus.weather.WeatherForecast.Day;
import campus.weather.WeatherBadge;

/** 상단의 주간 달력. 날짜를 누르면 그날의 일정으로 바뀝니다. */
final class WeekBar {
    final JPanel panel = Theme.card(new BorderLayout(0, 13));
    private final MainFrame frame;
    private final PlannerModel model;
    private final JLabel weekTitle = Theme.heading(" ", 19);
    private final JPanel weekDays = Theme.transparent(new GridLayout(1, 7, 8, 0));
    private Map<LocalDate, Day> weather = Map.of();

    WeekBar(MainFrame frame, PlannerModel model) {
        this.frame = frame;
        this.model = model;
        JPanel navigation = Theme.transparent(new BorderLayout());
        JPanel previous = Theme.transparent(new FlowLayout(FlowLayout.LEFT, 8, 0));
        previous.add(Theme.button("‹", false, () -> frame.changeDate(frame.selectedDate().minusWeeks(1))));
        previous.add(weekTitle);
        previous.add(Theme.button("›", false, () -> frame.changeDate(frame.selectedDate().plusWeeks(1))));
        navigation.add(previous, BorderLayout.WEST);
        navigation.add(Theme.button("오늘", false, () -> frame.changeDate(campus.calendar.PlannerTime.today())), BorderLayout.EAST);
        panel.add(navigation, BorderLayout.NORTH);
        weekDays.setPreferredSize(new Dimension(0, 58));
        panel.add(weekDays, BorderLayout.CENTER);
    }

    void showWeather(LocalDate selectedDate, Map<LocalDate, Day> forecast) {
        if (weather.equals(forecast)) return;
        weather = forecast;
        refresh(selectedDate);
        weekDays.revalidate();
        weekDays.repaint();
    }

    void refresh(LocalDate selectedDate) {
        LocalDate sunday = selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
        weekTitle.setText(sunday.getYear()+"년 "+sunday.getMonthValue()+"월 "+sunday.getDayOfMonth()+"일 — "+sunday.plusDays(6).getMonthValue()+"월 "+sunday.plusDays(6).getDayOfMonth()+"일");
        weekDays.removeAll();
        for (int i = 0; i < 7; i++) {
            LocalDate date = sunday.plusDays(i);
            List<Occurrence> daily = model.occurrences(date);
            long count = daily.stream().filter(o -> !o.canceled()).count()+model.deadlines(date).size();
            String day = date.getDayOfWeek().getDisplayName(TextStyle.SHORT,Locale.KOREAN);
            boolean chosen = date.equals(selectedDate);
            Day forecast = weather.get(date);
            String schedule = count == 0 ? "일정 없음" : count + "개 일정";
            JButton button = Theme.button("<html><center>" + day + "  " + date.getDayOfMonth()
                    + "<br><font size='2'>" + schedule + "</font></center></html>",
                    false, () -> frame.changeDate(date));
            if (forecast != null) {
                button.setBorder(BorderFactory.createEmptyBorder(2, 3, 2, 3));
                button.setIcon(new WeatherBadge(forecast, button.getFontMetrics(Theme.font(11, Font.PLAIN))));
                button.setVerticalTextPosition(SwingConstants.TOP);
                button.setHorizontalTextPosition(SwingConstants.CENTER);
                button.setIconTextGap(1);
                String description = forecast.description() + " · Open-Meteo";
                button.setToolTipText(description);
                button.getAccessibleContext().setAccessibleDescription(description);
            }
            if(date.getDayOfWeek()==DayOfWeek.SATURDAY || date.getDayOfWeek()==DayOfWeek.SUNDAY) {
                button.putClientProperty("calendar.fill",date.getDayOfWeek()==DayOfWeek.SATURDAY?new Color(190,227,250):new Color(250,207,224));
                button.setForeground(Theme.INK);
            }
            if (chosen) button.putClientProperty("calendar.fill", Theme.SELECTED_DAY);
            if (date.equals(campus.calendar.PlannerTime.today())) {
                button.setForeground(Theme.TODAY_TEXT);
                button.setToolTipText(forecast == null ? "오늘" : "오늘 · " + button.getToolTipText());
            }
            button.setName(date.toString());
            weekDays.add(button);
        }
    }
}
