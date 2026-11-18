package campus.ui;
import campus.calendar.PlannerTime;
import campus.model.PlannerModel;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import javax.swing.*;
public class WeekBarRegressionTest {
 static int passed;
 static Object field(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
 static void ok(boolean v,String name){if(!v)throw new AssertionError(name);passed++;System.out.println("PASS "+name);}
 public static void main(String[]a)throws Exception{
  SwingUtilities.invokeAndWait(()->{try{
   WeekBar bar=new WeekBar(null,new PlannerModel());
   for(LocalDate selected:new LocalDate[]{LocalDate.of(2026,11,15),LocalDate.of(2026,11,16),LocalDate.of(2026,11,21),LocalDate.of(2026,12,31),LocalDate.of(2027,1,1),LocalDate.of(2028,2,29)}){
    bar.refresh(selected);JPanel days=(JPanel)field(bar,"weekDays");LocalDate start=selected.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
    ok(days.getComponentCount()==7,"seven-days-"+selected);
    for(int i=0;i<7;i++){JButton b=(JButton)days.getComponent(i);if(!b.getName().equals(start.plusDays(i).toString()))throw new AssertionError("wrong date");}
    ok(true,"sunday-to-saturday-"+selected);
    JButton chosen=(JButton)days.getComponent((selected.getDayOfWeek().getValue())%7);
    ok(Theme.SELECTED_DAY.equals(chosen.getClientProperty("calendar.fill")),"selected-yellow-"+selected);
   }
   LocalDate today=PlannerTime.today();bar.refresh(today);JPanel days=(JPanel)field(bar,"weekDays");JButton button=(JButton)days.getComponent(today.getDayOfWeek().getValue()%7);
   ok(button.getForeground().equals(Theme.TODAY_TEXT),"today-red");
   ok(button.getClientProperty("calendar.today")==null,"no-old-today-outline");
   button.setSize(140,58);BufferedImage image=new BufferedImage(140,58,BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();button.paint(g);g.dispose();
   ok(new Color(image.getRGB(12,12)).equals(Theme.SELECTED_DAY),"selected-button-painted-yellow");
   LocalDate other=today.plusDays(today.getDayOfWeek()==DayOfWeek.SATURDAY?-1:1);bar.refresh(other);button=(JButton)((JPanel)field(bar,"weekDays")).getComponent(today.getDayOfWeek().getValue()%7);
   ok(button.getForeground().equals(Theme.TODAY_TEXT)&&!Theme.SELECTED_DAY.equals(button.getClientProperty("calendar.fill")),"unselected-today-red-without-yellow");
  }catch(Exception e){throw new RuntimeException(e);}});
  System.out.println("WEEK BAR RESULT: "+passed+" passed");
 }
}
