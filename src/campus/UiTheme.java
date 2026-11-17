package campus;
import java.awt.*;
import javax.swing.*;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.basic.BasicButtonUI;
public final class UiTheme {
    private UiTheme() {
    }
    public static void install() {
        FontUIResource font=new FontUIResource("Malgun Gothic",Font.PLAIN,13);
        java.util.Enumeration<Object> keys=UIManager.getDefaults().keys();
        while(keys.hasMoreElements()) {
            Object key=keys.nextElement();
            if(UIManager.get(key) instanceof Font)UIManager.put(key,font);
        }
        UIManager.put("List.selectionBackground",new Color(222,235,254));
        UIManager.put("List.selectionForeground",new Color(30,68,120));
        UIManager.put("TextField.selectionBackground",new Color(199,220,253));
    }
    public static void style(Container root) {
        for(Component c:root.getComponents()) {
            if(c instanceof JButton) {
                JButton b=(JButton)c;
                b.setUI(new BasicButtonUI());
                b.setBackground(Color.WHITE);
                b.setForeground(new Color(44,64,91));
                b.setFocusPainted(true);
                b.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(213,223,236)),BorderFactory.createEmptyBorder(7,10,7,10)));
            }
            if(c instanceof JTextField)((JTextField)c).setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(213,223,236)),BorderFactory.createEmptyBorder(6,8,6,8)));
            if(c instanceof Container)style((Container)c);
        }
    }
}
