package campus.ui;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import campus.model.PlannerModel.Deadline;
import campus.model.PlannerModel.Event;
import campus.model.PlannerModel.Occurrence;
import campus.model.PlannerModel;
import campus.service.DayPlanService.Leg;
import campus.service.Place;
import campus.service.PlaceCatalog;
import campus.service.ScheduleSlot;

final class SchedulePanel extends JPanel {
    interface Actions {
        void add(ScheduleSlot slot);
        void edit(Event event);
        void toggle(Occurrence occurrence);
        void select(Place place);
        void addDeadline();
        void editDeadline(Deadline deadline);
        void toggleDeadline(Deadline deadline);
        void noteChanged(String text);
    }

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN);
    private final PlannerModel model;
    private final PlaceCatalog places;
    private final Actions actions;
    private final JLabel title = Theme.heading(" ", 17);
    private final JPanel items = Theme.transparent(new GridBagLayout());
    private final JTextArea note = new JTextArea(3, 20);
    private boolean refreshing;
    private LocalDate date;

    SchedulePanel(PlannerModel model, PlaceCatalog places, Actions actions) {
        super(new BorderLayout(0, 12));
        this.model = model;
        this.places = places;
        this.actions = actions;
        setOpaque(false);
        JPanel listCard = Theme.card(new BorderLayout(0, 10));
        JPanel heading = Theme.transparent(new BorderLayout(5, 0));
        heading.add(title, BorderLayout.CENTER);
        heading.add(Theme.button("+ 과제", false, actions::addDeadline), BorderLayout.EAST);
        listCard.add(heading, BorderLayout.NORTH);
        listCard.add(Theme.scroll(items), BorderLayout.CENTER);
        add(listCard, BorderLayout.CENTER);
        JPanel memo = Theme.card(new BorderLayout(0, 8));
        memo.add(Theme.heading("메모", 14), BorderLayout.NORTH);
        note.setLineWrap(true);
        note.setWrapStyleWord(true);
        Theme.field(note);
        JScrollPane scroll = Theme.scroll(note);
        scroll.setPreferredSize(new Dimension(0, 84));
        memo.add(scroll, BorderLayout.CENTER);
        add(memo, BorderLayout.SOUTH);
        note.getDocument().addDocumentListener(new DocumentListener() {
            private void changed() {
                if (!refreshing) actions.noteChanged(note.getText());
            }
            public void insertUpdate(DocumentEvent event) { changed(); }
            public void removeUpdate(DocumentEvent event) { changed(); }
            public void changedUpdate(DocumentEvent event) { changed(); }
        });
    }

    void refresh(LocalDate date, List<Leg> legs) {
        this.date = date;
        refreshing = true;
        try {
            title.setText(date.format(DAY));
            note.setText(model.dayNote(date));
            note.setCaretPosition(0);
        } finally {
            refreshing = false;
        }
        items.removeAll();
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.anchor = GridBagConstraints.NORTH;
        constraints.insets = new Insets(0, 0, 9, 0);
        List<Occurrence> occurrences = model.occurrences(date);
        List<Occurrence> active = occurrences.stream().filter(value -> !value.canceled()).toList();
        if (active.isEmpty()) addSlot(new ScheduleSlot(LocalTime.of(9, 0), LocalTime.of(10, 0)), constraints);
        else addSlot(ScheduleSlot.before(active.get(0).event().start()), constraints);
        int activeIndex = 0;
        int routeIndex = 0;
        LocalTime occupiedUntil = null;
        for (Occurrence occurrence : occurrences) {
            if (!occurrence.canceled() && activeIndex > 0) {
                addSlot(ScheduleSlot.between(occupiedUntil, occurrence.event().start()), constraints);
                addItem(routeLabel(legs.get(routeIndex++)), constraints);
            }
            addItem(eventCard(occurrence), constraints);
            if (!occurrence.canceled()) {
                activeIndex++;
                if (occupiedUntil == null || occurrence.event().end().isAfter(occupiedUntil)) {
                    occupiedUntil = occurrence.event().end();
                }
            }
        }
        if (occupiedUntil != null) addSlot(ScheduleSlot.after(occupiedUntil), constraints);
        List<Deadline> tasks = model.deadlines(date);
        if (!tasks.isEmpty()) {
            addItem(Theme.heading("과제", 14), constraints);
            for (Deadline task : tasks) addItem(deadlineCard(task), constraints);
        }
        constraints.weighty = 1;
        constraints.fill = GridBagConstraints.BOTH;
        items.add(Theme.transparent(new BorderLayout()), constraints);
        revalidate();
        repaint();
    }

    private void addItem(Component item, GridBagConstraints constraints) {
        items.add(item, constraints);
        constraints.gridy++;
    }

    private void addSlot(ScheduleSlot slot, GridBagConstraints constraints) {
        if (slot == null) return;
        JButton button = Theme.button("+ 일정 추가", false, () -> actions.add(slot));
        button.setForeground(Theme.BLUE);
        button.setBorder(BorderFactory.createEmptyBorder(7, 8, 7, 8));
        button.setToolTipText(slot.start().format(CLOCK) + " – " + slot.end().format(CLOCK));
        addItem(button, constraints);
    }

    private JLabel routeLabel(Leg leg) {
        String text = leg.route().reachable()
                ? String.format(Locale.ROOT, "도보 %,.0f m · 약 %d분", leg.route().meters(), leg.minutes())
                : "경로 없음";
        JLabel label = Theme.label(text, 11, Theme.MUTED);
        label.setBorder(BorderFactory.createEmptyBorder(0, 7, 2, 0));
        label.setToolTipText(leg.route().message());
        return label;
    }

    private JPanel eventCard(Occurrence occurrence) {
        Event event = occurrence.event();
        JPanel card = Theme.card(new BorderLayout(0, 8));
        card.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        card.setBackground(occurrence.canceled() ? new Color(247, 248, 250) : Color.WHITE);
        JPanel details = Theme.transparent(new GridLayout(0, 1, 0, 5));
        Color color = occurrence.canceled() ? Theme.MUTED : Theme.INK;
        details.add(Theme.label(event.start().format(CLOCK) + " – " + event.end().format(CLOCK), 12, Theme.BLUE));
        JLabel name = Theme.heading(event.title(), 15);
        name.setForeground(color);
        name.setToolTipText(event.title());
        details.add(name);
        Place place = places.find(event.buildingId());
        if (place != null) {
            JLabel location = Theme.label(place.toString(), 12, Theme.MUTED);
            location.setToolTipText(place.toString());
            location.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            location.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) { actions.select(place); }
            });
            details.add(location);
        }
        if (!event.note().isBlank()) {
            JLabel memo = Theme.label(event.note().replace('\n', ' '), 11, Theme.MUTED);
            memo.setToolTipText(event.note());
            details.add(memo);
        }
        card.add(details, BorderLayout.CENTER);
        JPanel buttons = Theme.transparent(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.add(Theme.button("편집", false, () -> actions.edit(event)));
        String toggle = occurrence.canceled() ? "복원"
                : event.category() == PlannerModel.Category.CLASS ? "휴강" : "제외";
        buttons.add(Theme.button(toggle, occurrence.canceled(), () -> actions.toggle(occurrence)));
        card.add(buttons, BorderLayout.SOUTH);
        return card;
    }

    private JPanel deadlineCard(Deadline task) {
        JPanel card = Theme.card(new BorderLayout(0, 8));
        card.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        JPanel text = Theme.transparent(new GridLayout(0, 1, 0, 5));
        JLabel name = Theme.heading(task.title(), 14);
        name.setToolTipText(task.title());
        text.add(name);
        String status;
        if (task.completed()) {
            status = task.completedOn() == null ? "제출완료"
                    : task.completedOn().getMonthValue() + "월 " + task.completedOn().getDayOfMonth() + "일 제출완료";
        } else status = task.date().getMonthValue() + "월 " + task.date().getDayOfMonth() + "일 마감";
        Color statusColor = task.completed() ? Theme.MUTED : new Color(195, 48, 48);
        text.add(Theme.label(status, 12, statusColor));
        if (!task.note().isBlank()) {
            JLabel memo = Theme.label(task.note().replace('\n', ' '), 11, Theme.MUTED);
            memo.setToolTipText(task.note());
            text.add(memo);
        }
        card.add(text, BorderLayout.CENTER);
        JPanel buttons = Theme.transparent(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.add(Theme.button("편집", false, () -> actions.editDeadline(task)));
        buttons.add(Theme.button(task.completed() ? "미제출로" : "제출완료", false, () -> actions.toggleDeadline(task)));
        card.add(buttons, BorderLayout.SOUTH);
        return card;
    }
}

