package campus.ui;

import campus.map.CampusMap.Building;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import javax.swing.*;

/** The real map fills the surface; controls float above it instead of consuming rows. */
final class MapWorkspace extends JPanel {
    private final JLayeredPane layers;
    private final JPanel selection = Theme.card(new BorderLayout(0,16));
    private final JLabel placeName = Theme.heading("",18);
    private final JLabel placeType = Theme.label("캠퍼스 장소",12,Theme.MUTED);
    private final JButton favorite, add, files;
    private Runnable favoriteAction = ()->{}, routeAction = ()->{}, addAction = ()->{}, filesAction = ()->{};

    MapWorkspace(JPanel canvas,JComponent routeSwitch,JLabel subtitle,JLabel legend,
                 Runnable fit,Runnable schedules,Runnable zoomIn,Runnable zoomOut) {
        super(new BorderLayout());setOpaque(false);
        placeName.putClientProperty("html.disable",true);placeType.putClientProperty("html.disable",true);
        JPanel tabs=Theme.rounded(new FlowLayout(FlowLayout.LEFT,4,4),20);
        JButton campus=Theme.button("캠퍼스 지도",true,fit);
        JButton reset=Theme.quietButton("일정 경로","route",schedules);
        tabs.add(campus);tabs.add(reset);
        JPanel zoom=Theme.rounded(new GridLayout(0,1),22);
        JButton plus=Theme.quietButton("+","plus",zoomIn);plus.setIcon(null);plus.setFont(Theme.font(23,Font.PLAIN));
        JButton minus=Theme.quietButton("−","plus",zoomOut);minus.setIcon(null);minus.setText("-");minus.setFont(Theme.font(23,Font.PLAIN));
        zoom.add(plus);zoom.add(minus);
        JPanel status=Theme.card(new BorderLayout(0,8));status.setBorder(BorderFactory.createEmptyBorder(12,16,12,16));
        subtitle.setFont(Theme.font(12,Font.BOLD));subtitle.setForeground(Theme.INK);
        JPanel text=Theme.transparent(new GridLayout(0,1,0,4));text.add(subtitle);text.add(legend);
        status.add(text,BorderLayout.CENTER);status.add(routeSwitch,BorderLayout.SOUTH);

        JPanel title=Theme.transparent(new BorderLayout(12,0));
        JLabel icon=Theme.label("",20,Theme.BLUE);icon.setIcon(UiIcons.of("building",30));title.add(icon,BorderLayout.WEST);
        JPanel names=Theme.transparent(new GridLayout(0,1,0,4));names.add(placeName);names.add(placeType);title.add(names,BorderLayout.CENTER);
        JButton close=Theme.quietButton("","close",()->selection.setVisible(false));close.setBorder(BorderFactory.createEmptyBorder(4,4,4,4));
        close.getAccessibleContext().setAccessibleName("장소 정보 닫기");title.add(close,BorderLayout.EAST);selection.add(title,BorderLayout.NORTH);
        JPanel actions=Theme.transparent(new GridLayout(1,3,8,0));
        JButton route=Theme.button("경로 안내",false,()->routeAction.run());route.setIcon(UiIcons.of("route"));
        add=Theme.button("일정 추가",false,()->addAction.run());add.setIcon(UiIcons.of("calendar"));
        files=Theme.button("자료함",false,()->filesAction.run());files.setIcon(UiIcons.of("folder"));
        for(JButton button:java.util.List.of(route,add,files)){
            button.setVerticalTextPosition(SwingConstants.BOTTOM);button.setHorizontalTextPosition(SwingConstants.CENTER);
            button.setIconTextGap(8);button.setBorder(BorderFactory.createEmptyBorder(12,4,12,4));actions.add(button);
        }
        selection.add(actions,BorderLayout.CENTER);
        favorite=Theme.quietButton("즐겨찾기","star",()->favoriteAction.run());selection.add(favorite,BorderLayout.SOUTH);
        selection.setVisible(false);
        layers=new JLayeredPane(){
            @Override public void doLayout(){
                int width=getWidth(),height=getHeight();canvas.setBounds(0,0,width,height);
                reset.setVisible(width>=620);Dimension t=tabs.getPreferredSize();tabs.setBounds(16,16,t.width,t.height);
                zoom.setBounds(Math.max(0,width-56),Math.max(0,height-112),40,96);
                status.setBounds(16,Math.max(16,height-112),Math.max(0,Math.min(320,width-88)),96);
                int sw=Math.max(0,Math.min(288,width-32));int sh=Math.min(224,selection.getPreferredSize().height);
                selection.setBounds(Math.max(16,width-sw-16),Math.max(72,height-sh-128),sw,sh);
            }
        };
        layers.add(canvas,JLayeredPane.DEFAULT_LAYER);
        for(JComponent overlay:java.util.List.of(tabs,zoom,status,selection))layers.add(overlay,JLayeredPane.PALETTE_LAYER);
        add(layers,BorderLayout.CENTER);
    }

    void showPlace(Building building,boolean readOnly,boolean isFavorite,Runnable route,Runnable addEvent,Runnable openFiles,Runnable toggleFavorite){
        if(building==null){selection.setVisible(false);return;}
        placeName.setText(building.name());placeName.setToolTipText(building.name());
        placeType.setText(building.id().startsWith("pin:")?"내가 추가한 핀":building.id().startsWith("bus:")?"캠퍼스 정류장":"전남대학교 광주캠퍼스");
        routeAction=route;addAction=addEvent;filesAction=openFiles;favoriteAction=toggleFavorite;
        add.setEnabled(!readOnly);files.setEnabled(!readOnly);favorite.setEnabled(!readOnly);
        favorite.setText(isFavorite?"즐겨찾기 해제":"즐겨찾기 추가");selection.setVisible(true);layers.revalidate();layers.repaint();
    }
    @Override protected void paintChildren(Graphics graphics){
        Graphics2D g=(Graphics2D)graphics.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.clip(new RoundRectangle2D.Double(0,0,getWidth(),getHeight(),22,22));super.paintChildren(g);g.dispose();
    }
}
