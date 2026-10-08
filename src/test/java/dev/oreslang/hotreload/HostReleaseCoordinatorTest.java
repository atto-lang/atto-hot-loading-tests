package dev.oreslang.hotreload;

import dev.oreslang.runtime.ExecutionProfile;
import dev.oreslang.runtime.HotReloadManager;
import dev.oreslang.runtime.IsolatePolicy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A TEST-ONLY trusted-host release coordinator built on today's API.
 * Demonstrates the epoch and rollback protocol; NOT runtime supervisor/OTP
 * implementation. HotReloadManager.active() is NOT a safe routing pointer.
 */
@Timeout(40)
final class HostReleaseCoordinatorTest {
    private static final String GOOD = "pub routine main(): void { return; }";
    private static final String BAD = """
            pub routine main(): void {
              val values = arr[1];
              val failed = values[99];
              return;
            }
            """;

    @Test
    void stagingDoesNotChangeIndependentHostRoutingPointer() {
        try (var host = new HostCoordinator()) {
            var first = host.stage("service.ores", GOOD);
            assertNull(host.routed());
            assertEquals(0, host.epoch());
            assertTrue(host.promote(first, 0));
            var original = host.routed();

            var candidate = host.stage("service.ores", GOOD);
            assertSame(original, host.routed());
            assertEquals(1, host.epoch());
            host.abandon(candidate);
            assertSame(original, host.routed());
        }
    }

    @Test
    void failedCanaryLeavesOldHealthyGenerationRoutable() {
        try (var host = new HostCoordinator()) {
            assertTrue(host.promote(host.stage("service.ores", GOOD), 0));
            var healthy = host.routed();
            var bad = host.stage("service.ores", BAD);

            assertFalse(host.promote(bad, 1));
            assertSame(healthy, host.routed());
            assertEquals(1, host.epoch());
            assertFalse(healthy.closed());
            assertTrue(bad.generation.closed());
        }
    }

    @Test
    void concurrentUpgradePlansUseCompareAndSwapEpoch() {
        try (var host = new HostCoordinator()) {
            assertTrue(host.promote(host.stage("service.ores", GOOD), 0));
            var contenderA = host.stage("service.ores", GOOD);
            var contenderB = host.stage("service.ores", GOOD);
            assertTrue(host.promote(contenderA, 1));
            var winner = host.routed();
            assertFalse(host.promote(contenderB, 1));
            assertSame(winner, host.routed());
            assertEquals(2, host.epoch());
            assertTrue(contenderB.generation.closed());
        }
    }

    @Test
    void oldGenerationStaysAliveUntilExplicitDrainAndRetire() {
        try (var host = new HostCoordinator()) {
            assertTrue(host.promote(host.stage("service.ores", GOOD), 0));
            var previous = host.routed();
            assertTrue(host.promote(host.stage("service.ores", GOOD), 1));
            assertFalse(previous.closed());
            assertNotSame(previous, host.routed());
            host.retireOld(previous);
            assertTrue(previous.closed());
            assertFalse(host.routed().closed());
        }
    }

    @Test
    void failedCandidateDoesNotAdvanceEpochOrLeakGeneration() {
        try (var host = new HostCoordinator()) {
            assertTrue(host.promote(host.stage("service.ores", GOOD), 0));
            int live = host.manager.liveGenerations();
            var bad = host.stage("service.ores", BAD);
            assertFalse(host.promote(bad, 1));
            assertEquals(live, host.manager.liveGenerations());
            assertEquals(1, host.epoch());
        }
    }

    @Test
    void closeSealsAllHostedGenerations() {
        var host = new HostCoordinator();
        var first = host.stage("service.ores", GOOD);
        assertTrue(host.promote(first, 0));
        var next = host.stage("service.ores", GOOD);
        host.close();
        assertTrue(first.generation.closed());
        assertTrue(next.generation.closed());
    }

    @Test
    void twoConcurrentPromotionAttemptsHaveExactlyOneWinner() throws Exception {
        try (var host = new HostCoordinator()) {
            assertTrue(host.promote(host.stage("service.ores", GOOD), 0));
            var first = host.stage("service.ores", GOOD);
            var second = host.stage("service.ores", GOOD);
            var ready = new CountDownLatch(2);
            var release = new CountDownLatch(1);

            try (var pool = Executors.newFixedThreadPool(2)) {
                var left = pool.submit(() -> {
                    ready.countDown();
                    if (!release.await(10, TimeUnit.SECONDS)) {
                        throw new AssertionError("promotion barrier did not release");
                    }
                    return host.promote(first, 1);
                });
                var right = pool.submit(() -> {
                    ready.countDown();
                    if (!release.await(10, TimeUnit.SECONDS)) {
                        throw new AssertionError("promotion barrier did not release");
                    }
                    return host.promote(second, 1);
                });

                assertTrue(ready.await(10, TimeUnit.SECONDS));
                release.countDown();
                boolean a = left.get(15, TimeUnit.SECONDS);
                boolean b = right.get(15, TimeUnit.SECONDS);
                assertNotEquals(a, b, "exactly one candidate may win the epoch");
                assertEquals(2, host.epoch());
                assertEquals(2, host.manager.liveGenerations(),
                        "previous serving version and one successful candidate stay live");
            }
        }
    }

    @Test
    void consumedCandidateCannotBePromotedTwiceOrReusedAfterAbandon() {
        try (var host = new HostCoordinator()) {
            var first = host.stage("service.ores", GOOD);
            assertTrue(host.promote(first, 0));
            assertThrows(IllegalArgumentException.class, () -> host.promote(first, 1));

            var second = host.stage("service.ores", GOOD);
            host.abandon(second);
            assertThrows(IllegalArgumentException.class, () -> host.promote(second, 1));
            assertEquals(1, host.epoch());
        }
    }

    private static final class HostCoordinator implements AutoCloseable {
        private final HotReloadManager manager =
                new HotReloadManager(IsolatePolicy.developer(), ExecutionProfile.serverJit());
        private final Map<Long, Candidate> pending = new HashMap<>();
        private HotReloadManager.Generation routed;
        private long epoch;
        private boolean closed;

        private synchronized Candidate stage(String unit, String source) {
            ensureOpen();
            var generation = manager.load(unit, source);
            var candidate = new Candidate(generation);
            pending.put(generation.id(), candidate);
            return candidate;
        }

        private synchronized boolean promote(Candidate candidate, long expectedEpoch) {
            ensureOpen();
            if (pending.get(candidate.generation.id()) != candidate) {
                throw new IllegalArgumentException("not a pending candidate");
            }
            pending.remove(candidate.generation.id());
            if (expectedEpoch != epoch) {
                manager.retire(candidate.generation.id());
                return false;
            }
            try {
                candidate.generation.start();
            } catch (RuntimeException failure) {
                manager.retire(candidate.generation.id());
                return false;
            }
            routed = candidate.generation;
            epoch++;
            return true;
        }

        private synchronized void abandon(Candidate candidate) {
            ensureOpen();
            if (pending.remove(candidate.generation.id(), candidate)) {
                manager.retire(candidate.generation.id());
            }
        }

        private synchronized void retireOld(HotReloadManager.Generation generation) {
            ensureOpen();
            if (generation == routed) {
                throw new IllegalArgumentException("cannot retire currently routed generation");
            }
            manager.retire(generation.id());
        }

        private synchronized long epoch() { return epoch; }
        private synchronized HotReloadManager.Generation routed() { return routed; }

        private void ensureOpen() {
            if (closed) {
                throw new IllegalStateException("coordinator closed");
            }
        }

        @Override
        public synchronized void close() {
            if (closed) {
                return;
            }
            closed = true;
            pending.clear();
            routed = null;
            manager.close();
        }
    }

    private record Candidate(HotReloadManager.Generation generation) {}
}
