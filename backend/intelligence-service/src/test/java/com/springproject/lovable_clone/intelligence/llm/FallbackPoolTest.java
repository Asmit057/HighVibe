package com.springproject.lovable_clone.intelligence.llm;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FallbackPoolTest {

    private final MovableClock clock = new MovableClock();
    private final FallbackPool keys = new FallbackPool(List.of("a", "b", "c"), FallbackPool.Order.IN_TURN, clock);
    private final FallbackPool models = new FallbackPool(List.of("main", "backup"), FallbackPool.Order.IN_ORDER, clock);

    @Test
    void keysAreHandedOutInTurn() {
        assertThat(List.of(take(keys), take(keys), take(keys), take(keys))).containsExactly("a", "b", "c", "a");
    }

    @Test
    void aRestingKeyIsSkippedAndOfferedAgainWhenTheRestIsOver() {
        FallbackPool.Entry a = keys.take().orElseThrow();
        keys.rest(a, Duration.ofMinutes(10));

        assertThat(List.of(take(keys), take(keys), take(keys))).containsExactly("b", "c", "b");

        clock.advance(Duration.ofMinutes(10));
        assertThat(List.of(take(keys), take(keys))).containsExactly("c", "a");
    }

    @Test
    void theMainModelIsPreferredAndTheBackupOnlyUsedWhileItRests() {
        assertThat(List.of(take(models), take(models))).containsExactly("main", "main");

        models.rest(models.take().orElseThrow(), Duration.ofMinutes(5));
        assertThat(List.of(take(models), take(models))).containsExactly("backup", "backup");

        clock.advance(Duration.ofMinutes(5));
        assertThat(take(models)).isEqualTo("main");
    }

    @Test
    void aValueThatWorksAgainIsReported() {
        FallbackPool.Entry a = keys.take().orElseThrow();
        assertThat(keys.succeeded(a)).isFalse();

        keys.rest(a, Duration.ofSeconds(30));
        clock.advance(Duration.ofSeconds(30));

        assertThat(keys.succeeded(a)).isTrue();
        assertThat(keys.succeeded(a)).isFalse();
    }

    @Test
    void whenEverythingRestsNothingIsFreeAndTheSoonestIsKnown() {
        models.rest(new FallbackPool.Entry(1, "main"), Duration.ofSeconds(60));
        models.rest(new FallbackPool.Entry(2, "backup"), Duration.ofSeconds(40));

        assertThat(models.take()).isEmpty();
        assertThat(models.hasFree()).isFalse();
        assertThat(models.soonest().value()).isEqualTo("backup");
        assertThat(models.nextFreeIn()).isEqualTo(Duration.ofSeconds(40));
    }

    private static String take(FallbackPool pool) {
        return pool.take().orElseThrow().value();
    }

    static final class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
