package campus.service;
import campus.model.PlannerModel;
import java.nio.file.*;
import java.io.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Consumer;

/** One reminder per deadline/due date/day, persisted locally across app restarts. */
public final class DeadlineReminderService {
    public record Reminder(String key,String title,String message){}
    private final Path file;
    private final Properties sent=new Properties();
    private String lastError="";
    public DeadlineReminderService(Path file){
        this.file=file.toAbsolutePath();
        if(Files.exists(this.file))try(InputStream in=Files.newInputStream(this.file)){sent.load(in);}
        catch(IOException|RuntimeException e){sent.clear();lastError="이전 알림 기록을 읽지 못했습니다.";}
    }
    public synchronized String lastError(){return lastError;}
    public synchronized int check(PlannerModel model,LocalDate today,Consumer<Reminder> deliver){
        int count=0;boolean changed=false;
        var items=model.deadlines();
        for(String key:new HashSet<>(sent.stringPropertyNames())){
            String value=sent.getProperty(key);
            try{if(LocalDate.parse(value).isBefore(today.minusDays(7))){sent.remove(key);changed=true;}}
            catch(RuntimeException e){sent.remove(key);changed=true;}
        }
        for(var item:items){
            long days=ChronoUnit.DAYS.between(today,item.date());
            if(item.completed()||days<0||days>1)continue;
            String key=item.id()+"|"+item.date()+"|"+today;
            if(sent.containsKey(key))continue;
            Reminder reminder=new Reminder(key,"과제·할일 마감",item.title()+" · "+(days==0?"오늘 마감":"내일 마감")+" ("+item.date()+")");
            try{deliver.accept(reminder);}catch(RuntimeException e){lastError="알림 전달을 다시 시도합니다.";continue;}
            sent.setProperty(key,today.toString());changed=true;count++;
        }
        if(changed)save();return count;
    }
    private void save(){
        Path temp=null;
        try{
            Files.createDirectories(file.getParent());temp=Files.createTempFile(file.getParent(),"deadline-alert-",".tmp");
            try(OutputStream out=Files.newOutputStream(temp)){sent.store(out,"Local deadline reminder history");}
            try{Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException e){Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}
            lastError="";
        }catch(IOException|SecurityException e){lastError="알림 기록 저장 실패: 이번 실행의 중복 알림은 방지됩니다.";}
        finally{if(temp!=null)try{Files.deleteIfExists(temp);}catch(IOException|SecurityException ignored){}}
    }
}
