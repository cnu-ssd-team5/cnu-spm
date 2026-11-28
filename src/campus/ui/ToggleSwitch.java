package campus.ui;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import javax.swing.BorderFactory;
import javax.swing.JToggleButton;

/** 켜짐·꺼짐 스위치. JToggleButton을 그대로 써서 클릭·스페이스 키·접근성 동작은 Swing 기본을 따른다. */
final class ToggleSwitch extends JToggleButton {
    private static final int TRACK_WIDTH = 34, TRACK_HEIGHT = 18, GAP = 8;
    private static final Color OFF_TRACK = new Color(196, 204, 215);

    ToggleSwitch(String text, boolean selected) {
        super(text, selected);
        setFont(Theme.font(12, Font.PLAIN));
        setForeground(Theme.INK);
        setOpaque(false);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        putClientProperty("html.disable", true);
    }

    @Override public Dimension getPreferredSize() {
        Insets insets = getInsets();
        FontMetrics metrics = getFontMetrics(getFont());
        int label = getText() == null || getText().isEmpty() ? 0 : GAP + metrics.stringWidth(getText());
        return new Dimension(insets.left + TRACK_WIDTH + label + insets.right,
                insets.top + Math.max(TRACK_HEIGHT, metrics.getHeight()) + insets.bottom);
    }

    @Override public Dimension getMinimumSize() { return getPreferredSize(); }

    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int x = getInsets().left, y = (getHeight() - TRACK_HEIGHT) / 2;
        Color track = isSelected() ? Theme.BLUE : OFF_TRACK;
        if (!isEnabled()) track = new Color(track.getRed(), track.getGreen(), track.getBlue(), 90);
        g.setColor(track);
        g.fillRoundRect(x, y, TRACK_WIDTH, TRACK_HEIGHT, TRACK_HEIGHT, TRACK_HEIGHT);
        int knob = TRACK_HEIGHT - 4;
        g.setColor(Color.WHITE);
        g.fillOval(isSelected() ? x + TRACK_WIDTH - knob - 2 : x + 2, y + 2, knob, knob);
        if (isFocusOwner()) {
            g.setColor(new Color(40, 100, 230, 90));
            g.drawRoundRect(x - 2, y - 2, TRACK_WIDTH + 3, TRACK_HEIGHT + 3, TRACK_HEIGHT + 4, TRACK_HEIGHT + 4);
        }
        if (getText() != null && !getText().isEmpty()) {
            g.setFont(getFont());
            g.setColor(isEnabled() ? getForeground() : Theme.MUTED);
            FontMetrics metrics = g.getFontMetrics();
            g.drawString(getText(), x + TRACK_WIDTH + GAP, (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent());
        }
        g.dispose();
    }
}
