package campus;
import javax.swing.Icon;
import java.awt.*;
public final class DocumentIcon implements Icon {
    private final String type;
    private final boolean folder;
    private final int size;
    public DocumentIcon(String type, boolean folder, int size) {
        this.type=type;
        this.folder=folder;
        this.size=size;
    }
    public int getIconWidth() {
        return size;
    }
    public int getIconHeight() {
        return size;
    }
    public void paintIcon(Component c, Graphics graphics, int x, int y) {
        Graphics2D g=(Graphics2D)graphics.create();
        g.translate(x,y);
        g.scale(size/64.0,size/64.0);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        if(folder) {
            g.setColor(new Color(219,163,42));
            g.fillRoundRect(6,13,25,18,5,5);
            g.fillRoundRect(6,20,52,36,6,6);
            g.setColor(new Color(250,202,72));
            g.fillRoundRect(6,25,52,31,5,5);
        }
        else {
            Polygon p=new Polygon(new int[] {
                15,40,52,52,15
            }
            ,new int[] {
                5,5,17,58,58
            }
            ,5);
            g.setColor(Color.WHITE);
            g.fillPolygon(p);
            g.setColor(new Color(139,151,168));
            g.drawPolygon(p);
            g.drawLine(40,5,40,17);
            g.drawLine(40,17,52,17);
            g.setColor(type.equals("PDF")?new Color(210,73,65):type.equals("TXT")?new Color(111,124,144):new Color(47,111,203));
            g.fillRoundRect(3,32,49,21,4,4);
            g.setColor(Color.WHITE);
            g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,type.length()>4?9:12));
            g.drawString(type,27-g.getFontMetrics().stringWidth(type)/2,47);
        }
        g.dispose();
    }
}
