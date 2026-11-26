package campus.ui;

import java.awt.*;
import java.awt.event.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import javax.swing.*;
import campus.map.CampusMap;
import campus.map.CampusMap.Building;
import campus.model.PlannerModel;
import campus.model.PlannerModel.Event;
import campus.model.PlannerModel.Occurrence;
import campus.service.DayPlanService.Leg;
import campus.timetable.TimePicker;

/** 오른쪽의 하루 일정 카드와 일정 사이 이동 시간. */
final class SchedulePanel {
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN);
    final JPanel panel = Theme.transparent(new BorderLayout(0,14));
    private final MainFrame frame;
    private CampusMap data;
    private boolean readOnly;
    private final JButton addEvent;
    private PlannerModel model;
    private final JLabel dayTitle = Theme.heading(" ", 16);
    private final JLabel daySummary = Theme.label(" ", 12, Theme.MUTED);
    private final JPanel schedule = new JPanel(new GridBagLayout()) {
        @Override public Dimension getPreferredSize() {
            return new Dimension(0, super.getPreferredSize().height);
        }
    };

    SchedulePanel(MainFrame frame, CampusMap data, PlannerModel model) {
        this.frame = frame;
        this.data = data;
        this.model = model;
        JPanel eventsCard = Theme.card(new BorderLayout(0,12));
        JPanel title = Theme.transparent(new GridLayout(2,1,0,5));
        JPanel dateRow = Theme.transparent(new BorderLayout(5,0));
        dateRow.add(dayTitle,BorderLayout.CENTER);
        addEvent = Theme.button("일정 추가",false,() -> frame.edit(null,false));
        addEvent.setFont(Theme.font(11,Font.BOLD));
        addEvent.setBorder(BorderFactory.createEmptyBorder(8,8,8,8));
        dateRow.add(addEvent,BorderLayout.EAST);
        title.add(dateRow);
        title.add(daySummary);
        eventsCard.add(title,BorderLayout.NORTH);
        schedule.setOpaque(false);
        eventsCard.add(Theme.scroll(schedule),BorderLayout.CENTER);
        panel.add(eventsCard,BorderLayout.CENTER);
    }

    void setScheduleContext(CampusMap data, PlannerModel model, boolean readOnly) {
        this.data = data;
        this.model = model;
        this.readOnly = readOnly;
        addEvent.setVisible(!readOnly);
    }

    void refresh(LocalDate date, List<Occurrence> occurrences, List<Leg> legs) {
        dayTitle.setText(date.format(DAY));
        dayTitle.setToolTipText(date.format(DAY));
        refreshSchedule(occurrences, legs);
    }

    private void refreshSchedule(List<Occurrence> occurrences, List<Leg> legs) {
        schedule.removeAll();
        long count = occurrences.stream().filter(o -> !o.canceled()).count();
        long canceled = occurrences.size()-count;
        daySummary.setText("진행 "+count+"개"+(canceled==0 ? "" : " · 휴강/제외 "+canceled+"개"));
        GridBagConstraints c = new GridBagConstraints();
        c.gridx=0;
        c.gridy=0;
        c.weightx=1;
        c.fill=GridBagConstraints.HORIZONTAL;
        c.anchor=GridBagConstraints.NORTH;
        c.insets=new Insets(0,0,10,0);
        if (occurrences.isEmpty()) {
            JPanel empty = Theme.transparent(new GridLayout(0,1,0,8));
            empty.setBorder(BorderFactory.createEmptyBorder(12,4,8,4));
            empty.add(Theme.heading("등록된 일정이 없습니다.",14));
            if (!readOnly) empty.add(Theme.label("날짜 옆 일정 추가로 등록하세요.",11,Theme.MUTED));
            schedule.add(empty,c);
            c.gridy++;
        }
        int routeIndex=0;
        int activeIndex=0;
        for (Occurrence occurrence : occurrences) {
            if (!occurrence.canceled() && activeIndex>0) {
                Leg leg = legs.get(routeIndex++);
                if (showsWalkingLeg(leg)) {
                    schedule.add(legCard(leg),c);
                    c.gridy++;
                }
            }
            schedule.add(eventCard(occurrence,occurrence.canceled() ? 0 : ++activeIndex),c);
            c.gridy++;
        }
        c.weighty=1;
        c.fill=GridBagConstraints.BOTH;
        schedule.add(Theme.transparent(new BorderLayout()),c);
    }

    private JPanel eventCard(Occurrence occurrence,int index) {
        Event event=occurrence.event();
        boolean canceled=occurrence.canceled();
        JPanel card=Theme.card(new BorderLayout(0,9));
        card.setBackground(canceled ? new Color(247,248,250) : Color.WHITE);
        card.setBorder(BorderFactory.createEmptyBorder(12,12,12,12));
        JPanel details=Theme.transparent(new GridBagLayout());
        GridBagConstraints c=new GridBagConstraints();
        c.gridx=0;
        c.weightx=1;
        c.fill=GridBagConstraints.HORIZONTAL;
        c.insets=new Insets(0,0,5,0);
        c.gridy=0;
        JLabel time=Theme.heading((canceled ? "" : index+"  ")+TimePicker.display(event.start())+" – "+TimePicker.display(event.end()),13);
        time.setForeground(canceled ? Theme.MUTED : Theme.BLUE);
        details.add(time,c);
        c.gridy++;
        JLabel title=Theme.heading(event.title(),15);
        title.setToolTipText(event.title());
        if(canceled) title.setForeground(Theme.MUTED);
        details.add(title,c);
        c.gridy++;
        Building place=data.building(event.buildingId());
        String placeName = !event.customPlace().isBlank() ? event.customPlace()
                : place == null ? "OSM에서 장소를 찾을 수 없습니다" : place.name();
        JLabel location=Theme.label("",12,Theme.MUTED);
        location.putClientProperty("html.disable", true);
        location.setText(placeName);
        location.setToolTipText("<html>" + Theme.html(placeName) + "</html>");
        if (place != null) {
            location.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            location.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) { frame.selectBuilding(place); }
            });
        }
        details.add(location,c);
        c.gridy++;
        String state=canceled ? "휴강/제외 · 이 날짜에만 적용"
                : event.categoryLabel() + (event.recurring() ? "" : " · 이 날짜만");
        JLabel stateLabel = Theme.label("", 11, canceled ? Theme.WARNING : Theme.MUTED);
        stateLabel.putClientProperty("html.disable", true);
        stateLabel.setText(state);
        details.add(stateLabel,c);
        if(!canceled && model.occurrences(occurrence.date()).stream().anyMatch(other ->
                !other.canceled() && !other.event().id().equals(event.id())
                        && other.event().start().isBefore(event.end())
                        && event.start().isBefore(other.event().end()))) {
            c.gridy++;
            details.add(Theme.label("다른 일정과 시간이 겹칩니다",11,Theme.WARNING),c);
        }
        if(!event.note().isBlank()) {
            c.gridy++;
            String memo=event.note().length()>130 ? event.note().substring(0,130)+"…" : event.note();
            JLabel note=Theme.label("<html><div style='width:185px'>"+Theme.html(memo).replace("\n","<br>")+"</div></html>",11,Theme.MUTED);
            note.setToolTipText(event.note());
            details.add(note,c);
        }
        card.add(details,BorderLayout.CENTER);
        if (readOnly) return card;
        JPanel actions=Theme.transparent(new FlowLayout(FlowLayout.RIGHT,6,0));
        actions.add(Theme.button("편집",false,() -> frame.edit(event,event.recurring())));
        String cancelLabel=canceled ? "복원" : event.category()==PlannerModel.Category.CLASS ? "휴강" : "오늘 제외";
        actions.add(Theme.button(cancelLabel,canceled,() -> frame.toggleCancellation(event)));
        card.add(actions,BorderLayout.SOUTH);
        return card;
    }

    static boolean showsWalkingLeg(Leg leg) {
        return leg.from().event().customPlace().isBlank() && leg.to().event().customPlace().isBlank();
    }

    private JLabel legCard(Leg leg) {
        boolean reachable = leg.route().reachable();
        String travel = reachable ? String.format(Locale.ROOT, "↓  도보 약 %d분 · %,.0f m", leg.minutes(), leg.route().meters())
                : "도보 경로를 찾지 못했습니다";
        JLabel label = Theme.label(travel, 11, reachable ? Theme.MUTED : Theme.WARNING);
        label.setBorder(BorderFactory.createEmptyBorder(1, 9, 4, 4));
        return label;
    }
}
