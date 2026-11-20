package campus.ui;
import campus.service.*;
import campus.model.*;
import campus.model.PlannerModel.Deadline;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.*;
import javax.swing.*;

public final class TimeReminderTest {
 interface Checked{void run()throws Exception;}
 static int passed,failed;
 static void test(String n,Checked c){try{c.run();passed++;System.out.println("PASS "+n);}catch(Throwable e){failed++;System.out.println("FAIL "+n);e.printStackTrace(System.out);}}
 static void ok(boolean b,String m){if(!b)throw new AssertionError(m);}
 static Deadline item(String id,LocalDate date,boolean completed){return new Deadline(id,id,date,"",completed,completed?date:null);}
 public static void main(String[]args)throws Exception{
  test("timer-rejects-invalid-duration",()->{for(long v:new long[]{0,-1,86401}){try{new CountdownTimer(v);throw new AssertionError("invalid accepted");}catch(IllegalArgumentException expected){}}});
  test("timer-counts-with-monotonic-time",()->{AtomicLong n=new AtomicLong();CountdownTimer t=new CountdownTimer(60,n::get);t.start();n.set(5_000_000_000L);ok(t.remainingSeconds()==55,"wrong remaining");});
  test("timer-pause-freezes-and-resume-continues",()->{AtomicLong n=new AtomicLong();CountdownTimer t=new CountdownTimer(60,n::get);t.start();n.set(10_000_000_000L);t.pause();n.set(100_000_000_000L);ok(t.remainingSeconds()==50,"pause failed");t.start();n.addAndGet(5_000_000_000L);ok(t.remainingSeconds()==45,"resume reset");});
  test("timer-finish-emitted-once",()->{AtomicLong n=new AtomicLong();CountdownTimer t=new CountdownTimer(1,n::get);t.start();n.set(1_000_000_000L);ok(t.pollFinished()&&!t.pollFinished()&&!t.isRunning(),"duplicate finish");});
  test("timer-rounds-fraction-up",()->{AtomicLong n=new AtomicLong();CountdownTimer t=new CountdownTimer(1,n::get);t.start();n.set(999_999_999L);ok(t.remainingSeconds()==1&&!t.pollFinished(),"early finish");});
  test("timer-pause-at-expiry-keeps-finish",()->{AtomicLong n=new AtomicLong();CountdownTimer t=new CountdownTimer(1,n::get);t.start();n.set(2_000_000_000L);t.pause();ok(t.pollFinished(),"lost finish");});
  test("timer-reset-clears-completion",()->{AtomicLong n=new AtomicLong();CountdownTimer t=new CountdownTimer(1,n::get);t.start();n.set(2_000_000_000L);t.reset();ok(t.remainingSeconds()==1&&!t.pollFinished()&&!t.isRunning(),"bad reset");});
  test("timer-restarts-after-finishing",()->{AtomicLong n=new AtomicLong();CountdownTimer t=new CountdownTimer(1,n::get);t.start();n.set(2_000_000_000L);t.pollFinished();t.start();ok(t.remainingSeconds()==1&&t.isRunning(),"restart failed");});
  test("timer-1000-start-pause-reset-cycles",()->{AtomicLong n=new AtomicLong();CountdownTimer t=new CountdownTimer(10,n::get);for(int i=0;i<1000;i++){t.start();n.addAndGet(1000);t.pause();t.reset();}ok(t.remainingSeconds()==10&&!t.isRunning(),"stress state");});
  Path root=Files.createTempDirectory("cnu-reminder-");LocalDate today=LocalDate.of(2026,11,19);
  test("reminds-today-and-tomorrow-only",()->{PlannerModel m=new PlannerModel();m.putDeadline(item("today",today,false));m.putDeadline(item("tomorrow",today.plusDays(1),false));m.putDeadline(item("past",today.minusDays(1),false));m.putDeadline(item("later",today.plusDays(2),false));m.putDeadline(item("done",today,true));var s=new DeadlineReminderService(root.resolve("range.properties"));List<String> notices=new ArrayList<>();ok(s.check(m,today,r->notices.add(r.message()))==2&&notices.size()==2,"wrong range");});
  test("same-day-dedup-across-repeated-checks",()->{PlannerModel m=new PlannerModel();m.putDeadline(item("x",today,false));var s=new DeadlineReminderService(root.resolve("dedup.properties"));AtomicInteger count=new AtomicInteger();for(int i=0;i<200;i++)s.check(m,today,r->count.incrementAndGet());ok(count.get()==1,"spam");});
  test("dedup-survives-restart",()->{PlannerModel m=new PlannerModel();m.putDeadline(item("x",today,false));Path file=root.resolve("restart.properties");new DeadlineReminderService(file).check(m,today,r->{});ok(new DeadlineReminderService(file).check(m,today,r->{throw new AssertionError("duplicate");})==0,"restart duplicate");});
  test("next-day-gets-new-reminder",()->{PlannerModel m=new PlannerModel();m.putDeadline(item("x",today.plusDays(1),false));var s=new DeadlineReminderService(root.resolve("next.properties"));ok(s.check(m,today,r->{})==1&&s.check(m,today.plusDays(1),r->{})==1,"next day lost");});
  test("completion-and-deletion-suppress-reminder",()->{PlannerModel m=new PlannerModel();m.putDeadline(item("x",today,true));var s=new DeadlineReminderService(root.resolve("done.properties"));ok(s.check(m,today,r->{})==0,"completed alerted");m.removeDeadline("x");ok(s.check(m,today,r->{})==0,"deleted alerted");});
  test("changed-due-date-can-remind-again",()->{PlannerModel m=new PlannerModel();m.putDeadline(item("x",today,false));var s=new DeadlineReminderService(root.resolve("change.properties"));s.check(m,today,r->{});m.putDeadline(item("x",today.plusDays(1),false));ok(s.check(m,today,r->{})==1,"date edit not reflected");});
  test("delivery-failure-can-retry",()->{PlannerModel m=new PlannerModel();m.putDeadline(item("x",today,false));var s=new DeadlineReminderService(root.resolve("retry.properties"));ok(s.check(m,today,r->{throw new RuntimeException("failed");})==0,"failed marked delivered");ok(s.check(m,today,r->{})==1,"retry blocked");});
  test("record-write-failure-no-in-process-spam",()->{Path parent=root.resolve("not-directory");Files.writeString(parent,"x");PlannerModel m=new PlannerModel();m.putDeadline(item("x",today,false));var s=new DeadlineReminderService(parent.resolve("ledger"));ok(s.check(m,today,r->{})==1&&!s.lastError().isEmpty(),"save error missing");ok(s.check(m,today,r->{throw new AssertionError("spam");})==0,"write error spam");});
  test("malformed-records-recover",()->{Path file=root.resolve("malformed.properties");Files.writeString(file,"x=\\uNOTHEX");var s=new DeadlineReminderService(file);PlannerModel m=new PlannerModel();m.putDeadline(item("x",today,false));ok(s.check(m,today,r->{})==1,"malformed blocked");});
  SwingUtilities.invokeAndWait(()->{
   test("time-tools-start-pause-reset-and-single-notice",()->{AtomicLong n=new AtomicLong();AtomicInteger notices=new AtomicInteger();TimeToolsPanel p=new TimeToolsPanel(new CountdownTimer(60,n::get),s->notices.incrementAndGet());try{JSpinner spinner=(JSpinner)ScheduleEntryWhiteBoxTest.field(p,"minutes");spinner.setValue(1);JPanel timer=(JPanel)ScheduleEntryWhiteBoxTest.field(p,"timerPanel");ScheduleEntryWhiteBoxTest.button(timer,"시작").doClick();n.set(30_000_000_000L);ScheduleEntryWhiteBoxTest.button(timer,"일시정지").doClick();ok(!spinner.isEnabled(),"paused duration editable");n.set(90_000_000_000L);ScheduleEntryWhiteBoxTest.button(timer,"계속").doClick();n.addAndGet(31_000_000_000L);p.tick();p.tick();ok(notices.get()==1&&spinner.isEnabled(),"finish callback");ScheduleEntryWhiteBoxTest.button(timer,"초기화").doClick();ok(((JLabel)ScheduleEntryWhiteBoxTest.field(p,"remaining")).getText().equals("01:00"),"reset text");}finally{p.close();}});
   test("time-tools-close-stops-ticker",()->{TimeToolsPanel p=new TimeToolsPanel(s->{});p.close();p.close();ok(!((javax.swing.Timer)ScheduleEntryWhiteBoxTest.field(p,"ticker")).isRunning(),"ticker leaked");});
   test("notification-fallback-and-close-safe",()->{JLabel label=new JLabel();NotificationCenter c=new NotificationCenter(label);c.show("test","message");ok(label.getText().contains("message"),"fallback missing");c.close();c.close();String old=label.getText();c.show("after","close");ok(label.getText().equals(old),"closed notified");});
  });
  System.out.println("TIME REMINDER RESULT: "+passed+" passed, "+failed+" failed");System.exit(failed==0?0:1);
 }
}
