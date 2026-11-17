package campus;

import java.awt.Point;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import campus.PlannerModel.Event;

/** Maps buildings and recurring courses to one existing, semester-scoped source of files. */
final class MaterialsLibrary implements AutoCloseable {
    private final PlannerModel model;
    private final CourseMaterialsPanel materials;
    private final Path root, linksFile;
    private final FolderService folders;
    private final Properties links = new Properties();
    private final MaterialBrowser browser;
    private final java.nio.channels.FileChannel channel;
    private final java.nio.channels.FileLock lock;

    MaterialsLibrary(PlannerModel model, CourseMaterialsPanel materials, Path appRoot, Runnable updated) throws IOException {
        this.model = model;
        this.materials = materials;
        folders = new FolderService(appRoot.resolve("materials"));
        root = folders.getRoot();
        linksFile = appRoot.resolve("data/material-folders.properties");
        if (Files.exists(linksFile)) try (InputStream input = Files.newInputStream(linksFile)) { links.load(input); }
        browser = new MaterialBrowser(root, updated);
        for (String key : links.stringPropertyNames()) checkedLink(links.getProperty(key));
        Files.createDirectories(linksFile.getParent());
        channel = java.nio.channels.FileChannel.open(linksFile.resolveSibling("material-files.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try {
            lock = channel.tryLock();
            if (lock == null) throw new IOException("같은 자료함을 사용하는 프로그램이 이미 실행 중입니다.");
        } catch (IOException | RuntimeException failure) {
            channel.close();
            throw new IOException("자료함을 열지 못했습니다. 다른 실행 창을 확인해 주세요.", failure);
        }
        materials.setLibrary(this);
    }
    private String key(Event event, Semester semester) { return semester.id() + ":" + event.id(); }
    private String groupKey(Event event, Semester semester) { return "group:" + semester.id() + ":" + event.recurringGroup(); }
    private Path checkedLink(String value) throws IOException {
        Path relative = Path.of(value);
        Path path = root.resolve(relative).normalize();
        if (relative.isAbsolute() || !path.startsWith(root) || path.equals(root)) throw new IOException("잘못된 과목 자료 경로입니다.");
        return folders.checked(path);
    }
    Path courseFolder(String title, Semester semester) {
        for (Event event : model.events(semester)) {
            if (!event.recurring() || event.category() != PlannerModel.Category.CLASS || !event.title().equals(title)) continue;
            String saved = links.getProperty(key(event, semester));
            if (saved == null) saved = links.getProperty(groupKey(event, semester));
            if (saved != null) {
                try { return checkedLink(saved); }
                catch (IOException failure) { throw new IllegalStateException(failure.getMessage(), failure); }
            }
        }
        try { return folders.checked(materials.defaultCourseFolder(title, semester)); }
        catch (IOException failure) { throw new IllegalStateException(failure.getMessage(), failure); }
    }
    void sync() throws IOException {
        Properties next = new Properties();
        next.putAll(links);
        for (Event event : model.events()) {
            if (!event.recurring() || event.category() != PlannerModel.Category.CLASS) continue;
            Semester semester = model.semesterOf(event.id());
            if (semester == null) semester = Semester.forDate(PlannerTime.today());
            Path folder = courseFolder(event.title(), semester);
            next.setProperty(key(event, semester), root.relativize(folder).toString());
            next.setProperty(groupKey(event, semester), root.relativize(folder).toString());
        }
        if (next.equals(links) && Files.exists(linksFile)) return;
        Files.createDirectories(linksFile.getParent());
        Path temporary = Files.createTempFile(linksFile.getParent(), ".material-links-", ".tmp");
        try {
            try (OutputStream output = Files.newOutputStream(temporary)) { next.store(output, "Local course folder references"); }
            try { Files.move(temporary, linksFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException failure) { Files.move(temporary, linksFile, StandardCopyOption.REPLACE_EXISTING); }
            links.clear(); links.putAll(next);
        } finally { Files.deleteIfExists(temporary); }
    }
    List<MaterialBrowser.Location> locations(Place place, Semester semester) {
        Map<Path, String> paths = new LinkedHashMap<>();
        for (Event event : model.events(semester)) {
            if (event.recurring() && event.category() == PlannerModel.Category.CLASS && event.buildingId().equals(place.id())) {
                paths.put(courseFolder(event.title(), semester), event.title());
            }
        }
        Path general = root.resolve(semester.folderName()).resolve("장소 자료").resolve(CourseMaterialsPanel.folderName(place.id()));
        paths.put(general, "일반 자료");
        return paths.entrySet().stream().map(entry -> new MaterialBrowser.Location(entry.getKey(), entry.getValue())).toList();
    }
    void openPlace(JComponent anchor, Place place, Semester semester) {
        try { sync(); browser.openGroup(anchor, new Point(anchor.getWidth() / 2, 35), place.name() + " · " + semester, locations(place, semester)); }
        catch (IOException failure) { JOptionPane.showMessageDialog(anchor, failure.getMessage(), "자료함", JOptionPane.ERROR_MESSAGE); }
    }
    Path occurrenceFolder(PlannerModel.Occurrence occurrence) throws IOException {
        Event event = occurrence.event();
        Semester semester = model.semesterOf(event.id());
        if (semester == null) semester = Semester.forDate(occurrence.date());
        if (event.category() == PlannerModel.Category.CLASS) return materials.prepareFolder(event.title(), semester);
        Path general = root.resolve(semester.folderName()).resolve("장소 자료").resolve(CourseMaterialsPanel.folderName(event.buildingId()));
        Files.createDirectories(folders.checked(general));
        return general;
    }
    void openOccurrence(JComponent anchor, PlannerModel.Occurrence occurrence) {
        try {
            sync();
            browser.openAt(anchor, new Point(anchor.getWidth() / 2, 35), occurrenceFolder(occurrence), occurrence.event().title());
        } catch (IOException failure) { JOptionPane.showMessageDialog(anchor, failure.getMessage(), "일정 자료함", JOptionPane.ERROR_MESSAGE); }
    }
    void openCourse(JComponent anchor, String title, Semester semester) {
        try { sync(); browser.openAt(anchor, new Point(anchor.getWidth() / 2, 35), materials.prepareFolder(title, semester), title + " · " + semester); }
        catch (IOException failure) { JOptionPane.showMessageDialog(anchor, failure.getMessage(), "자료함", JOptionPane.ERROR_MESSAGE); }
    }
    Path latest(Place place, Semester semester) { return browser.latest(locations(place, semester).stream().map(MaterialBrowser.Location::path).toList()); }
    Map<String, Path> recentPlaces(List<Place> places, Semester semester) {
        Map<String, Path> result = new LinkedHashMap<>();
        try {
            List<Path> files = browser.recentFiles();
            if (files.isEmpty()) return result;
            for (Place place : places) {
                List<MaterialBrowser.Location> folders = locations(place, semester);
                files.stream().filter(file -> folders.stream().anyMatch(folder -> file.startsWith(folder.path())))
                        .findFirst().ifPresent(file -> result.put(place.id(), file));
            }
        } catch (IOException ignored) { /* An unavailable recent-file index never hides actual materials. */ }
        return result;
    }
    void openLatest(JComponent owner, Place place, Semester semester) { browser.openLatest(owner, locations(place, semester).stream().map(MaterialBrowser.Location::path).toList()); }
    void search(JComponent owner) {
        Map<Path, String> labels = new LinkedHashMap<>();
        for (Event event : model.events()) {
            if (!event.recurring() || event.category() != PlannerModel.Category.CLASS) continue;
            Semester semester = model.semesterOf(event.id());
            if (semester == null) semester = Semester.forDate(PlannerTime.today());
            labels.put(courseFolder(event.title(), semester), semester + " / " + event.title());
        }
        browser.search(owner, path -> {
            for (Map.Entry<Path, String> entry : labels.entrySet()) if (path.startsWith(entry.getKey())) {
                return entry.getValue() + (path.equals(entry.getKey()) ? "" : " / " + entry.getKey().relativize(path));
            }
            return root.relativize(path).toString();
        });
    }
    void moveIntoCourse(JComponent owner, String title, Semester semester, List<Path> files) throws IOException {
        sync();
        browser.moveInto(owner, materials.prepareFolder(title, semester), files);
    }
    void moveIntoPlace(JComponent owner, Place place, Semester semester, List<Path> files) {
        if (isWorking()) return;
        List<MaterialBrowser.Location> choices = locations(place, semester);
        MaterialBrowser.Location chosen = choices.size() <= 2 ? choices.get(0)
                : (MaterialBrowser.Location) JOptionPane.showInputDialog(owner, "이동할 자료 폴더", place.name(),
                JOptionPane.PLAIN_MESSAGE, null, choices.toArray(), choices.get(0));
        if (chosen != null) browser.moveInto(owner, chosen.path(), files);
    }
    boolean isWorking() { return browser.isWorking() || materials.isWorking(); }
    @Override public void close() {
        if (isWorking()) return;
        browser.close();
        try { if (lock.isValid()) lock.release(); channel.close(); }
        catch (IOException ignored) { /* Process exit releases an already closed material lock. */ }
    }
}
