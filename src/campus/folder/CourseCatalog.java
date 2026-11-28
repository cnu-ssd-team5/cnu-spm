package campus.folder;
import campus.map.CampusMap;
import campus.model.PlannerModel;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.io.IOException;

/** OSM ID를 저장 키로 사용하며 현재 시간표의 수업을 중복 없이 연결한다. */
public final class CourseCatalog {
    private final CampusMap map;
    private final PlannerModel model;
    private final Map<String,String> storageKeys=new HashMap<>();
    public CourseCatalog(CampusMap map, PlannerModel model) {
        this.map=map; this.model=model;
        Map<String,Integer> counts=new HashMap<>();
        map.buildings().forEach(b->counts.merge(safeBuildingName(map.originalName(b.id())).toLowerCase(Locale.ROOT),1,Integer::sum));
        map.buildings().forEach(b->{
            String name=safeBuildingName(map.originalName(b.id()));
            storageKeys.put(b.id(), b.id().startsWith("pin:") ? "핀 ("+b.id().replace(':','_')+")" : counts.get(name.toLowerCase(Locale.ROOT))>1?name+" ("+b.id().replace(':','_')+")":name);
        });
    }
    public static String key(String id) {
        return "b-"+Base64.getUrlEncoder().withoutPadding().encodeToString(id.getBytes(StandardCharsets.UTF_8));
    }
    public String storageKey(String id) {
        String name=storageKeys.get(id);
        if(name==null) {
            CampusMap.Building building=map.building(id);
            if(building==null)throw new IllegalArgumentException("Unknown building: "+id);
            name=id.startsWith("pin:") ? "핀 ("+id.replace(':','_')+")" : safeBuildingName(map.originalName(id))+" ("+id.replace(':','_')+")";
            storageKeys.put(id,name);
        }
        return name;
    }
    private static String safeBuildingName(String name) {
        String value=name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").strip().replaceAll("[. ]+$", "");
        if(value.length()>100)value=value.substring(0,100);
        try { return FolderService.validName(value); }
        catch(IllegalArgumentException e) { return "건물_"+value; }
    }
    private boolean belongs(String id,String value) { return key(id).equals(value)||storageKey(id).equals(value); }
    public void migrate(FolderService service)throws IOException {
        for(CampusMap.Building b:map.buildings()) {
            Path old=service.checked(service.getRoot().resolve(key(b.id())));
            Path target=service.checked(service.getRoot().resolve(storageKey(b.id())));
            if(Files.exists(old)) {
                if(Files.exists(target))throw new IOException("기존 폴더와 건물명 폴더가 모두 존재합니다: "+target);
                Files.move(old,target);
            }
            Path meta=service.getRoot().getParent().resolve(".folder-recent");
            Path before=meta.resolve(encoded(key(b.id()))+".properties");
            Path after=meta.resolve(encoded(storageKey(b.id()))+".properties");
            if(Files.exists(before)&&!Files.exists(after))Files.move(before,after);
        }
    }
    private static String encoded(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
    public String displayName(String key) {
        return map.buildings().stream().filter(b->belongs(b.id(),key)).map(CampusMap.Building::name).findFirst().orElse(key);
    }
    public List<String> names(String key) {
        return model.events().stream().filter(e->e.category()==PlannerModel.Category.CLASS && map.building(e.buildingId())!=null && belongs(e.buildingId(),key))
                .map(e -> folderName(e.title())).distinct().sorted().toList();
    }
    public Path relativeCoursePath(PlannerModel.Event event) {
        return Path.of(storageKey(event.buildingId()), folderName(event.title()));
    }
    public Path prepareCourse(FolderService service,PlannerModel.Event event)throws IOException {
        Path folder=service.checked(service.getRoot().resolve(relativeCoursePath(event)));
        Files.createDirectories(folder);
        return folder;
    }
    public static String folderName(String title) {
        String value=title.strip();
        try { if(value.length()>100)throw new IllegalArgumentException(); return FolderService.validName(value); }
        catch(IllegalArgumentException e) {
            return "course-"+UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
        }
    }
    public Path prepare(FolderService service,String key)throws IOException {
        String name=map.buildings().stream().filter(b->belongs(b.id(),key)).map(b->storageKey(b.id())).findFirst().orElseThrow();
        Path building=service.checked(service.getRoot().resolve(FolderService.validName(name)));
        for(String course:names(key))Files.createDirectories(service.checked(building.resolve(course)));
        return building;
    }
    public Path initial(Path building) {
        List<String> courses=names(building.getFileName().toString());
        return courses.size()==1 && Files.isDirectory(building.resolve(courses.get(0)))?building.resolve(courses.get(0)):building;
    }
}
