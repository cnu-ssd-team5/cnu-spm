package campus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/** 화면과 독립적인 건물/강의 자료 저장 서비스. */
public final class FolderService {
    private final Path root;
    private final Path configuredRoot;

    public FolderService(Path root) throws IOException {
        configuredRoot = root.toAbsolutePath().normalize();
        Files.createDirectories(root);
        this.root = root.toRealPath();
    }

    public Path getRoot() { return root; }

    Path metadataDirectory(String name) throws IOException {
        Path path = root.getParent().resolve(name);
        if (Files.isSymbolicLink(path) || Files.exists(path) && !path.toRealPath().startsWith(root.getParent())) {
            throw new IOException("자료 메타데이터 경로를 확인해 주세요.");
        }
        Files.createDirectories(path);
        return path;
    }

    public Path checked(Path path) throws IOException {
        Path normalized = path.toAbsolutePath().normalize();
        if (normalized.startsWith(configuredRoot)) normalized = root.resolve(configuredRoot.relativize(normalized));
        if (!normalized.startsWith(root)) throw new IOException("자료 폴더 밖에는 접근할 수 없습니다.");
        Path current = normalized;
        while (current != null && current.startsWith(root)) {
            if (Files.isSymbolicLink(current)) throw new IOException("심볼릭 링크는 지원하지 않습니다.");
            if (Files.exists(current) && !current.toRealPath().startsWith(root)) throw new IOException("자료 폴더 밖으로 연결된 경로입니다.");
            current = current.getParent();
        }
        return normalized;
    }

    public static String validName(String name) {
        String value = name.trim();
        if (value.isEmpty() || value.equals(".") || value.equals("..")
                || value.matches(".*[\\\\/:*?\"<>|\\p{Cntrl}].*") || value.endsWith("."))
            throw new IllegalArgumentException("사용할 수 없는 이름입니다.");
        String base = value.split("\\.", 2)[0].toUpperCase(Locale.ROOT);
        if (base.matches("CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]"))
            throw new IllegalArgumentException("Windows 예약 이름은 사용할 수 없습니다.");
        return value;
    }

    public Path createFolder(Path parent, String name) throws IOException {
        return Files.createDirectory(checked(parent.resolve(validName(name))));
    }

    public List<Path> list(Path folder) throws IOException {
        try (java.util.stream.Stream<Path> stream = Files.list(checked(folder))) {
            return stream.filter(p -> !Files.isSymbolicLink(p) && !p.getFileName().toString().startsWith(".")).collect(Collectors.toList());
        }
    }

    /** 동일 이름을 덮어쓰지 않고 (1), (2) 접미사를 붙인다. */
    Path available(Path folder, String name) throws IOException {
        Path result = checked(folder.resolve(name));
        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name;
        String suffix = dot > 0 ? name.substring(dot) : "";
        for (int i = 1; Files.exists(result); i++)
            result = checked(folder.resolve(stem + " (" + i + ")" + suffix));
        return result;
    }

    public Path importFile(Path source, Path folder, boolean move) throws IOException {
        if (Files.isSymbolicLink(source) || !Files.isRegularFile(source))
            throw new IOException("일반 파일만 추가할 수 있습니다: " + source.getFileName());
        checked(folder);
        if (source.toRealPath().getParent().equals(folder.toRealPath()))
            throw new IOException("이미 선택한 폴더에 있는 파일입니다: " + source.getFileName());
        Path target = available(folder, source.getFileName().toString());
        if (move) Files.move(source, target);
        else Files.copy(source, target);
        return target;
    }

    public static String extension(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 && dot < name.length() - 1 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }

    /** UTF-8 메모를 안전하게 저장한다. */
    public Path saveNote(Path folder, Path existing, String name, String text) throws IOException {
        String safe = validName(name);
        if (!safe.toLowerCase(Locale.ROOT).endsWith(".txt")) safe += ".txt";
        Path target = existing == null ? available(folder, safe) : checked(existing);
        Files.createDirectories(checked(folder));
        Path temporary = Files.createTempFile(checked(folder), ".note-", ".tmp");
        try {
            Files.writeString(temporary, text, StandardCharsets.UTF_8);
            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
        return target;
    }
}
