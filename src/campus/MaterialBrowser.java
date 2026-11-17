package campus;

import java.awt.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import javax.swing.*;

/** Hosts the demo's file browser over the planner's existing materials folders. */
final class MaterialBrowser implements AutoCloseable {
    record Location(Path path, String name) { @Override public String toString() { return name; } }
    private final FolderService service;
    private final Runnable updated;
    private final Map<Path, FileOperations> operations = new HashMap<>();
    private final Map<Path, String> names = new HashMap<>();
    private final List<MapFileSearch> searches = new ArrayList<>();
    private MiniFolderPopup preview;
    private boolean moving;
    private Path groupRoot;
    private List<Path> groupEntries = List.of();

    MaterialBrowser(Path root, Runnable updated) throws IOException {
        service = new FolderService(root);
        this.updated = updated;
    }

    boolean isWorking() { return moving || preview != null && preview.isWorking(); }
    Path initial(Path root) { return root; }
    String displayName(Path path) { return names.getOrDefault(path, path.getFileName().toString()); }
    String locationTitle(Path root, Path current) {
        if (current.equals(root)) return displayName(root);
        for (Path entry : groupEntries) if (current.startsWith(entry)) {
            return displayName(root) + " / " + displayName(entry)
                    + (current.equals(entry) ? "" : " / " + entry.relativize(current));
        }
        return displayName(root) + " / " + root.relativize(current);
    }
    boolean virtualRoot(Path path, Path root) { return path.equals(root) && path.equals(groupRoot); }
    List<Path> list(Path path, Path root) throws IOException {
        return virtualRoot(path, root) ? new ArrayList<>(groupEntries) : service.list(path);
    }
    FileOperations operationsFor(Path root) throws IOException {
        service.checked(root);
        Path key = service.getRoot();
        if (!operations.containsKey(key)) operations.put(key, new FileOperations(service));
        return operations.get(key);
    }
    void moveInto(JComponent owner, Path destination, List<Path> sources) {
        if (isWorking()) return;
        closePopup();
        owner.setEnabled(false);
        moving = true;
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() throws Exception {
                Files.createDirectories(service.checked(destination));
                operationsFor(destination).paste(sources, destination, true);
                return null;
            }
            @Override protected void done() {
                moving = false;
                owner.setEnabled(true);
                try { get(); }
                catch (Exception failure) { error(owner, failure.getCause() instanceof Exception cause ? cause : failure); }
                updated.run();
            }
        }.execute();
    }
    void record(Path file) throws IOException { new RecentFiles(service, service.getRoot()).record(file); }
    List<Path> recentFiles() throws IOException { return new RecentFiles(service, service.getRoot()).list(); }
    Path latest(List<Path> roots) {
        try {
            return recentFiles().stream()
                    .filter(file -> roots.stream().anyMatch(file::startsWith)).findFirst().orElse(null);
        } catch (IOException failure) { return null; }
    }
    void openLatest(Component owner, List<Path> roots) {
        Path file = latest(roots);
        if (file == null) return;
        try { service.checked(file); Desktop.getDesktop().open(file.toFile()); record(file); updated.run(); }
        catch (Exception failure) { error(owner, failure); }
    }
    void openAt(JComponent anchor, Point point, Path folder, String name) {
        show(anchor, point, folder, name, List.of());
    }
    void openGroup(JComponent anchor, Point point, String name, List<Location> entries) {
        show(anchor, point, service.getRoot(), name, entries);
    }
    private void show(JComponent anchor, Point point, Path root, String name, List<Location> entries) {
        if (isWorking()) return;
        closePopup();
        try {
            root = service.checked(root);
            Files.createDirectories(root);
            names.put(root, name);
            groupRoot = entries.isEmpty() ? null : root;
            List<Path> paths = new ArrayList<>();
            for (Location entry : entries) {
                Path path = service.checked(entry.path());
                Files.createDirectories(path);
                names.put(path, entry.name());
                paths.add(path);
            }
            groupEntries = List.copyOf(paths);
            preview = new MiniFolderPopup(SwingUtilities.getWindowAncestor(anchor), this, service, root, updated);
            preview.showAt(anchor, point);
        } catch (Exception failure) { error(anchor, failure); }
    }
    void search(Component owner, java.util.function.Function<Path, String> displayPath) {
        if (isWorking()) return;
        String query = JOptionPane.showInputDialog(owner, "파일·폴더 이름", "자료 검색", JOptionPane.PLAIN_MESSAGE);
        if (query == null || query.isBlank()) return;
        closePopup();
        searches.removeIf(window -> !window.isDisplayable());
        searches.add(new MapFileSearch(SwingUtilities.getWindowAncestor(owner), service, query.strip(), updated,
                (folder, anchor) -> openAt(anchor, new Point(anchor.getWidth(), 0), folder, folder.getFileName().toString()),
                displayPath));
    }
    void closePopup() {
        if (isWorking()) return;
        if (preview != null) preview.dispose();
        preview = null;
    }
    @Override public void close() {
        if (isWorking()) return;
        closePopup();
        searches.forEach(Window::dispose);
        searches.clear();
    }
    private static void error(Component owner, Exception failure) {
        JOptionPane.showMessageDialog(owner, failure.getMessage(), "자료함", JOptionPane.ERROR_MESSAGE);
    }
}
