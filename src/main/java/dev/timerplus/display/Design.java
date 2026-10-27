package dev.timerplus.display;

/** Ein Anzeige-Design: Zeitformat plus MiniMessage-Vorlagen fuer jeden Zustand. */
public record Design(String name, String timeFormat, String idle, String running, String finished) { }
