package dev.eclipse.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** Tiny type-based event bus. Higher priority runs first. */
public final class EventBus {
    private record Handler(int priority, Consumer<Object> fn) {}

    private final Map<Class<?>, List<Handler>> handlers = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public <E> void subscribe(Class<E> type, Consumer<E> handler, int priority) {
        List<Handler> list = handlers.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>());
        list.add(new Handler(priority, (Consumer<Object>) handler));
        list.sort((a, b) -> Integer.compare(b.priority, a.priority));
    }

    public <E> void subscribe(Class<E> type, Consumer<E> handler) {
        subscribe(type, handler, 0);
    }

    /** Posts an event to subscribers of its class and its superclasses. Returns the event. */
    public <E> E post(E event) {
        List<Handler> all = new ArrayList<>();
        for (Class<?> c = event.getClass(); c != null; c = c.getSuperclass()) {
            List<Handler> l = handlers.get(c);
            if (l != null) all.addAll(l);
        }
        all.sort((a, b) -> Integer.compare(b.priority, a.priority));
        for (Handler h : all) {
            try {
                h.fn.accept(event);
            } catch (Throwable t) {
                System.err.println("[Eclipse] Event handler crashed on " + event.getClass().getSimpleName());
                t.printStackTrace();
            }
            if (event instanceof Event e && e.isCancelled()) break;
        }
        return event;
    }
}
