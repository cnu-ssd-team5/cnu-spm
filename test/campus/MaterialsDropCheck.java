package campus;

import java.awt.Component;
import java.awt.Container;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;

/** File drops use temporary folders only and never touch personal materials. */
public final class MaterialsDropCheck {
    private static int checks;
    private static int unsupportedLinkChecks;

    public static void main(String[] args) throws Exception {
        Path temporary = Files.createTempDirectory("campus-material-drop-");
        try {
            Path materialsRoot = temporary.resolve("materials");
            Semester term = new Semester(2026, Semester.Season.FALL);
            PlannerModel model = new PlannerModel();
            model.putEvent(new PlannerModel.Event("course", "운영체제", "building", LocalTime.of(9, 0),
                    LocalTime.of(10, 0), PlannerModel.Category.CLASS, DayOfWeek.MONDAY, null, ""), term);
            AtomicReference<CourseMaterialsPanel> panelRef = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> {
                CourseMaterialsPanel panel = new CourseMaterialsPanel(model, materialsRoot, event -> { });
                panel.refresh(term);
                panelRef.set(panel);
            });
            CourseMaterialsPanel panel = panelRef.get();
            Path course = panel.prepareFolder("운영체제", term);
            check(course.endsWith(Path.of(term.folderName(), "운영체제")), "course uses the selected season's full-year folder");
            check(Files.isDirectory(course), "destination course folder is created");
            check(!course.equals(panel.courseFolder("운영체제", new Semester(2027, Semester.Season.FALL))), "another year has a separate folder");
            check(panel.courseFolder("../위험:이름", term).getParent().equals(course.getParent()), "course title cannot escape semester directory");

            Path legacy = materialsRoot.resolve("2026-가을").resolve("이전과목");
            Files.createDirectories(legacy);
            Files.writeString(legacy.resolve("기존자료.txt"), "keep");
            check(panel.courseFolder("이전과목", term).equals(legacy), "existing legacy semester materials remain accessible");
            check(panel.prepareFolder("이전과목", term).equals(legacy), "opening legacy course does not create a competing empty folder");

            Path first = temporary.resolve("강의자료.pdf");
            Path second = temporary.resolve("과제.txt");
            Files.writeString(first, "lecture bytes");
            Files.writeString(second, "homework bytes");
            CourseMaterialsPanel.MoveResult moved = CourseMaterialsPanel.moveFiles(List.of(first, second), course);
            check(moved.moved() == 2 && moved.failures().isEmpty(), "both dropped files are moved");
            check(!Files.exists(first) && !Files.exists(second), "successful move removes original paths");
            check(Files.readString(course.resolve(first.getFileName())).equals("lecture bytes")
                    && Files.readString(course.resolve(second.getFileName())).equals("homework bytes"), "moved file contents remain intact");
            CourseMaterialsPanel.MoveResult same = CourseMaterialsPanel.moveFiles(List.of(course.resolve("과제.txt")), course);
            check(same.moved() == 0 && same.skipped() == 1 && same.failures().isEmpty(), "dropping a file onto its own folder is a no-op");

            Path clean = temporary.resolve("새자료.txt");
            Files.writeString(clean, "new");
            Files.writeString(second, "different source bytes");
            rejected(() -> CourseMaterialsPanel.moveFiles(List.of(clean, second), course), "an existing target rejects the whole drop");
            check(Files.exists(clean) && Files.exists(second), "name collision preserves every original in the batch");
            check(!Files.exists(course.resolve(clean.getFileName())), "name collision does not partially move the first file");
            check(Files.readString(course.resolve("과제.txt")).equals("homework bytes"), "existing target is never overwritten");

            Path otherDirectory = temporary.resolve("other-source");
            Files.createDirectories(otherDirectory);
            Path duplicate = otherDirectory.resolve(clean.getFileName());
            Files.writeString(duplicate, "other");
            rejected(() -> CourseMaterialsPanel.moveFiles(List.of(clean, duplicate), course), "same-name files from two folders are rejected before moving");
            check(Files.exists(clean) && Files.exists(duplicate), "duplicate names preserve both source files");
            rejected(() -> CourseMaterialsPanel.moveFiles(List.of(otherDirectory), course), "directory drops are rejected");
            rejected(() -> CourseMaterialsPanel.moveFiles(List.of(temporary.resolve("missing.txt")), course), "missing source is rejected");

            Transferable files = new Transferable() {
                public DataFlavor[] getTransferDataFlavors() { return new DataFlavor[] { DataFlavor.javaFileListFlavor }; }
                public boolean isDataFlavorSupported(DataFlavor flavor) { return DataFlavor.javaFileListFlavor.equals(flavor); }
                public Object getTransferData(DataFlavor flavor) { return List.of(clean.toFile()); }
            };
            SwingUtilities.invokeAndWait(() -> {
                JButton tile = buttons(panel).stream().filter(button -> button.getText().contains("자료 폴더 열기")).findFirst().orElseThrow();
                check(tile.getTransferHandler() != null, "course folder tile accepts file drop handling");
                check(tile.getTransferHandler().canImport(new TransferHandler.TransferSupport(tile, files)), "native file-list flavor is accepted");
                tile.setEnabled(false);
                check(!tile.getTransferHandler().canImport(new TransferHandler.TransferSupport(tile, files)), "busy course tile rejects a concurrent drop");
                tile.setEnabled(true);
            });

            try {
                Path sourceLink = temporary.resolve("source-link.txt");
                Files.createSymbolicLink(sourceLink, clean);
                rejected(() -> CourseMaterialsPanel.moveFiles(List.of(sourceLink), course), "symbolic-link source is rejected");
                Path outside = temporary.resolve("outside-materials");
                Files.createDirectories(outside);
                Path escaped = materialsRoot.resolve(term.folderName()).resolve("연결과목");
                Files.createSymbolicLink(escaped, outside);
                rejected(() -> panel.prepareFolder("연결과목", term), "course folder link cannot escape materials root");
            } catch (UnsupportedOperationException | java.nio.file.FileSystemException permission) {
                unsupportedLinkChecks++;
            }
            System.out.println("PASS " + checks + " materials move and drop checks"
                    + (unsupportedLinkChecks == 0 ? "" : " (link creation unavailable on this host)"));
        } finally {
            try (var paths = Files.walk(temporary)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }

    private static List<JButton> buttons(Container container) {
        List<JButton> result = new ArrayList<>();
        for (Component child : container.getComponents()) {
            if (child instanceof JButton button) result.add(button);
            if (child instanceof Container nested) result.addAll(buttons(nested));
        }
        return result;
    }

    private interface Checked { void run() throws Exception; }

    private static void rejected(Checked action, String label) throws Exception {
        try {
            action.run();
            throw new AssertionError(label);
        } catch (IOException expected) { checks++; }
    }

    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
        checks++;
    }
}
