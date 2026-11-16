package campus;

import java.awt.*;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import javax.swing.*;
import campus.PlannerModel.Deadline;

final class DeadlineDialog extends JDialog {
    record Result(Deadline deadline, boolean deleted) { }
    private final JTextField title = new JTextField();
    private final JTextField dueDate = new JTextField();
    private final JTextArea note = new JTextArea(5, 24);
    private final JCheckBox completed = new JCheckBox("제출완료");
    private final JLabel error = Theme.label(" ", 12, Theme.WARNING);
    private final JLabel status = Theme.label(" ", 12, Theme.MUTED);
    private final Deadline original;
    private Result result;

    static Result open(JFrame owner, LocalDate selectedDate, Deadline original) {
        DeadlineDialog dialog = new DeadlineDialog(owner, selectedDate, original);
        dialog.setVisible(true);
        return dialog.result;
    }

    private DeadlineDialog(JFrame owner, LocalDate selectedDate, Deadline original) {
        super(owner, original == null ? "과제 등록" : "과제 수정", true);
        this.original = original;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        JPanel content = new JPanel(new BorderLayout(0, 16));
        content.setBackground(Color.WHITE);
        content.setBorder(BorderFactory.createEmptyBorder(24, 24, 20, 24));
        content.add(Theme.heading(original == null ? "과제 등록" : "과제 수정", 20), BorderLayout.NORTH);
        JPanel fields = Theme.transparent(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 0, 7, 0);
        c.gridy = 0;
        fields.add(Theme.label("과제 이름", 12, Theme.MUTED), c);
        c.gridy++;
        fields.add(title, c);
        c.gridy++;
        fields.add(Theme.label("마감일 (YYYY-MM-DD)", 12, Theme.MUTED), c);
        c.gridy++;
        fields.add(dueDate, c);
        c.gridy++;
        fields.add(Theme.label("메모", 12, Theme.MUTED), c);
        c.gridy++;
        note.setLineWrap(true);
        note.setWrapStyleWord(true);
        JScrollPane memo = new JScrollPane(note);
        memo.setPreferredSize(new Dimension(0, 110));
        fields.add(memo, c);
        c.gridy++;
        completed.setFont(Theme.font(13, Font.PLAIN));
        completed.setOpaque(false);
        completed.addActionListener(e -> updateStatus());
        fields.add(completed, c);
        c.gridy++;
        fields.add(status, c);
        content.add(fields, BorderLayout.CENTER);
        Theme.field(title);
        Theme.field(dueDate);
        Theme.field(note);
        dueDate.setText(selectedDate.toString());
        if (original != null) {
            title.setText(original.title());
            dueDate.setText(original.date().toString());
            note.setText(original.note());
            completed.setSelected(original.completed());
        }
        updateStatus();

        JPanel bottom = Theme.transparent(new BorderLayout(0, 9));
        bottom.add(error, BorderLayout.NORTH);
        JPanel buttons = Theme.transparent(new FlowLayout(FlowLayout.RIGHT, 7, 0));
        if (original != null) buttons.add(Theme.button("삭제", false, () -> {
            result = new Result(original, true);
            dispose();
        }));
        buttons.add(Theme.button("취소", false, this::dispose));
        JButton save = Theme.button("저장", true, this::save);
        buttons.add(save);
        bottom.add(buttons, BorderLayout.CENTER);
        content.add(bottom, BorderLayout.SOUTH);
        setContentPane(content);
        getRootPane().setDefaultButton(save);
        getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke("ESCAPE"), JComponent.WHEN_IN_FOCUSED_WINDOW);
        pack();
        setSize(480, Math.max(550, getHeight()));
        setMinimumSize(new Dimension(440, 520));
        setLocationRelativeTo(owner);
    }

    private LocalDate completedOn() {
        if (!completed.isSelected()) return null;
        return original != null && original.completed() ? original.completedOn() : LocalDate.now();
    }

    private void updateStatus() {
        if (completed.isSelected()) {
            LocalDate submitted = completedOn();
            status.setText(submitted == null ? "제출완료" : submitted.getMonthValue() + "월 " + submitted.getDayOfMonth() + "일 제출완료");
            status.setForeground(Theme.MUTED);
        } else {
            status.setText("미제출 · 마감일에 표시됩니다");
            status.setForeground(new Color(196, 54, 54));
        }
    }

    private void save() {
        try {
            Deadline deadline = new Deadline(original == null ? UUID.randomUUID().toString() : original.id(),
                    title.getText().strip(), LocalDate.parse(dueDate.getText().strip()), note.getText(),
                    completed.isSelected(), completedOn());
            result = new Result(deadline, false);
            dispose();
        } catch (DateTimeParseException ex) {
            error.setText("날짜는 2026-11-16 형식으로 입력해 주세요.");
        } catch (IllegalArgumentException ex) {
            error.setText("과제 이름과 마감일을 확인해 주세요.");
        }
    }
}
