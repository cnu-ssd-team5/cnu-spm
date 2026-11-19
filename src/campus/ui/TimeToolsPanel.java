package campus.ui;
import campus.calendar.PlannerTime;
import campus.service.CountdownTimer;
import java.awt.*;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;
import javax.swing.*;

/** Korea clock and a pauseable countdown, kept across central-view switches. */
public final class TimeToolsPanel extends JPanel implements AutoCloseable {
    private final CountdownTimer countdown;
    private final Consumer<String> notify;
    private final JLabel clock=Theme.label(" ",12,Theme.INK);
    private final JLabel remaining=Theme.heading("25:00",12);
    private final JSpinner minutes=new JSpinner(new SpinnerNumberModel(25,1,1440,1));
    private final JButton start,pause,reset;
    private final javax.swing.Timer ticker;
    private boolean active,closed;
    public TimeToolsPanel(Consumer<String> notify){this(new CountdownTimer(1500),notify);}
    TimeToolsPanel(CountdownTimer countdown,Consumer<String> notify){
        super(new FlowLayout(FlowLayout.LEFT,5,4));setOpaque(false);this.countdown=countdown;this.notify=notify;
        clock.setToolTipText("한국 시각 (Asia/Seoul)");add(clock);minutes.setPreferredSize(new Dimension(58,28));add(minutes);add(Theme.label("분",11,Theme.MUTED));add(remaining);
        start=Theme.button("시작",false,()->{try{minutes.commitEdit();if(!active)countdown.setDurationSeconds(((Number)minutes.getValue()).longValue()*60);countdown.start();active=true;tick();}catch(java.text.ParseException|IllegalArgumentException e){remaining.setText("시간 확인");}});
        pause=Theme.button("일시정지",false,()->{countdown.pause();tick();});
        reset=Theme.button("초기화",false,()->{countdown.setDurationSeconds(((Number)minutes.getValue()).longValue()*60);active=false;tick();});
        for(JButton button:java.util.List.of(start,pause,reset)){button.setFont(Theme.font(11,Font.PLAIN));button.setBorder(BorderFactory.createEmptyBorder(6,7,6,7));add(button);}
        minutes.addChangeListener(e->{if(!active){countdown.setDurationSeconds(((Number)minutes.getValue()).longValue()*60);tick();}});
        ticker=new javax.swing.Timer(250,e->tick());tick();ticker.start();
    }
    void tick(){
        if(closed)return;
        clock.setText(PlannerTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        long seconds=countdown.remainingSeconds();remaining.setText(String.format(java.util.Locale.ROOT,"%02d:%02d",seconds/60,seconds%60));
        boolean running=countdown.isRunning();start.setEnabled(!running);pause.setEnabled(running);minutes.setEnabled(!active||seconds==0);
        start.setText(active&&seconds>0?"계속":"시작");
        if(countdown.pollFinished()){active=false;minutes.setEnabled(true);notify.accept("설정한 타이머가 끝났습니다.");}
    }
    @Override public void close(){if(closed)return;closed=true;ticker.stop();countdown.reset();}
}
