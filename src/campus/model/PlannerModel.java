package campus.model;
import campus.calendar.Semester;
import campus.calendar.PlannerTime;


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

        public static Category fromLabel(String label) {
            for (Category category : values()) {
                if (category.toString().equals(label)) return category;
            }
            return OTHER;
        }
    }

    public record Event(String id, String title, String buildingId,
                        LocalTime start, LocalTime end, Category category,
                        DayOfWeek weekday, LocalDate date, String note,
                        String customCategory, String customPlace) {
        public static final String CUSTOM_PLACE_ID = "custom-place";

        public Event(String id, String title, String buildingId,
                     LocalTime start, LocalTime end, Category category,
                     DayOfWeek weekday, LocalDate date, String note) {
            this(id, title, buildingId, start, end, category, weekday, date, note, "", "");
        }

        public Event {
            id = requiredText(id, "일정 ID");
            title = requiredText(title, "일정 이름");
            customCategory = Objects.requireNonNullElse(customCategory, "").strip();
            customPlace = Objects.requireNonNullElse(customPlace, "").strip();
            buildingId = customPlace.isEmpty() ? requiredText(buildingId, "장소") : CUSTOM_PLACE_ID;
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

        public String categoryLabel() {
            return customCategory.isEmpty() ? category.toString() : customCategory;
        }

        public boolean recurring() {
            return weekday != null;
        }

        public String recurringGroup() {
            return id.startsWith("tt:") && id.lastIndexOf(':') > 3 ? id.substring(3, id.lastIndexOf(':')) : id;
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

    public enum TaskPriority {
        HIGH("높음"), NORMAL("보통"), LOW("낮음");

        private final String label;
        TaskPriority(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    public record Deadline(String id, String title, LocalDate date, String note,
                           boolean completed, LocalDate completedOn, TaskPriority priority,
                           LocalDate plannerDate, boolean deadlineLinked) {
        public Deadline {
            id = requiredText(id, "마감 일정 ID");
            title = requiredText(title, "마감 일정 이름");
            Objects.requireNonNull(date, "마감 날짜");
            Objects.requireNonNull(plannerDate, "플래너 날짜");
            if (!deadlineLinked) date = plannerDate;
            note = Objects.requireNonNullElse(note, "");
            priority = Objects.requireNonNullElse(priority, TaskPriority.NORMAL);
            if (!completed) completedOn = null;
        }

        public Deadline(String id, String title, LocalDate date, String note, boolean completed,
                        LocalDate completedOn, TaskPriority priority) {
            this(id, title, date, note, completed, completedOn, priority, date, true);
        }

        public Deadline(String id, String title, LocalDate date, String note, boolean completed, LocalDate completedOn) {
            this(id, title, date, note, completed, completedOn, TaskPriority.NORMAL);
        }

        public Deadline(String id, String title, LocalDate date, String note, boolean completed) {
            this(id, title, date, note, completed, completed ? PlannerTime.today() : null);
        }
    }

    record Cancellation(String eventId, LocalDate date) { }

    private static final Comparator<Event> EVENT_ORDER = Comparator.comparing(Event::start)
            .thenComparing(Event::end).thenComparing(Event::title).thenComparing(Event::id);

    private final Map<String, Event> events = new LinkedHashMap<>();
    private final Map<String, Semester> eventSemesters = new LinkedHashMap<>();
    private final Set<String> favorites = new LinkedHashSet<>();
    private final Set<Cancellation> cancellations = new LinkedHashSet<>();
    private final Map<LocalDate, String> dayNotes = new LinkedHashMap<>();
    private final Map<String, String> buildingNames = new LinkedHashMap<>();
    private final Map<String, String> buildingNotes = new LinkedHashMap<>();
    private final Map<String, Pin> pins = new LinkedHashMap<>();
    private final Map<String, Deadline> deadlines = new LinkedHashMap<>();

    public PlannerModel() { }

    public synchronized List<Event> events() {
        return List.copyOf(events.values());
    }

    public synchronized List<Event> events(Semester semester) {
        Objects.requireNonNull(semester, "학기");
        return events.values().stream().filter(event -> eventSemesters.get(event.id()) == null
                || semester.equals(eventSemesters.get(event.id()))).toList();
    }

    public synchronized Semester semesterOf(String eventId) {
        return eventSemesters.get(eventId);
    }

    public synchronized List<Semester> semesters() {
        return eventSemesters.values().stream().distinct()
                .sorted(Comparator.comparingInt(Semester::year).thenComparing(Semester::season)).toList();
    }

    public synchronized List<Occurrence> occurrences(LocalDate date) {
        Objects.requireNonNull(date, "날짜");
        List<Event> matching = events.values().stream()
                .filter(event -> occursOn(event, date)).sorted(EVENT_ORDER).toList();
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

    public synchronized void putEvent(Event event, Semester semester) {
        Objects.requireNonNull(semester, "학기");
        putEvent(event);
        eventSemesters.put(event.id(), semester);
    }

    public synchronized void removeEvent(String id) {
        String eventId = requiredText(id, "일정 ID");
        events.remove(eventId);
        eventSemesters.remove(eventId);
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
        if (event == null || !occursOn(event, date)) {
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

    public synchronized String buildingNote(String id) {
        return buildingNotes.getOrDefault(id, "");
    }

    public synchronized void setBuildingNote(String id, String note) {
        String key = requiredText(id, "건물 ID");
        if (note.isBlank()) buildingNotes.remove(key);
        else buildingNotes.put(key, note);
    }

    synchronized Map<String, String> buildingNotes() {
        return Map.copyOf(buildingNotes);
    }

    public synchronized List<Pin> pins() {
        return List.copyOf(pins.values());
    }

    public synchronized void putPin(Pin pin) {
        Objects.requireNonNull(pin, "핀");
        pins.put(pin.id(), pin);
    }

    public synchronized void removePin(String id) {
        String pinId = requiredText(id, "핀 ID");
        if (pins.remove(pinId) == null) return;
        favorites.remove(pinId);
        buildingNames.remove(pinId);
        List<String> related = events.values().stream().filter(event -> event.buildingId().equals(pinId))
                .map(Event::id).toList();
        related.forEach(this::removeEvent);
    }

    public synchronized List<Event> conflicts(Event candidate, Semester semester) {
        Objects.requireNonNull(candidate, "일정");
        Semester candidateSemester = semester == null ? eventSemesters.get(candidate.id()) : semester;
        return events.values().stream()
                .filter(event -> !event.id().equals(candidate.id()))
                .filter(event -> candidate.start().isBefore(event.end()) && event.start().isBefore(candidate.end()))
                .filter(event -> overlapOnDate(candidate, candidateSemester, event))
                .sorted(EVENT_ORDER).toList();
    }

    private boolean occursOn(Event event, LocalDate date) {
        Semester semester = eventSemesters.get(event.id());
        return event.occursOn(date) && (!event.recurring() || semester == null || semester.contains(date));
    }

    private boolean activeOn(Event event, Semester semester, LocalDate date) {
        return event.occursOn(date) && (!event.recurring() || semester == null || semester.contains(date))
                && !cancellations.contains(new Cancellation(event.id(), date));
    }

    private boolean overlapOnDate(Event candidate, Semester candidateSemester, Event existing) {
        Semester existingSemester = eventSemesters.get(existing.id());
        if (!candidate.recurring()) {
            return activeOn(candidate, candidateSemester, candidate.date())
                    && activeOn(existing, existingSemester, candidate.date());
        }
        if (!existing.recurring()) {
            return activeOn(candidate, candidateSemester, existing.date())
                    && activeOn(existing, existingSemester, existing.date());
        }
        if (candidate.weekday() != existing.weekday()) return false;
        // Finite date exceptions cannot cancel every week of two unbounded schedules.
        if (candidateSemester == null && existingSemester == null) return true;
        Semester window = candidateSemester != null ? candidateSemester
                : existingSemester != null ? existingSemester : Semester.forDate(PlannerTime.today());
        for (LocalDate date = window.start(); !date.isAfter(window.end()); date = date.plusDays(1)) {
            if (activeOn(candidate, candidateSemester, date) && activeOn(existing, existingSemester, date)) return true;
        }
        return false;
    }

    public synchronized List<Deadline> deadlines() {
        return deadlines.values().stream().filter(Deadline::deadlineLinked).sorted(Comparator.comparing(Deadline::date)
                .thenComparing(Deadline::completed).thenComparing(Deadline::title)
                .thenComparing(Deadline::id)).toList();
    }

    public synchronized List<Deadline> deadlines(LocalDate date) {
        Objects.requireNonNull(date, "마감 날짜");
        return deadlines().stream().filter(deadline -> deadline.date().equals(date)).toList();
    }

    public synchronized List<Deadline> plannerTasks(LocalDate date) {
        Objects.requireNonNull(date, "플래너 날짜");
        return deadlines.values().stream().filter(task -> task.plannerDate().equals(date)).toList();
    }

    synchronized List<Deadline> allTasks() {
        return List.copyOf(deadlines.values());
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
                deadline.note(), completed, completed ? PlannerTime.today() : null, deadline.priority(),
                deadline.plannerDate(), deadline.deadlineLinked()));
    }

    /** Merge a shared planner without replacing existing personal records. */
    public void mergeFrom(PlannerModel incoming) {
        Objects.requireNonNull(incoming, "가져올 일정");
        if (incoming == this) return;
        List<Event> sharedEvents;
        Map<String, Semester> sharedSemesters;
        List<Pin> sharedPins;
        List<Deadline> sharedDeadlines;
        Map<String, String> sharedNames;
        Set<String> sharedFavorites;
        Set<Cancellation> sharedCancellations;
        Map<LocalDate, String> sharedNotes;
        // Copy before locking this model so two simultaneous reciprocal imports cannot deadlock.
        synchronized (incoming) {
            sharedEvents = incoming.events();
            sharedSemesters = incoming.eventSemesters();
            sharedPins = incoming.pins();
            sharedDeadlines = incoming.allTasks();
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
            mergeCourseGroups(sharedEvents, sharedSemesters, placeIds, eventIds);
            for (Event event : sharedEvents) {
                if (eventIds.containsKey(event.id())) continue;
                String placeId = placeIds.getOrDefault(event.buildingId(), event.buildingId());
                Event candidate = copyEvent(event, event.id(), placeId);
                Event matching = events.values().stream()
                        .filter(existing -> copyEvent(candidate, existing.id(), placeId).equals(existing))
                        .filter(existing -> Objects.equals(eventSemesters.get(existing.id()), sharedSemesters.get(event.id())))
                        .findFirst().orElse(null);
                String id = matching != null ? matching.id()
                        : events.containsKey(event.id()) ? UUID.randomUUID().toString() : event.id();
                eventIds.put(event.id(), id);
                if (matching == null) {
                    events.put(id, copyEvent(event, id, placeId));
                    Semester semester = sharedSemesters.get(event.id());
                    if (semester != null) eventSemesters.put(id, semester);
                }
            }
            for (Deadline deadline : sharedDeadlines) {
                Deadline matching = deadlines.values().stream()
                        .filter(existing -> copyDeadline(deadline, existing.id()).equals(existing))
                        .findFirst().orElse(null);
                if (matching != null) continue;
                String id = deadlines.containsKey(deadline.id()) ? UUID.randomUUID().toString() : deadline.id();
                deadlines.put(id, copyDeadline(deadline, id));
            }
            sharedNames.forEach((id,name)->buildingNames.putIfAbsent(placeIds.getOrDefault(id,id),name));
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
                event.weekday(), event.date(), event.note(), event.customCategory(), event.customPlace());
    }

    private record SharedGroup(String root, Semester semester) { }

    private void mergeCourseGroups(List<Event> shared, Map<String, Semester> semesters,
                                   Map<String, String> places, Map<String, String> ids) {
        Map<SharedGroup, List<Event>> groups = new LinkedHashMap<>();
        for (Event event : shared) {
            if (!event.recurring()) continue;
            boolean grouped = event.id().startsWith("tt:") || shared.stream()
                    .anyMatch(other -> other.recurring() && other.id().startsWith("tt:" + event.id() + ":"));
            if (grouped) groups.computeIfAbsent(new SharedGroup(event.recurringGroup(), semesters.get(event.id())),
                    ignored -> new ArrayList<>()).add(event);
        }
        for (Map.Entry<SharedGroup, List<Event>> entry : groups.entrySet()) {
            Semester semester = entry.getKey().semester();
            List<Event> proposed = entry.getValue().stream().map(event -> copyEvent(event, event.id(),
                    places.getOrDefault(event.buildingId(), event.buildingId()))).toList();
            Map<String, List<Event>> candidates = new LinkedHashMap<>();
            events.values().stream().filter(Event::recurring)
                    .filter(event -> Objects.equals(eventSemesters.get(event.id()), semester))
                    .forEach(event -> candidates.computeIfAbsent(event.recurringGroup(), ignored -> new ArrayList<>()).add(event));
            String targetRoot = null;
            // Prefer an already imported whole group, preserving idempotence after ID remapping.
            for (Map.Entry<String, List<Event>> candidate : candidates.entrySet()) {
                if (proposed.stream().allMatch(event -> matchingEvent(event, candidate.getValue()) != null)) {
                    targetRoot = candidate.getKey(); break;
                }
            }
            if (targetRoot == null) {
                List<Event> sameRoot = candidates.get(entry.getKey().root());
                if (sameRoot != null && proposed.stream().allMatch(event -> sameRoot.stream().allMatch(old ->
                        event.title().equals(old.title()) && event.buildingId().equals(old.buildingId())
                        && event.start().equals(old.start()) && event.end().equals(old.end())
                        && event.category() == old.category() && event.note().equals(old.note())
                        && event.customCategory().equals(old.customCategory())
                        && event.customPlace().equals(old.customPlace())))) {
                    targetRoot = entry.getKey().root();
                }
            }
            if (targetRoot == null) {
                String incomingRoot = entry.getKey().root();
                boolean collision = events.values().stream().anyMatch(event -> event.recurringGroup().equals(incomingRoot));
                targetRoot = collision ? UUID.randomUUID().toString() : incomingRoot;
            }
            List<Event> existingGroup = candidates.getOrDefault(targetRoot, List.of());
            for (Event event : proposed) {
                Event matching = matchingEvent(event, existingGroup);
                String targetId = matching != null ? matching.id()
                        : targetRoot.equals(entry.getKey().root()) && !events.containsKey(event.id()) ? event.id()
                        : "tt:" + targetRoot + ":" + event.weekday().name();
                ids.put(event.id(), targetId);
                if (matching == null) {
                    events.put(targetId, copyEvent(event, targetId, event.buildingId()));
                    if (semester != null) eventSemesters.put(targetId, semester);
                }
            }
        }
    }

    private static boolean sameEvent(Event left, Event right) {
        return copyEvent(left, right.id(), left.buildingId()).equals(right);
    }
    private static Event matchingEvent(Event candidate, List<Event> events) {
        return events.stream().filter(event -> sameEvent(candidate, event)).findFirst().orElse(null);
    }

    private static Deadline copyDeadline(Deadline deadline, String id) {
        return new Deadline(id, deadline.title(), deadline.date(), deadline.note(),
                deadline.completed(), deadline.completedOn(), deadline.priority(),
                deadline.plannerDate(), deadline.deadlineLinked());
    }

    synchronized Set<Cancellation> cancellations() {
        return Set.copyOf(cancellations);
    }

    synchronized Map<String, Semester> eventSemesters() {
        return Map.copyOf(eventSemesters);
    }

    synchronized void restoreCancellation(String eventId, LocalDate date) {
        Event event = events.get(eventId);
        if (event != null && event.occursOn(date)) cancellations.add(new Cancellation(eventId, date));
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
