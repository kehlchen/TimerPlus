package dev.timerplus.event;

import dev.timerplus.GameTimer;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/** Wird ausgeloest, wenn ein Timer seinen Zustand aendert (API fuer andere Plugins). */
public class TimerStateChangeEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID owner;
    private final GameTimer.State oldState;
    private final GameTimer.State newState;
    private final long displayMillis;

    public TimerStateChangeEvent(UUID owner, GameTimer.State oldState, GameTimer.State newState, long displayMillis) {
        this.owner = owner;
        this.oldState = oldState;
        this.newState = newState;
        this.displayMillis = displayMillis;
    }

    /** UUID des Besitzers bei persoenlichen Timern, {@code null} beim globalen Timer. */
    public UUID getOwner() {
        return owner;
    }

    public boolean isGlobal() {
        return owner == null;
    }

    public GameTimer.State getOldState() {
        return oldState;
    }

    public GameTimer.State getNewState() {
        return newState;
    }

    public long getDisplayMillis() {
        return displayMillis;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
