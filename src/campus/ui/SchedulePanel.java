package campus.ui;

import java.awt.*;
import java.awt.event.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import javax.swing.*;
import campus.calendar.PlannerTime;
import java.time.temporal.ChronoUnit;
import campus.map.CampusMap;
import campus.map.CampusMap.Building;
import campus.model.PlannerModel;
import campus.model.PlannerModel.Event;
import campus.model.PlannerModel.Occurrence;
import campus.service.DayPlanService.Leg;

/** 오른쪽의 하루 일정 카드, 일정 사이 이동 시간, 과제·할일 마감. */
final class SchedulePanel {
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN);
    final JPanel panel = Theme.transparent(new BorderLayout(0,14));
    private final MainFrame frame;
    private final CampusMap data;
    private final PlannerModel model;
    private final JLabel dayTitle = Theme.heading(" ", 16);
    private final JLabel daySummary = Theme.label(" ", 12, Theme.MUTED);
    private final JPanel schedule = Theme.transparent(new GridBagLayout());

    SchedulePanel(MainFrame frame, CampusMap data, PlannerModel model) {
        this.frame = frame;
        this.data = data;
        this.model = model;
        JPanel eventsCard = Theme.card(new BorderLayout(0,12));
        JPanel title = Theme.transparent(new GridLayout(2,1,0,5));
        JPanel dateRow = Theme.transparent(new BorderLayout(5,0));
        dateRow.add(dayTitle,BorderLayout.CENTER);
        JButton addEvent = Theme.button("일정 추가",false,() -> frame.edit(null,false));
        addEvent.setFont(Theme.font(11,Font.BOLD));
        addEvent.setBorder(BorderFactory.createEmptyBorder(8,8,8,8));
        dateRow.add(addEvent,BorderLayout.EAST);
        title.add(dateRow);
        title.add(daySummary);
        eventsCard.add(title,BorderLayout.NORTH);
        eventsCard.add(Theme.scroll(schedule),BorderLayout.CENTER);
        panel.add(eventsCard,BorderLayout.CENTER);
    }

    void refresh(LocalDate date, List<Occurrence> occurrences, List<Leg> legs) {
        dayTitle.setText(date.format(DAY));
        dayTitle.setToolTipText(date.format(DAY));
        refreshSchedule(date, occurrences, legs);
    }

    private void refreshSchedule(LocalDate date, List<Occurrence> occurrences, List<Leg> legs) {
        schedule.removeAll();
        long count = occurrences.stream().filter(o -> !o.canceled()).count();
        long canceled = occurrences.size()-count;
        daySummary.setText("진행 "+count+"개"+(canceled==0 ? "" : " · 휴강/제외 "+canceled+"개")+(model.deadlines(date).isEmpty()?"":" · 할일 "+model.deadlines(date).size()+"개"));
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
            empty.add(Theme.label("날짜 옆 일정 추가로 등록하세요.",11,Theme.MUTED));
            schedule.add(empty,c);
            c.gridy++;
        }
        int routeIndex=0;
        int activeIndex=0;
        for (Occurrence occurrence : occurrences) {
            if (!occurrence.canceled() && activeIndex>0) {
                schedule.add(legCard(legs.get(routeIndex++)),c);
                c.gridy++;
            }
            schedule.add(eventCard(occurrence,occurrence.canceled() ? 0 : ++activeIndex),c);
            c.gridy++;
        }
        c.insets = new Insets(14,0,8,0);
        schedule.add(Theme.heading("과제·할일 마감",14),c);c.gridy++;
        c.insets = new Insets(0,0,8,0);
        LocalDate today = PlannerTime.today();
        List<PlannerModel.Deadline> pending = model.deadlines().stream().filter(d -> !d.completed()).toList();
        schedule.add(Theme.label("오늘 기준 · 미완료 " + pending.size() + "개",11,Theme.MUTED),c);c.gridy++;
        if (pending.isEmpty()) {
            schedule.add(Theme.label("남은 과제·할일이 없습니다.",12,Theme.MUTED),c);c.gridy++;
        }
        for (var deadline : pending) {
            schedule.add(deadlineCard(deadline,today),c);c.gridy++;
        }
        List<PlannerModel.Deadline> completed = model.deadlines(date).stream().filter(PlannerModel.Deadline::completed).toList();
        if (!completed.isEmpty()) {
            schedule.add(Theme.label("선택한 날짜의 완료 항목",11,Theme.MUTED),c);c.gridy++;
            for (var deadline : completed) {
                schedule.add(deadlineCard(deadline,today),c);c.gridy++;
            }
        }
        c.weighty=1;
        c.fill=GridBagConstraints.BOTH;
        schedule.add(Theme.transparent(new BorderLayout()),c);
    }

    static String deadlineStatus(PlannerModel.Deadline deadline, LocalDate today) {
        if (deadline.completed()) return "완료";
        long days = ChronoUnit.DAYS.between(today,deadline.date());
        if (days < 0) return "마감 " + (-days) + "일 지남";
        if (days == 0) return "D-day · 오늘 마감";
        return "D-" + days + " · " + days + "일 남음";
    }

    private JButton deadlineCard(PlannerModel.Deadline deadline, LocalDate today) {
        String status = deadlineStatus(deadline,today);
        String date = deadline.date().format(DateTimeFormatter.ofPattern("yyyy.MM.dd"));
        JButton item = Theme.button("<html>" + Theme.html(deadline.title()) + "<br><small>"
                + date + " · " + status + "</small></html>",false,
                () -> frame.editDeadline(deadline,deadline.date()));
        item.setName("deadline:"+deadline.id());
        item.setHorizontalAlignment(SwingConstants.LEFT);
        item.setToolTipText(deadline.title()+" · "+date+" · "+status);
        item.setForeground(deadline.completed()?Theme.MUTED:
                deadline.date().isAfter(today)?Theme.INK:new Color(196,54,54));
        return item;
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
        JLabel time=Theme.heading((canceled ? "" : index+"  ")+event.start().format(CLOCK)+" – "+event.end().format(CLOCK),13);
        time.setForeground(canceled ? Theme.MUTED : Theme.BLUE);
        details.add(time,c);
        c.gridy++;
        JLabel title=Theme.heading(event.title(),15);
        title.setToolTipText(event.title());
        if(canceled) title.setForeground(Theme.MUTED);
        details.add(title,c);
        c.gridy++;
        Building place=data.building(event.buildingId());
        JLabel location=Theme.label(place==null ? "OSM에서 장소를 찾을 수 없습니다" : place.name(),12,Theme.MUTED);
        location.setToolTipText(location.getText());
        location.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        location.addMouseListener(new MouseAdapter(){
            @Override public void mouseClicked(MouseEvent e){if(place!=null)frame.selectBuilding(place);}
        });
        details.add(location,c);
        c.gridy++;
        String state=canceled ? "휴강/제외 · 이 날짜에만 적용" : event.category()+" · "+(event.recurring() ? "매주 반복" : "하루 일정");
        details.add(Theme.label(state,11,canceled ? Theme.WARNING : Theme.MUTED),c);
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
            JLabel note=Theme.label("<html><div style='width:225px'>"+Theme.html(memo).replace("\n","<br>")+"</div></html>",11,Theme.MUTED);
            note.setToolTipText(event.note());
            details.add(note,c);
        }
        card.add(details,BorderLayout.CENTER);
        JPanel actions=Theme.transparent(new FlowLayout(FlowLayout.RIGHT,6,0));
        actions.add(Theme.button("편집",false,() -> frame.edit(event,event.recurring())));
        String cancelLabel=canceled ? "복원" : event.category()==PlannerModel.Category.CLASS ? "휴강" : "오늘 제외";
        actions.add(Theme.button(cancelLabel,canceled,() -> frame.toggleCancellation(event)));
        card.add(actions,BorderLayout.SOUTH);
        return card;
    }

    private JPanel legCard(Leg leg) {
        JPanel panel=Theme.transparent(new BorderLayout(0,5));
        panel.setBorder(BorderFactory.createEmptyBorder(1,9,4,4));
        String travel;
        Color color=Theme.MUTED;
        if(!leg.route().reachable()) {
            travel="도보 경로를 찾지 못했습니다";
            color=Theme.WARNING;
            JLabel details=Theme.label("OSM 보행로 연결을 확인해 주세요",10,Theme.MUTED);
            details.setToolTipText(leg.route().message());
            panel.add(details,BorderLayout.CENTER);
        } else {
            travel=String.format(Locale.ROOT,"↓  도보 약 %d분 · %,.0f m",leg.minutes(),leg.route().meters());
            long slack=leg.gapMinutes()-leg.minutes();
            String timing;
            if(leg.gapMinutes()<0) {
                timing="일정 시간이 "+(-leg.gapMinutes())+"분 겹칩니다";
                color=Theme.WARNING;
            } else if(slack<0) {
                timing="다음 일정까지 이동 시간이 "+(-slack)+"분 부족합니다";
                color=Theme.WARNING;
            } else {
                timing="이동 후 "+slack+"분 여유 · "+leg.to().event().start().minusMinutes(leg.minutes()).format(CLOCK)+"까지 출발";
            }
            panel.add(Theme.label(timing,10,color),BorderLayout.CENTER);
            if(leg.route().message().contains("추정")) {
                JLabel estimate = Theme.label("출입구와 보행로 사이 연결 일부 추정",10,Theme.MUTED);
                estimate.setToolTipText(leg.route().message());
                panel.add(estimate,BorderLayout.SOUTH);
            }
        }
        panel.add(Theme.label(travel,11,color),BorderLayout.NORTH);
        return panel;
    }
}
