package campus.ui;

import java.awt.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.FontUIResource;
import java.util.Enumeration;
import java.util.List;

public final class Theme {
    public static final Color BACKGROUND = new Color(245, 247, 251);
    public static final Color INK = new Color(30, 42, 61);
    public static final Color MUTED = new Color(106, 119, 137);
    public static final Color BLUE = new Color(40, 100, 230);
    public static final Color PALE_BLUE = new Color(234, 241, 255);
    public static final Color LINE = new Color(225, 231, 240);
    public static final Color SELECTED_DAY = new Color(255, 235, 130);
    public static final Color TODAY_TEXT = new Color(196, 54, 54);
    public static final Color WARNING = new Color(172, 80, 33);

    // 맑은 고딕이 없는 PC(영문 Windows, Mac, Linux)에서도 한글이 깨지지 않도록 설치된 폰트 중에서 고릅니다.
    private static final String FONT = pickFont("맑은 고딕", "Malgun Gothic", "Apple SD Gothic Neo", "AppleGothic",
            "Noto Sans KR", "Noto Sans CJK KR", "NanumGothic", "나눔고딕");

    private Theme() { }

    private static String pickFont(String... names) {
        List<String> installed = List.of(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
        for (String name : names) if (installed.contains(name)) return name;
        return Font.DIALOG;
    }

    public static Font font(int size, int style) {
        return new Font(FONT, style, size);
    }

    public static void configureLookAndFeel() {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
        catch(Exception ignored) { /* Swing 기본 LookAndFeel로 실행합니다. */ }
        FontUIResource font=new FontUIResource(font(13,Font.PLAIN));
        Enumeration<Object> keys=UIManager.getDefaults().keys();
        while(keys.hasMoreElements()) {
            Object key=keys.nextElement();
            if(UIManager.get(key) instanceof FontUIResource)UIManager.put(key,font);
        }
        UIManager.put("ToolTip.font",font(12,Font.PLAIN));
        UIManager.put("OptionPane.messageFont",font(13,Font.PLAIN));
        UIManager.put("Button.font",font(12,Font.PLAIN));
    }

    public static JLabel label(String text, int size, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(font(size, Font.PLAIN));
        label.setForeground(color);
        return label;
    }

    public static JLabel heading(String text, int size) {
        JLabel label = label(text, size, INK);
        label.setFont(font(size, Font.BOLD));
        return label;
    }

    public static JButton button(String text, boolean primary, Runnable action) {
        JButton button = new JButton(text) {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color custom=(Color)getClientProperty("calendar.fill");
                Color fill = custom!=null?custom:primary ? BLUE : Color.WHITE;
                if (getModel().isRollover()) fill = custom!=null?custom.darker():primary ? BLUE.darker() : PALE_BLUE;
                if (!isEnabled()) fill = LINE;
                g.setColor(fill);
                g.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                if (!primary) {
                    g.setColor(isFocusOwner() ? BLUE : LINE);
                    g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
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

    public static JPanel card(LayoutManager layout) {
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

    public static JPanel transparent(LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setOpaque(false);
        return panel;
    }

    public static JScrollPane scroll(Component component) {
        JScrollPane scroll = new JScrollPane(component);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        return scroll;
    }

    public static void field(JComponent field) {
        field.setFont(font(13, Font.PLAIN));
        field.setForeground(INK);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(LINE), new EmptyBorder(8, 9, 8, 9)));
    }

    public static String html(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
