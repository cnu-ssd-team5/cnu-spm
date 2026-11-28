package campus.folder.storage;

import campus.model.PlannerModel;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.BasicFileAttributes;
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
        else {
            // A pre-metadata installation may still have shared building/course folders.
            previous=current();
            for(var event:model.events())if(event.category()==PlannerModel.Category.CLASS) {
                try {
                    Path legacy=Path.of(catalog.storageKey(event.buildingId()),CourseCatalog.folderName(event.title()));
                    if(Files.exists(service.checked(service.getRoot().resolve(legacy))))previous.setProperty(event.id(),legacy.toString());
                } catch(IllegalArgumentException ignored) { }
            }
        }
    }
    private Properties current() {
        Properties result=new Properties();
        for(var event:model.events())if(event.category()==PlannerModel.Category.CLASS) {
            try {
                result.setProperty(event.id(),catalog.relativeCoursePath(event).toString());
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
            boolean legacy=Path.of(transfer.getKey()).getNameCount()==2;
            // Snapshot shared pre-semester data once. The original remains available for recovery.
            // This also permits a legacy course whose name is the new semester directory name.
            if(legacy&&Files.exists(from))from=legacySnapshot(from,transfer.getKey());
            boolean retain=next.containsValue(transfer.getKey());
            for(int i=0;i<targets.size();i++) {
                Path to=service.checked(service.getRoot().resolve(targets.get(i)));
                if(Files.exists(from))merge(from,to,legacy||retain||i<targets.size()-1,legacy);
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
    private Path legacySnapshot(Path source,String relative)throws IOException {
        Path backups=service.getRoot().getParent().resolve(".folder-course-backups");
        if(Files.isSymbolicLink(backups))throw new IOException("자료 백업 경로가 링크입니다.");
        Files.createDirectories(backups);
        Path snapshot=backups.resolve(UUID.nameUUIDFromBytes(relative.getBytes(StandardCharsets.UTF_8)).toString());
        if(Files.isSymbolicLink(snapshot))throw new IOException("자료 백업 경로가 링크입니다.");
        if(Files.isDirectory(snapshot))return snapshot;
        Path temporary=Files.createTempDirectory(backups,"copy-");
        Files.walkFileTree(source,new SimpleFileVisitor<Path>() {
            public FileVisitResult preVisitDirectory(Path directory,BasicFileAttributes attrs)throws IOException {
                service.checked(directory);
                Files.createDirectories(temporary.resolve(source.relativize(directory)));
                return FileVisitResult.CONTINUE;
            }
            public FileVisitResult visitFile(Path file,BasicFileAttributes attrs)throws IOException {
                service.checked(file);
                Files.copy(file,temporary.resolve(source.relativize(file)));
                return FileVisitResult.CONTINUE;
            }
        });
        Files.move(temporary,snapshot);
        return snapshot;
    }
    private void merge(Path from,Path to,boolean copy,boolean legacy)throws IOException {
        if(!legacy)service.checked(from);
        service.checked(to);
        if(from.equals(to))return;
        if(to.startsWith(from)||from.startsWith(to))throw new IOException("자료 폴더를 자기 하위 경로로 옮길 수 없습니다: "+from);
        if(Files.isSymbolicLink(from)||Files.isSymbolicLink(to))throw new IOException("링크 폴더는 병합할 수 없습니다.");
        if(Files.isDirectory(from)) {
            if(Files.exists(to)&&!Files.isDirectory(to))to=service.available(to.getParent(),to.getFileName().toString());
            Files.createDirectories(to);
            try(var children=Files.list(from)) {
                for(Path child:children.toList())merge(child,to.resolve(child.getFileName()),copy,legacy);
            }
            if(!copy)Files.delete(from);
        } else {
            Files.createDirectories(to.getParent());
            if(legacy&&Files.isRegularFile(to)&&Files.mismatch(from,to)==-1)return;
            if(Files.exists(to))to=service.available(to.getParent(),to.getFileName().toString());
            if(copy)Files.copy(from,to);else Files.move(from,to);
        }
    }
}
