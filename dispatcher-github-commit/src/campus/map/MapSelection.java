package campus.map;

import campus.model.PlannerModel;
import campus.model.PlannerStore;
import campus.places.PlaceService;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** 지도 파일과 학교별 저장 위치를 준비합니다. 화면 교체는 App이 담당합니다. */
public final class MapSelection {
    private static final String SELECTED_MAP_FILE = "selected-map.osm";
    private static final String LEGACY_CHONNAM_ID = "w105125653";

    private MapSelection() { }

    public record Loaded(CampusMap map, RouteService routes, PlannerModel model,
                         Path statePath, Path source, boolean linked) { }

    public static Loaded load(Path defaultState, Path source) throws IOException {
        if (source == null) {
            throw new IOException("사용할 학교의 OSM 파일을 선택해 주세요.");
        }
        CampusMap map = CampusMap.load(source);
        Path statePath = statePathFor(map, defaultState);
        PlannerModel model = PlannerStore.load(statePath);
        boolean linked = PlaceService.applyPlaces(map, model);
        RouteService routes = new RouteService(map);
        return new Loaded(map, routes, model, statePath, source, linked);
    }

    private static Path statePathFor(CampusMap map, Path defaultState) {
        if (usesLegacyStorage(map, defaultState)) {
            return defaultState;
        }
        return defaultState.resolveSibling("maps").resolve(map.campusId()).resolve(defaultState.getFileName());
    }

    /** 이전 전남대 자료는 기존 폴더에 남겨 두어 연결이 끊기지 않게 합니다. */
    private static boolean usesLegacyStorage(CampusMap map, Path defaultState) {
        return LEGACY_CHONNAM_ID.equals(map.campusId())
                && (Files.exists(defaultState)
                    || Files.isDirectory(defaultState.resolveSibling("buildings"))
                    || Files.isDirectory(defaultState.resolveSibling("../Folder/data")));
    }

    private static Path selectedMapPath(Path defaultState) {
        return defaultState.resolveSibling(SELECTED_MAP_FILE);
    }

    public static Path savedMap(Path defaultState) {
        Path file = selectedMapPath(defaultState);
        return Files.exists(file) ? file : null;
    }

    /** 첫 실행에는 기본 OSM을 복사하고, 이후에는 선택해 둔 파일을 읽습니다. */
    public static Path startupMap(Path defaultState) throws IOException {
        Path selected = savedMap(defaultState);
        if (selected != null) {
            return selected;
        }
        selected = selectedMapPath(defaultState);
        try (InputStream input = CampusMap.class.getResourceAsStream(CampusMap.BUNDLED_OSM)) {
            if (input == null) {
                throw new IOException("기본 OSM 리소스를 찾을 수 없습니다.");
            }
            copySelected(selected, input);
        }
        return selected;
    }

    /** 선택한 원본이 이동·삭제되어도 다시 실행할 수 있도록 프로젝트 안에 복사합니다. */
    public static void remember(Path defaultState, Loaded selected) throws IOException {
        Path target = selectedMapPath(defaultState);
        if (selected.source().toAbsolutePath().normalize().equals(target.toAbsolutePath().normalize())) {
            return;
        }
        try (InputStream input = Files.newInputStream(selected.source())) {
            copySelected(target, input);
        }
    }

    private static void copySelected(Path target, InputStream input) throws IOException {
        Files.createDirectories(target.getParent());
        // 복사 도중 실패해도 기존 지도를 보존하려고 임시 파일을 먼저 만듭니다.
        Path temporary = Files.createTempFile(target.getParent(), "selected-map-", ".tmp");
        try {
            Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException error) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
