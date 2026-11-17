package campus;

import java.awt.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

final class Theme {
    static final Color BACKGROUND = new Color(245, 247, 251);
    static final Color INK = new Color(30, 42, 61);
    static final Color MUTED = new Color(106, 119, 137);
    static final Color BLUE = new Color(40, 100, 230);
    static final Color PALE_BLUE = new Color(234, 241, 255);
    static final Color LINE = new Color(225, 231, 240);
    static final Color WARNING = new Color(172, 80, 33);
    static final Color SATURDAY = new Color(190, 227, 250);
    static final Color SUNDAY = new Color(250, 207, 224);
    private static final String FONT = pickFont();

    private Theme() { }

    static Font font(int size, int style) {
        return new Font(FONT, style, size + 3);
    }

    private static String pickFont() {
        java.util.List<String> installed = java.util.List.of(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
        for (String name : java.util.List.of("맑은 고딕", "Malgun Gothic", "Apple SD Gothic Neo", "Noto Sans CJK KR", "NanumGothic")) {
            if (installed.contains(name)) return name;
        }
        return Font.DIALOG;
    }

    static Color courseColor(String title) {
        return Color.getHSBColor(Math.floorMod(title.hashCode(), 360) / 360f, .18f, .97f);
    }

    static JLabel label(String text, int size, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(font(size, Font.PLAIN));
        label.setForeground(color);
        return label;
    }

    static JLabel heading(String text, int size) {
        JLabel label = label(text, size, INK);
        label.setFont(font(size, Font.BOLD));
        return label;
    }

    static JButton button(String text, boolean primary, Runnable action) {
        JButton button = new JButton(text) {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                boolean danger = Boolean.TRUE.equals(getClientProperty("danger"));
                Color custom = (Color) getClientProperty("calendar.fill");
                Color fill = danger ? new Color(190, 47, 47) : custom != null ? custom : primary ? BLUE : Color.WHITE;
                if (getModel().isRollover()) fill = danger || primary || custom != null ? fill.darker() : PALE_BLUE;
                if (!isEnabled()) fill = LINE;
                g.setColor(fill);
                g.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                if (!primary) {
                    g.setColor(isFocusOwner() ? BLUE : LINE);
                    g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                }
                if (Boolean.TRUE.equals(getClientProperty("calendar.selected"))) {
                    g.setColor(BLUE); g.setStroke(new BasicStroke(2));
                    g.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 12, 12);
                }
                if (Boolean.TRUE.equals(getClientProperty("calendar.today"))) {
                    g.setColor(new Color(230, 173, 30)); g.setStroke(new BasicStroke(3));
                    g.drawRoundRect(3, 3, getWidth() - 7, getHeight() - 7, 10, 10);
                }
                g.dispose();
                super.paintComponent(graphics);
            }
        };
        button.setFont(font(12, Font.BOLD));
        button.setForeground(primary ? Color.WHITE : INK);
        button.setBorder(new EmptyBorder(9, 13, 9, 13));
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.addActionListener(e -> action.run());
        return button;
    }

    static JButton dangerButton(String text, Runnable action) {
        JButton button = button(text, true, action);
        button.putClientProperty("danger", true);
        return button;
    }

    static JPanel card(LayoutManager layout) {
        JPanel panel = new JPanel(layout) {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(getBackground());
                g.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                g.setColor(LINE);
                g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
                g.dispose();
            }
        };
        panel.setOpaque(false);
        panel.setBackground(Color.WHITE);
        panel.setBorder(new EmptyBorder(16, 16, 16, 16));
        return panel;
    }

    static JPanel transparent(LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setOpaque(false);
        return panel;
    }

    static JScrollPane scroll(Component component) {
        JScrollPane scroll = new JScrollPane(component);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        return scroll;
    }

    static void field(JComponent field) {
        field.setFont(font(13, Font.PLAIN));
        field.setForeground(INK);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(LINE), new EmptyBorder(8, 9, 8, 9)));
    }

    static String html(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
