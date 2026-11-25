package com.springproject.lovable_clone.intelligence.llm;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * Values that can fail for a while, such as Gemini API keys or models. A value that fails rests; meanwhile the
 * others are used, and once the rest is over it is offered again like before. Keys are identified in logs only
 * by their position ("key 2 of 3"), never by their value.
 */
final class FallbackPool {

    enum Order {
        /** spread the work: every take moves on to the next value (API keys) */
        IN_TURN,
        /** prefer the first value and use the others only while it rests (main model, then fallbacks) */
        IN_ORDER
    }

    /** A value and its 1-based position in the configuration. */
    record Entry(int number, String value) {}

    private final List<String> values;
    private final Order order;
    private final Instant[] restingUntil;
    private final Clock clock;
    private int next;

    FallbackPool(List<String> values, Order order, Clock clock) {
        this.values = List.copyOf(values);
        this.order = order;
        this.restingUntil = new Instant[values.size()];
        this.clock = clock;
    }

    int size() {
        return values.size();
    }

    List<String> values() {
        return values;
    }

    /** The next value that is not resting, or empty when every value rests. */
    synchronized Optional<Entry> take() {
        Instant now = clock.instant();
        int start = order == Order.IN_TURN ? next : 0;
        for (int i = 0; i < values.size(); i++) {
            int index = (start + i) % values.size();
            if (isFree(index, now)) {
                if (order == Order.IN_TURN) {
                    next = index + 1;
                }
                return Optional.of(entry(index));
            }
        }
        return Optional.empty();
    }

    /** The value whose rest ends first: used when every value rests but waiting is not an option. */
    synchronized Entry soonest() {
        return entry(IntStream.range(0, values.size()).boxed()
                .min(Comparator.comparing(index -> restingUntil[index] == null ? Instant.MIN : restingUntil[index]))
                .orElseThrow());
    }

    synchronized boolean hasFree() {
        Instant now = clock.instant();
        return IntStream.range(0, values.size()).anyMatch(index -> isFree(index, now));
    }

    synchronized void rest(Entry entry, Duration duration) {
        restingUntil[entry.number() - 1] = clock.instant().plus(duration);
    }

    /** Marks the value healthy; true when it had failed before, so it works again. */
    synchronized boolean succeeded(Entry entry) {
        boolean recovered = restingUntil[entry.number() - 1] != null;
        restingUntil[entry.number() - 1] = null;
        return recovered;
    }

    /** Time until the first resting value can be used again (zero when one is free now). */
    synchronized Duration nextFreeIn() {
        Instant now = clock.instant();
        return hasFree() ? Duration.ZERO : Duration.between(now, restingUntil[soonest().number() - 1]);
    }

    private boolean isFree(int index, Instant now) {
        return restingUntil[index] == null || !now.isBefore(restingUntil[index]);
    }

    private Entry entry(int index) {
        return new Entry(index + 1, values.get(index));
    }
}
