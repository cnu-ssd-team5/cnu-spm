package campus;

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
import java.util.UUID;

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

    public record Pin(String id, String name, double lat, double lon) {
        public Pin {
            id = requiredText(id, "핀 ID");
            name = requiredText(name, "핀 이름");
            if (!id.startsWith("pin:")) {
                throw new IllegalArgumentException("핀 ID는 pin:으로 시작해야 합니다.");
            }
            if (!Double.isFinite(lat) || !Double.isFinite(lon)
                    || lat < -90 || lat > 90 || lon < -180 || lon > 180) {
                throw new IllegalArgumentException("핀의 위경도 좌표가 올바르지 않습니다.");
            }
        }
    }

    public record Deadline(String id, String title, LocalDate date, String note,
                           boolean completed, LocalDate completedOn) {
        public Deadline {
            id = requiredText(id, "마감 일정 ID");
            title = requiredText(title, "마감 일정 이름");
            Objects.requireNonNull(date, "마감 날짜");
            note = Objects.requireNonNullElse(note, "");
            if (!completed) completedOn = null;
        }

        public Deadline(String id, String title, LocalDate date, String note, boolean completed) {
            this(id, title, date, note, completed, completed ? LocalDate.now() : null);
        }
    }

    record Cancellation(String eventId, LocalDate date) { }

    private static final Comparator<Event> EVENT_ORDER = Comparator.comparing(Event::start)
            .thenComparing(Event::end).thenComparing(Event::title).thenComparing(Event::id);

    private final Map<String, Event> events = new LinkedHashMap<>();
    private final Set<String> favorites = new LinkedHashSet<>();
    private final Set<Cancellation> cancellations = new LinkedHashSet<>();
    private final Map<LocalDate, String> dayNotes = new LinkedHashMap<>();
    private final Map<String, String> buildingNames = new LinkedHashMap<>();
    private final Map<String, Pin> pins = new LinkedHashMap<>();
    private final Map<String, Deadline> deadlines = new LinkedHashMap<>();

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

    public synchronized String customName(String buildingId) {
        return buildingNames.getOrDefault(buildingId, "");
    }

    public synchronized void renameBuilding(String buildingId, String name) {
        buildingNames.put(requiredText(buildingId, "건물 ID"), requiredText(name, "건물 이름"));
    }

    public synchronized Map<String, String> buildingNames() {
        return Map.copyOf(buildingNames);
    }

    public synchronized List<Pin> pins() {
        return List.copyOf(pins.values());
    }

    public synchronized void putPin(Pin pin) {
        Objects.requireNonNull(pin, "핀");
        pins.put(pin.id(), pin);
    }

    public synchronized List<Deadline> deadlines() {
        return deadlines.values().stream().sorted(Comparator.comparing(Deadline::date)
                .thenComparing(Deadline::completed).thenComparing(Deadline::title)
                .thenComparing(Deadline::id)).toList();
    }

    public synchronized List<Deadline> deadlines(LocalDate date) {
        Objects.requireNonNull(date, "마감 날짜");
        return deadlines().stream().filter(deadline -> deadline.date().equals(date)).toList();
    }

    public synchronized void putDeadline(Deadline deadline) {
        Objects.requireNonNull(deadline, "마감 일정");
        deadlines.put(deadline.id(), deadline);
    }

    public synchronized void removeDeadline(String id) {
        deadlines.remove(requiredText(id, "마감 일정 ID"));
    }

    public synchronized void toggleDeadline(String id) {
        Deadline deadline = deadlines.get(requiredText(id, "마감 일정 ID"));
        if (deadline == null) throw new IllegalArgumentException("마감 일정을 찾을 수 없습니다.");
        boolean completed = !deadline.completed();
        deadlines.put(deadline.id(), new Deadline(deadline.id(), deadline.title(), deadline.date(),
                deadline.note(), completed, completed ? LocalDate.now() : null));
    }

    /** Merge a shared planner without replacing existing personal records. */
    public void mergeFrom(PlannerModel incoming) {
        Objects.requireNonNull(incoming, "가져올 일정");
        if (incoming == this) return;
        List<Event> sharedEvents;
        List<Pin> sharedPins;
        List<Deadline> sharedDeadlines;
        Map<String, String> sharedNames;
        Set<String> sharedFavorites;
        Set<Cancellation> sharedCancellations;
        Map<LocalDate, String> sharedNotes;
        // Copy before locking this model so two simultaneous reciprocal imports cannot deadlock.
        synchronized (incoming) {
            sharedEvents = incoming.events();
            sharedPins = incoming.pins();
            sharedDeadlines = incoming.deadlines();
            sharedNames = incoming.buildingNames();
            sharedFavorites = incoming.favorites();
            sharedCancellations = incoming.cancellations();
            sharedNotes = incoming.dayNotes();
        }
        synchronized (this) {
            Map<String, String> placeIds = new LinkedHashMap<>();
            for (Pin pin : sharedPins) {
                Pin matching = pins.values().stream().filter(existing -> existing.name().equals(pin.name())
                        && Double.compare(existing.lat(), pin.lat()) == 0
                        && Double.compare(existing.lon(), pin.lon()) == 0).findFirst().orElse(null);
                String id = matching != null ? matching.id()
                        : pins.containsKey(pin.id()) ? "pin:" + UUID.randomUUID() : pin.id();
                placeIds.put(pin.id(), id);
                if (matching == null) pins.put(id, new Pin(id, pin.name(), pin.lat(), pin.lon()));
            }
            Map<String, String> eventIds = new LinkedHashMap<>();
            for (Event event : sharedEvents) {
                String placeId = placeIds.getOrDefault(event.buildingId(), event.buildingId());
                Event candidate = copyEvent(event, event.id(), placeId);
                Event matching = events.values().stream()
                        .filter(existing -> copyEvent(candidate, existing.id(), placeId).equals(existing))
                        .findFirst().orElse(null);
                String id = matching != null ? matching.id()
                        : events.containsKey(event.id()) ? UUID.randomUUID().toString() : event.id();
                eventIds.put(event.id(), id);
                if (matching == null) events.put(id, copyEvent(event, id, placeId));
            }
            for (Deadline deadline : sharedDeadlines) {
                Deadline matching = deadlines.values().stream()
                        .filter(existing -> copyDeadline(deadline, existing.id()).equals(existing))
                        .findFirst().orElse(null);
                if (matching != null) continue;
                String id = deadlines.containsKey(deadline.id()) ? UUID.randomUUID().toString() : deadline.id();
                deadlines.put(id, copyDeadline(deadline, id));
            }
            sharedNames.forEach(buildingNames::putIfAbsent);
            sharedFavorites.forEach(id -> favorites.add(placeIds.getOrDefault(id, id)));
            for (Cancellation cancellation : sharedCancellations) {
                String id = eventIds.get(cancellation.eventId());
                Event event = events.get(id);
                if (event != null && event.occursOn(cancellation.date())) {
                    cancellations.add(new Cancellation(id, cancellation.date()));
                }
            }
            sharedNotes.forEach((date, text) -> dayNotes.merge(date, text, (existing, added) -> {
                if (existing.contains(added)) return existing;
                if (added.contains(existing)) return added;
                return existing + "\n\n---\n\n" + added;
            }));
        }
    }

    private static Event copyEvent(Event event, String id, String placeId) {
        return new Event(id, event.title(), placeId, event.start(), event.end(), event.category(),
                event.weekday(), event.date(), event.note());
    }

    private static Deadline copyDeadline(Deadline deadline, String id) {
        return new Deadline(id, deadline.title(), deadline.date(), deadline.note(),
                deadline.completed(), deadline.completedOn());
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
