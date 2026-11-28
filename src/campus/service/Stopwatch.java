package campus.service;

import java.util.Objects;
import java.util.function.LongSupplier;

/** Measures elapsed time independently of wall-clock changes. */
public final class Stopwatch {
    private final LongSupplier clock;
    private long accumulated;
    private long started;
    private boolean running;

    public Stopwatch() { this(System::nanoTime); }

    Stopwatch(LongSupplier clock) { this.clock = Objects.requireNonNull(clock); }

    public void start() {
        if (running) return;
        started = clock.getAsLong();
        running = true;
    }

    public void pause() {
        if (!running) return;
        accumulated = elapsedNanos();
        running = false;
    }

    public void reset() {
        running = false;
        accumulated = 0;
    }

    public boolean isRunning() { return running; }

    public long elapsedNanos() {
        return running ? accumulated + (clock.getAsLong() - started) : accumulated;
    }
}
