package campus.friends;

import campus.ui.Theme;
import java.awt.*;
import javax.swing.*;

/** 일반 보기에서 좌우 분할 또는 마스킹 비교를 연다. */
public final class ScheduleComparison extends JPanel {
    private final CardLayout layout = new CardLayout();
    private final JPanel content = Theme.transparent(layout);
    private final CardLayout comparisonLayout = new CardLayout();
    private final JPanel comparison = Theme.transparent(comparisonLayout);
    private final JLabel friendTitle = Theme.heading("", 14);
    private final JPanel left = Theme.card(new BorderLayout());
    private final JPanel right = Theme.card(new BorderLayout());
    private final ScheduleMask mask = new ScheduleMask();
    private final JComponent mine;
    private final JComponent friend;
    private final JButton mode;
    private final JButton close;
    private final JPanel navigationSlot = Theme.transparent(new BorderLayout());
    private boolean masking;

    public ScheduleComparison(JComponent normal, JComponent mine, JComponent friend,
                              Runnable chooseFriend, Runnable stopComparing) {
        super(new BorderLayout(0, 8));
        this.mine = mine;
        this.friend = friend;
        setOpaque(false);
        JPanel controls = Theme.transparent(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        controls.add(Theme.button("친구와 비교하기", false, chooseFriend));
        mode = Theme.button("마스킹으로 보기", false, this::toggleMask);
        close = Theme.button("비교 닫기", false, stopComparing);
        mode.setVisible(false);
        close.setVisible(false);
        controls.add(mode);
        controls.add(close);
        JPanel toolbar = Theme.transparent(new BorderLayout(8, 0));
        toolbar.add(navigationSlot, BorderLayout.WEST);
        toolbar.add(controls, BorderLayout.EAST);
        add(toolbar, BorderLayout.NORTH);

        JPanel headings = Theme.transparent(new GridLayout(1, 2, 12, 0));
        headings.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
        headings.add(Theme.heading("내 일정", 14));
        friendTitle.putClientProperty("html.disable", true);
        friendTitle.setHorizontalAlignment(SwingConstants.RIGHT);
        headings.add(friendTitle);

        JPanel pair = Theme.transparent(new GridLayout(1, 2, 12, 0));
        left.add(mine, BorderLayout.CENTER);
        right.add(friend, BorderLayout.CENTER);
        pair.add(left);
        pair.add(right);
        comparison.add(pair, "split");

        JPanel masked = Theme.card(new BorderLayout());
        masked.add(mask, BorderLayout.CENTER);
        comparison.add(masked, "mask");

        JPanel compare = Theme.transparent(new BorderLayout(0, 8));
        compare.add(headings, BorderLayout.NORTH);
        compare.add(comparison, BorderLayout.CENTER);
        content.add(normal, "normal");
        content.add(compare, "compare");
        add(content, BorderLayout.CENTER);
    }

    /** 시간표의 주 이동 또는 달력 비교의 날짜 이동을 상단에 배치한다. */
    public void installNavigation(JComponent navigation) {
        if (navigation.getParent() != navigationSlot) navigationSlot.add(navigation, BorderLayout.CENTER);
    }

    public void showComparison(String name) {
        if (name != null) {
            friendTitle.setText(name + " · 친구 일정");
            friendTitle.setToolTipText(name);
        }
        mode.setVisible(name != null);
        close.setVisible(name != null);
        layout.show(content, name == null ? "normal" : "compare");
    }

    private void toggleMask() {
        masking = !masking;
        if (masking) {
            mask.setSchedules(mine, friend);
        } else {
            left.add(mine, BorderLayout.CENTER);
            right.add(friend, BorderLayout.CENTER);
        }
        mode.setText(masking ? "분할로 보기" : "마스킹으로 보기");
        comparisonLayout.show(comparison, masking ? "mask" : "split");
        comparison.revalidate();
        comparison.repaint();
    }
}
