package campus;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

/** 세션 내 파일 작업 실행 취소. 삭제/생성 취소 파일은 자료함 외부 보관함으로 이동한다. */
public final class FileOperations {
    private interface Undo { void run() throws IOException; }
    private final Deque<Deque<Undo>> history = new ArrayDeque<>();
    private final FolderService service;
    private final Path trash;
    public FileOperations(FolderService service) throws IOException {
        this.service = service;
        trash = service.metadataDirectory(".folder-trash");
    }
    public boolean canUndo() { return !history.isEmpty(); }
    /** 폴더 생성과 선택 항목 이동을 한 번의 실행 취소로 묶는다. */
    public Path createFolderWithSelection(Path parent, String name, List<Path> selected) throws IOException {
        Path directory = service.checked(parent);
        Path target = service.checked(directory.resolve(FolderService.validName(name)));
        List<Path> sources = new ArrayList<>();
        for (Path item : selected) {
            Path source = service.checked(item);
            if (!directory.equals(source.getParent()) || !Files.exists(source))
                throw new IOException("선택한 항목이 현재 폴더에 없습니다: " + source.getFileName());
            if (!sources.contains(source)) sources.add(source);
        }
        Files.createDirectories(directory);
        Files.createDirectory(target);
        Deque<Undo> steps = new ArrayDeque<>();
        steps.push(() -> Files.delete(service.checked(target)));
        history.push(steps);
        for (Path source : sources) {
            Path moved = service.checked(target.resolve(source.getFileName()));
            Files.move(source, moved);
            steps.push(() -> Files.move(service.checked(moved), service.checked(source)));
        }
        return target;
    }
    public void undo() throws IOException {
        if (history.isEmpty()) return;
        Deque<Undo> steps = history.peek();
        while (!steps.isEmpty()) { steps.peek().run(); steps.pop(); }
        history.pop();
    }
    private void stash(Path p) throws IOException {
        Files.move(service.checked(p), trash.resolve(UUID.randomUUID() + "_" + p.getFileName()));
    }
    public void created(Path p) {
        Deque<Undo> steps = new ArrayDeque<>(); steps.push(() -> stash(p)); history.push(steps);
    }
    public void rename(Path from, Path to) throws IOException {
        Path source = service.checked(from), target = service.checked(to);
        if (source.equals(service.getRoot())) throw new IOException("자료함 루트는 이름을 바꿀 수 없습니다.");
        Files.move(source, target);
        Deque<Undo> steps = new ArrayDeque<>(); steps.push(() -> Files.move(service.checked(target), service.checked(source))); history.push(steps);
    }
    private List<Path> roots(List<Path> paths) {
        paths = paths.stream().map(path -> path.toAbsolutePath().normalize()).toList();
        List<Path> result = new ArrayList<>();
        for (Path p : paths) {
            boolean child = false;
            for (Path q : paths) if (!p.equals(q) && p.startsWith(q)) { child = true; break; }
            if (!child && !result.contains(p)) result.add(p);
        }
        return result;
    }
    public void delete(List<Path> paths) throws IOException {
        Deque<Undo> steps = new ArrayDeque<>(); history.push(steps);
        for (Path p : roots(paths)) {
            Path source = service.checked(p);
            if (source.equals(service.getRoot())) throw new IOException("Cannot delete storage root");
            Path saved = trash.resolve(UUID.randomUUID() + "_" + source.getFileName());
            Files.move(source, saved);
            steps.push(() -> Files.move(saved, service.checked(source)));
        }
    }
    public void paste(List<Path> paths, Path destination, boolean cut) throws IOException {
        destination = service.checked(destination);
        List<Path> sources = roots(paths);
        if(sources.isEmpty())return;
        for (Path source : sources) {
            if (!Files.exists(source) || Files.isSymbolicLink(source)) throw new IOException("Unavailable source: " + source);
            if (cut && source.toAbsolutePath().normalize().startsWith(service.getRoot())) service.checked(source);
            if (Files.isDirectory(source) && destination.startsWith(source.toRealPath())) throw new IOException("폴더를 자기 자신 또는 하위 폴더에 붙여넣을 수 없습니다.");
        }
        Files.createDirectories(destination);
        Deque<Undo> steps = new ArrayDeque<>(); history.push(steps);
        for (Path source : sources) {
            if (cut && source.getParent().toRealPath().equals(destination.toRealPath())) continue;
            Path target = service.available(destination, source.getFileName().toString());
            if (cut) {
                Files.move(source, target);
                steps.push(() -> Files.move(service.checked(target), source));
            } else {
                // 실패한 복사도 취소할 수 있게 생성 직후 기록한다.
                if (Files.isDirectory(source)) {
                    Files.createDirectory(target); steps.push(() -> stash(target));
                    Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
                        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                            if (!dir.equals(source)) Files.createDirectory(target.resolve(source.relativize(dir)));
                            return FileVisitResult.CONTINUE;
                        }
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                            if (attrs.isSymbolicLink()) throw new IOException("Symbolic links are not supported");
                            Files.copy(file, target.resolve(source.relativize(file))); return FileVisitResult.CONTINUE;
                        }
                    });
                } else { Files.copy(source, target); steps.push(() -> stash(target)); }
            }
        }
    }
}
