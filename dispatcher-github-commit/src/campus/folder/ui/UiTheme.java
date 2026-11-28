package campus.folder.ui;

import java.awt.*;
import javax.swing.*;
import javax.swing.plaf.basic.BasicButtonUI;

/** 자료함 내부 버튼과 입력창에 적용하는 공통 스타일. */
public final class UiTheme {
    private UiTheme() { }

    public static void style(Container root) {
        for (Component component : root.getComponents()) {
            if (component instanceof JButton button) {
                button.setUI(new BasicButtonUI());
                button.setBackground(Color.WHITE);
                button.setForeground(new Color(44, 64, 91));
                button.setFocusPainted(true);
                button.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(213, 223, 236)),
                        BorderFactory.createEmptyBorder(7, 10, 7, 10)));
            }
            if (component instanceof JTextField field)
                field.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(213, 223, 236)),
                        BorderFactory.createEmptyBorder(6, 8, 6, 8)));
            if (component instanceof Container child) style(child);
        }
    }
}
