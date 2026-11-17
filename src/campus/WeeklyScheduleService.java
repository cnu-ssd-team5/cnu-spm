package campus;

import java.time.*;
import java.util.*;
import java.util.List;
import campus.PlannerModel.Event;
import campus.PlannerModel.Category;

/** Multi-day courses keep one stable Event per weekday and the existing XML format. */
final class WeeklyScheduleService {
    private final PlannerModel model;
    private Semester semester;

    WeeklyScheduleService(PlannerModel model, Semester semester) { this.model = model; this.semester = semester; }
    void semester(Semester semester) { this.semester = semester; }
    List<Event> events() { return model.events(semester).stream().filter(Event::recurring).toList(); }
    static String root(Event event) {
        return event.recurringGroup();
    }
    List<Event> group(Event original) {
        if (original == null || !original.recurring()) return List.of();
        String id = root(original);
        return events().stream().filter(event -> event.id().equals(id) || event.id().startsWith("tt:" + id + ":")).toList();
    }
    List<Event> prepare(Event original, String title, String place, LocalTime start, LocalTime end,
                        Category category, Set<DayOfWeek> days, String note) {
        if (days.isEmpty()) throw new IllegalArgumentException("반복 요일을 하나 이상 선택해 주세요.");
        List<Event> old = group(original);
        String root = original == null ? UUID.randomUUID().toString() : root(original);
        List<Event> result = new ArrayList<>();
        for (DayOfWeek day : new TreeSet<>(days)) {
            String id = old.stream().filter(event -> event.weekday() == day).map(Event::id).findFirst()
                    .orElse("tt:" + root + ":" + day.name());
            result.add(new Event(id, title, place, start, end, category, day, null, note));
        }
        return List.copyOf(result);
    }
    List<Event> conflicts(Event original, List<Event> proposed) {
        Set<String> old = new HashSet<>();
        group(original).forEach(event -> old.add(event.id()));
        return proposed.stream().flatMap(event -> model.conflicts(event, semester).stream())
                .filter(event -> !old.contains(event.id())).distinct().toList();
    }
    void apply(Event original, List<Event> proposed) {
        synchronized (model) {
            if (original != null && !original.recurring()) model.removeEvent(original.id());
            Set<String> keep = new HashSet<>(); proposed.forEach(event -> keep.add(event.id()));
            group(original).stream().filter(event -> !keep.contains(event.id())).forEach(event -> model.removeEvent(event.id()));
            proposed.forEach(event -> model.putEvent(event, semester));
        }
    }
    void delete(Event original) { group(original).forEach(event -> model.removeEvent(event.id())); }
}
