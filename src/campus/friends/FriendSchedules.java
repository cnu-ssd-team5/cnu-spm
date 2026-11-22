package campus.friends;

import campus.model.PlannerModel;
import campus.model.PlannerStore;
import campus.model.PlannerStore.NamedSchedule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 친구별 일정 파일을 사용하므로 내 일정과 별개로 갱신할 수 있다. */
public final class FriendSchedules {
    public record Friend(UUID id, String name, PlannerModel model) {
        @Override public String toString() { return name; }
    }

    private final Path directory;
    private final Map<UUID, Friend> friends = new LinkedHashMap<>();
    private final List<String> unreadable = new ArrayList<>();

    public FriendSchedules(Path directory) throws IOException {
        this.directory = directory;
        if (Files.notExists(directory)) return;
        try (var files = Files.newDirectoryStream(directory, "*.xml")) {
            for (Path file : files) {
                try {
                    String filename = file.getFileName().toString();
                    UUID id = UUID.fromString(filename.substring(0, filename.length() - 4));
                    NamedSchedule schedule = PlannerStore.loadFriendSchedule(file);
                    friends.put(id, new Friend(id, schedule.name(), schedule.model()));
                } catch (IllegalArgumentException | IOException ex) {
                    unreadable.add(file.getFileName().toString());
                }
            }
        }
    }

    public List<Friend> all() {
        return friends.values().stream().sorted(Comparator.comparing(Friend::name)).toList();
    }

    public List<String> unreadableFiles() { return List.copyOf(unreadable); }

    public Friend find(String name) {
        return all().stream().filter(f -> f.name().equalsIgnoreCase(name.strip())).findFirst().orElse(null);
    }

    public Friend importSchedule(String name, PlannerModel incoming) throws IOException {
        String cleanName = name.strip();
        if (cleanName.isEmpty() || cleanName.codePointCount(0, cleanName.length()) > 40
                || cleanName.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("친구 이름은 1~40자로 입력해 주세요.");
        }
        Friend existing = find(cleanName);
        UUID id = existing == null ? UUID.randomUUID() : existing.id();
        PlannerModel merged = new PlannerModel();
        if (existing != null) merged.mergeFrom(existing.model());
        merged.mergeFrom(incoming);
        Friend saved = new Friend(id, existing == null ? cleanName : existing.name(), merged);
        PlannerStore.saveFriendSchedule(directory.resolve(id + ".xml"), new NamedSchedule(saved.name(), merged));
        friends.put(id, saved);
        return saved;
    }
}
