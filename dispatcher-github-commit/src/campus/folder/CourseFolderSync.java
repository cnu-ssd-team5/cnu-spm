package campus.folder;

import campus.model.PlannerModel;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.regex.Pattern;

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
    }

    private static boolean formerTermFolder(Path name) {
        return name.toString().matches("[0-9]{1,4}-(1학기|2학기|여름방학|겨울방학)");
    }

    private void recoverUnindexedCourses(Properties courses, Map<String, Set<String>> transfers) throws IOException {
        for (Object value : new HashSet<>(courses.values())) {
            Path relative = Path.of(value.toString());
            Path building = service.checked(service.getRoot().resolve(relative.getParent()));
            if (!Files.isDirectory(building)) continue;
            try (var children = Files.list(building)) {
                for (Path child : children.filter(p -> Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)
                        && formerTermFolder(p.getFileName())).toList()) {
                    Path source = service.checked(child.resolve(relative.getFileName()));
                    if (Files.isDirectory(source)) {
                        transfers.computeIfAbsent(service.getRoot().relativize(source).toString(), key -> new TreeSet<>())
                                .add(relative.toString());
                    }
                }
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
        if (Files.notExists(state)) recoverUnindexedCourses(next, transfers);
        // 여러 일정이 같은 자료 폴더를 사용하면 한 번만 합친다.
        // 기존 건물에도 수업이 남는 경우 원본을 보존하고 새 건물에 복사한다.
        for(var transfer:transfers.entrySet()) {
            Path from=service.checked(service.getRoot().resolve(transfer.getKey()));
            List<String> targets=new ArrayList<>(transfer.getValue());
            Path relative = Path.of(transfer.getKey());
            boolean legacy = relative.getNameCount() == 3 && formerTermFolder(relative.getName(1));
            boolean retain = next.containsValue(transfer.getKey());
            if (legacy && Files.isDirectory(from) && targets.stream().map(service.getRoot()::resolve)
                    .anyMatch(to -> from.startsWith(to) || to.startsWith(from))) {
                Path snapshot = legacySnapshot(from, transfer.getKey());
                for (String target : targets) merge(snapshot, service.getRoot().resolve(target), true, true, true);
                continue;
            }
            for (int i = 0; i < targets.size(); i++) {
                Path to = service.checked(service.getRoot().resolve(targets.get(i)));
                if (Files.exists(from)) merge(from, to, legacy || retain || i < targets.size() - 1, legacy, false);
            }
        }
        for(Object value:new HashSet<>(next.values()))Files.createDirectories(service.checked(service.getRoot().resolve(value.toString())));
        if (!changed && Files.isRegularFile(state)) return false;
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
    private void merge(Path from,Path to,boolean copy,boolean legacy,boolean snapshot)throws IOException {
        if(!snapshot)service.checked(from);
        service.checked(to);
        if(from.equals(to))return;
        if(to.startsWith(from)||from.startsWith(to))throw new IOException("자료 폴더를 자기 하위 경로로 옮길 수 없습니다: "+from);
        if(Files.isSymbolicLink(from)||Files.isSymbolicLink(to))throw new IOException("링크 폴더는 병합할 수 없습니다.");
        if(Files.isDirectory(from)) {
            if(Files.exists(to)&&!Files.isDirectory(to))to=service.available(to.getParent(),to.getFileName().toString());
            Files.createDirectories(to);
            try(var children=Files.list(from)) {
                for(Path child:children.toList())merge(child,to.resolve(child.getFileName()),copy,legacy,snapshot);
            }
            if(!copy)Files.delete(from);
        } else {
            Files.createDirectories(to.getParent());
            if (legacy && Files.exists(to) && alreadyCopied(from, to)) return;
            if(Files.exists(to))to=service.available(to.getParent(),to.getFileName().toString());
            if(copy)Files.copy(from,to);else Files.move(from,to);
        }
    }

    private static boolean alreadyCopied(Path from, Path to) throws IOException {
        String name = to.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name;
        String extension = dot > 0 ? name.substring(dot) : "";
        var alternate = Pattern.compile(Pattern.quote(stem)
                + " [(][1-9][0-9]*[)]" + Pattern.quote(extension),
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        try (var siblings = Files.list(to.getParent())) {
            for (Path candidate : siblings.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)
                    && (p.getFileName().toString().equalsIgnoreCase(name)
                    || alternate.matcher(p.getFileName().toString()).matches())).toList()) {
                if (Files.mismatch(from, candidate) == -1) return true;
            }
        }
        return false;
    }
}
