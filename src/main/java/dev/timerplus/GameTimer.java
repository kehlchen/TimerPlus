package dev.timerplus;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reiner Zeit-Zustand (Stoppuhr oder Countdown). Nutzt System.nanoTime(),
 * dadurch ist die Zeit unabhaengig von der Server-TPS.
 */
public final class GameTimer {

    public enum State { IDLE, RUNNING, PAUSED, FINISHED }

    public enum Direction { UP, DOWN }

    public record Lap(String name, long millis) { }

    private State state = State.IDLE;
    private Direction direction = Direction.UP;
    private long targetMillis = 0L;
    private long accumulatedMillis = 0L;
    private long runningSinceNanos = 0L;
    private long lastWarnedSecond = -1L;
    private final List<Lap> laps = new ArrayList<>();

    public State state() {
        return state;
    }

    public Direction direction() {
        return direction;
    }

    public long targetMillis() {
        return targetMillis;
    }

    public boolean isRunning() {
        return state == State.RUNNING;
    }

    public List<Lap> laps() {
        return Collections.unmodifiableList(laps);
    }

    public long lastWarnedSecond() {
        return lastWarnedSecond;
    }

    public void lastWarnedSecond(long value) {
        this.lastWarnedSecond = value;
    }

    /** Vergangene Zeit seit dem Start (ohne Pausen). */
    public long elapsedMillis() {
        if (state == State.RUNNING) {
            return accumulatedMillis + (System.nanoTime() - runningSinceNanos) / 1_000_000L;
        }
        return accumulatedMillis;
    }

    /** Angezeigte Zeit: Stoppuhr = vergangene Zeit, Countdown = Restzeit. */
    public long displayMillis() {
        if (direction == Direction.DOWN) {
            return Math.max(0L, targetMillis - elapsedMillis());
        }
        return elapsedMillis();
    }

    public boolean start() {
        if (state == State.RUNNING || state == State.FINISHED) return false;
        if (direction == Direction.DOWN && accumulatedMillis >= targetMillis) return false;
        runningSinceNanos = System.nanoTime();
        state = State.RUNNING;
        lastWarnedSecond = -1L;
        return true;
    }

    public boolean pause() {
        if (state != State.RUNNING) return false;
        accumulatedMillis = elapsedMillis();
        state = State.PAUSED;
        return true;
    }

    public boolean finish() {
        if (state != State.RUNNING && state != State.PAUSED) return false;
        long elapsed = elapsedMillis();
        accumulatedMillis = direction == Direction.DOWN ? Math.min(elapsed, targetMillis) : elapsed;
        state = State.FINISHED;
        return true;
    }

    public void reset() {
        state = State.IDLE;
        accumulatedMillis = 0L;
        runningSinceNanos = 0L;
        lastWarnedSecond = -1L;
        laps.clear();
    }

    /** Wechselt den Modus und setzt den Timer zurueck. */
    public void setDirection(Direction newDirection) {
        if (this.direction == newDirection) return;
        this.direction = newDirection;
        reset();
    }

    /** Countdown-Startzeit setzen (Restzeit = Startzeit). */
    public void setTarget(long millis) {
        targetMillis = Math.max(0L, millis);
        accumulatedMillis = 0L;
        lastWarnedSecond = -1L;
        if (state == State.RUNNING) {
            runningSinceNanos = System.nanoTime();
        } else if (state == State.FINISHED) {
            state = State.IDLE;
        }
    }

    /** Stoppuhr auf eine bestimmte Zeit setzen. */
    public void setElapsed(long millis) {
        accumulatedMillis = Math.max(0L, millis);
        lastWarnedSecond = -1L;
        if (state == State.RUNNING) {
            runningSinceNanos = System.nanoTime();
        } else if (state == State.FINISHED) {
            state = State.PAUSED;
        }
    }

    /** Zeit addieren (positiv) oder abziehen (negativ) - bezogen auf die angezeigte Zeit. */
    public void adjust(long deltaMillis) {
        if (direction == Direction.DOWN) {
            targetMillis = Math.max(0L, targetMillis + deltaMillis);
            if (state == State.FINISHED && targetMillis > accumulatedMillis) {
                state = State.PAUSED;
            }
        } else {
            setElapsed(elapsedMillis() + deltaMillis);
        }
    }

    public void addLap(String name) {
        laps.add(new Lap(name, displayMillis()));
    }

    public void save(ConfigurationSection section) {
        section.set("direction", direction.name());
        section.set("target", targetMillis);
        section.set("elapsed", elapsedMillis());
        section.set("state", state.name());
        List<String> out = new ArrayList<>();
        for (Lap lap : laps) {
            out.add(lap.name() + ";" + lap.millis());
        }
        section.set("laps", out);
    }

    public void load(ConfigurationSection section, boolean resumeIfRunning) {
        direction = parseEnum(Direction.class, section.getString("direction"), Direction.UP);
        targetMillis = section.getLong("target", 0L);
        accumulatedMillis = section.getLong("elapsed", 0L);
        State saved = parseEnum(State.class, section.getString("state"), State.IDLE);
        state = saved == State.RUNNING ? State.PAUSED : saved;
        laps.clear();
        for (String raw : section.getStringList("laps")) {
            int idx = raw.lastIndexOf(';');
            if (idx < 0) continue;
            try {
                laps.add(new Lap(raw.substring(0, idx), Long.parseLong(raw.substring(idx + 1))));
            } catch (NumberFormatException ignored) {
                // kaputte Zeile ueberspringen
            }
        }
        if (saved == State.RUNNING && resumeIfRunning) {
            start();
        }
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String name, E fallback) {
        if (name == null) return fallback;
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
