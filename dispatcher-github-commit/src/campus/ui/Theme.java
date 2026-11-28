package campus.ui;

import java.awt.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.FontUIResource;
import java.util.Enumeration;
import java.util.List;

public final class Theme {
    public static final Color BACKGROUND = new Color(248, 250, 254);
    public static final Color INK = new Color(23, 35, 59);
    public static final Color MUTED = new Color(106, 119, 137);
    public static final Color BLUE = new Color(47, 119, 255);
    public static final Color PALE_BLUE = new Color(234, 241, 255);
    public static final Color LINE = new Color(225, 231, 240);
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
                boolean deleting = Boolean.TRUE.equals(getClientProperty("delete.action"));
                Color fill = custom!=null?custom:primary ? BLUE : Color.WHITE;
                if (getModel().isRollover() && !deleting) fill = custom!=null?custom.darker():primary ? BLUE.darker() : PALE_BLUE;
                if (!isEnabled()) fill = LINE;
                boolean navigation = Boolean.TRUE.equals(getClientProperty("button.navigation"));
                boolean flat = navigation || Boolean.TRUE.equals(getClientProperty("button.flat"));
                boolean selected = Boolean.TRUE.equals(getClientProperty("navigation.selected"));
                if (flat && custom == null && !getModel().isRollover() && !selected) fill = new Color(0, 0, 0, 0);
                int radius = 16;
                g.setColor(fill);
                g.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
                if (!primary && !flat) {
                    g.setColor(deleting && (isFocusOwner() || getModel().isRollover()) ? TODAY_TEXT : isFocusOwner() ? BLUE : LINE);
                    g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
                }
                if (Boolean.TRUE.equals(getClientProperty("calendar.selected"))) {
                    g.setColor(BLUE);
                    g.setStroke(new BasicStroke(2));
                    g.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, radius, radius);
                } else if (isFocusOwner()) {
                    g.setColor(primary ? Color.WHITE : BLUE);
                    g.drawRoundRect(2, 2, getWidth() - 5, getHeight() - 5, radius, radius);
                }
                g.dispose();
                super.paintComponent(graphics);
            }
        };
        button.setFont(font(13, Font.BOLD));
        button.setForeground(primary ? Color.WHITE : INK);
        button.setBorder(new EmptyBorder(8, 12, 8, 12));
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.addActionListener(e -> action.run());
        return button;
    }

    public static JButton navigationButton(String text, Runnable action) {
        JButton button = button(text, false, action);
        button.putClientProperty("button.navigation", true);
        button.setHorizontalAlignment(SwingConstants.CENTER);
        button.setVerticalTextPosition(SwingConstants.BOTTOM);
        button.setHorizontalTextPosition(SwingConstants.CENTER);
        button.setIconTextGap(8);
        button.setBorder(new EmptyBorder(12, 4, 12, 4));
        button.setFont(font(12, Font.BOLD));
        return button;
    }


    public static JButton deleteButton(String text, Runnable action) {
        JButton button = button(text, false, action);
        button.putClientProperty("delete.action", true);
        button.setForeground(TODAY_TEXT);
        return button;
    }

    public static JMenuItem deleteMenuItem(String text, Runnable action) {
        JMenuItem item = new JMenuItem(text);
        item.setOpaque(true);
        item.setBackground(Color.WHITE);
        item.setForeground(TODAY_TEXT);
        item.addActionListener(e -> action.run());
        return item;
    }

    public static JPanel card(LayoutManager layout) {
        JPanel panel = rounded(layout, 8);
        panel.setBackground(Color.WHITE);
        panel.setBorder(new EmptyBorder(16, 16, 16, 16));
        return panel;
    }

    public static JPanel rounded(LayoutManager layout, int radius) {
        JPanel panel = new JPanel(layout) {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(getBackground());
                g.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
                g.setColor(LINE);
                g.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, radius, radius);
                g.dispose();
            }
        };
        panel.setOpaque(false);
        panel.setBackground(Color.WHITE);
        return panel;
    }

    public static JButton quietButton(String text, String icon, Runnable action) {
        JButton button = button(text, false, action);
        button.putClientProperty("button.flat", true);
        button.setIcon(UiIcons.of(icon));
        button.setIconTextGap(8);
        return button;
    }

    public static JButton actionRow(String text, String icon, Runnable action) {
        JButton button = quietButton(text, icon, action);
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setBorder(new EmptyBorder(12, 16, 12, 16));
        button.setFont(font(13, Font.PLAIN));
        return button;
    }

    public static JTextField searchField(String placeholder) {
        JTextField field = new JTextField() {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g=(Graphics2D)graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(new Color(246,248,252));g.fillRoundRect(0,0,getWidth(),getHeight(),18,18);
                g.setColor(isFocusOwner()?BLUE:LINE);g.drawRoundRect(0,0,getWidth()-1,getHeight()-1,18,18);
                g.dispose();super.paintComponent(graphics);
                g=(Graphics2D)graphics.create();g.setColor(MUTED);
                UiIcons.of("search",18).paintIcon(this,g,12,(getHeight()-18)/2);
                if(getText().isEmpty()&&!isFocusOwner()) {
                    g.setFont(getFont());g.drawString(placeholder,40,(getHeight()-g.getFontMetrics().getHeight())/2+g.getFontMetrics().getAscent());
                }
                g.dispose();
            }
        };
        field.setOpaque(false);field.setFont(font(14,Font.PLAIN));field.setForeground(INK);
        field.setBorder(new EmptyBorder(12,40,12,12));
        field.setPreferredSize(new Dimension(260,44));
        field.getAccessibleContext().setAccessibleName(placeholder);
        field.setToolTipText(placeholder);
        return field;
    }

    public static JCheckBox checkBox(String text, boolean selected) {
        JCheckBox box=new JCheckBox(text,selected);box.setOpaque(false);box.setFocusPainted(false);
        box.putClientProperty("html.disable",true);
        Icon icon=new Icon(){
            public int getIconWidth(){return 17;}
            public int getIconHeight(){return 17;}
            public void paintIcon(Component component,Graphics graphics,int x,int y){
                AbstractButton button=(AbstractButton)component;Graphics2D g=(Graphics2D)graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(button.isSelected()?(button.isEnabled()?BLUE:MUTED):Color.WHITE);g.fillRoundRect(x+1,y+1,14,14,4,4);
                g.setColor(button.isSelected()?(button.isEnabled()?BLUE:MUTED):MUTED);g.drawRoundRect(x+1,y+1,14,14,4,4);
                if(button.isSelected()){g.setColor(Color.WHITE);g.setStroke(new BasicStroke(1.6f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
                    g.drawPolyline(new int[]{x+4,x+7,x+12},new int[]{y+8,y+11,y+5},3);}
                g.dispose();
            }
        };
        box.setIcon(icon);box.setSelectedIcon(icon);box.setDisabledIcon(icon);box.setDisabledSelectedIcon(icon);
        return box;
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
        scroll.getVerticalScrollBar().setUI(new javax.swing.plaf.basic.BasicScrollBarUI(){
            @Override protected JButton createDecreaseButton(int orientation){return noArrow();}
            @Override protected JButton createIncreaseButton(int orientation){return noArrow();}
            private JButton noArrow(){JButton button=new JButton();button.setPreferredSize(new Dimension(0,0));button.setMinimumSize(new Dimension(0,0));return button;}
            @Override public Dimension getPreferredSize(JComponent component){return new Dimension(8,40);}
            @Override protected void paintTrack(Graphics graphics,JComponent component,Rectangle bounds){ }
            @Override protected void paintThumb(Graphics graphics,JComponent component,Rectangle bounds){
                if(bounds.isEmpty()||!scrollbar.isEnabled())return;Graphics2D g=(Graphics2D)graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(isDragging?new Color(153,168,190):new Color(202,212,227));
                g.fillRoundRect(bounds.x+1,bounds.y,bounds.width-2,bounds.height,6,6);g.dispose();
            }
        });
        scroll.getVerticalScrollBar().setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        return scroll;
    }

    public static void comboBox(JComboBox<?> choice){
        choice.setBackground(Color.WHITE);choice.setOpaque(false);choice.setBorder(new EmptyBorder(4,4,4,4));
        choice.setUI(new javax.swing.plaf.basic.BasicComboBoxUI(){
            @Override protected JButton createArrowButton(){
                JButton arrow=quietButton("","down",()->{});arrow.setBorder(new EmptyBorder(4,4,4,4));return arrow;
            }
            @Override public void paintCurrentValueBackground(Graphics graphics,Rectangle bounds,boolean focused){ }
        });
    }

    public static void field(JComponent field) {
        field.setFont(font(13, Font.PLAIN));
        field.setForeground(INK);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(LINE), new EmptyBorder(8, 8, 8, 8)));
    }

    public static String html(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
