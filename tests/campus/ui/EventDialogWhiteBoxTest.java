package campus.ui;

import campus.map.CampusMap;
import campus.model.PlannerModel;
import campus.model.PlannerModel.Event;
import campus.timetable.TimePicker;
import campus.service.ScheduleSlot;
import java.lang.reflect.*;
import java.nio.file.Path;
import java.time.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.*;

/** Constructs hidden dialogs and calls their save path; no native input or visible windows. */
public final class EventDialogWhiteBoxTest {
    static Object field(Object o,String name)throws Exception {Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    static void ok(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception {
        CampusMap map=CampusMap.load(Path.of(args[0]));var building=map.buildings().get(0);
        var constructor=EventDialog.class.getDeclaredConstructor(JFrame.class,CampusMap.class,PlannerModel.class,LocalDate.class,CampusMap.Building.class,Event.class,boolean.class,ScheduleSlot.class);constructor.setAccessible(true);
        var save=EventDialog.class.getDeclaredMethod("save");save.setAccessible(true);
        AtomicReference<Throwable> failure=new AtomicReference<>();
        SwingUtilities.invokeAndWait(()->{
            try {
                LocalDate past=LocalDate.of(2020,1,6);
                EventDialog dialog=constructor.newInstance(null,map,new PlannerModel(),past,building,null,false,null);
                try {
                    ok(((JTextField)field(dialog,"date")).getText().equals(past.toString()),"past date clamped");
                    ((JTextField)field(dialog,"title")).setText("Late study");
                    ((TimePicker)field(dialog,"start")).setTime(LocalTime.of(23,55));((TimePicker)field(dialog,"end")).setTime(LocalTime.MAX);
                    save.invoke(dialog);var result=(EventDialog.Result)field(dialog,"result");
                    ok(result!=null&&result.event().date().equals(past)&&result.event().end().equals(LocalTime.MAX),"save changed date or midnight");
                    System.out.println("PASS daily-dialog-past-date-preserved-and-24-end-saved");
                } finally {dialog.dispose();}
                Event original=new Event("old","Legacy",building.id(),LocalTime.of(7,3,14),LocalTime.of(8,17,22),PlannerModel.Category.OTHER,null,past,"memo");
                dialog=constructor.newInstance(null,map,new PlannerModel(),LocalDate.of(2027,3,1),building,original,false,null);
                try {
                    save.invoke(dialog);var result=(EventDialog.Result)field(dialog,"result");ok(result.event().equals(original),"legacy edit changed original values");
                    System.out.println("PASS daily-dialog-edit-preserves-off-grid-times-and-original-date");
                }finally{dialog.dispose();}
                dialog=constructor.newInstance(null,map,new PlannerModel(),past,building,null,false,null);
                try {
                    ((JTextField)field(dialog,"title")).setText("Invalid");((TimePicker)field(dialog,"start")).setTime(LocalTime.of(12,0));((TimePicker)field(dialog,"end")).setTime(LocalTime.of(10,0));save.invoke(dialog);
                    ok(field(dialog,"result")==null&&!((JLabel)field(dialog,"error")).getText().isBlank(),"invalid interval saved");
                    System.out.println("PASS daily-dialog-invalid-duration-displays-error");
                }finally{dialog.dispose();}
            }catch(Throwable error){failure.set(error);}
        });
        if(failure.get()!=null){failure.get().printStackTrace();System.exit(1);}
        System.out.println("DIALOG RESULT: 3 passed, 0 failed");
    }
}
