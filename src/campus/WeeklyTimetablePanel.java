package campus;

import java.awt.*;
import java.time.*;
import javax.swing.*;
import campus.PlannerModel.Event;

final class WeeklyTimetablePanel extends JPanel {
    interface Editor { void open(Event event, DayOfWeek day, LocalTime time); }

    private final WeeklyScheduleService service;
    private final WeeklyGrid grid;
    private final JLabel period = Theme.label(" ", 12, Theme.MUTED);

    WeeklyTimetablePanel(PlannerModel model, PlaceCatalog places, Editor editor) {
        super(new BorderLayout());
        setOpaque(false);
        service = new WeeklyScheduleService(model, Semester.forDate(PlannerTime.today()));
        grid = new WeeklyGrid(service, places, editor);
        JPanel content = Theme.card(new BorderLayout(0, 12));
        JPanel bar = Theme.transparent(new BorderLayout(12, 0));
        JPanel heading = Theme.transparent(new GridLayout(0, 1, 0, 6));
        heading.add(Theme.heading("주간 시간표", 17));
        heading.add(period);
        bar.add(heading, BorderLayout.CENTER);
        bar.add(Theme.button("+ 수업 추가", true, () -> editor.open(null, DayOfWeek.MONDAY, LocalTime.of(9, 0))), BorderLayout.EAST);
        content.add(bar, BorderLayout.NORTH);
        content.add(Theme.scroll(grid), BorderLayout.CENTER);
        content.add(Theme.label("더블클릭 · 우클릭으로 수업 추가·수정", 12, Theme.MUTED), BorderLayout.SOUTH);
        add(content);
        refresh(Semester.forDate(PlannerTime.today()));
    }

    void refresh(Semester semester) {
        service.semester(semester);
        period.setText(semester + " · " + semester.start() + " ~ " + semester.end());
        grid.refresh();
    }
}
