package dev.oreslang.hotreload;

import dev.oreslang.runtime.ExecutionProfile;
import dev.oreslang.runtime.HotReloadManager;
import dev.oreslang.runtime.IsolatePolicy;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real expected behavior tests that are currently RED, explicitly disabled.
 * Do not convert these into simulated passes or report them as passing.
 * Enable alongside the corresponding Oreslang runtime implementation.
 */
final class PendingRuntimeContractTest {
    private static final String GOOD = "pub routine main(): void { return; }";
    private static final String BAD = """
            pub routine main(): void {
              val values = arr[1];
              val failed = values[99];
              return;
            }
            """;

    @Test
    @Disabled("Not implemented: stage must not change active routing pointer; HotReloadManager.load currently does")
    void stagingNextVersionMustNotPromoteItBeforeHealthCheck() {
        try (var manager = new HotReloadManager(IsolatePolicy.developer(), ExecutionProfile.serverJit())) {
            var healthy = manager.loadAndStart("service.ores", GOOD);
            manager.load("service.ores", GOOD);
            assertSame(healthy, manager.active("service.ores"));
        }
    }

    @Test
    @Disabled("Not implemented: failed activation must atomically roll back active pointer and retire failure")
    void failedGuestStartMustPreserveEarlierHealthyActiveVersion() {
        try (var manager = new HotReloadManager(IsolatePolicy.developer(), ExecutionProfile.serverJit())) {
            var healthy = manager.loadAndStart("service.ores", GOOD);
            var bad = manager.load("service.ores", BAD);
            assertThrows(RuntimeException.class, bad::start);
            assertSame(healthy, manager.active("service.ores"));
            assertEquals(1, manager.liveGenerations());
        }
    }

    @Test
    void unsuccessfulLoadAndStartMustReleaseGenerationResources() {
        try (var manager = new HotReloadManager(IsolatePolicy.developer(), ExecutionProfile.serverJit())) {
            assertThrows(RuntimeException.class, () -> manager.loadAndStart("service.ores", BAD));
            assertEquals(0, manager.liveGenerations());
            assertNull(manager.active("service.ores"));
        }
    }
    @Test
    void hotLoadManagerMustRejectCapabilityEscalationAtConstruction() {
        var supervisor = IsolatePolicy.developer();
        var escalatedGuest = supervisor.withCapabilities(IsolatePolicy.Capability.NATIVE);
        assertThrows(SecurityException.class,
                () -> new HotReloadManager(supervisor, escalatedGuest, ExecutionProfile.serverJit()));
    }

    @Test
    void closedManagerMustRejectFutureGenerationLoads() {
        var manager = new HotReloadManager(IsolatePolicy.developer(), ExecutionProfile.serverJit());
        manager.close();
        assertThrows(IllegalStateException.class,
                () -> manager.load("post-close.ores", GOOD));
        assertEquals(0, manager.liveGenerations());
    }

    @Test
    void activeGenerationMustNeverPointToAClosedContext() {
        try (var manager = new HotReloadManager(IsolatePolicy.developer(), ExecutionProfile.serverJit())) {
            var generation = manager.loadAndStart("service.ores", GOOD);
            generation.close();
            assertNull(manager.active("service.ores"));
        }
    }

    @Test
    @Disabled("Not implemented: atomic activation must validate public ABI compatibility or demand an explicit typed adapter")
    void incompatiblePublicAbiMustNotReplaceServingVersion() {
        var compiler = new dev.oreslang.compiler.IncrementalCompiler();
        var v1 = compiler.compile(java.util.Map.of("service.ores",
                "pub routine main(): void { return; }")).units().get("service.ores");
        var v2 = compiler.compile(java.util.Map.of("service.ores",
                "pub routine main(): void { return; } pub routine added(): void { return; }"))
                .units().get("service.ores");
        assertNotEquals(v1.abiDigest(), v2.abiDigest());
        try (var manager = new HotReloadManager(IsolatePolicy.developer(), ExecutionProfile.serverJit())) {
            var healthy = manager.load(v1);
            assertThrows(IllegalStateException.class, () -> manager.load(v2));
            assertSame(healthy, manager.active("service.ores"));
        }
    }

}
