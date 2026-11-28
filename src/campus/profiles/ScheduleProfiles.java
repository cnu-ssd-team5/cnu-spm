package campus.profiles;

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

/** 이름이 있는 시간표를 개별 XML에 보관한다. */
public final class ScheduleProfiles {
    public record Profile(UUID id, String name, PlannerModel model) {
        @Override public String toString() { return name; }
    }

    private final Path directory;
    private final Map<UUID, Profile> profiles = new LinkedHashMap<>();
    private final List<String> unreadable = new ArrayList<>();

    public ScheduleProfiles(Path directory) throws IOException {
        this.directory = directory;
        if (Files.notExists(directory)) return;
        try (var files = Files.newDirectoryStream(directory, "*.xml")) {
            for (Path file : files) {
                try {
                    String filename = file.getFileName().toString();
                    UUID id = UUID.fromString(filename.substring(0, filename.length() - 4));
                    NamedSchedule schedule = PlannerStore.loadProfileSchedule(file);
                    profiles.put(id, new Profile(id, schedule.name(), schedule.model()));
                } catch (IllegalArgumentException | IOException ex) {
                    unreadable.add(file.getFileName().toString());
                }
            }
        }
    }

    public List<Profile> all() {
        return profiles.values().stream().sorted(Comparator.comparing(Profile::name)).toList();
    }

    public List<String> unreadableFiles() { return List.copyOf(unreadable); }

    public Profile find(String name) {
        return all().stream().filter(f -> f.name().equalsIgnoreCase(name.strip())).findFirst().orElse(null);
    }

    public Path file(Profile profile) { return directory.resolve(profile.id() + ".xml"); }

    public void save(Profile profile) throws IOException {
        PlannerStore.saveProfileSchedule(file(profile), new NamedSchedule(profile.name(), profile.model()));
    }

    public Profile importSchedule(String name, PlannerModel incoming) throws IOException {
        String cleanName = name.strip();
        if (cleanName.isEmpty() || cleanName.codePointCount(0, cleanName.length()) > 40
                || cleanName.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("시간표 이름은 1~40자로 입력해 주세요.");
        }
        Profile existing = find(cleanName);
        if (existing == null && cleanName.equals("기본 시간표"))
            throw new IllegalArgumentException("다른 시간표 이름을 입력해 주세요.");
        UUID id = existing == null ? UUID.randomUUID() : existing.id();
        PlannerModel merged = new PlannerModel();
        if (existing != null) merged.mergeFrom(existing.model());
        merged.mergeFrom(incoming);
        Profile saved = new Profile(id, existing == null ? cleanName : existing.name(), merged);
        save(saved);
        profiles.put(id, saved);
        return saved;
    }
}
