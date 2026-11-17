package campus.folder.storage;

import campus.model.PlannerModel;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** 일정 ID와 자료 위치를 기록하여 건물 변경 시 강의 폴더를 병합한다. */
public final class CourseFolderSync {
    private final FolderService service;
    private final CourseCatalog catalog;
    private final PlannerModel model;
    private final Path state;
    private Properties previous=new Properties();

    public CourseFolderSync(FolderService service,CourseCatalog catalog,PlannerModel model)throws IOException {
        this.service=service;this.catalog=catalog;this.model=model;
        state=service.getRoot().getParent().resolve(".folder-course-links.properties");
        if(Files.exists(state))try(InputStream in=Files.newInputStream(state)){previous.load(in);}
        else previous=current();
    }
    private Properties current() {
        Properties result=new Properties();
        for(var event:model.events())if(event.category()==PlannerModel.Category.CLASS) {
            try {
                result.setProperty(event.id(),Path.of(catalog.storageKey(event.buildingId()),CourseCatalog.folderName(event.title())).toString());
            } catch(IllegalArgumentException ignored) { /* 지도에서 사라진 건물의 자료는 보존한다. */ }
        }
        return result;
    }
    public boolean sync()throws IOException {
        Properties next=current();
        boolean changed=!next.equals(previous);
        Map<String,Set<String>> transfers=new TreeMap<>();
        for(String id:next.stringPropertyNames()) {
            String old=previous.getProperty(id),target=next.getProperty(id);
            if(old==null) {
                String group=groupId(id);
                old=previous.stringPropertyNames().stream().filter(key->groupId(key).equals(group))
                        .sorted().map(previous::getProperty).findFirst().orElse(null);
            }
            if(old!=null&&!old.equals(target))transfers.computeIfAbsent(old,k->new TreeSet<>()).add(target);
        }
        // 여러 일정이 같은 자료 폴더를 사용하면 한 번만 합친다.
        // 기존 건물에도 수업이 남는 경우 원본을 보존하고 새 건물에 복사한다.
        for(var transfer:transfers.entrySet()) {
            Path from=service.checked(service.getRoot().resolve(transfer.getKey()));
            List<String> targets=new ArrayList<>(transfer.getValue());
            boolean retain=next.containsValue(transfer.getKey());
            for(int i=0;i<targets.size();i++) {
                Path to=service.checked(service.getRoot().resolve(targets.get(i)));
                if(Files.exists(from))merge(from,to,retain||i<targets.size()-1);
            }
        }
        for(Object value:new HashSet<>(next.values()))Files.createDirectories(service.checked(service.getRoot().resolve(value.toString())));
        Path temporary=Files.createTempFile(state.getParent(),"course-links-",".tmp");
        try {
            try(OutputStream out=Files.newOutputStream(temporary)){next.store(out,"Course folder locations");}
            Files.move(temporary,state,StandardCopyOption.REPLACE_EXISTING);
            previous=next;
        } finally {Files.deleteIfExists(temporary);}
        return changed;
    }
    private static String groupId(String id) {
        return id.startsWith("tt:")&&id.lastIndexOf(':')>3?id.substring(3,id.lastIndexOf(':')):id;
    }
    private void merge(Path from,Path to,boolean copy)throws IOException {
        service.checked(from);service.checked(to);
        if(from.equals(to))return;
        if(Files.isSymbolicLink(from)||Files.isSymbolicLink(to))throw new IOException("링크 폴더는 병합할 수 없습니다.");
        if(Files.isDirectory(from)) {
            if(Files.exists(to)&&!Files.isDirectory(to))to=service.available(to.getParent(),to.getFileName().toString());
            Files.createDirectories(to);
            try(var children=Files.list(from)) {
                for(Path child:children.toList())merge(child,to.resolve(child.getFileName()),copy);
            }
            if(!copy)Files.delete(from);
        } else {
            Files.createDirectories(to.getParent());
            if(Files.exists(to))to=service.available(to.getParent(),to.getFileName().toString());
            if(copy)Files.copy(from,to);else Files.move(from,to);
        }
    }
}
