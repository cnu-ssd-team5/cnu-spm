package campus.ui;

import java.awt.*;
import java.time.*;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import javax.swing.*;
import campus.model.PlannerModel;
import campus.model.PlannerModel.Occurrence;

/** 상단의 주간 달력. 날짜를 누르면 그날의 일정으로 바뀝니다. */
final class WeekBar {
    final JPanel panel = Theme.card(new BorderLayout(0, 13));
    private final MainFrame frame;
    private final PlannerModel model;
    private final JLabel weekTitle = Theme.heading(" ", 19);
    private final JPanel weekDays = Theme.transparent(new GridLayout(1, 7, 8, 0));

    WeekBar(MainFrame frame, PlannerModel model) {
        this.frame = frame;
        this.model = model;
        JPanel navigation = Theme.transparent(new BorderLayout());
        JPanel previous = Theme.transparent(new FlowLayout(FlowLayout.LEFT, 8, 0));
        previous.add(Theme.button("‹", false, () -> frame.changeDate(frame.selectedDate().minusWeeks(1))));
        previous.add(weekTitle);
        previous.add(Theme.button("›", false, () -> frame.changeDate(frame.selectedDate().plusWeeks(1))));
        navigation.add(previous, BorderLayout.WEST);
        navigation.add(Theme.button("오늘", false, () -> frame.changeDate(LocalDate.now())), BorderLayout.EAST);
        panel.add(navigation, BorderLayout.NORTH);
        weekDays.setPreferredSize(new Dimension(0, 70));
        panel.add(weekDays, BorderLayout.CENTER);
    }

    void refresh(LocalDate selectedDate) {
        LocalDate monday = selectedDate.with(DayOfWeek.MONDAY);
        weekTitle.setText(monday.getYear()+"년 "+monday.getMonthValue()+"월 "+monday.getDayOfMonth()+"일 — "+monday.plusDays(6).getMonthValue()+"월 "+monday.plusDays(6).getDayOfMonth()+"일");
        weekDays.removeAll();
        for (int i = 0; i < 7; i++) {
            LocalDate date = monday.plusDays(i);
            List<Occurrence> daily = model.occurrences(date);
            long count = daily.stream().filter(o -> !o.canceled()).count();
            String day = date.getDayOfWeek().getDisplayName(TextStyle.SHORT,Locale.KOREAN);
            boolean chosen = date.equals(selectedDate);
            JButton button = Theme.button("<html><center>"+day+"  "+date.getDayOfMonth()+"<br><font size='2'>"+(count==0 ? "일정 없음" : count+"개 일정")+"</font></center></html>",chosen,() -> frame.changeDate(date));
            if(date.getDayOfWeek()==DayOfWeek.SATURDAY || date.getDayOfWeek()==DayOfWeek.SUNDAY) {
                button.putClientProperty("calendar.fill",date.getDayOfWeek()==DayOfWeek.SATURDAY?new Color(190,227,250):new Color(250,207,224));
                button.setForeground(Theme.INK);
                if(chosen)button.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.BLUE,2),BorderFactory.createEmptyBorder(7,11,7,11)));
            }
            button.putClientProperty("calendar.today",date.equals(LocalDate.now()));
            if (date.equals(LocalDate.now())) button.setToolTipText("오늘");
            weekDays.add(button);
        }
    }
}
