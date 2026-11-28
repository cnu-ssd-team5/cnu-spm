package campus.service;
import java.util.function.LongSupplier;

/** Monotonic countdown, independent of Swing and wall-clock changes. */
public final class CountdownTimer {
    private final LongSupplier nanos;
    private long duration,remaining,deadline;
    private boolean running,finished;
    public CountdownTimer(long seconds) {this(seconds,System::nanoTime);}
    public CountdownTimer(long seconds,LongSupplier nanos) {this.nanos=java.util.Objects.requireNonNull(nanos);setDurationSeconds(seconds);}
    public synchronized void setDurationSeconds(long seconds) {
        if(seconds<1||seconds>86400)throw new IllegalArgumentException("타이머는 1초~24시간 사이로 설정하세요.");
        duration=seconds*1_000_000_000L;reset();
    }
    private void update() {
        if(running){remaining=Math.max(0,deadline-nanos.getAsLong());if(remaining==0){running=false;finished=true;}}
    }
    public synchronized void start() {
        update();if(running)return;
        if(remaining==0)remaining=duration;
        finished=false;deadline=nanos.getAsLong()+remaining;running=true;
    }
    public synchronized void pause() {update();running=false;}
    public synchronized void reset() {running=false;finished=false;remaining=duration;}
    public synchronized long remainingSeconds() {update();return (remaining+999_999_999L)/1_000_000_000L;}
    public synchronized boolean isRunning(){update();return running;}
    public synchronized boolean pollFinished(){update();boolean result=finished;finished=false;return result;}
}
