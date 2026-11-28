package campus.calendar;
import campus.ui.Theme;


import java.awt.*;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import javax.swing.*;
import campus.model.PlannerModel.Deadline;
import campus.model.PlannerModel.TaskPriority;

public final class DeadlineDialog extends JDialog {
    public record Result(Deadline deadline, boolean deleted) { }
    private final JTextField title = new JTextField();
    private final JTextField dueDate = new JTextField();
    private final JTextArea note = new JTextArea(5, 24);
    private final JComboBox<TaskPriority> priority = new JComboBox<>(TaskPriority.values());
    private final JLabel error = Theme.label(" ", 12, Theme.WARNING);
    private final LocalDate plannerDate;
    private final Deadline original;
    private final boolean linked;
    private Result result;

    public static Result open(JFrame owner, LocalDate selectedDate, Deadline original, boolean linked) {
        DeadlineDialog dialog = new DeadlineDialog(owner, selectedDate, original, linked);
        dialog.setVisible(true);
        return dialog.result;
    }

    private DeadlineDialog(JFrame owner, LocalDate selectedDate, Deadline original, boolean linked) {
        super(owner, (linked ? "과제·할일 마감" : "체크리스트") + (original == null ? " 추가" : " 편집"), true);
        this.original = original;
        this.linked = linked;
        plannerDate = original == null ? selectedDate : original.plannerDate();
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        JPanel content = new JPanel(new BorderLayout(0, 16));
        content.setBackground(Color.WHITE);
        content.setBorder(BorderFactory.createEmptyBorder(24, 24, 20, 24));
        content.add(Theme.heading(getTitle(), 20), BorderLayout.NORTH);
        JPanel fields = Theme.transparent(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 0, 7, 0);
        c.gridy = 0;
        fields.add(Theme.label("목표", 12, Theme.MUTED), c);
        c.gridy++;
        fields.add(title, c);
        c.gridy++;
        priority.setSelectedItem(original == null ? TaskPriority.NORMAL : original.priority());
        if (!linked) {
            fields.add(Theme.label("우선순위", 12, Theme.MUTED), c);
            c.gridy++;
            fields.add(priority, c);
            c.gridy++;
        }
        fields.add(Theme.label("내용", 12, Theme.MUTED), c);
        c.gridy++;
        note.setLineWrap(true);
        note.setWrapStyleWord(true);
        JScrollPane memo = new JScrollPane(note);
        memo.setPreferredSize(new Dimension(0, 110));
        fields.add(memo, c);
        c.gridy++;
        if (linked) {
            fields.add(Theme.label("마감 날짜 (YYYY-MM-DD)", 12, Theme.MUTED), c);
            c.gridy++;
            fields.add(dueDate, c);
        }
        content.add(fields, BorderLayout.CENTER);
        Theme.field(title);
        Theme.field(dueDate);
        Theme.field(note);
        dueDate.setText((original == null ? plannerDate : original.date()).toString());
        title.getAccessibleContext().setAccessibleName("목표");
        note.getAccessibleContext().setAccessibleName("내용");
        priority.getAccessibleContext().setAccessibleName("우선순위");
        dueDate.getAccessibleContext().setAccessibleName("마감 날짜");
        if (original != null) {
            title.setText(original.title());
            note.setText(original.note());
        }

        JPanel bottom = Theme.transparent(new BorderLayout(0, 9));
        bottom.add(error, BorderLayout.NORTH);
        JPanel buttons = Theme.transparent(new FlowLayout(FlowLayout.RIGHT, 7, 0));
        if (original != null) buttons.add(Theme.deleteButton("삭제", () -> {
            int answer = JOptionPane.showConfirmDialog(this, "이 항목을 삭제할까요?", "항목 삭제",
                    JOptionPane.YES_NO_OPTION);
            if (answer != JOptionPane.YES_OPTION) return;
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
        setSize(500, Math.max(500, getHeight()));
        setMinimumSize(getSize());
        setLocationRelativeTo(owner);
    }

    private void save() {
        try {
            LocalDate chosenDate = linked ? LocalDate.parse(dueDate.getText().strip()) : plannerDate;
            if (linked && original == null && chosenDate.isBefore(plannerDate)) {
                error.setText("마감 날짜는 플래너 날짜 이후로 지정해 주세요.");
                return;
            }
            Deadline deadline = new Deadline(original == null ? UUID.randomUUID().toString() : original.id(),
                    title.getText().strip(), chosenDate, note.getText(),
                    original != null && original.completed(), original == null ? null : original.completedOn(),
                    (TaskPriority) priority.getSelectedItem(), plannerDate, linked);
            result = new Result(deadline, false);
            dispose();
        } catch (DateTimeParseException ex) {
            error.setText("날짜는 2026-11-16 형식으로 입력해 주세요.");
        } catch (IllegalArgumentException ex) {
            error.setText("목표와 날짜를 확인해 주세요.");
        }
    }
}
