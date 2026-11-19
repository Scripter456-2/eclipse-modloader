package dev.eclipse.api;

/** Base class for events. Extend it; extend and use cancel() for cancellable ones. */
public abstract class Event {
    private boolean cancelled;
    public boolean isCancelled() { return cancelled; }
    public void cancel() { this.cancelled = true; }
}
