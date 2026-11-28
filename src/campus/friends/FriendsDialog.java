package campus.friends;

import campus.friends.FriendSchedules.Friend;
import campus.ui.Theme;
import java.awt.*;
import java.util.function.Consumer;
import javax.swing.*;

public final class FriendsDialog extends JDialog {
    private final JPanel rows = Theme.transparent(new GridLayout(0, 1, 0, 8));
    private final Consumer<Friend> apply;

    public FriendsDialog(Window owner, Consumer<Friend> apply) {
        this(owner, "친구", ModalityType.MODELESS, apply);
    }

    private FriendsDialog(Window owner, String title, ModalityType modality, Consumer<Friend> apply) {
        super(owner, title, modality);
        this.apply = apply;
        JPanel content = Theme.card(new BorderLayout());
        JPanel list = Theme.transparent(new BorderLayout());
        list.add(rows, BorderLayout.NORTH);
        content.add(Theme.scroll(list), BorderLayout.CENTER);
        setContentPane(content);
        setDefaultCloseOperation(HIDE_ON_CLOSE);
        getRootPane().registerKeyboardAction(e -> setVisible(false), KeyStroke.getKeyStroke("ESCAPE"),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        setMinimumSize(new Dimension(300, 240));
        setSize(340, 330);
        setLocationRelativeTo(owner);
    }

    public static void chooseForComparison(Window owner, FriendSchedules schedules,
                                           Friend selected, Consumer<Friend> compare) {
        FriendsDialog dialog = new FriendsDialog(owner, "비교할 친구 선택", ModalityType.APPLICATION_MODAL, compare);
        dialog.refresh(schedules, selected);
        try {
            dialog.setVisible(true);
        } finally {
            dialog.dispose();
        }
    }

    public void refresh(FriendSchedules schedules, Friend active) {
        rows.removeAll();
        var friends = schedules.all();
        if (friends.isEmpty()) {
            rows.add(Theme.label("등록된 친구가 없습니다.", 13, Theme.MUTED));
            if (isModal()) rows.add(Theme.label("일정 공유에서 친구 일정을 가져올 수 있어요.", 12, Theme.MUTED));
        }
        for (Friend friend : friends) {
            boolean selected = active != null && active.id().equals(friend.id());
            JButton button = Theme.button("", selected, () -> {
                if (isModal()) setVisible(false);
                apply.accept(friend);
            });
            button.putClientProperty("html.disable", true);
            button.setText(friend.name());
            button.setToolTipText(friend.name());
            button.setHorizontalAlignment(SwingConstants.LEFT);
            rows.add(button);
        }
        if (!schedules.unreadableFiles().isEmpty()) {
            JLabel warning = Theme.label("읽을 수 없는 친구 일정 " + schedules.unreadableFiles().size() + "개",
                    12, Theme.WARNING);
            warning.setToolTipText(String.join(", ", schedules.unreadableFiles()));
            rows.add(warning);
        }
        rows.revalidate();
        rows.repaint();
    }

}
