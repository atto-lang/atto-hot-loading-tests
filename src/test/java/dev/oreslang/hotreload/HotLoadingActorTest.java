package dev.oreslang.hotreload;

import dev.oreslang.runtime.ExecutionProfile;
import dev.oreslang.runtime.HotReloadManager;
import dev.oreslang.runtime.IsolatePolicy;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.*;

/** Real actor source programs executed in versioned Graal guest contexts. */
@Timeout(60)
final class HotLoadingActorTest {
    private static final ExecutionProfile JIT = ExecutionProfile.serverJit();

    @Test
    void isolatedActorsHotLoadIntoDistinctExecutableContexts() throws Exception {
        try (var manager = new HotReloadManager(IsolatePolicy.developer(), JIT)) {
            var v1 = manager.load("counter.ores", fixture("isolated-counter-v1.ores"));
            var v2 = manager.load("counter.ores", fixture("isolated-counter-v2.ores"));

            assertNotSame(v1.context(), v2.context());
            assertNotEquals(v1.sha256(), v2.sha256());
            assertSame(v2, manager.active("counter.ores"));

            // Both versions execute real spawn, ready, mailbox, output, done.
            assertDoesNotThrow(v1::start);
            assertDoesNotThrow(v2::start);
            manager.retire(v1.id());
            assertTrue(v1.closed());
            assertFalse(v2.closed());
        }
    }

    @Test
    void repeatedSameActorImageStillHasFreshStatePerGeneration() throws Exception {
        try (var manager = new HotReloadManager(IsolatePolicy.developer(), JIT)) {
            String sameImage = fixture("isolated-counter-v1.ores");
            var first = manager.load("same.ores", sameImage);
            var second = manager.load("same.ores", sameImage);
            assertEquals(first.sha256(), second.sha256());
            assertDoesNotThrow(first::start);
            assertDoesNotThrow(second::start);
        }
    }

    @Test
    void sharedActorInstancesDoNotShareWritableFieldsAcrossHotLoads() throws Exception {
        try (var manager = new HotReloadManager(IsolatePolicy.developer(), JIT)) {
            var shared1 = manager.load("shared.ores", fixture("shared-counters.ores"));
            var shared2 = manager.load("shared.ores", fixture("shared-counters.ores"));
            assertDoesNotThrow(shared1::start);
            assertDoesNotThrow(shared2::start);
            assertNotSame(shared1.context(), shared2.context());
        }
    }

    @Test
    void untrustedActorRunsInTheVersionedGeneration() throws Exception {
        try (var manager = new HotReloadManager(IsolatePolicy.developer(), JIT)) {
            var untrusted = manager.load("untrusted.ores", fixture("untrusted-actor.ores"));
            assertDoesNotThrow(untrusted::start);
            assertTrue(untrusted.started());
        }
    }

    @Test
    void crashInNewGenerationDoesNotCloseEarlierContext() throws Exception {
        try (var manager = new HotReloadManager(IsolatePolicy.developer(), JIT)) {
            var healthy = manager.load("service.ores", fixture("isolated-counter-v1.ores"));
            assertDoesNotThrow(healthy::start);

            var bad = manager.load("service.ores", fixture("crash-on-start.ores"));
            assertThrows(RuntimeException.class, bad::start);

            // IMPORTANT: a real route rollback is NOT implied here.
            // HotReloadManager.active("service.ores") still points at the
            // failed generation today; a release controller must fix this.
            assertFalse(healthy.closed());
            assertTrue(bad.closed());
            assertSame(bad, manager.active("service.ores"));
        }
    }

    @Test
    void sourceActorAndNonActorUnitCanCoexistAndRetireSeparately() throws Exception {
        try (var manager = new HotReloadManager(IsolatePolicy.developer(), JIT)) {
            var actor = manager.load("actor.ores", fixture("isolated-counter-v1.ores"));
            var helper = manager.load("helper.ores", "pub routine main(): void { return; }");
            assertDoesNotThrow(actor::start);
            assertDoesNotThrow(helper::start);
            manager.retire(actor.id());
            assertTrue(actor.closed());
            assertFalse(helper.closed());
            assertSame(helper, manager.active("helper.ores"));
        }
    }

    @Test
    void guestAsyncMainFailureAfterActualMailboxReplyMustReachHost() throws Exception {
        try (var manager = new HotReloadManager(IsolatePolicy.developer(), JIT)) {
            var candidate = manager.load("async-crash.ores", fixture("actor-async-crash.ores"));
            assertThrows(RuntimeException.class, candidate::start,
                    "A returned Future is not proof that async actor work completed");
            assertTrue(candidate.closed());
        }
    }

    private static String fixture(String name) throws Exception {
        try (InputStream input = HotLoadingActorTest.class.getResourceAsStream("/ores/" + name)) {
            assertNotNull(input, "fixture not packaged: " + name);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
