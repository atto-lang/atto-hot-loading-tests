package dev.oreslang.hotreload;

import dev.oreslang.runtime.ExecutionProfile;
import dev.oreslang.runtime.HotReloadManager;
import dev.oreslang.runtime.IsolatePolicy;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Source-backed edge cases using the pinned, real Graal/Oreslang runtime.
 * These tests do not claim that a supervisor, mailbox router, or atomic
 * activation API exists. The active pointer's current failure is explicit.
 */
@Timeout(90)
final class HotLoadingUpgradeEdgeCasesTest {
    private static final ExecutionProfile JIT = ExecutionProfile.serverJit();
    private static final String NOOP = "pub routine main(): void { return; }";
    private static final String SYNTAX_ERROR = "pub routine main(: void { return; }";
    private static final String FAILS_AT_START = """
            pub routine main(): void {
              val numbers = arr[1];
              val invalid = numbers[99];
              return;
            }
            """;

    @Test
    void malformedReplacementMustNotDisplaceEarlierLoadedActor() throws Exception {
        try (var manager = new HotReloadManager(IsolatePolicy.developer(), JIT)) {
            var healthy = manager.load("counter.ores", fixture("isolated-counter-v1.ores"));
            assertThrows(RuntimeException.class,
                    () -> manager.load("counter.ores", SYNTAX_ERROR));
            assertSame(healthy, manager.active("counter.ores"));
            assertEquals(1, manager.liveGenerations());
            assertDoesNotThrow(healthy::start);
            assertFalse(healthy.closed());
        }
    }

    @Test
    void rejectedSourceCannotConsumeAGenerationId() {
        try (var manager = new HotReloadManager(IsolatePolicy.developer(), JIT)) {
            var first = manager.load("service.ores", NOOP);
            assertThrows(RuntimeException.class,
                    () -> manager.load("service.ores", SYNTAX_ERROR));
            var next = manager.load("service.ores", NOOP);
            assertEquals(first.id() + 1, next.id());
            assertEquals(2, manager.liveGenerations());
        }
    }

    @Test
    void failedStartupClosesCandidateButDoesNotKillOldVersion() throws Exception {
        try (var manager = new HotReloadManager(IsolatePolicy.developer(), JIT)) {
            var healthy = manager.load("service.ores", fixture("isolated-counter-v1.ores"));
            assertDoesNotThrow(healthy::start);
            var failed = manager.load("service.ores", FAILS_AT_START);
            assertThrows(RuntimeException.class, failed::start);
            assertTrue(failed.closed());
            assertFalse(healthy.closed());
            assertNull(manager.active("service.ores"));
        }
    }

    @Test
    void retirementOfOldActorDoesNotKillPromotedActorsContext() throws Exception {
        try (var manager = new HotReloadManager(IsolatePolicy.developer(), JIT)) {
            var old = manager.load("counter.ores", fixture("isolated-counter-v1.ores"));
            var newer = manager.load("counter.ores", fixture("isolated-counter-v2.ores"));
            assertDoesNotThrow(old::start);
            assertDoesNotThrow(newer::start);
            manager.retire(old.id());
            assertTrue(old.closed());
            assertFalse(newer.closed());
            assertSame(newer, manager.active("counter.ores"));
            assertEquals(1, manager.liveGenerations());
        }
    }

    @Test
    void failingOneUnitStartupDoesNotKillUnrelatedRunningActor() throws Exception {
        try (var manager = new HotReloadManager(IsolatePolicy.developer(), JIT)) {
            var actor = manager.load("other-actor.ores", fixture("isolated-counter-v1.ores"));
            assertDoesNotThrow(actor::start);
            var bad = manager.load("service.ores", FAILS_AT_START);
            assertThrows(RuntimeException.class, bad::start);
            assertSame(actor, manager.active("other-actor.ores"));
            assertFalse(actor.closed());
        }
    }

    @Test
    void reloadingIdenticalActorSourceAfterRetirementCreatesFreshContext() throws Exception {
        try (var manager = new HotReloadManager(IsolatePolicy.developer(), JIT)) {
            String source = fixture("isolated-counter-v1.ores");
            var original = manager.load("counter.ores", source);
            assertDoesNotThrow(original::start);
            manager.retire(original.id());
            var replacement = manager.load("counter.ores", source);
            assertTrue(original.closed());
            assertNotSame(original.context(), replacement.context());
            assertNotEquals(original.id(), replacement.id());
            assertEquals(original.sha256(), replacement.sha256());
            assertDoesNotThrow(replacement::start);
        }
    }

    @Test
    void twoSequentialMailboxTurnsExecuteOnIsolatedVersionOne() throws Exception {
        try (var manager = new HotReloadManager(IsolatePolicy.developer(), JIT)) {
            var generation = manager.load("counter.ores", fixture("two-ticks-v1.ores"));
            // The guest performs two round-trips and traps on bad output/order.
            assertDoesNotThrow(generation::start);
            assertTrue(generation.started());
        }
    }

    @Test
    void twoSequentialMailboxTurnsExecuteOnIsolatedVersionTwo() throws Exception {
        try (var manager = new HotReloadManager(IsolatePolicy.developer(), JIT)) {
            var generation = manager.load("counter.ores", fixture("two-ticks-v2.ores"));
            assertDoesNotThrow(generation::start);
            assertTrue(generation.started());
        }
    }

    @Test
    void oldAndNewTwoTurnActorProgramsRunBeforeOldGenerationRetires() throws Exception {
        try (var manager = new HotReloadManager(IsolatePolicy.developer(), JIT)) {
            var v1 = manager.load("counter.ores", fixture("two-ticks-v1.ores"));
            var v2 = manager.load("counter.ores", fixture("two-ticks-v2.ores"));
            assertNotSame(v1.context(), v2.context());
            assertNotEquals(v1.sha256(), v2.sha256());
            assertDoesNotThrow(v1::start);
            assertDoesNotThrow(v2::start);
            manager.retire(v1.id());
            assertTrue(v1.closed());
            assertFalse(v2.closed());
        }
    }

    @Test
    void rejectedThirdVersionCannotAffectTwoRunningVersions() throws Exception {
        try (var manager = new HotReloadManager(IsolatePolicy.developer(), JIT)) {
            var v1 = manager.load("counter.ores", fixture("isolated-counter-v1.ores"));
            var v2 = manager.load("counter.ores", fixture("isolated-counter-v2.ores"));
            assertDoesNotThrow(v1::start);
            assertDoesNotThrow(v2::start);
            assertThrows(RuntimeException.class,
                    () -> manager.load("counter.ores", SYNTAX_ERROR));
            assertSame(v2, manager.active("counter.ores"));
            assertFalse(v1.closed());
            assertFalse(v2.closed());
            assertEquals(2, manager.liveGenerations());
        }
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="polyglot.engine.IsolateLibrary", matches=".+")
    void compilationFailureDoesNotConsumeAdversarialGenerationQuota() {
        try (var manager = HotReloadManager.forUntrustedActors(
                IsolatePolicy.developer(), JIT)) {
            var first = manager.load("one.ores", NOOP);
            assertThrows(RuntimeException.class,
                    () -> manager.load("bad.ores", SYNTAX_ERROR));
            for (int i = 2; i <= 8; i++) {
                manager.load("worker-" + i + ".ores", NOOP);
            }
            assertEquals(8, manager.liveGenerations());
            assertThrows(IllegalStateException.class,
                    () -> manager.load("overflow.ores", NOOP));
            manager.retire(first.id());
            assertDoesNotThrow(() -> manager.load("recovered.ores", NOOP));
            assertEquals(8, manager.liveGenerations());
        }
    }

    @Test
    void interleavedCodeUnitUpgradesPreserveIndependentActivePointers() throws Exception {
        try (var manager = new HotReloadManager(IsolatePolicy.developer(), JIT)) {
            var a1 = manager.load("a.ores", fixture("isolated-counter-v1.ores"));
            var b1 = manager.load("b.ores", NOOP);
            var a2 = manager.load("a.ores", fixture("isolated-counter-v2.ores"));
            assertSame(a2, manager.active("a.ores"));
            assertSame(b1, manager.active("b.ores"));
            assertDoesNotThrow(a1::start);
            assertDoesNotThrow(b1::start);
            assertDoesNotThrow(a2::start);
            manager.retire(a1.id());
            assertSame(b1, manager.active("b.ores"));
            assertSame(a2, manager.active("a.ores"));
            assertFalse(b1.closed());
        }
    }

    private static String fixture(String name) throws Exception {
        try (InputStream input =
                HotLoadingUpgradeEdgeCasesTest.class.getResourceAsStream("/ores/" + name)) {
            assertNotNull(input, "fixture not packaged: " + name);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
