package campus.timetable;

import campus.model.PlannerModel;
import campus.calendar.*;
import campus.model.PlannerModel.Event;
import campus.model.PlannerModel.Category;
import java.time.*;
import java.util.*;

/** 여러 요일을 기존 Event 여러 개로 저장하여 지도·휴강·XML 형식을 유지한다. */
public final class TimetableService {
    private final PlannerModel model;
    private Semester semester;
    private LocalDate weekStart;
    public void weekOf(LocalDate date) {
        weekStart = date.with(DayOfWeek.MONDAY);
        semester = Semester.forDate(date);
    }
    public LocalDate dateFor(DayOfWeek day) {
        return (weekStart == null ? PlannerTime.today().with(DayOfWeek.MONDAY) : weekStart)
                .plusDays(day.getValue() - 1);
    }
    public List<PlannerModel.Occurrence> occurrences(DayOfWeek day) {
        return model.occurrences(dateFor(day));
    }
    public List<Event> visibleEvents() {
        List<Event> result = new ArrayList<>();
        for (DayOfWeek day : DayOfWeek.values())
            occurrences(day).forEach(o -> result.add(o.event()));
        return List.copyOf(result);
    }
    private Semester targetSemester(Event original) {
        Semester existing = original == null || !original.recurring() ? null : model.semesterOf(original.id());
        return existing == null ? semester : existing;
    }
    public Event prepareOnce(Event original, String title, String building, LocalTime start,
                             LocalTime end, Category category, LocalDate date, String note) {
        String id = original != null && !original.recurring() ? original.id() : UUID.randomUUID().toString();
        return new Event(id, title, building, start, end, category, null, date, note);
    }
    public void semester(Semester value) { semester=value; }
    public TimetableService(PlannerModel model) { this.model=model; }
    public List<Event> events() { return (semester==null?model.events():model.events(semester)).stream().filter(Event::recurring).toList(); }
    public List<Event> group(Event event) {
        if(event==null)return List.of();
        if (!event.recurring()) return List.of(event);
        String root=root(event);
        return model.events().stream().filter(Event::recurring)
                .filter(e -> Objects.equals(model.semesterOf(e.id()), model.semesterOf(event.id())))
                .filter(e->e.id().equals(root)||e.id().startsWith("tt:"+root+":")).toList();
    }
    private static String root(Event e) { return e.id().startsWith("tt:")?e.id().substring(3,e.id().lastIndexOf(':')):e.id(); }
    public List<Event> prepare(Event original,String title,String building,LocalTime start,LocalTime end,
                               Category category,Set<DayOfWeek> days,String note) {
        if(days.isEmpty())throw new IllegalArgumentException("요일을 하나 이상 선택하세요.");
        List<Event> old=group(original);
        String prefix="tt:"+(original==null?UUID.randomUUID():root(original))+":";
        List<Event> result=new ArrayList<>();
        for(DayOfWeek day:new TreeSet<>(days)) {
            String id=old.stream().filter(e->e.weekday()==day).map(Event::id).findFirst().orElse(prefix+day.name());
            result.add(new Event(id,title,building,start,end,category,day,null,note));
        }
        return List.copyOf(result);
    }
    public boolean overlaps(Event original,List<Event> proposed) {
        Set<String> excluded=new HashSet<>();group(original).forEach(e->excluded.add(e.id()));
        return proposed.stream().flatMap(p->model.conflicts(p,targetSemester(original)).stream()).anyMatch(e->!excluded.contains(e.id()));
    }
    public void apply(Event original,List<Event> proposed) {
        synchronized(model) {
            Semester term = targetSemester(original);
            List<Event> old=group(original);
            // 새 시간표 그룹은 요일별 ID가 안정적이므로 시간 수정 시 휴강 예외가 유지된다.
            Set<String> ids=new HashSet<>();proposed.forEach(e->ids.add(e.id()));
            old.stream().filter(e->!ids.contains(e.id())).forEach(e->model.removeEvent(e.id()));
            proposed.forEach(e -> {
                if (!e.recurring()) model.putEvent(e, Semester.forDate(e.date()));
                else if (term == null) model.putEvent(e);
                else model.putEvent(e, term);
            });
        }
    }
    public void delete(Event original) { group(original).forEach(e->model.removeEvent(e.id())); }
}
