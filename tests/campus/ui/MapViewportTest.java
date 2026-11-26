package campus.ui;

import campus.map.CampusMap;
import campus.map.CampusMap.GeoPoint;
import campus.model.PlannerModel;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.lang.reflect.*;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.*;

/** Uses actual pointer events and projected campus bounds, without touching the desktop. */
public final class MapViewportTest {
    private interface Check { void run() throws Exception; }
    private static int passed,failed;
    private static CampusMap data;
    private static void test(String name,Check check){
        try{check.run();passed++;System.out.println("PASS "+name);}
        catch(Throwable error){failed++;System.out.println("FAIL "+name);error.printStackTrace(System.out);}
    }
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static double field(MapPanel panel,String name)throws Exception{
        Field field=MapPanel.class.getDeclaredField(name);field.setAccessible(true);return field.getDouble(panel);
    }
    private static Point2D point(MapPanel panel,GeoPoint value)throws Exception{
        Method method=MapPanel.class.getDeclaredMethod("point",GeoPoint.class);method.setAccessible(true);return (Point2D)method.invoke(panel,value);
    }
    private static GeoPoint geo(MapPanel panel,Point value)throws Exception{
        Method method=MapPanel.class.getDeclaredMethod("geo",Point.class);method.setAccessible(true);return (GeoPoint)method.invoke(panel,value);
    }
    private static MapPanel panel(){MapPanel panel=new MapPanel(data,new PlannerModel(),building->{});panel.setSize(800,600);return panel;}
    private static void drag(MapPanel panel,int dx,int dy){
        panel.dispatchEvent(new MouseEvent(panel,MouseEvent.MOUSE_PRESSED,0,0,200,180,1,false,MouseEvent.BUTTON1));
        panel.dispatchEvent(new MouseEvent(panel,MouseEvent.MOUSE_DRAGGED,1,InputEvent.BUTTON1_DOWN_MASK,200+dx,180+dy,0,false,MouseEvent.NOBUTTON));
        panel.dispatchEvent(new MouseEvent(panel,MouseEvent.MOUSE_RELEASED,2,0,200+dx,180+dy,1,false,MouseEvent.BUTTON1));
    }
    private static void wheel(MapPanel panel,int amount,int x,int y){
        panel.dispatchEvent(new MouseWheelEvent(panel,MouseEvent.MOUSE_WHEEL,0,0,x,y,0,false,MouseWheelEvent.WHEEL_UNIT_SCROLL,3,amount));
    }
    private static void bounded(MapPanel panel)throws Exception{
        Point2D top=point(panel,new GeoPoint(data.maxLat(),data.minLon()));
        Point2D bottom=point(panel,new GeoPoint(data.minLat(),data.maxLon()));
        double width=panel.getWidth(),height=panel.getHeight();
        double padX=Math.min(35,width/4),padY=Math.min(42,height/4);
        double campusWidth=bottom.getX()-top.getX(),campusHeight=bottom.getY()-top.getY();
        require(Double.isFinite(top.getX()+top.getY()+bottom.getX()+bottom.getY()),"invalid projection");
        if(campusWidth<=width-2*padX+0.001)require(Math.abs((top.getX()+bottom.getX())/2-width/2)<0.001,"fitted horizontal axis drifted");
        else require(top.getX()<=padX+0.001&&bottom.getX()>=width-padX-0.001,"horizontal edge escaped");
        if(campusHeight<=height-2*padY+0.001)require(Math.abs((top.getY()+bottom.getY())/2-height/2)<0.001,"fitted vertical axis drifted");
        else require(top.getY()<=padY+0.001&&bottom.getY()>=height-padY-0.001,"vertical edge escaped");
    }
    public static void main(String[]args)throws Exception{
        data=CampusMap.load(Path.of(args[0]));
        SwingUtilities.invokeAndWait(()->{
            test("zoom-out-stops-at-full-campus",()->{
                MapPanel panel=panel();for(int i=0;i<100;i++)panel.zoom(1/1.3);
                require(field(panel,"zoom")==1,"campus can still shrink to a tiny rectangle");bounded(panel);
            });
            test("dragging-fitted-map-cannot-move-it-offscreen",()->{
                MapPanel panel=panel();for(int x:new int[]{-100000,100000})for(int y:new int[]{-100000,100000}){
                    drag(panel,x,y);bounded(panel);require(field(panel,"panX")==0&&field(panel,"panY")==0,"fit view moved");
                }
            });
            test("zoomed-map-clamps-all-four-pan-directions",()->{
                MapPanel panel=panel();panel.zoom(8);
                for(int x:new int[]{-100000,100000})for(int y:new int[]{-100000,100000}){drag(panel,x,y);bounded(panel);}
            });
            test("wheel-at-corners-cannot-drift-at-either-limit",()->{
                MapPanel panel=panel();for(Point corner:new Point[]{new Point(0,0),new Point(799,0),new Point(0,599),new Point(799,599)}){
                    for(int i=0;i<70;i++)wheel(panel,-1,corner.x,corner.y);require(field(panel,"zoom")==8,"maximum zoom lost");bounded(panel);
                    for(int i=0;i<100;i++)wheel(panel,1,corner.x,corner.y);require(field(panel,"zoom")==1,"minimum zoom lost");bounded(panel);
                }
            });
            test("resize-reclamps-panned-map-before-rendering",()->{
                MapPanel panel=panel();panel.zoom(8);drag(panel,100000,-100000);
                for(Dimension size:new Dimension[]{new Dimension(420,900),new Dimension(1300,400),new Dimension(80,60),new Dimension(800,600)}){
                    panel.setSize(size);bounded(panel);
                }
            });
            test("selecting-building-keeps-camera-bounded-and-location-visible",()->{
                MapPanel panel=panel();panel.zoom(8);drag(panel,-100000,100000);
                var building=data.buildings().stream().filter(b->b.center().lat()>=data.minLat()&&b.center().lat()<=data.maxLat()
                        &&b.center().lon()>=data.minLon()&&b.center().lon()<=data.maxLon()).findFirst().orElseThrow();
                panel.select(building.id());bounded(panel);Point2D location=point(panel,building.center());
                require(location.getX()>=0&&location.getX()<=800&&location.getY()>=0&&location.getY()<=600,"selected place remains outside view");
            });
            test("free-zoom-preserves-location-below-pointer",()->{
                MapPanel panel=panel();panel.zoom(2);Point anchor=new Point(370,280);GeoPoint before=geo(panel,anchor);
                wheel(panel,-1,anchor.x,anchor.y);GeoPoint after=geo(panel,anchor);
                require(Math.abs(before.lat()-after.lat())<1e-10&&Math.abs(before.lon()-after.lon())<1e-10,"cursor zoom jumped");bounded(panel);
            });
            test("invalid-zoom-factors-cannot-corrupt-transform",()->{
                MapPanel panel=panel();for(double factor:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY})panel.zoom(factor);
                require(field(panel,"zoom")==1,"invalid zoom accepted");bounded(panel);
                panel.setSize(0,0);panel.zoom(2);panel.setSize(800,600);bounded(panel);
            });
            test("campus-reset-restores-both-pan-and-scale",()->{
                MapPanel panel=panel();panel.zoom(8);drag(panel,10000,-10000);panel.resetView();
                require(field(panel,"zoom")==1&&field(panel,"panX")==0&&field(panel,"panY")==0,"reset did not restore fit");bounded(panel);
            });
            test("slow-drag-does-not-turn-into-a-building-click",()->{
                AtomicInteger selected=new AtomicInteger();MapPanel panel=new MapPanel(data,new PlannerModel(),building->selected.incrementAndGet());panel.setSize(800,600);
                var building=data.buildings().stream().filter(b->b.center().lat()>=data.minLat()&&b.center().lat()<=data.maxLat()
                        &&b.center().lon()>=data.minLon()&&b.center().lon()<=data.maxLon()).findFirst().orElseThrow();
                Point2D center=point(panel,building.center());int x=(int)center.getX(),y=(int)center.getY();
                panel.dispatchEvent(new MouseEvent(panel,MouseEvent.MOUSE_PRESSED,0,0,x,y,1,false,1));
                for(int i=1;i<=6;i++)panel.dispatchEvent(new MouseEvent(panel,MouseEvent.MOUSE_DRAGGED,i,InputEvent.BUTTON1_DOWN_MASK,x+i,y,0,false,0));
                panel.dispatchEvent(new MouseEvent(panel,MouseEvent.MOUSE_RELEASED,7,0,x+6,y,1,false,1));
                require(selected.get()==0,"slow drag selected a building");bounded(panel);
            });
        });
        System.out.println("MAP VIEWPORT RESULT: "+passed+" passed, "+failed+" failed");System.exit(failed==0?0:1);
    }
}
