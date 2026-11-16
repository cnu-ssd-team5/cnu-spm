package campus.timetable;

import campus.model.PlannerModel;
import campus.model.PlannerModel.Event;
import campus.model.PlannerModel.Category;
import java.time.*;
import java.util.*;

/** 여러 요일을 기존 Event 여러 개로 저장하여 지도·휴강·XML 형식을 유지한다. */
public final class TimetableService {
    private final PlannerModel model;
    public TimetableService(PlannerModel model) { this.model=model; }
    public List<Event> events() { return model.events().stream().filter(Event::recurring).toList(); }
    public List<Event> group(Event event) {
        if(event==null)return List.of();
        String root=root(event);
        return events().stream().filter(e->e.id().equals(root)||e.id().startsWith("tt:"+root+":")).toList();
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
        return proposed.stream().anyMatch(p->events().stream().anyMatch(e->!excluded.contains(e.id())
                &&p.weekday()==e.weekday()&&p.start().isBefore(e.end())&&e.start().isBefore(p.end())));
    }
    public void apply(Event original,List<Event> proposed) {
        synchronized(model) {
            List<Event> old=group(original);
            // 새 시간표 그룹은 요일별 ID가 안정적이므로 시간 수정 시 휴강 예외가 유지된다.
            Set<String> ids=new HashSet<>();proposed.forEach(e->ids.add(e.id()));
            old.stream().filter(e->!ids.contains(e.id())).forEach(e->model.removeEvent(e.id()));
            proposed.forEach(model::putEvent);
        }
    }
    public void delete(Event original) { group(original).forEach(e->model.removeEvent(e.id())); }
}
