package campus;

import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import javax.swing.*;
import campus.PlannerModel.Event;

final class CourseMaterialsPanel extends JPanel {
    private final PlannerModel model;
    private final Path materialsRoot;
    private final Consumer<Event> onEditEvent;
    private final JLabel heading = Theme.heading("과목 자료", 18);
    private final JPanel courses = Theme.transparent(new GridLayout(0, 3, 12, 12));
    private Semester selectedSemester = Semester.forDate(PlannerTime.today());
    private MaterialsLibrary library;
    private int working;

    void setLibrary(MaterialsLibrary library) { this.library = library; }
    boolean isWorking() { return working > 0; }

    CourseMaterialsPanel(PlannerModel model, Path materialsRoot, Consumer<Event> onEditEvent) {
        super(new BorderLayout(0, 14));
        this.model = model;
        this.materialsRoot = materialsRoot.toAbsolutePath().normalize();
        this.onEditEvent = onEditEvent;
        setOpaque(false);
        add(heading, BorderLayout.NORTH);
        JPanel topAligned = Theme.transparent(new BorderLayout());
        topAligned.add(courses, BorderLayout.NORTH);
        add(Theme.scroll(topAligned), BorderLayout.CENTER);
    }

    void refresh() {
        refresh(Semester.forDate(PlannerTime.today()));
    }

    void refresh(Semester semester) {
        selectedSemester = semester;
        heading.setText("과목 자료 · " + semester);
        Map<String, Event> unique = new LinkedHashMap<>();
        for (Event event : model.events(semester)) {
            if (event.recurring() && event.category() == PlannerModel.Category.CLASS) {
                unique.putIfAbsent(event.title().strip().toLowerCase(Locale.ROOT), event);
            }
        }
        courses.removeAll();
        if (unique.isEmpty()) {
            courses.add(Theme.label("매주 반복되는 수업을 추가하면 과목이 표시됩니다", 12, Theme.MUTED));
        } else {
            for (Event event : unique.values()) {
                JPanel card = Theme.transparent(new BorderLayout(0, 5));
                JButton folder = Theme.button("<html><center>" + Theme.html(event.title()) + "<br><br>자료 폴더 열기</center></html>", false,
                        () -> { });
                folder.addMouseListener(new java.awt.event.MouseAdapter() {
                    @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                        if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2) openFolder(event.title());
                    }
                });
                folder.setPreferredSize(new Dimension(150, 122));
                folder.setToolTipText("더블클릭하여 자료함 열기 · 파일을 끌어 놓으면 이동 · " + courseFolder(event.title(), semester));
                folder.getInputMap().put(KeyStroke.getKeyStroke("ENTER"),"openMaterials");
                folder.getActionMap().put("openMaterials", new AbstractAction() {
                    @Override public void actionPerformed(java.awt.event.ActionEvent e) { openFolder(event.title()); }
                });
                folder.setTransferHandler(fileDrop(event.title(), semester, folder));
                card.setTransferHandler(folder.getTransferHandler());
                card.add(folder, BorderLayout.CENTER);
                card.add(Theme.button("수업 편집", false, () -> onEditEvent.accept(event)), BorderLayout.SOUTH);
                courses.add(card);
            }
        }
        revalidate();
        repaint();
    }

    private void openFolder(String title) {
        if (library != null) { library.openCourse(this, title, selectedSemester); return; }
        try {
            Path folder = prepareFolder(title, selectedSemester);
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                throw new IOException("폴더가 생성되었습니다. 파일 탐색기에서 열어 주세요.\n" + folder);
            }
            Desktop.getDesktop().open(folder.toFile());
        } catch (IOException | RuntimeException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "자료 폴더", JOptionPane.ERROR_MESSAGE);
        }
    }

    Path courseFolder(String title, Semester semester) {
        return library == null ? defaultCourseFolder(title, semester) : library.courseFolder(title, semester);
    }

    Path defaultCourseFolder(String title, Semester semester) {
        Path folder = materialsRoot.resolve(semester.folderName()).resolve(folderName(title)).normalize();
        if (!Files.exists(folder) && (semester.season() == Semester.Season.SPRING || semester.season() == Semester.Season.FALL)) {
            String oldTerm = semester.year() + "-" + (semester.season() == Semester.Season.SPRING ? "봄" : "가을");
            Path previous = materialsRoot.resolve(oldTerm).resolve(folderName(title));
            if (Files.isDirectory(previous)) return previous;
        }
        return folder;
    }

    Path prepareFolder(String title, Semester semester) throws IOException {
        Path folder = courseFolder(title, semester);
        Path semesterRoot = folder.getParent();
        Files.createDirectories(materialsRoot);
        Path realRoot = materialsRoot.toRealPath();
        if (!semesterRoot.getParent().toRealPath().equals(realRoot)) throw new IOException("과목 폴더 경로를 확인해 주세요.");
        for (Path directory : List.of(semesterRoot, folder)) {
            if (Files.exists(directory) && !directory.toRealPath().startsWith(realRoot)) {
                throw new IOException("자료 폴더 밖으로 연결된 경로에는 저장할 수 없습니다.");
            }
            Files.createDirectories(directory);
            if (!directory.toRealPath().startsWith(realRoot)) throw new IOException("자료 폴더 경로를 확인해 주세요.");
        }
        return folder;
    }

    private TransferHandler fileDrop(String title, Semester semester, JButton tile) {
        return new TransferHandler() {
            @Override public boolean canImport(TransferSupport support) {
                boolean accepted = tile.isEnabled() && (library == null || !library.isWorking()) && support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
                // The worker moves files itself; COPY negotiation prevents premature deletion by the drag source.
                if (accepted && support.isDrop()) {
                    if ((support.getSourceDropActions() & COPY) == 0) return false;
                    support.setDropAction(COPY);
                }
                return accepted;
            }

            @Override public boolean importData(TransferSupport support) {
                if (!canImport(support)) return false;
                try {
                    Object value = support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    if (!(value instanceof List<?> dropped)) return false;
                    List<Path> files = new ArrayList<>();
                    for (Object entry : dropped) {
                        if (!(entry instanceof File file)) throw new IOException("파일만 끌어 놓아 주세요.");
                        files.add(file.toPath());
                    }
                    if (files.isEmpty()) return false;
                    if (library != null) { library.moveIntoCourse(tile, title, semester, files); return true; }
                    tile.setEnabled(false);
                    working++;
                    new SwingWorker<MoveResult, Void>() {
                        @Override protected MoveResult doInBackground() throws Exception {
                            return moveFiles(files, prepareFolder(title, semester));
                        }

                        @Override protected void done() {
                            working--;
                            tile.setEnabled(true);
                            try {
                                MoveResult result = get();
                                String message = result.moved() + "개 파일 이동";
                                if (result.skipped() > 0) message += " · 같은 위치 " + result.skipped() + "개 유지";
                                if (!result.failures().isEmpty()) message += "\n이동하지 못한 파일:\n" + String.join("\n", result.failures());
                                JOptionPane.showMessageDialog(CourseMaterialsPanel.this, message, "자료 파일 이동",
                                        result.failures().isEmpty() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
                            } catch (Exception failure) {
                                Throwable cause = failure.getCause() == null ? failure : failure.getCause();
                                JOptionPane.showMessageDialog(CourseMaterialsPanel.this, cause.getMessage(), "자료 파일 이동", JOptionPane.ERROR_MESSAGE);
                            }
                        }
                    }.execute();
                    return true;
                } catch (Exception failure) {
                    JOptionPane.showMessageDialog(CourseMaterialsPanel.this, failure.getMessage(), "자료 파일 이동", JOptionPane.ERROR_MESSAGE);
                    return false;
                }
            }
        };
    }

    record MoveResult(int moved, int skipped, List<String> failures) {
        MoveResult { failures = List.copyOf(failures); }
    }

    static MoveResult moveFiles(List<Path> sources, Path directory) throws IOException {
        Path folder = directory.toRealPath();
        if (!Files.isDirectory(folder)) throw new IOException("자료 폴더를 찾을 수 없습니다.");
        Map<Path, Path> moves = new LinkedHashMap<>();
        Set<Path> destinations = new HashSet<>();
        int skipped = 0;
        // Validate the entire drop before moving any file, so a name collision preserves every source.
        for (Path entry : sources) {
            Path source = entry.toAbsolutePath().normalize();
            if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(source)) {
                throw new IOException("폴더나 바로가기는 이동할 수 없습니다: " + source.getFileName());
            }
            Path target = folder.resolve(source.getFileName()).normalize();
            if (!target.getParent().equals(folder)) throw new IOException("잘못된 파일 이름입니다.");
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS) && Files.isSameFile(source, target)) {
                skipped++;
                continue;
            }
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS) || !destinations.add(target)) {
                throw new IOException("같은 이름의 파일이 있어 이동하지 않았습니다: " + target.getFileName());
            }
            moves.put(source, target);
        }
        int moved = 0;
        List<String> failures = new ArrayList<>();
        for (Map.Entry<Path, Path> move : moves.entrySet()) {
            try {
                Files.move(move.getKey(), move.getValue());
                moved++;
            } catch (IOException failure) {
                failures.add(move.getKey().getFileName() + " · " + failure.getMessage());
            }
        }
        return new MoveResult(moved, skipped, failures);
    }

    static String folderName(String title) {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("과목 이름을 입력해 주세요.");
        String original = title.strip();
        String safe = original.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").replaceAll("[. ]+$", "");
        if (safe.isEmpty() || safe.equals(".") || safe.equals("..")) safe = "과목";
        if (safe.matches("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\\..*)?")) safe = "_" + safe;
        if (safe.length() > 80) {
            int end = Character.isHighSurrogate(safe.charAt(79)) ? 79 : 80;
            safe = safe.substring(0, end);
        }
        if (!safe.equals(original)) {
            try {
                byte[] hash = MessageDigest.getInstance("SHA-256").digest(original.getBytes(StandardCharsets.UTF_8));
                safe += "-" + HexFormat.of().formatHex(hash, 0, 4);
            } catch (NoSuchAlgorithmException ex) {
                throw new IllegalStateException(ex);
            }
        }
        return safe;
    }
}
