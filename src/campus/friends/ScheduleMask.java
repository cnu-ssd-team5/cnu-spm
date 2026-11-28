package campus.friends;

import campus.ui.Theme;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

/** 두 일정을 같은 크기로 배치하고 경계선 양쪽으로 각 일정을 잘라서 보여준다. */
final class ScheduleMask extends JLayeredPane {
    private static final int HANDLE_WIDTH = 17;
    private final JPanel ownClip = Theme.transparent(null);
    private final JPanel friendClip = Theme.transparent(null);
    private final JButton handle = new JButton() {
        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int line = boundary() - getX();
            int center = getHeight() / 2;
            g.setColor(Theme.BLUE);
            g.fillRect(Math.max(0, Math.min(line - 1, getWidth() - 2)), 0, 2, getHeight());
            g.setColor(isFocusOwner() ? Theme.INK : Theme.BLUE);
            g.fillRoundRect(0, center - 18, getWidth(), 36, 10, 10);
            g.setColor(Color.WHITE);
            g.drawLine(6, center - 6, 6, center + 6);
            g.drawLine(10, center - 6, 10, center + 6);
            g.dispose();
        }
    };
    private double fraction = 0.5;

    ScheduleMask() {
        add(ownClip, DEFAULT_LAYER);
        add(friendClip, DEFAULT_LAYER);
        handle.setCursor(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
        handle.setContentAreaFilled(false);
        handle.setBorderPainted(false);
        handle.setToolTipText("좌우로 드래그하거나 방향키로 비교 경계 이동");
        handle.getAccessibleContext().setAccessibleName("일정 비교 경계");
        MouseAdapter drag = new MouseAdapter() {
            private int offset;

            @Override public void mousePressed(MouseEvent event) {
                offset = handle.getX() + event.getX() - boundary();
            }

            @Override public void mouseDragged(MouseEvent event) {
                if (SwingUtilities.isLeftMouseButton(event) && getWidth() > 0) {
                    setFraction((handle.getX() + event.getX() - offset) / (double) getWidth());
                }
            }
        };
        handle.addMouseListener(drag);
        handle.addMouseMotionListener(drag);
        bind("LEFT", () -> setFraction(fraction - 0.05));
        bind("RIGHT", () -> setFraction(fraction + 0.05));
        bind("HOME", () -> setFraction(0));
        bind("END", () -> setFraction(1));
        add(handle, PALETTE_LAYER);
    }

    void setSchedules(JComponent mine, JComponent friend) {
        ownClip.add(mine);
        friendClip.add(friend);
    }

    @Override public void doLayout() {
        int width = getWidth();
        int height = getHeight();
        int split = boundary();
        ownClip.setBounds(0, 0, split, height);
        friendClip.setBounds(split, 0, width - split, height);
        if (ownClip.getComponentCount() > 0) ownClip.getComponent(0).setBounds(0, 0, width, height);
        if (friendClip.getComponentCount() > 0) friendClip.getComponent(0).setBounds(-split, 0, width, height);
        int handleWidth = Math.min(HANDLE_WIDTH, width);
        int handleX = Math.max(0, Math.min(split - handleWidth / 2, width - handleWidth));
        handle.setBounds(handleX, 0, handleWidth, height);
    }

    private int boundary() { return (int) Math.round(getWidth() * fraction); }

    private void setFraction(double value) {
        fraction = Math.max(0, Math.min(1, value));
        doLayout();
        repaint();
    }

    private void bind(String key, Runnable move) {
        handle.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key), key);
        handle.getActionMap().put(key, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) { move.run(); }
        });
    }
}
