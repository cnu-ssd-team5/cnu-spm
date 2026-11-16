package campus.ui;

import java.awt.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.*;
import campus.model.PlannerModel.Event;
import campus.model.PlannerModel;

final class CourseMaterialsPanel extends JPanel {
    private final PlannerModel model;
    private final Path materialsRoot;
    private final Consumer<Event> onEditEvent;
    private final JPanel courses = Theme.transparent(new GridLayout(0, 3, 12, 12));

    CourseMaterialsPanel(PlannerModel model, Path materialsRoot, Consumer<Event> onEditEvent) {
        super(new BorderLayout(0, 14));
        this.model = model;
        this.materialsRoot = materialsRoot.toAbsolutePath().normalize();
        this.onEditEvent = onEditEvent;
        setOpaque(false);
        JLabel heading = Theme.label("과목을 누르면 강의 자료와 과제를 저장할 폴더가 열립니다", 12, Theme.MUTED);
        add(heading, BorderLayout.NORTH);
        JPanel topAligned = Theme.transparent(new BorderLayout());
        topAligned.add(courses, BorderLayout.NORTH);
        add(Theme.scroll(topAligned), BorderLayout.CENTER);
    }

    void refresh() {
        Map<String, Event> unique = new LinkedHashMap<>();
        for (Event event : model.events()) {
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
                        () -> openFolder(event.title()));
                folder.setPreferredSize(new Dimension(150, 122));
                folder.setToolTipText(materialsRoot.resolve(folderName(event.title())).toString());
                card.add(folder, BorderLayout.CENTER);
                card.add(Theme.button("수업 편집", false, () -> onEditEvent.accept(event)), BorderLayout.SOUTH);
                courses.add(card);
            }
        }
        revalidate();
        repaint();
    }

    private void openFolder(String title) {
        try {
            Path folder = materialsRoot.resolve(folderName(title)).normalize();
            if (!folder.getParent().equals(materialsRoot)) throw new IOException("과목 폴더 경로를 확인해 주세요.");
            Files.createDirectories(materialsRoot);
            if (Files.exists(folder) && !folder.toRealPath().startsWith(materialsRoot.toRealPath())) {
                throw new IOException("자료 폴더 밖으로 연결된 경로는 열 수 없습니다.");
            }
            Files.createDirectories(folder);
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                throw new IOException("폴더가 생성되었습니다. 파일 탐색기에서 열어 주세요.\n" + folder);
            }
            Desktop.getDesktop().open(folder.toFile());
        } catch (IOException | RuntimeException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "자료 폴더", JOptionPane.ERROR_MESSAGE);
        }
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
