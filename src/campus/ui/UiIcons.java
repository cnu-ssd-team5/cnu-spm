package campus.ui;

import java.awt.*;
import java.awt.geom.*;
import javax.swing.*;

/** Small, font-independent line icons for the desktop workspace. */
final class UiIcons {
    private UiIcons() { }
    static Icon of(String name) { return of(name, 20); }
    static Icon of(String name, int size) {
        return new Icon() {
            public int getIconWidth() { return size; }
            public int getIconHeight() { return size; }
            public void paintIcon(Component component, Graphics graphics, int x, int y) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.translate(x, y); g.scale(size / 24.0, size / 24.0);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(component.isEnabled() ? component.getForeground() : Theme.MUTED);
                g.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                switch (name) {
                    case "map" -> {
                        Path2D p = new Path2D.Double();
                        p.moveTo(3,5);p.lineTo(9,2);p.lineTo(15,5);p.lineTo(21,2);
                        p.lineTo(21,19);p.lineTo(15,22);p.lineTo(9,19);p.lineTo(3,22);p.closePath();
                        g.draw(p);g.drawLine(9,2,9,19);g.drawLine(15,5,15,22);
                    }
                    case "지도", "pin" -> {
                        Path2D p = new Path2D.Double(); p.moveTo(12,22); p.curveTo(8,17,4,13,4,9);
                        p.curveTo(4,-1,20,-1,20,9); p.curveTo(20,13,16,17,12,22); g.draw(p); g.drawOval(9,6,6,6);
                    }
                    case "달력", "calendar" -> {
                        g.drawRoundRect(3,5,18,16,3,3); g.drawLine(3,10,21,10); g.drawLine(8,2,8,7); g.drawLine(16,2,16,7);
                    }
                    case "주간시간표", "week" -> {
                        for(int a=0;a<2;a++)for(int b=0;b<2;b++)g.drawRoundRect(3+a*11,3+b*11,7,7,1,1);
                    }
                    case "플래너", "planner" -> {
                        g.drawRoundRect(4,4,16,18,2,2); g.drawRoundRect(8,2,8,4,2,2);
                        g.drawLine(8,11,10,13); g.drawLine(10,13,15,8); g.drawLine(8,17,16,17);
                    }
                    case "과목자료", "folder" -> {
                        Path2D p = new Path2D.Double(); p.moveTo(3,7);p.lineTo(3,4);p.lineTo(10,4);p.lineTo(13,7);p.lineTo(21,7);p.lineTo(21,21);p.lineTo(3,21);p.closePath();g.draw(p);g.drawLine(3,10,21,10);
                    }
                    case "cap" -> {
                        g.fillPolygon(new int[]{1,12,23,12},new int[]{8,3,8,13},4);
                        g.drawLine(5,12,5,17);g.drawLine(5,17,12,21);g.drawLine(12,21,19,17);g.drawLine(19,17,19,12);g.drawLine(22,9,22,17);
                    }
                    case "menu" -> {g.drawLine(4,6,20,6);g.drawLine(4,12,20,12);g.drawLine(4,18,20,18);}
                    case "panel" -> {g.drawRoundRect(3,4,18,16,2,2);g.drawLine(15,4,15,20);}
                    case "expand" -> {
                        g.drawLine(14,4,20,4);g.drawLine(20,4,20,10);g.drawLine(20,4,13,11);
                        g.drawLine(4,14,4,20);g.drawLine(4,20,10,20);g.drawLine(4,20,11,13);
                    }
                    case "search" -> {g.drawOval(3,3,13,13);g.drawLine(14,14,21,21);}
                    case "star" -> {
                        Path2D p=new Path2D.Double();for(int i=0;i<10;i++){double a=-Math.PI/2+i*Math.PI/5,r=i%2==0?10:4.6;double px=12+Math.cos(a)*r,py=12+Math.sin(a)*r;if(i==0)p.moveTo(px,py);else p.lineTo(px,py);}p.closePath();g.draw(p);
                    }
                    case "route" -> {
                        g.drawOval(2,3,5,5);g.drawOval(17,16,5,5);g.drawLine(5,8,5,15);g.draw(new Arc2D.Double(5,12,14,8,0,180,Arc2D.OPEN));g.drawLine(19,12,19,16);
                    }
                    case "share" -> {g.drawOval(16,2,5,5);g.drawOval(2,9,5,5);g.drawOval(16,17,5,5);g.drawLine(7,11,16,5);g.drawLine(7,13,16,19);}
                    case "import" -> {g.drawLine(12,2,12,15);g.drawLine(7,10,12,15);g.drawLine(12,15,17,10);g.drawLine(3,16,3,21);g.drawLine(3,21,21,21);g.drawLine(21,21,21,16);}
                    case "clock" -> {g.drawOval(2,2,20,20);g.drawLine(12,6,12,12);g.drawLine(12,12,16,15);}
                    case "close" -> {g.drawLine(6,6,18,18);g.drawLine(18,6,6,18);}
                    case "back" -> {g.drawLine(15,5,8,12);g.drawLine(8,12,15,19);}
                    case "chevron" -> {g.drawLine(9,5,16,12);g.drawLine(16,12,9,19);}
                    case "down" -> {g.drawLine(6,9,12,15);g.drawLine(12,15,18,9);}
                    case "plus" -> {g.drawLine(4,12,20,12);g.drawLine(12,4,12,20);}
                    case "building" -> {g.drawRect(5,3,14,19);g.drawLine(2,22,22,22);for(int a=8;a<=16;a+=4)for(int b=7;b<=15;b+=4)g.fillRect(a,b,1,1);}
                    default -> {g.drawRoundRect(4,4,16,16,4,4);g.drawLine(8,12,11,15);g.drawLine(11,15,17,8);}
                }
                g.dispose();
            }
        };
    }
}
