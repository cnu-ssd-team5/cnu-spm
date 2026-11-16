package campus.model;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;

/** Run with java -cp build/classes:build/test-classes campus.PlannerModelCheck. */
public final class PlannerModelCheck {
    private static int checks;
    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 16);

    private PlannerModelCheck() { }

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("campus-model-check-");
        try {
            Path state = directory.resolve("planner.xml");
            Files.writeString(state, """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <campus-planner version="1">
                      <events>
                        <event id="class" title="운영체제" buildingId="w1" start="09:00" end="10:00" category="CLASS" weekday="MONDAY">
                          <note>302호 &amp; 노트북</note>
                        </event>
                        <event id="meeting" title="조별 모임" buildingId="w2" start="13:00" end="14:00" date="2026-11-16"><note/></event>
                      </events>
                      <favorites><building id="w1"/></favorites>
                      <cancellations><occurrence eventId="class" date="2026-11-16"/></cancellations>
                      <day-notes><day date="2026-11-16">준비물 &amp; 메모</day></day-notes>
                    </campus-planner>
                    """, StandardCharsets.UTF_8);
            PlannerModel model = PlannerStore.load(state);
            check(model.events().size() == 2, "legacy events");
            check(model.events().get(0).note().equals("302호 & 노트북"), "legacy note escaping");
            check(model.events().get(1).category() == PlannerModel.Category.OTHER, "legacy category default");
            check(model.occurrences(MONDAY).get(0).canceled(), "legacy date-only cancellation");
            check(!model.occurrences(MONDAY.plusWeeks(1)).get(0).canceled(), "next week retained");
            check(model.isFavorite("w1"), "legacy favorite");
            check(model.dayNote(MONDAY).equals("준비물 & 메모"), "legacy daily note");
            check(model.pins().isEmpty() && model.deadlines().isEmpty()
                    && model.buildingNames().isEmpty(), "legacy optional collections");

            model.renameBuilding("w3", "내 강의실 & 연구실");
            check(model.customName("w3").equals("내 강의실 & 연구실"), "custom building name");
            check(model.customName("absent").isEmpty(), "unknown custom name");
            PlannerModel.Pin pin = new PlannerModel.Pin("pin:meeting", "만나는 곳", 35.1764, 126.9061);
            model.putPin(pin);
            model.putPin(new PlannerModel.Pin(pin.id(), "중앙 모임 장소", pin.lat(), pin.lon()));
            check(model.pins().size() == 1 && model.pins().get(0).name().equals("중앙 모임 장소"), "pin update keeps id");
            model.putEvent(new PlannerModel.Event("pin-event", "야외 모임", pin.id(),
                    LocalTime.of(15, 0), LocalTime.of(16, 0), PlannerModel.Category.GROUP,
                    null, MONDAY, "분수 앞\n자료 확인"));
            check(model.occurrences(MONDAY).get(2).event().buildingId().equals(pin.id()), "pin schedule compatibility");

            LocalDate submitted = MONDAY.minusDays(2);
            model.putDeadline(new PlannerModel.Deadline("task", "자료구조 과제", MONDAY, "제출 파일 & 설명\n두 번째 줄", false));
            model.putDeadline(new PlannerModel.Deadline("done", "발표 자료", MONDAY, "", true, submitted));
            check(model.deadlines(MONDAY).size() == 2, "deadlines by due date");
            check(!model.deadlines(MONDAY).get(0).completed(), "unsubmitted tasks first");
            check(model.deadlines(MONDAY.plusDays(1)).isEmpty(), "deadline date isolation");
            model.toggleDeadline("task");
            PlannerModel.Deadline task = deadline(model, "task");
            check(task.completed() && LocalDate.now().equals(task.completedOn()), "submission records actual day");
            model.putDeadline(new PlannerModel.Deadline(task.id(), "자료구조 과제 수정", task.date(),
                    task.note(), task.completed(), task.completedOn()));
            check(deadline(model, "task").completedOn().equals(task.completedOn()), "edit preserves completion day");
            model.toggleDeadline("task");
            check(!deadline(model, "task").completed() && deadline(model, "task").completedOn() == null,
                    "submission undo clears completion day");
            check(deadline(model, "done").completedOn().equals(submitted), "supplied completion day preserved");
            PlannerModel.Deadline pending = new PlannerModel.Deadline("normalized", "정리", MONDAY, null, false, submitted);
            check(pending.note().isEmpty() && pending.completedOn() == null, "pending completion normalization");

            PlannerStore.save(state, model);
            String xml = Files.readString(state);
            check(xml.contains("version=\"2\""), "new version written");
            PlannerModel restored = PlannerStore.load(state);
            check(restored.events().equals(model.events()), "all events round-trip");
            check(restored.favorites().equals(model.favorites()), "favorites round-trip");
            check(restored.dayNote(MONDAY).equals(model.dayNote(MONDAY)), "day notes round-trip");
            check(restored.occurrences(MONDAY).get(0).canceled(), "cancellations round-trip");
            check(restored.buildingNames().equals(model.buildingNames()), "custom names round-trip");
            check(restored.pins().equals(model.pins()), "pins round-trip");
            check(restored.deadlines().equals(model.deadlines()), "deadlines including submission dates round-trip");
            model.setDayNote(MONDAY, "두 번째 저장");
            PlannerStore.save(state, model);
            check(PlannerStore.load(state).dayNote(MONDAY).equals("두 번째 저장"), "atomic replacement");

            rejectsArgument(() -> new PlannerModel.Pin("pin:bad", "위치", Double.NaN, 126), "NaN latitude");
            rejectsArgument(() -> new PlannerModel.Pin("pin:bad", "위치", 91, 126), "latitude range");
            rejectsArgument(() -> new PlannerModel.Pin("pin:bad", "위치", 35, -181), "longitude range");
            rejectsArgument(() -> new PlannerModel.Pin("pin:bad", "위치", 35, Double.POSITIVE_INFINITY), "infinite longitude");
            rejectsArgument(() -> new PlannerModel.Pin("pin:bad", " ", 35, 126), "blank pin name");
            rejectsArgument(() -> new PlannerModel.Pin("w123", "위치", 35, 126), "pin id cannot shadow OSM building");
            rejectsArgument(() -> model.renameBuilding("w3", " "), "blank custom name");
            rejectsArgument(() -> new PlannerModel.Deadline("bad", " ", MONDAY, "", false), "blank task title");
            rejectsArgument(() -> model.toggleDeadline("missing"), "unknown task toggle");
            check(model.pins().size() == 1 && model.buildingNames().size() == 1, "invalid mutations preserve state");

            byte[] goodState = Files.readAllBytes(state);
            Path blocked = Files.createDirectory(directory.resolve("blocked.xml"));
            rejectsIo(() -> PlannerStore.save(blocked, model), "failed save is reported");
            check(Files.isDirectory(blocked) && Arrays.equals(goodState, Files.readAllBytes(state)), "failed save leaves other state intact");
            try (var files = Files.list(directory)) {
                check(files.noneMatch(path -> path.getFileName().toString().startsWith(".campus-planner-")), "temporary save cleaned");
            }

            Path invalid = directory.resolve("invalid.xml");
            Files.writeString(invalid, "<campus-planner version=\"99\"/>");
            rejectsIo(() -> PlannerStore.load(invalid), "future version rejected");
            Files.writeString(invalid, "<campus-planner version=\"2\"><pins><pin id=\"pin:p\" name=\"x\" lat=\"NaN\" lon=\"126\"/></pins></campus-planner>");
            rejectsIo(() -> PlannerStore.load(invalid), "invalid stored coordinates rejected");
            Files.writeString(invalid, "<campus-planner version=\"2\"><deadlines><deadline id=\"d\" title=\"x\" date=\"2026-11-16\" completed=\"invalid\"/></deadlines></campus-planner>");
            rejectsIo(() -> PlannerStore.load(invalid), "invalid submission state rejected");
            Files.writeString(invalid, "<campus-planner version=\"2\"><deadlines><deadline id=\"d\" title=\"x\" date=\"2026-11-16\" completed=\"true\"/></deadlines></campus-planner>");
            PlannerModel legacyCompleted = PlannerStore.load(invalid);
            check(legacyCompleted.deadlines().get(0).completedOn() == null, "legacy completion date remains unknown");
            Files.writeString(invalid, "<campus-planner");
            byte[] corrupt = Files.readAllBytes(invalid);
            rejectsIo(() -> PlannerStore.load(invalid), "corrupt state rejected");
            check(Arrays.equals(corrupt, Files.readAllBytes(invalid)), "failed load leaves original bytes intact");
            Files.writeString(invalid, "<!DOCTYPE state [<!ENTITY xxe SYSTEM 'file:///unavailable'>]><campus-planner version=\"2\">&xxe;</campus-planner>");
            rejectsIo(() -> PlannerStore.load(invalid), "external entities rejected");
            model.removeDeadline("task");
            check(model.deadlines().size() == 1, "task removal");
            checkMerge(directory);
            System.out.println("PASS " + checks + " planner model and storage regression checks");
        } finally {
            try (var files = Files.walk(directory)) {
                for (Path path : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }

    private static PlannerModel.Deadline deadline(PlannerModel model, String id) {
        return model.deadlines().stream().filter(value -> value.id().equals(id)).findFirst().orElseThrow();
    }

    private static void checkMerge(Path directory) throws Exception {
        PlannerModel personal = new PlannerModel();
        personal.putPin(new PlannerModel.Pin("pin:shared-id", "내 모임 장소", 35.17, 126.90));
        personal.putEvent(new PlannerModel.Event("shared-event", "내 수업", "pin:shared-id",
                LocalTime.of(9, 0), LocalTime.of(10, 0), PlannerModel.Category.CLASS,
                java.time.DayOfWeek.MONDAY, null, "개인 메모"));
        personal.putDeadline(new PlannerModel.Deadline("shared-task", "내 과제", MONDAY, "", true, MONDAY.minusDays(1)));
        personal.renameBuilding("w1", "내 건물명");
        personal.setDayNote(MONDAY, "내 준비물");

        PlannerModel incoming = new PlannerModel();
        incoming.putPin(new PlannerModel.Pin("pin:shared-id", "공유 모임 장소", 35.18, 126.91));
        incoming.putEvent(new PlannerModel.Event("shared-event", "공유 수업", "pin:shared-id",
                LocalTime.of(11, 0), LocalTime.of(12, 0), PlannerModel.Category.CLASS,
                java.time.DayOfWeek.MONDAY, null, "공유 메모"));
        incoming.toggleCancellation("shared-event", MONDAY);
        incoming.toggleFavorite("pin:shared-id");
        incoming.putDeadline(new PlannerModel.Deadline("shared-task", "공유 과제", MONDAY, "함께 제출", true, MONDAY.minusDays(2)));
        incoming.renameBuilding("w1", "공유 건물명");
        incoming.renameBuilding("w2", "새로운 공유 건물명");
        incoming.setDayNote(MONDAY, "공유 준비물");

        personal.mergeFrom(incoming);
        check(personal.pins().size() == 2, "import keeps conflicting pin");
        PlannerModel.Pin importedPin = personal.pins().stream()
                .filter(pin -> pin.name().equals("공유 모임 장소")).findFirst().orElseThrow();
        check(!importedPin.id().equals("pin:shared-id") && importedPin.id().startsWith("pin:"), "import assigns new pin id");
        check(personal.events().size() == 2, "import keeps conflicting event");
        PlannerModel.Event importedEvent = personal.events().stream()
                .filter(event -> event.title().equals("공유 수업")).findFirst().orElseThrow();
        check(importedEvent.buildingId().equals(importedPin.id()) && !importedEvent.id().equals("shared-event"), "import remaps event and pin references");
        check(personal.isFavorite(importedPin.id()), "import remaps favorite pin");
        check(!personal.occurrences(MONDAY).get(0).canceled()
                && personal.occurrences(MONDAY).get(1).canceled(), "import remaps cancellation without canceling personal event");
        check(personal.deadlines().size() == 2, "import keeps conflicting task");
        check(personal.deadlines().stream().filter(value -> value.title().equals("공유 과제"))
                .findFirst().orElseThrow().completedOn().equals(MONDAY.minusDays(2)), "import preserves submission date");
        check(personal.customName("w1").equals("내 건물명")
                && personal.customName("w2").equals("새로운 공유 건물명"), "import preserves conflicting name and adds new name");
        String mergedNote = personal.dayNote(MONDAY);
        check(mergedNote.contains("내 준비물") && mergedNote.contains("공유 준비물"), "import merges day notes");
        personal.mergeFrom(incoming);
        check(personal.pins().size() == 2 && personal.events().size() == 2
                && personal.deadlines().size() == 2, "reimport records are idempotent");
        check(personal.dayNote(MONDAY).equals(mergedNote), "reimport notes are idempotent");
        check(incoming.pins().size() == 1 && incoming.events().get(0).buildingId().equals("pin:shared-id"), "import does not mutate sender");
        personal.mergeFrom(personal);
        check(personal.events().size() == 2, "self import is a no-op");
        Path merged = directory.resolve("merged.xml");
        PlannerStore.save(merged, personal);
        PlannerModel loaded = PlannerStore.load(merged);
        loaded.mergeFrom(incoming);
        check(loaded.events().equals(personal.events()) && loaded.pins().equals(personal.pins())
                && loaded.deadlines().equals(personal.deadlines()), "persisted reimport remains idempotent");

        PlannerModel left = new PlannerModel();
        PlannerModel right = new PlannerModel();
        left.renameBuilding("w1", "left");
        right.renameBuilding("w2", "right");
        Thread first = new Thread(() -> left.mergeFrom(right));
        Thread second = new Thread(() -> right.mergeFrom(left));
        first.start();
        second.start();
        first.join(3000);
        second.join(3000);
        check(!first.isAlive() && !second.isAlive(), "reciprocal imports do not deadlock");
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        checks++;
    }

    private static void rejectsArgument(Runnable action, String label) {
        try {
            action.run();
            throw new AssertionError(label);
        } catch (IllegalArgumentException expected) {
            checks++;
        }
    }

    @FunctionalInterface private interface IoAction { void run() throws IOException; }

    private static void rejectsIo(IoAction action, String label) {
        try {
            action.run();
            throw new AssertionError(label);
        } catch (IOException expected) {
            checks++;
        }
    }
}
