package campus.model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Personal schedules and date-specific exceptions, independent of the map UI. */
public final class PlannerModel {
    public enum Category {
        CLASS("수업"), CLUB("동아리"), GROUP("조별과제"),
        APPOINTMENT("약속"), LECTURE("특강"), OTHER("기타");

        private final String label;

        Category(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public record Event(String id, String title, String buildingId,
                        LocalTime start, LocalTime end, Category category,
                        DayOfWeek weekday, LocalDate date, String note) {
        public Event {
            id = requiredText(id, "일정 ID");
            title = requiredText(title, "일정 이름");
            buildingId = requiredText(buildingId, "건물 ID");
            Objects.requireNonNull(start, "시작 시각");
            Objects.requireNonNull(end, "종료 시각");
            Objects.requireNonNull(category, "일정 분류");
            if (!end.isAfter(start)) {
                throw new IllegalArgumentException("종료 시각은 시작 시각보다 늦어야 합니다.");
            }
            if ((weekday == null) == (date == null)) {
                throw new IllegalArgumentException("반복 요일 또는 일회성 날짜 중 하나를 지정해야 합니다.");
            }
            note = Objects.requireNonNullElse(note, "");
        }

        public boolean recurring() {
            return weekday != null;
        }

        boolean occursOn(LocalDate target) {
            return recurring() ? weekday == target.getDayOfWeek() : date.equals(target);
        }
    }

    public record Occurrence(Event event, LocalDate date, boolean canceled) {
        public Occurrence {
            Objects.requireNonNull(event, "일정");
            Objects.requireNonNull(date, "날짜");
            if (!event.occursOn(date)) {
                throw new IllegalArgumentException("해당 날짜에 발생하는 일정이 아닙니다.");
            }
        }
    }

    record Cancellation(String eventId, LocalDate date) { }

    private static final Comparator<Event> EVENT_ORDER = Comparator.comparing(Event::start)
            .thenComparing(Event::end).thenComparing(Event::title).thenComparing(Event::id);

    private final Map<String, Event> events = new LinkedHashMap<>();
    private final Set<String> favorites = new LinkedHashSet<>();
    private final Set<Cancellation> cancellations = new LinkedHashSet<>();
    private final Map<LocalDate, String> dayNotes = new LinkedHashMap<>();

    public PlannerModel() { }

    public synchronized List<Event> events() {
        return List.copyOf(events.values());
    }

    public synchronized List<Occurrence> occurrences(LocalDate date) {
        Objects.requireNonNull(date, "날짜");
        List<Event> matching = events.values().stream()
                .filter(event -> event.occursOn(date)).sorted(EVENT_ORDER).toList();
        List<Occurrence> result = new ArrayList<>(matching.size());
        for (Event event : matching) {
            boolean canceled = cancellations.contains(new Cancellation(event.id(), date));
            result.add(new Occurrence(event, date, canceled));
        }
        return List.copyOf(result);
    }

    public synchronized void putEvent(Event event) {
        Objects.requireNonNull(event, "일정");
        events.put(event.id(), event);
        // Retain exceptions when editing time or notes, and discard obsolete dates.
        cancellations.removeIf(value -> value.eventId().equals(event.id())
                && !event.occursOn(value.date()));
    }

    public synchronized void removeEvent(String id) {
        String eventId = requiredText(id, "일정 ID");
        events.remove(eventId);
        cancellations.removeIf(value -> value.eventId().equals(eventId));
    }

    public synchronized boolean isFavorite(String buildingId) {
        return favorites.contains(buildingId);
    }

    public synchronized void toggleFavorite(String buildingId) {
        String id = requiredText(buildingId, "건물 ID");
        if (!favorites.remove(id)) {
            favorites.add(id);
        }
    }

    public synchronized Set<String> favorites() {
        return Set.copyOf(favorites);
    }

    public synchronized void toggleCancellation(String eventId, LocalDate date) {
        String id = requiredText(eventId, "일정 ID");
        Event event = events.get(id);
        Objects.requireNonNull(date, "날짜");
        if (event == null || !event.occursOn(date)) {
            throw new IllegalArgumentException("해당 날짜의 일정을 찾을 수 없습니다.");
        }
        Cancellation key = new Cancellation(id, date);
        if (!cancellations.remove(key)) {
            cancellations.add(key);
        }
    }

    public synchronized String dayNote(LocalDate date) {
        return dayNotes.getOrDefault(Objects.requireNonNull(date, "날짜"), "");
    }

    public synchronized void setDayNote(LocalDate date, String text) {
        Objects.requireNonNull(date, "날짜");
        Objects.requireNonNull(text, "메모");
        if (text.isBlank()) {
            dayNotes.remove(date);
        } else {
            dayNotes.put(date, text);
        }
    }

    synchronized Set<Cancellation> cancellations() {
        return Set.copyOf(cancellations);
    }

    synchronized Map<LocalDate, String> dayNotes() {
        return Map.copyOf(dayNotes);
    }

    private static String requiredText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + "을(를) 입력해야 합니다.");
        }
        return value.strip();
    }
}
