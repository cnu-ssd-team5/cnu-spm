package campus.ui;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.swing.*;

/** Native tray notification with an always-visible in-app fallback. */
public final class NotificationCenter implements AutoCloseable {
    private final JLabel status;
    private TrayIcon tray;
    private boolean closed;
    public NotificationCenter(JLabel status){
        this.status=status;
        try{
            if(!GraphicsEnvironment.isHeadless()&&SystemTray.isSupported()){
                BufferedImage image=new BufferedImage(24,24,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();
                g.setColor(Theme.BLUE);g.fillOval(1,1,22,22);g.setColor(Color.WHITE);g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,16));g.drawString("C",6,18);g.dispose();
                tray=new TrayIcon(image,"캠퍼스 플래너 알림");tray.setImageAutoSize(true);SystemTray.getSystemTray().add(tray);
            }
        }catch(AWTException|RuntimeException e){tray=null;}
    }
    public void show(String title,String message){
        if(closed)return;
        String text=title+": "+message;
        status.setText("알림 · "+(text.length()>65?text.substring(0,65)+"…":text));status.setToolTipText(text);
        if(tray!=null)try{tray.displayMessage(title,message,TrayIcon.MessageType.INFO);}catch(RuntimeException e){/* In-app notice remains available. */}
    }
    public boolean nativeAvailable(){return tray!=null;}
    @Override public void close(){if(closed)return;closed=true;if(tray!=null){try{SystemTray.getSystemTray().remove(tray);}catch(RuntimeException ignored){}tray=null;}}
}
