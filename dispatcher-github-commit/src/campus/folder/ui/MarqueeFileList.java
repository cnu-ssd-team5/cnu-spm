package campus.folder.ui;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.Path;
/** 빈 영역에서 드래그하여 사각형으로 선택한다. */
public final class MarqueeFileList extends JList<Path> {
    private Point anchor;
    private Rectangle marquee;
    private int[] initial=new int[0];
    public MarqueeFileList(ListModel<Path> model) {
        super(model);
    }
    public boolean itemAt(Point p) {
        int i=locationToIndex(p);
        return i>=0 && nameBounds(i).contains(p);
    }
    public Rectangle nameBounds(int index) {
        Rectangle cell=getCellBounds(index,index);
        Component component=getCellRenderer().getListCellRendererComponent(this,getModel().getElementAt(index),index,false,false);
        if(!(component instanceof JLabel))return new Rectangle();
        JLabel label=(JLabel)component;
        Insets insets=label.getInsets();
        Rectangle view=new Rectangle(insets.left,insets.top,Math.max(0,cell.width-insets.left-insets.right),Math.max(0,cell.height-insets.top-insets.bottom));
        Rectangle icon=new Rectangle(),text=new Rectangle();
        SwingUtilities.layoutCompoundLabel(label,label.getFontMetrics(label.getFont()),label.getText(),label.getIcon(),label.getVerticalAlignment(),label.getHorizontalAlignment(),label.getVerticalTextPosition(),label.getHorizontalTextPosition(),view,icon,text,label.getIconTextGap());
        if(label.getIcon()!=null)text=text.union(icon);
        text.translate(cell.x,cell.y);
        return text;
    }
    protected void processMouseEvent(MouseEvent e) {
        if(SwingUtilities.isLeftMouseButton(e)&&e.getID()==MouseEvent.MOUSE_PRESSED&&!itemAt(e.getPoint())) {
            requestFocusInWindow();
            anchor=e.getPoint();
            initial=e.isControlDown()?getSelectedIndices():new int[0];
            if(!e.isControlDown())clearSelection();
            return;
        }
        if(e.getID()==MouseEvent.MOUSE_RELEASED&&anchor!=null) {
            anchor=null;
            marquee=null;
            repaint();
            return;
        }
        super.processMouseEvent(e);
    }
    protected void processMouseMotionEvent(MouseEvent e) {
        if(anchor!=null&&e.getID()==MouseEvent.MOUSE_DRAGGED) {
            marquee=new Rectangle(Math.min(anchor.x,e.getX()),Math.min(anchor.y,e.getY()),Math.abs(anchor.x-e.getX()),Math.abs(anchor.y-e.getY()));
            setValueIsAdjusting(true);
            clearSelection();
            for (int i:initial)addSelectionInterval(i,i); for(int i=0; i<getModel().getSize();
            i++) {
                Rectangle r=nameBounds(i);
                if(marquee.intersects(r))addSelectionInterval(i,i);
            }
            setValueIsAdjusting(false);
            scrollRectToVisible(new Rectangle(e.getX(),e.getY(),1,1));
            repaint();
            return;
        }
        super.processMouseMotionEvent(e);
    }
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        if(marquee!=null) {
            Graphics2D g=(Graphics2D)graphics.create();
            g.setColor(new Color(45,112,230,35));
            g.fill(marquee);
            g.setColor(new Color(45,112,230));
            g.draw(marquee);
            g.dispose();
        }
    }
}
