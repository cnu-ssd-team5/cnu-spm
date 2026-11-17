package campus.ui;

import java.awt.*;
import java.awt.event.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import javax.swing.*;
import javax.swing.event.*;
import campus.map.CampusMap;
import campus.map.CampusMap.Building;
import campus.model.PlannerModel;
import campus.model.PlannerModel.Event;
import campus.model.PlannerModel.Occurrence;
import campus.service.DayPlanService.Leg;
import campus.service.ScheduleSlot;

/** 오른쪽의 하루 일정 카드, 일정 사이 이동 시간, 오늘의 메모. */
final class SchedulePanel {
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN);
    final JPanel panel = Theme.transparent(new BorderLayout(0,14));
    private final MainFrame frame;
    private final CampusMap data;
    private final PlannerModel model;
    private final JLabel dayTitle = Theme.heading(" ", 18);
    private final JLabel daySummary = Theme.label(" ", 12, Theme.MUTED);
    private final JPanel schedule = Theme.transparent(new GridBagLayout());
    private final JTextArea dayNote = new JTextArea(3, 20);
    private boolean refreshing;

    SchedulePanel(MainFrame frame, CampusMap data, PlannerModel model) {
        this.frame = frame;
        this.data = data;
        this.model = model;
        JPanel eventsCard = Theme.card(new BorderLayout(0,12));
        JPanel title = Theme.transparent(new GridLayout(2,1,0,5));
        title.add(dayTitle);
        title.add(daySummary);
        eventsCard.add(title,BorderLayout.NORTH);
        eventsCard.add(Theme.scroll(schedule),BorderLayout.CENTER);
        panel.add(eventsCard,BorderLayout.CENTER);
        JPanel memo = Theme.card(new BorderLayout(0,10));
        JPanel memoTitle = Theme.transparent(new BorderLayout());
        memoTitle.add(Theme.heading("오늘의 메모",14),BorderLayout.WEST);
        memoTitle.add(Theme.label("자동 저장",10,Theme.MUTED),BorderLayout.EAST);
        memo.add(memoTitle,BorderLayout.NORTH);
        dayNote.setLineWrap(true);
        dayNote.setWrapStyleWord(true);
        Theme.field(dayNote);
        dayNote.setToolTipText("선택한 날짜의 준비물이나 할 일을 적어보세요");
        JScrollPane memoScroll = Theme.scroll(dayNote);
        memoScroll.setPreferredSize(new Dimension(0,86));
        memo.add(memoScroll,BorderLayout.CENTER);
        dayNote.getDocument().addDocumentListener(new DocumentListener() {
            private void changed() {
                if (refreshing) return;
                model.setDayNote(frame.selectedDate(),dayNote.getText());
                frame.noteChanged();
            }
            public void insertUpdate(DocumentEvent e) { changed(); }
            public void removeUpdate(DocumentEvent e) { changed(); }
            public void changedUpdate(DocumentEvent e) { changed(); }
        });
        panel.add(memo,BorderLayout.SOUTH);
    }

    void refresh(LocalDate date, List<Occurrence> occurrences, List<Leg> legs) {
        refreshing = true;
        try {
            refreshSchedule(occurrences, legs);
            dayTitle.setText(date.format(DAY));
            dayNote.setText(model.dayNote(date));
            dayNote.setCaretPosition(0);
        } finally {
            refreshing = false;
        }
    }

    private void refreshSchedule(List<Occurrence> occurrences, List<Leg> legs) {
        schedule.removeAll();
        long count = occurrences.stream().filter(o -> !o.canceled()).count();
        long canceled = occurrences.size()-count;
        daySummary.setText("진행 "+count+"개"+(canceled==0 ? "" : " · 휴강/제외 "+canceled+"개")+(model.deadlines(frame.selectedDate()).isEmpty()?"":" · 할일 "+model.deadlines(frame.selectedDate()).size()+"개"));
        GridBagConstraints c = new GridBagConstraints();
        c.gridx=0;
        c.gridy=0;
        c.weightx=1;
        c.fill=GridBagConstraints.HORIZONTAL;
        c.anchor=GridBagConstraints.NORTH;
        c.insets=new Insets(0,0,10,0);
        if (occurrences.isEmpty()) {
            JPanel empty = Theme.transparent(new GridLayout(0,1,0,12));
            empty.setBorder(BorderFactory.createEmptyBorder(35,4,25,4));
            empty.add(Theme.heading("오늘의 일정을 채워보세요",15));
            empty.add(Theme.label("고정 수업은 매주 자동으로 표시됩니다.",12,Theme.MUTED));
            empty.add(Theme.label("동아리와 약속은 하루 일정으로 추가하세요.",11,Theme.MUTED));
            empty.add(Theme.button("+ 이 날짜에 일정 추가",true,() -> frame.edit(null,false)));
            schedule.add(empty,c);
            c.gridy++;
        }
        int routeIndex=0;
        int activeIndex=0;
        List<Occurrence> activeOccurrences = occurrences.stream().filter(o -> !o.canceled()).toList();
        LocalTime occupiedUntil = null;
        if (!activeOccurrences.isEmpty()) {
            addSlotButton(ScheduleSlot.before(activeOccurrences.get(0).event().start()), c);
        } else if (!occurrences.isEmpty()) {
            addSlotButton(new ScheduleSlot(LocalTime.of(9, 0), LocalTime.of(10, 0)), c);
        }
        for (Occurrence occurrence : occurrences) {
            if (!occurrence.canceled() && activeIndex>0) {
                addSlotButton(ScheduleSlot.between(occupiedUntil, occurrence.event().start()), c);
                schedule.add(legCard(legs.get(routeIndex++)),c);
                c.gridy++;
            }
            schedule.add(eventCard(occurrence,occurrence.canceled() ? 0 : ++activeIndex),c);
            c.gridy++;
            if (!occurrence.canceled() && (occupiedUntil == null || occurrence.event().end().isAfter(occupiedUntil))) {
                occupiedUntil = occurrence.event().end();
            }
        }
        if (occupiedUntil != null) addSlotButton(ScheduleSlot.after(occupiedUntil), c);
        for(var deadline:model.deadlines(frame.selectedDate())) {
            JButton item=Theme.button(deadline.title()+" · "+(deadline.completed()?"완료":"미완료"),false,
                    ()->frame.editDeadline(deadline,deadline.date()));
            item.setForeground(deadline.completed()?Theme.MUTED:new Color(196,54,54));
            schedule.add(item,c);c.gridy++;
        }
        c.weighty=1;
        c.fill=GridBagConstraints.BOTH;
        schedule.add(Theme.transparent(new BorderLayout()),c);
    }

    private void addSlotButton(ScheduleSlot slot, GridBagConstraints constraints) {
        if (slot == null) return;
        String text = "+  " + slot.start().format(CLOCK) + " – " + slot.end().format(CLOCK) + " 일정 추가";
        JButton button = Theme.button(text, false, () -> frame.edit(null, false, slot));
        button.setForeground(Theme.BLUE);
        button.setBorder(BorderFactory.createEmptyBorder(7, 8, 7, 8));
        button.setToolTipText("선택한 날짜의 새 일정에 이 시간대를 미리 채웁니다");
        schedule.add(button, constraints);
        constraints.gridy++;
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
