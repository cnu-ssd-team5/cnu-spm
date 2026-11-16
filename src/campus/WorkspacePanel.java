package campus;

import java.awt.*;
import java.util.List;
import javax.swing.*;

/** Switches workspace views while keeping the daily sidebar intact. */
final class WorkspacePanel extends JLayeredPane {
    private final CardLayout layout = new CardLayout();
    private final JPanel cards = Theme.transparent(layout);
    private final List<String> names = List.of("지도", "달력", "하루 시간표", "과목 자료");
    private final JButton previous;
    private final JButton next;
    private int selected;

    WorkspacePanel(JComponent map, JComponent calendar, JComponent timetable, JComponent materials) {
        List<JComponent> views = List.of(map, calendar, timetable, materials);
        for (int index = 0; index < views.size(); index++) cards.add(views.get(index), names.get(index));
        add(cards, DEFAULT_LAYER);
        previous = arrow(false);
        next = arrow(true);
        add(previous, PALETTE_LAYER);
        add(next, PALETTE_LAYER);
        showView(0);
    }

    int selectedView() { return selected; }

    void showView(int index) {
        selected = Math.floorMod(index, names.size());
        layout.show(cards, names.get(selected));
        previous.setToolTipText(names.get(Math.floorMod(selected - 1, names.size())) + " 보기");
        next.setToolTipText(names.get((selected + 1) % names.size()) + " 보기");
        repaint();
    }

    private JButton arrow(boolean right) {
        JButton button = new JButton() {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(new Color(255, 255, 255, getModel().isRollover() ? 235 : 170));
                g.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
                g.setColor(new Color(43, 76, 114, 190));
                g.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int middle = getHeight() / 2;
                int outer = right ? 12 : 24;
                int inner = right ? 24 : 12;
                g.drawLine(outer, middle - 12, inner, middle);
                g.drawLine(inner, middle, outer, middle + 12);
                g.dispose();
            }
        };
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFocusable(true);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.getAccessibleContext().setAccessibleName(right ? "다음 화면" : "이전 화면");
        button.addActionListener(event -> showView(selected + (right ? 1 : -1)));
        return button;
    }

    @Override public void doLayout() {
        cards.setBounds(0, 0, getWidth(), getHeight());
        int y = Math.max(0, (getHeight() - 64) / 2);
        previous.setBounds(6, y, 36, 64);
        next.setBounds(Math.max(0, getWidth() - 42), y, 36, 64);
    }

    @Override public Dimension getPreferredSize() { return new Dimension(650, 580); }
}
