package campus.profiles;

import campus.profiles.ScheduleProfiles.Profile;
import campus.ui.Theme;
import java.awt.*;
import java.util.function.Consumer;
import javax.swing.*;

public final class ScheduleListDialog extends JDialog {
    private final JPanel rows = Theme.transparent(new GridLayout(0, 1, 0, 8));
    private final Consumer<Profile> apply;

    public ScheduleListDialog(Window owner, Consumer<Profile> apply, Runnable create, Runnable importFile) {
        super(owner, "시간표 목록", ModalityType.MODELESS);
        this.apply = apply;
        JPanel content = Theme.card(new BorderLayout());
        JPanel list = Theme.transparent(new BorderLayout());
        list.add(rows, BorderLayout.NORTH);
        content.add(Theme.scroll(list), BorderLayout.CENTER);
        JPanel actions = Theme.transparent(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actions.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        actions.add(Theme.button("가져오기", false, importFile));
        actions.add(Theme.button("새 시간표", true, create));
        content.add(actions, BorderLayout.SOUTH);
        setContentPane(content);
        setDefaultCloseOperation(HIDE_ON_CLOSE);
        getRootPane().registerKeyboardAction(e -> setVisible(false), KeyStroke.getKeyStroke("ESCAPE"),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        setMinimumSize(new Dimension(300, 240));
        setSize(340, 330);
        setLocationRelativeTo(owner);
    }

    public void refresh(ScheduleProfiles schedules, Profile active) {
        rows.removeAll();
        rows.add(Theme.button("기본 시간표", active == null, () -> apply.accept(null)));
        for (Profile profile : schedules.all()) {
            boolean selected = active != null && active.id().equals(profile.id());
            JButton button = Theme.button("", selected, () -> {
                apply.accept(profile);
            });
            button.putClientProperty("html.disable", true);
            button.setText(profile.name());
            button.setToolTipText(profile.name());
            button.setHorizontalAlignment(SwingConstants.LEFT);
            rows.add(button);
        }
        if (!schedules.unreadableFiles().isEmpty()) {
            JLabel warning = Theme.label("읽을 수 없는 시간표 " + schedules.unreadableFiles().size() + "개",
                    12, Theme.WARNING);
            warning.setToolTipText(String.join(", ", schedules.unreadableFiles()));
            rows.add(warning);
        }
        rows.revalidate();
        rows.repaint();
    }

}
